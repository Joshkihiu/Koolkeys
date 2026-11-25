package com.koolkeys.app.session;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.koolkeys.app.bluetooth.BluetoothManager;

public class SessionManager {
    public interface Listener {
        void onProgress(int completedCharacters, int totalCharacters, int currentLine,
                        long elapsedMillis, long durationMillis);
        void onStateChanged(String state);
        default void onComplete() { }
        default void onReviewReminder() { }
    }

    public interface LifecycleListener {
        void onComplete();
        void onReviewReminder();
        void onStopped();
    }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final BluetoothManager bluetooth;
    private Listener listener;
    private LifecycleListener lifecycleListener;
    private String text = "";
    private int position;
    private int currentLine = 1;
    private boolean running;
    private boolean paused;
    private long intervalMillis = 40;
    private long elapsedMillis;
    private long durationMillis;
    private long reviewIntervalMillis;
    private long reviewRemainingMillis;
    private long nextTickAt;
    private long nextReviewAt;
    private long tickRemainingMillis;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running || paused) return;
            if (!bluetooth.isConnected()) {
                stop();
                notifyState("Connection lost. Insertion stopped safely.");
                return;
            }
            if (position >= text.length()) {
                complete();
                return;
            }

            char character = text.charAt(position++);
            boolean sent;
            if (character == '\r') {
                if (position < text.length() && text.charAt(position) == '\n') position++;
                sent = bluetooth.sendEnter();
                currentLine++;
            } else if (character == '\n') {
                sent = bluetooth.sendEnter();
                currentLine++;
            } else if (character == '\t') {
                sent = bluetooth.sendTab();
            } else if (character == '\b') {
                sent = bluetooth.sendBackspace();
            } else {
                sent = bluetooth.sendCharacter(character);
            }

            if (!sent) {
                stop();
                notifyState("Input could not be sent. Check the connection and keyboard layout.");
                return;
            }
            elapsedMillis = Math.min(durationMillis, elapsedMillis + intervalMillis);
            notifyProgress();
            if (position >= text.length()) {
                complete();
            } else {
                scheduleNext(intervalMillis);
            }
        }
    };

    private final Runnable reviewTick = new Runnable() {
        @Override public void run() {
            if (!running || paused || reviewIntervalMillis <= 0) return;
            if (listener != null) listener.onReviewReminder();
            if (lifecycleListener != null) lifecycleListener.onReviewReminder();
            reviewRemainingMillis = reviewIntervalMillis;
            nextReviewAt = SystemClock.elapsedRealtime() + reviewIntervalMillis;
            handler.postDelayed(this, reviewIntervalMillis);
        }
    };

    public SessionManager(BluetoothManager bluetooth) {
        this.bluetooth = bluetooth;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener == null) return;
        listener.onStateChanged(getState());
        notifyProgress();
    }

    public void setLifecycleListener(LifecycleListener listener) {
        lifecycleListener = listener;
    }

    public void configure(String text, long durationMillis, long reviewIntervalMillis, boolean extendedMode) {
        stopCallbacks();
        this.text = text == null ? "" : text;
        this.position = 0;
        this.currentLine = 1;
        this.elapsedMillis = 0;
        this.durationMillis = Math.max(1, durationMillis);
        this.reviewIntervalMillis = Math.max(0, reviewIntervalMillis);
        this.reviewRemainingMillis = this.reviewIntervalMillis;
        int actionCount = countActions(this.text);
        this.intervalMillis = extendedMode && actionCount > 0
            ? Math.max(1, this.durationMillis / actionCount)
            : 40;
        this.tickRemainingMillis = this.intervalMillis;
        this.running = false;
        this.paused = false;
    }

    public void start() {
        if (running) return;
        if (text.isEmpty()) {
            notifyState("No code to insert");
            return;
        }
        if (!bluetooth.isConnected()) {
            notifyState("Connect to a Windows host before starting insertion");
            return;
        }
        String validationError = bluetooth.validateText(text);
        if (validationError != null) {
            notifyState(validationError);
            return;
        }
        running = true;
        paused = false;
        notifyState("Automated insertion running");
        scheduleNext(tickRemainingMillis);
        scheduleReviewReminder();
    }

    public void pause() {
        if (!running || paused) return;
        paused = true;
        long now = SystemClock.elapsedRealtime();
        tickRemainingMillis = Math.max(1, nextTickAt - now);
        handler.removeCallbacks(tick);
        if (reviewIntervalMillis > 0) {
            reviewRemainingMillis = Math.max(0, nextReviewAt - now);
            handler.removeCallbacks(reviewTick);
        }
        notifyState("Paused");
    }

    public void resume() {
        if (!running || !paused) return;
        paused = false;
        notifyState("Automated insertion running");
        scheduleNext(tickRemainingMillis);
        if (reviewIntervalMillis > 0) {
            handler.postDelayed(reviewTick, Math.max(1, reviewRemainingMillis));
        }
    }

    public void stop() {
        running = false;
        paused = false;
        stopCallbacks();
        notifyState("Stopped");
        if (lifecycleListener != null) lifecycleListener.onStopped();
    }

    public boolean isRunning() { return running; }
    public boolean isPaused() { return paused; }
    public long getElapsedMillis() { return elapsedMillis; }
    public long getDurationMillis() { return durationMillis; }

    private void complete() {
        running = false;
        paused = false;
        stopCallbacks();
        elapsedMillis = durationMillis;
        notifyProgress();
        notifyState("Completed");
        if (listener != null) listener.onComplete();
        if (lifecycleListener != null) lifecycleListener.onComplete();
    }

    private void scheduleNext(long delayMillis) {
        tickRemainingMillis = delayMillis;
        nextTickAt = SystemClock.elapsedRealtime() + delayMillis;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, delayMillis);
    }

    private void scheduleReviewReminder() {
        if (reviewIntervalMillis <= 0) return;
        reviewRemainingMillis = reviewIntervalMillis;
        nextReviewAt = SystemClock.elapsedRealtime() + reviewIntervalMillis;
        handler.removeCallbacks(reviewTick);
        handler.postDelayed(reviewTick, reviewIntervalMillis);
    }

    private void stopCallbacks() {
        handler.removeCallbacks(tick);
        handler.removeCallbacks(reviewTick);
    }

    private String getState() {
        if (running && paused) return "Paused";
        if (running) return "Automated insertion running";
        if (position >= text.length() && !text.isEmpty()) return "Completed";
        return "Ready";
    }

    private void notifyState(String state) {
        if (listener != null) listener.onStateChanged(state);
    }

    private void notifyProgress() {
        if (listener != null) {
            listener.onProgress(position, text.length(), currentLine, elapsedMillis, durationMillis);
        }
    }

    private int countActions(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\r' && i + 1 < value.length() && value.charAt(i + 1) == '\n') i++;
            count++;
        }
        return count;
    }
}
