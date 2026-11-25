package com.koolkeys.app.session;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.koolkeys.app.bluetooth.BluetoothManager;

import java.util.Collections;
import java.util.List;
import java.util.Random;

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
    private final Random random = new Random();
    private Listener listener;
    private LifecycleListener lifecycleListener;
    private String text = "";
    private List<TypingAction> actions = Collections.emptyList();
    private int position;
    private int actionPosition;
    private int currentLine = 1;
    private boolean running;
    private boolean paused;
    private long intervalMillis = 40;
    private long elapsedMillis;
    private long elapsedAtPause;
    private long sessionStartedAt;
    private long durationMillis;
    private long reviewIntervalMillis;
    private long reviewRemainingMillis;
    private long nextTickAt;
    private long nextReviewAt;
    private long tickRemainingMillis;
    private long stateGeneration;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!running || paused || actionPosition >= actions.size()) {
                if (running && !paused && actionPosition >= actions.size()) complete();
                return;
            }
            long tickGeneration = stateGeneration;
            if (!bluetooth.isConnected()) {
                stop();
                notifyState("Connection lost. Insertion stopped safely.");
                return;
            }

            TypingAction action = actions.get(actionPosition++);
            position = action.positionAfter;
            boolean sent;
            if (action.type == TypingAction.Type.ENTER) {
                sent = bluetooth.sendEnter();
                currentLine++;
            } else if (action.type == TypingAction.Type.TAB) {
                sent = bluetooth.sendTab();
            } else if (action.type == TypingAction.Type.BACKSPACE) {
                sent = bluetooth.sendBackspace();
            } else {
                sent = bluetooth.sendCharacter(action.character);
            }

            if (!sent) {
                stop();
                notifyState("Input could not be sent. Check the connection and keyboard layout.");
                return;
            }
            updateElapsed();
            notifyProgress();
            if (actionPosition >= actions.size()) {
                complete();
            } else if (tickGeneration == stateGeneration) {
                scheduleNext(nextRandomDelay());
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
        stateGeneration++;
        stopCallbacks();
        running = false;
        paused = false;
        this.text = text == null ? "" : text;
        this.actions = TypingAction.build(this.text);
        this.position = 0;
        this.actionPosition = 0;
        this.currentLine = 1;
        this.elapsedMillis = 0;
        this.elapsedAtPause = 0;
        this.durationMillis = Math.max(1, durationMillis);
        this.reviewIntervalMillis = Math.max(0, reviewIntervalMillis);
        this.reviewRemainingMillis = this.reviewIntervalMillis;
        int actionCount = actions.size();
        intervalMillis = extendedMode && actionCount > 0
            ? Math.max(8, this.durationMillis / actionCount)
            : 40;
        tickRemainingMillis = nextRandomDelay();
        notifyProgress();
        notifyState(getState());
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
        elapsedMillis = 0;
        elapsedAtPause = 0;
        sessionStartedAt = SystemClock.elapsedRealtime();
        notifyState("Automated insertion running");
        scheduleNext(nextRandomDelay());
        scheduleReviewReminder();
    }

    public void pause() {
        if (!running || paused) return;
        paused = true;
        long now = SystemClock.elapsedRealtime();
        tickRemainingMillis = Math.max(1, nextTickAt - now);
        elapsedAtPause = getElapsedNow(now);
        elapsedMillis = elapsedAtPause;
        handler.removeCallbacks(tick);
        if (reviewIntervalMillis > 0) {
            reviewRemainingMillis = Math.max(0, nextReviewAt - now);
            handler.removeCallbacks(reviewTick);
        }
        notifyProgress();
        notifyState("Paused");
    }

    public void resume() {
        if (!running || !paused) return;
        paused = false;
        elapsedAtPause = elapsedMillis;
        sessionStartedAt = SystemClock.elapsedRealtime();
        notifyState("Automated insertion running");
        scheduleNext(tickRemainingMillis);
        if (reviewIntervalMillis > 0) {
            handler.postDelayed(reviewTick, Math.max(1, reviewRemainingMillis));
            nextReviewAt = SystemClock.elapsedRealtime() + Math.max(1, reviewRemainingMillis);
        }
    }

    public void stop() {
        stateGeneration++;
        running = false;
        paused = false;
        stopCallbacks();
        position = 0;
        actionPosition = 0;
        elapsedMillis = 0;
        elapsedAtPause = 0;
        currentLine = 1;
        tickRemainingMillis = intervalMillis;
        notifyProgress();
        notifyState("Stopped");
        if (lifecycleListener != null) lifecycleListener.onStopped();
    }

    public boolean isRunning() { return running; }
    public boolean isPaused() { return paused; }
    public long getElapsedMillis() { return elapsedMillis; }
    public long getDurationMillis() { return durationMillis; }

    private void complete() {
        stateGeneration++;
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
        tickRemainingMillis = Math.max(1, delayMillis);
        nextTickAt = SystemClock.elapsedRealtime() + tickRemainingMillis;
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, tickRemainingMillis);
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

    private long nextRandomDelay() {
        long minimum = Math.max(8, (intervalMillis * 70L) / 100L);
        long maximum = Math.min(5000, Math.max(minimum + 1,
            (intervalMillis * 130L) / 100L));
        if (maximum <= minimum) return minimum;
        return minimum + random.nextInt((int) (maximum - minimum));
    }

    private void updateElapsed() {
        elapsedMillis = getElapsedNow(SystemClock.elapsedRealtime());
    }

    private long getElapsedNow(long now) {
        long runningElapsed = elapsedAtPause + Math.max(0, now - sessionStartedAt);
        return Math.min(durationMillis, runningElapsed);
    }

    private void notifyState(String state) {
        if (listener != null) listener.onStateChanged(state);
    }

    private void notifyProgress() {
        if (listener != null) {
            listener.onProgress(position, text.length(), currentLine, elapsedMillis, durationMillis);
        }
    }

    private String getState() {
        if (running && paused) return "Paused";
        if (running) return "Automated insertion running";
        if (position >= text.length() && !text.isEmpty()) return "Completed";
        return "Ready";
    }
}
