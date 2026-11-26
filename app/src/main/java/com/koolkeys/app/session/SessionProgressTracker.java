package com.koolkeys.app.session;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SessionProgressTracker {
    private static final int ACTION_HISTORY_LIMIT = 256;

    public enum State {
        READY,
        RUNNING,
        PAUSED,
        STOPPED,
        DISCONNECTED,
        LOCALLY_SUBMITTED
    }

    public static final class ActionTicket {
        private final long generation;
        private final int actionIndex;

        private ActionTicket(long generation, int actionIndex) {
            this.generation = generation;
            this.actionIndex = actionIndex;
        }

        public int getActionIndex() {
            return actionIndex;
        }
    }

    public static final class ActionTiming {
        private final int actionIndex;
        private final long durationNanos;

        private ActionTiming(int actionIndex, long durationNanos) {
            this.actionIndex = actionIndex;
            this.durationNanos = durationNanos;
        }

        public int getActionIndex() {
            return actionIndex;
        }

        public long getDurationNanos() {
            return durationNanos;
        }
    }

    public static final class Snapshot {
        private final long generation;
        private final long revision;
        private final State state;
        private final int totalActions;
        private final int submittedActions;
        private final int inFlightActionIndex;
        private final int sourcePosition;
        private final int inFlightSourceStart;
        private final int inFlightSourceEnd;
        private final String inFlightActionName;
        private final long lastActionNanos;
        private final long averageActionNanos;
        private final long maxActionNanos;
        private final long lastActionStartGapNanos;
        private final List<ActionTiming> recentActionTimings;
        private final long lastStateChangeGapNanos;
        private final long lastBluetoothCallbackGapNanos;
        private final String lastBluetoothCallback;
        private final int bluetoothCallbackCount;
        private final long lastPauseResponseNanos;
        private final long lastResumeResponseNanos;
        private final long lastCancellationResponseNanos;
        private final long sessionDurationNanos;
        private final boolean hostReceiptConfirmed;

        private Snapshot(long generation, long revision, State state,
                         int totalActions, int submittedActions,
                         int inFlightActionIndex, int sourcePosition, int inFlightSourceStart,
                         int inFlightSourceEnd, String inFlightActionName, long lastActionNanos,
                         long averageActionNanos, long maxActionNanos,
                         long lastActionStartGapNanos,
                         List<ActionTiming> recentActionTimings, long lastStateChangeGapNanos,
                         long lastBluetoothCallbackGapNanos, String lastBluetoothCallback,
                         int bluetoothCallbackCount, long lastPauseResponseNanos,
                         long lastResumeResponseNanos, long lastCancellationResponseNanos,
                         long sessionDurationNanos) {
            this.generation = generation;
            this.revision = revision;
            this.state = state;
            this.totalActions = totalActions;
            this.submittedActions = submittedActions;
            this.inFlightActionIndex = inFlightActionIndex;
            this.sourcePosition = sourcePosition;
            this.inFlightSourceStart = inFlightSourceStart;
            this.inFlightSourceEnd = inFlightSourceEnd;
            this.inFlightActionName = inFlightActionName;
            this.lastActionNanos = lastActionNanos;
            this.averageActionNanos = averageActionNanos;
            this.maxActionNanos = maxActionNanos;
            this.lastActionStartGapNanos = lastActionStartGapNanos;
            this.recentActionTimings = Collections.unmodifiableList(recentActionTimings);
            this.lastStateChangeGapNanos = lastStateChangeGapNanos;
            this.lastBluetoothCallbackGapNanos = lastBluetoothCallbackGapNanos;
            this.lastBluetoothCallback = lastBluetoothCallback;
            this.bluetoothCallbackCount = bluetoothCallbackCount;
            this.lastPauseResponseNanos = lastPauseResponseNanos;
            this.lastResumeResponseNanos = lastResumeResponseNanos;
            this.lastCancellationResponseNanos = lastCancellationResponseNanos;
            this.sessionDurationNanos = sessionDurationNanos;
            this.hostReceiptConfirmed = false;
        }

        public long getGeneration() { return generation; }
        public long getRevision() { return revision; }
        public State getState() { return state; }
        public int getTotalActions() { return totalActions; }
        public int getSubmittedActions() { return submittedActions; }
        public int getUnsubmittedActions() {
            return totalActions - submittedActions - (inFlightActionIndex >= 0 ? 1 : 0);
        }
        public int getInFlightActionIndex() { return inFlightActionIndex; }
        public int getSourcePosition() { return sourcePosition; }
        public int getInFlightSourceStart() { return inFlightSourceStart; }
        public int getInFlightSourceEnd() { return inFlightSourceEnd; }
        public String getInFlightActionName() { return inFlightActionName; }
        public long getLastActionNanos() { return lastActionNanos; }
        public long getAverageActionNanos() { return averageActionNanos; }
        public long getMaxActionNanos() { return maxActionNanos; }
        public long getLastActionStartGapNanos() { return lastActionStartGapNanos; }
        public List<ActionTiming> getRecentActionTimings() { return recentActionTimings; }
        public long getLastStateChangeGapNanos() { return lastStateChangeGapNanos; }
        public long getLastBluetoothCallbackGapNanos() { return lastBluetoothCallbackGapNanos; }
        public String getLastBluetoothCallback() { return lastBluetoothCallback; }
        public int getBluetoothCallbackCount() { return bluetoothCallbackCount; }
        public long getLastPauseResponseNanos() { return lastPauseResponseNanos; }
        public long getLastResumeResponseNanos() { return lastResumeResponseNanos; }
        public long getLastCancellationResponseNanos() { return lastCancellationResponseNanos; }
        public long getSessionDurationNanos() { return sessionDurationNanos; }
        public boolean isHostReceiptConfirmed() { return hostReceiptConfirmed; }
        public boolean canCompleteLocally() {
            return state == State.RUNNING && submittedActions == totalActions
                && inFlightActionIndex < 0;
        }
    }

    private final ArrayDeque<ActionTiming> actionHistory = new ArrayDeque<>();
    private long generation;
    private long revision;
    private State state = State.READY;
    private int totalActions;
    private int submittedActions;
    private int inFlightActionIndex = -1;
    private int sourcePosition;
    private int inFlightSourceStart;
    private int inFlightSourceEnd;
    private String inFlightActionName = "";
    private long lastActionNanos;
    private long totalActionNanos;
    private long maxActionNanos;
    private long lastActionStartedAtNanos;
    private long lastActionStartGapNanos;
    private long lastStateChangeAtNanos;
    private long lastStateChangeGapNanos;
    private long lastBluetoothCallbackAtNanos;
    private long lastBluetoothCallbackGapNanos;
    private String lastBluetoothCallback = "none";
    private int bluetoothCallbackCount;
    private long lastPauseResponseNanos;
    private long lastResumeResponseNanos;
    private long lastCancellationResponseNanos;
    private long sessionStartedAtNanos;
    private long sessionDurationNanos;

    public synchronized void configure(int totalActions, long nowNanos) {
        generation++;
        state = State.READY;
        this.totalActions = Math.max(0, totalActions);
        submittedActions = 0;
        inFlightActionIndex = -1;
        sourcePosition = 0;
        inFlightSourceStart = 0;
        inFlightSourceEnd = 0;
        inFlightActionName = "";
        lastActionNanos = 0;
        totalActionNanos = 0;
        maxActionNanos = 0;
        lastActionStartedAtNanos = 0;
        lastActionStartGapNanos = 0;
        actionHistory.clear();
        lastStateChangeAtNanos = 0;
        lastStateChangeGapNanos = 0;
        lastBluetoothCallbackAtNanos = 0;
        lastBluetoothCallbackGapNanos = 0;
        lastBluetoothCallback = "none";
        bluetoothCallbackCount = 0;
        lastPauseResponseNanos = 0;
        lastResumeResponseNanos = 0;
        lastCancellationResponseNanos = 0;
        sessionStartedAtNanos = 0;
        sessionDurationNanos = 0;
        revision++;
        recordStateChange(nowNanos);
    }

    public synchronized boolean start(long nowNanos) {
        if (state != State.READY && state != State.STOPPED) return false;
        generation++;
        revision++;
        state = State.RUNNING;
        sessionStartedAtNanos = nowNanos;
        sessionDurationNanos = 0;
        lastActionStartedAtNanos = 0;
        lastActionStartGapNanos = 0;
        recordStateChange(nowNanos);
        return true;
    }

    public synchronized ActionTicket beginAction(int sourceStart, int sourceEnd,
                                                 String actionName, long nowNanos) {
        if (state != State.RUNNING || inFlightActionIndex >= 0
            || submittedActions >= totalActions) return null;
        if (lastActionStartedAtNanos > 0) {
            lastActionStartGapNanos = Math.max(0, nowNanos - lastActionStartedAtNanos);
        }
        lastActionStartedAtNanos = nowNanos;
        inFlightActionIndex = submittedActions;
        inFlightSourceStart = Math.max(0, sourceStart);
        inFlightSourceEnd = Math.max(inFlightSourceStart, sourceEnd);
        inFlightActionName = actionName == null ? "" : actionName;
        revision++;
        return new ActionTicket(generation, inFlightActionIndex);
    }

    public synchronized boolean submit(ActionTicket ticket, int sourcePositionAfter,
                                       long durationNanos, long nowNanos) {
        if (!isCurrent(ticket) || state != State.RUNNING) return false;
        lastActionNanos = Math.max(0, durationNanos);
        totalActionNanos += lastActionNanos;
        maxActionNanos = Math.max(maxActionNanos, lastActionNanos);
        actionHistory.addLast(new ActionTiming(ticket.actionIndex, lastActionNanos));
        if (actionHistory.size() > ACTION_HISTORY_LIMIT) actionHistory.removeFirst();
        submittedActions++;
        sourcePosition = Math.max(0, sourcePositionAfter);
        inFlightActionIndex = -1;
        inFlightSourceStart = sourcePosition;
        inFlightSourceEnd = sourcePosition;
        inFlightActionName = "";
        revision++;
        return true;
    }

    public synchronized boolean fail(ActionTicket ticket, long nowNanos) {
        if (!isCurrent(ticket) || state != State.RUNNING) return false;
        inFlightActionIndex = -1;
        inFlightSourceStart = sourcePosition;
        inFlightSourceEnd = sourcePosition;
        inFlightActionName = "";
        generation++;
        revision++;
        state = State.STOPPED;
        sessionDurationNanos = elapsedSessionNanos(nowNanos);
        recordStateChange(nowNanos);
        return true;
    }

    public synchronized boolean pause(long nowNanos, long responseNanos) {
        if (state != State.RUNNING || inFlightActionIndex >= 0) return false;
        generation++;
        revision++;
        state = State.PAUSED;
        lastPauseResponseNanos = Math.max(0, responseNanos);
        recordStateChange(nowNanos);
        return true;
    }

    public synchronized boolean resume(long nowNanos, long responseNanos) {
        if (state != State.PAUSED) return false;
        generation++;
        revision++;
        state = State.RUNNING;
        lastResumeResponseNanos = Math.max(0, responseNanos);
        lastActionStartedAtNanos = 0;
        lastActionStartGapNanos = 0;
        recordStateChange(nowNanos);
        return true;
    }

    public synchronized boolean cancel(long nowNanos, long responseNanos) {
        if (state == State.LOCALLY_SUBMITTED || state == State.DISCONNECTED
            || state == State.STOPPED) return false;
        generation++;
        revision++;
        state = State.STOPPED;
        inFlightActionIndex = -1;
        inFlightSourceStart = sourcePosition;
        inFlightSourceEnd = sourcePosition;
        inFlightActionName = "";
        lastCancellationResponseNanos = Math.max(0, responseNanos);
        sessionDurationNanos = elapsedSessionNanos(nowNanos);
        recordStateChange(nowNanos);
        return true;
    }

    public synchronized boolean disconnect(long nowNanos) {
        if (state == State.LOCALLY_SUBMITTED || state == State.DISCONNECTED) return false;
        generation++;
        revision++;
        state = State.DISCONNECTED;
        inFlightActionIndex = -1;
        inFlightSourceStart = sourcePosition;
        inFlightSourceEnd = sourcePosition;
        inFlightActionName = "";
        sessionDurationNanos = elapsedSessionNanos(nowNanos);
        recordStateChange(nowNanos);
        return true;
    }

    public synchronized boolean completeLocally(long nowNanos) {
        if (state != State.RUNNING || submittedActions != totalActions
            || inFlightActionIndex >= 0) return false;
        generation++;
        revision++;
        state = State.LOCALLY_SUBMITTED;
        sessionDurationNanos = elapsedSessionNanos(nowNanos);
        recordStateChange(nowNanos);
        return true;
    }

    public synchronized void recordBluetoothCallback(String callbackName, long nowNanos) {
        if (lastBluetoothCallbackAtNanos > 0) {
            lastBluetoothCallbackGapNanos = Math.max(0, nowNanos - lastBluetoothCallbackAtNanos);
        }
        lastBluetoothCallbackAtNanos = nowNanos;
        lastBluetoothCallback = callbackName == null ? "unknown" : callbackName;
        bluetoothCallbackCount++;
        revision++;
    }

    public synchronized Snapshot snapshot(long nowNanos) {
        long averageActionNanos = submittedActions == 0
            ? 0
            : totalActionNanos / submittedActions;
        long sessionDuration = sessionDurationNanos > 0
            ? sessionDurationNanos
            : elapsedSessionNanos(nowNanos);
        return new Snapshot(generation, revision, state, totalActions, submittedActions,
            inFlightActionIndex, sourcePosition, inFlightSourceStart, inFlightSourceEnd,
            inFlightActionName, lastActionNanos, averageActionNanos, maxActionNanos,
            lastActionStartGapNanos, new ArrayList<>(actionHistory), lastStateChangeGapNanos,
            lastBluetoothCallbackGapNanos, lastBluetoothCallback, bluetoothCallbackCount,
            lastPauseResponseNanos, lastResumeResponseNanos, lastCancellationResponseNanos,
            sessionDuration);
    }

    private boolean isCurrent(ActionTicket ticket) {
        return ticket != null && ticket.generation == generation
            && ticket.actionIndex == inFlightActionIndex;
    }

    private long elapsedSessionNanos(long nowNanos) {
        return sessionStartedAtNanos == 0 ? 0 : Math.max(0, nowNanos - sessionStartedAtNanos);
    }

    private void recordStateChange(long nowNanos) {
        if (lastStateChangeAtNanos > 0) {
            lastStateChangeGapNanos = Math.max(0, nowNanos - lastStateChangeAtNanos);
        }
        lastStateChangeAtNanos = nowNanos;
        revision++;
    }
}
