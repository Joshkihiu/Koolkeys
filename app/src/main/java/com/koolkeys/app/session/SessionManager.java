package com.koolkeys.app.session;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;

import com.koolkeys.app.bluetooth.BluetoothManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SessionManager {
    private static final String STATE_LOCAL_SENT =
        "Accepted by Android Bluetooth; Windows receipt is unconfirmed";
    private static final long MIN_PROGRESS_UPDATE_INTERVAL_MILLIS = 16;

    public interface Listener {
        void onProgress(int completedCharacters, int totalCharacters, int currentLine,
                        long elapsedMillis, long remainingMillis,
                        SessionProgressTracker.Snapshot progressSnapshot);
        void onStateChanged(String state);
        default void onComplete() { }
        default void onReviewReminder() { }
    }

    public interface LifecycleListener {
        void onComplete();
        void onReviewReminder();
        void onStopped();
    }

    private final Object stateLock = new Object();
    private final Object inputDispatchLock = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final HandlerThread inputThread;
    private final Handler inputHandler;
    private final BluetoothManager bluetooth;
    private final SessionProgressTracker progressTracker = new SessionProgressTracker();
    private volatile Listener listener;
    private volatile LifecycleListener lifecycleListener;
    private String text = "";
    private volatile List<TypingAction> actions = Collections.emptyList();
    private volatile int position;
    private volatile int actionPosition;
    private volatile int currentLine = 1;
    private volatile boolean running;
    private volatile boolean paused;
    private volatile boolean pauseRequested;
    private volatile String terminalState = "Ready";
    private volatile long elapsedMillis;
    private volatile long elapsedAtPause;
    private volatile long sessionStartedAt;
    private volatile long durationMillis;
    private long targetDurationMillis;
    private long reviewIntervalMillis;
    private long reviewRemainingMillis;
    private volatile long nextTickAt;
    private long nextReviewAt;
    private long tickRemainingMillis;
    private volatile long stateGeneration;
    private volatile long sessionGeneration;
    private volatile long lastProgressNotificationAt;
    private volatile long averageActionMillis = 1;
    private volatile int wordsPerMinute = TypingTiming.DEFAULT_WORDS_PER_MINUTE;

    private final Runnable progressTick = new Runnable() {
        @Override public void run() {
            synchronized (stateLock) {
                if (!running || paused || pauseRequested) return;
                updateElapsed();
            }
            notifyProgress();
            if (running && !paused) mainHandler.postDelayed(this, 1000);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long tickGeneration;
            long tickSession;
            String sendError = null;
            boolean completeSession = false;
            boolean notifyProgress = false;
            synchronized (inputDispatchLock) {
                synchronized (stateLock) {
                    if (!running || paused || pauseRequested) return;
                    tickRemainingMillis = 0;
                }
                tickGeneration = stateGeneration;
                tickSession = sessionGeneration;
                List<TypingAction> sessionActions = actions;
                int actionIndex = actionPosition;
                if (actionIndex >= sessionActions.size()) {
                    completeSession = true;
                } else if (!bluetooth.isConnected()) {
                    progressTracker.disconnect(SystemClock.elapsedRealtimeNanos());
                    sendError = "Connection lost. Insertion stopped safely.";
                } else {
                    TypingAction action = sessionActions.get(actionIndex);
                    SessionProgressTracker.ActionTicket ticket = progressTracker.beginAction(
                        position, action.positionAfter, action.type.name(),
                        SystemClock.elapsedRealtimeNanos());
                    if (ticket == null) return;
                    postProgress();
                    long sendStartedAt = SystemClock.elapsedRealtimeNanos();
                    boolean sent = sendAction(action);
                    long actionDurationNanos = Math.max(0,
                        SystemClock.elapsedRealtimeNanos() - sendStartedAt);
                    if (!sent) {
                        progressTracker.fail(ticket, SystemClock.elapsedRealtimeNanos());
                        sendError = "Input could not be sent. Check the connection and keyboard layout.";
                    } else {
                        synchronized (stateLock) {
                            if (tickSession != sessionGeneration) return;
                            if (!progressTracker.submit(ticket, action.positionAfter,
                                actionDurationNanos, SystemClock.elapsedRealtimeNanos())) return;

                            long actionDurationMillis = Math.max(1,
                                (actionDurationNanos + 999_999) / 1_000_000);
                            averageActionMillis = (averageActionMillis * 7 + actionDurationMillis) / 8;
                            actionPosition = actionIndex + 1;
                            position = action.positionAfter;
                            if (action.type == TypingAction.Type.ENTER) currentLine++;
                            updateElapsed();
                            long delayUntilNext = actionPosition >= sessionActions.size()
                                ? 0
                                : TypingTiming.delayAfterActionMillis(
                                    actionDurationMillis, wordsPerMinute);
                            durationMillis = TypingTiming.estimateRemainingMillis(
                                actions, actionPosition, averageActionMillis, delayUntilNext,
                                wordsPerMinute);

                            if (actionPosition >= sessionActions.size()) {
                                completeSession = true;
                            } else if (running && !paused && !pauseRequested
                                && tickGeneration == stateGeneration) {
                                scheduleNextLocked(delayUntilNext);
                            }
                            notifyProgress = action.type == TypingAction.Type.ENTER
                                || !running || paused || pauseRequested
                                || tickGeneration != stateGeneration
                                || shouldNotifyProgress();
                        }
                    }
                }
            }

            if (sendError != null) postStopWithError(tickSession, tickGeneration, sendError);
            else if (completeSession) postComplete(tickSession, tickGeneration);
            else if (notifyProgress) postProgress();
        }
    };

    private final Runnable reviewTick = new Runnable() {
        @Override public void run() {
            if (!running || paused || reviewIntervalMillis <= 0) return;
            Listener currentListener = listener;
            LifecycleListener currentLifecycleListener = lifecycleListener;
            if (currentListener != null) currentListener.onReviewReminder();
            if (currentLifecycleListener != null) currentLifecycleListener.onReviewReminder();
            reviewRemainingMillis = reviewIntervalMillis;
            nextReviewAt = SystemClock.elapsedRealtime() + reviewIntervalMillis;
            mainHandler.postDelayed(this, reviewIntervalMillis);
        }
    };

    public SessionManager(BluetoothManager bluetooth) {
        this.bluetooth = bluetooth;
        inputThread = new HandlerThread("KoolKeysInsertion");
        inputThread.start();
        inputHandler = new Handler(inputThread.getLooper());
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
        configure(text, durationMillis, reviewIntervalMillis, extendedMode,
            TypingTiming.DEFAULT_WORDS_PER_MINUTE);
    }

    public void configure(String text, long durationMillis, long reviewIntervalMillis,
                          boolean extendedMode, int wordsPerMinute) {
        synchronized (inputDispatchLock) {
            synchronized (stateLock) {
                stateGeneration++;
                sessionGeneration++;
                stopCallbacks();
                running = false;
                paused = false;
                pauseRequested = false;
                terminalState = "Ready";
                targetDurationMillis = extendedMode ? Math.max(0, durationMillis) : 0;
                this.text = text == null ? "" : text;
                actions = buildSessionActions(this.text);
                progressTracker.configure(actions.size(), SystemClock.elapsedRealtimeNanos());
                position = 0;
                actionPosition = 0;
                currentLine = 1;
                elapsedMillis = 0;
                elapsedAtPause = 0;
                averageActionMillis = 1;
                this.wordsPerMinute = TypingTiming.clampWordsPerMinute(wordsPerMinute);
                this.reviewIntervalMillis = Math.max(0, reviewIntervalMillis);
                this.reviewRemainingMillis = this.reviewIntervalMillis;
                this.durationMillis = TypingTiming.estimateRemainingMillis(
                    actions, 0, averageActionMillis, 0, this.wordsPerMinute);
            }
        }
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

        synchronized (inputDispatchLock) {
            synchronized (stateLock) {
                if (running) return;
                if (!progressTracker.start(SystemClock.elapsedRealtimeNanos())) return;
                sessionGeneration++;
                stateGeneration++;
                running = true;
                paused = false;
                pauseRequested = false;
                terminalState = "Automated insertion running";
                elapsedMillis = 0;
                elapsedAtPause = 0;
                sessionStartedAt = SystemClock.elapsedRealtime();
                scheduleNextLocked();
                scheduleProgressTick();
                scheduleReviewReminder();
            }
        }
        notifyState("Automated insertion running");
        notifyProgress();
    }

    public void pause() {
        long responseStartedAt = SystemClock.elapsedRealtimeNanos();
        synchronized (stateLock) {
            if (!running || paused || pauseRequested) return;
            pauseRequested = true;
        }
        synchronized (inputDispatchLock) {
            synchronized (stateLock) {
                if (!running || paused) {
                    pauseRequested = false;
                    return;
                }
                long nowNanos = SystemClock.elapsedRealtimeNanos();
                if (!progressTracker.pause(nowNanos, nowNanos - responseStartedAt)) {
                    pauseRequested = false;
                    return;
                }
                paused = true;
                pauseRequested = false;
                stateGeneration++;
                long now = SystemClock.elapsedRealtime();
                tickRemainingMillis = Math.max(0, nextTickAt - now);
                elapsedAtPause = getElapsedNow(now);
                elapsedMillis = elapsedAtPause;
                stopInputCallback();
                mainHandler.removeCallbacks(progressTick);
                if (reviewIntervalMillis > 0) {
                    reviewRemainingMillis = Math.max(0, nextReviewAt - now);
                    mainHandler.removeCallbacks(reviewTick);
                }
            }
        }
        notifyProgress();
        notifyState("Paused");
    }

    public void resume() {
        long responseStartedAt = SystemClock.elapsedRealtimeNanos();
        synchronized (inputDispatchLock) {
            synchronized (stateLock) {
                if (!running || !paused) return;
                long nowNanos = SystemClock.elapsedRealtimeNanos();
                if (!progressTracker.resume(nowNanos, nowNanos - responseStartedAt)) return;
                paused = false;
                pauseRequested = false;
                stateGeneration++;
                elapsedAtPause = elapsedMillis;
                sessionStartedAt = SystemClock.elapsedRealtime();
                scheduleNextLocked(tickRemainingMillis);
                scheduleProgressTick();
                if (reviewIntervalMillis > 0) {
                    mainHandler.postDelayed(reviewTick, Math.max(1, reviewRemainingMillis));
                    nextReviewAt = SystemClock.elapsedRealtime() + Math.max(1, reviewRemainingMillis);
                }
            }
        }
        notifyState("Automated insertion running");
        notifyProgress();
    }

    public void stop() {
        long responseStartedAt = SystemClock.elapsedRealtimeNanos();
        synchronized (inputDispatchLock) {
            synchronized (stateLock) {
                long nowNanos = SystemClock.elapsedRealtimeNanos();
                progressTracker.cancel(nowNanos, nowNanos - responseStartedAt);
                stateGeneration++;
                if (running && !paused) updateElapsed();
                running = false;
                paused = false;
                pauseRequested = false;
                terminalState = "Stopped";
                stopCallbacks();
                tickRemainingMillis = 0;
            }
        }
        notifyProgress();
        notifyState("Stopped");
        LifecycleListener currentLifecycleListener = lifecycleListener;
        if (currentLifecycleListener != null) currentLifecycleListener.onStopped();
    }

    public void shutdown() {
        synchronized (inputDispatchLock) {
            synchronized (stateLock) {
                progressTracker.cancel(SystemClock.elapsedRealtimeNanos(), 0);
                running = false;
                paused = false;
                pauseRequested = false;
                stateGeneration++;
                sessionGeneration++;
                stopCallbacks();
            }
        }
        inputThread.quitSafely();
    }

    public boolean isRunning() { return running; }
    public boolean isPaused() { return paused; }
    public long getElapsedMillis() { return elapsedMillis; }
    public long getDurationMillis() { return durationMillis; }
    public long getTargetDurationMillis() { return targetDurationMillis; }
    public int getWordsPerMinute() { return wordsPerMinute; }
    public String getText() { return text; }
    public SessionProgressTracker.Snapshot getProgressSnapshot() {
        return progressTracker.snapshot(SystemClock.elapsedRealtimeNanos());
    }

    public void recordBluetoothCallback(String callbackName, boolean connected) {
        progressTracker.recordBluetoothCallback(callbackName, SystemClock.elapsedRealtimeNanos());
        if (!connected) inputHandler.post(this::stopAfterDisconnect);
    }

    static List<TypingAction> buildSessionActions(String text) {
        List<TypingAction> sourceActions = TypingAction.build(text);
        if (sourceActions.isEmpty()) return Collections.emptyList();
        ArrayList<TypingAction> sessionActions = new ArrayList<>(
            sourceActions.size() + countEnterActions(sourceActions));
        int position = 0;
        for (TypingAction action : sourceActions) {
            if (action.type == TypingAction.Type.ENTER) {
                sessionActions.add(TypingAction.escape(position));
            }
            sessionActions.add(action);
            position = action.positionAfter;
        }
        return sessionActions;
    }

    private void complete() {
        String completedState;
        synchronized (stateLock) {
            if (!running || paused || actionPosition < actions.size()) return;
            if (!progressTracker.completeLocally(SystemClock.elapsedRealtimeNanos())) return;
            stateGeneration++;
            running = false;
            paused = false;
            terminalState = STATE_LOCAL_SENT;
            stopCallbacks();
            elapsedMillis = getElapsedNow(SystemClock.elapsedRealtime());
            completedState = terminalState;
        }
        notifyProgress();
        notifyState(completedState);
        Listener currentListener = listener;
        LifecycleListener currentLifecycleListener = lifecycleListener;
        if (currentListener != null) currentListener.onComplete();
        if (currentLifecycleListener != null) currentLifecycleListener.onComplete();
    }

    private void postComplete(long expectedSession, long expectedGeneration) {
        mainHandler.post(() -> {
            synchronized (stateLock) {
                if (sessionGeneration != expectedSession
                    || stateGeneration != expectedGeneration
                    || !running || paused || actionPosition < actions.size()) return;
            }
            complete();
        });
    }

    private void postStopWithError(long expectedSession, long expectedGeneration, String error) {
        mainHandler.post(() -> {
            synchronized (stateLock) {
                if (sessionGeneration != expectedSession
                    || stateGeneration != expectedGeneration || !running) return;
            }
            stop();
            notifyState(error);
        });
    }

    private boolean sendAction(TypingAction action) {
        switch (action.type) {
            case ESCAPE: return bluetooth.sendEscape();
            case ENTER: return bluetooth.sendEnter();
            case TAB: return bluetooth.sendTab();
            case BACKSPACE: return bluetooth.sendBackspace();
            case CHARACTER: return bluetooth.sendCharacter(action.character);
            default: throw new IllegalStateException("Unsupported typing action: " + action.type);
        }
    }

    private long estimateRemainingMillis() {
        if ("Stopped".equals(terminalState) || STATE_LOCAL_SENT.equals(terminalState)) return 0;
        long delayUntilNext = 0;
        if (running) {
            delayUntilNext = Math.max(0, nextTickAt - SystemClock.elapsedRealtime());
        } else if (paused) {
            delayUntilNext = tickRemainingMillis;
        }
        return TypingTiming.estimateRemainingMillis(
            actions, actionPosition, averageActionMillis, delayUntilNext, wordsPerMinute);
    }

    private static int countEnterActions(List<TypingAction> actions) {
        int count = 0;
        for (TypingAction action : actions) {
            if (action.type == TypingAction.Type.ENTER) count++;
        }
        return count;
    }

    private void scheduleNextLocked() {
        scheduleNextLocked(0);
    }

    private void scheduleNextLocked(long delayMillis) {
        tickRemainingMillis = Math.max(0, delayMillis);
        nextTickAt = SystemClock.elapsedRealtime() + tickRemainingMillis;
        inputHandler.removeCallbacks(tick);
        inputHandler.postDelayed(tick, tickRemainingMillis);
    }

    private void scheduleReviewReminder() {
        if (reviewIntervalMillis <= 0) return;
        reviewRemainingMillis = reviewIntervalMillis;
        nextReviewAt = SystemClock.elapsedRealtime() + reviewIntervalMillis;
        mainHandler.removeCallbacks(reviewTick);
        mainHandler.postDelayed(reviewTick, reviewIntervalMillis);
    }

    private void scheduleProgressTick() {
        mainHandler.removeCallbacks(progressTick);
        mainHandler.postDelayed(progressTick, 1000);
    }

    private void stopInputCallback() {
        inputHandler.removeCallbacks(tick);
    }

    private void stopCallbacks() {
        stopInputCallback();
        mainHandler.removeCallbacks(reviewTick);
        mainHandler.removeCallbacks(progressTick);
    }

    private void updateElapsed() {
        elapsedMillis = paused
            ? elapsedAtPause
            : getElapsedNow(SystemClock.elapsedRealtime());
    }

    private long getElapsedNow(long now) {
        return elapsedAtPause + Math.max(0, now - sessionStartedAt);
    }

    private void notifyState(String state) {
        Listener currentListener = listener;
        if (currentListener != null) currentListener.onStateChanged(state);
    }

    private void notifyProgress() {
        Listener currentListener = listener;
        int completed;
        int total;
        int line;
        long elapsed;
        long remaining;
        SessionProgressTracker.Snapshot progressSnapshot;
        synchronized (stateLock) {
            lastProgressNotificationAt = SystemClock.elapsedRealtime();
            completed = position;
            total = text.length();
            line = currentLine;
            elapsed = elapsedMillis;
            remaining = estimateRemainingMillis();
            progressSnapshot = progressTracker.snapshot(SystemClock.elapsedRealtimeNanos());
        }
        if (currentListener != null) currentListener.onProgress(
            completed, total, line, elapsed, remaining, progressSnapshot);
    }

    private void postProgress() {
        long expectedSession = sessionGeneration;
        long expectedState = stateGeneration;
        long expectedRevision = progressTracker.snapshot(
            SystemClock.elapsedRealtimeNanos()).getRevision();
        mainHandler.post(() -> {
            if (sessionGeneration != expectedSession || stateGeneration != expectedState) return;
            if (progressTracker.snapshot(SystemClock.elapsedRealtimeNanos()).getRevision()
                != expectedRevision) return;
            notifyProgress();
        });
    }

    private boolean shouldNotifyProgress() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastProgressNotificationAt < MIN_PROGRESS_UPDATE_INTERVAL_MILLIS) return false;
        lastProgressNotificationAt = now;
        return true;
    }

    private String getState() {
        if (running && paused) return "Paused";
        if (running) return terminalState;
        return terminalState;
    }

    private void stopAfterDisconnect() {
        synchronized (inputDispatchLock) {
            synchronized (stateLock) {
                if (!running || progressTracker.snapshot(
                    SystemClock.elapsedRealtimeNanos()).getState()
                    == SessionProgressTracker.State.LOCALLY_SUBMITTED) return;
                progressTracker.disconnect(SystemClock.elapsedRealtimeNanos());
                stateGeneration++;
                if (!paused) updateElapsed();
                running = false;
                paused = false;
                terminalState = "Connection lost. Insertion stopped safely.";
                stopCallbacks();
                tickRemainingMillis = 0;
            }
        }
        notifyProgress();
        notifyState(terminalState);
        LifecycleListener currentLifecycleListener = lifecycleListener;
        if (currentLifecycleListener != null) currentLifecycleListener.onStopped();
    }
}
