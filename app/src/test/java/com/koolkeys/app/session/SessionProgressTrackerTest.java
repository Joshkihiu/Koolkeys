package com.koolkeys.app.session;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SessionProgressTrackerTest {
    @Test
    public void previewSeparatesInFlightSubmittedAndUnsubmittedActions() {
        SessionProgressTracker tracker = new SessionProgressTracker();
        tracker.configure(3, 10);
        assertTrue(tracker.start(20));

        SessionProgressTracker.ActionTicket ticket =
            tracker.beginAction(0, 1, "CHARACTER", 30);
        SessionProgressTracker.Snapshot inFlight = tracker.snapshot(31);
        assertEquals(0, inFlight.getSubmittedActions());
        assertEquals(2, inFlight.getUnsubmittedActions());
        assertEquals(0, inFlight.getInFlightActionIndex());
        assertEquals(0, inFlight.getSourcePosition());
        assertEquals(0, inFlight.getInFlightSourceStart());
        assertEquals(1, inFlight.getInFlightSourceEnd());
        assertFalse(inFlight.isHostReceiptConfirmed());

        assertTrue(tracker.submit(ticket, 1, 500_000, 40));
        SessionProgressTracker.Snapshot submitted = tracker.snapshot(41);
        assertEquals(1, submitted.getSubmittedActions());
        assertEquals(2, submitted.getUnsubmittedActions());
        assertEquals(-1, submitted.getInFlightActionIndex());
        assertEquals(1, submitted.getSourcePosition());
    }

    @Test
    public void completionRequiresAllActionsSubmittedAndNoActionInFlight() {
        SessionProgressTracker tracker = new SessionProgressTracker();
        tracker.configure(1, 10);
        tracker.start(20);
        SessionProgressTracker.ActionTicket ticket =
            tracker.beginAction(0, 1, "CHARACTER", 30);

        assertFalse(tracker.snapshot(31).canCompleteLocally());
        assertFalse(tracker.completeLocally(32));
        assertTrue(tracker.submit(ticket, 1, 800_000, 40));
        assertTrue(tracker.snapshot(41).canCompleteLocally());
        assertTrue(tracker.completeLocally(42));
        assertEquals(SessionProgressTracker.State.LOCALLY_SUBMITTED,
            tracker.snapshot(43).getState());
    }

    @Test
    public void cancellationInvalidatesQueuedActionTickets() {
        SessionProgressTracker tracker = runningTracker(2);
        SessionProgressTracker.ActionTicket ticket =
            tracker.beginAction(0, 1, "CHARACTER", 30);

        assertTrue(tracker.cancel(40, 2_000_000));
        assertFalse(tracker.submit(ticket, 1, 500_000, 50));
        SessionProgressTracker.Snapshot cancelled = tracker.snapshot(60);
        assertEquals(SessionProgressTracker.State.STOPPED, cancelled.getState());
        assertEquals(2, cancelled.getUnsubmittedActions());
        assertEquals(-1, cancelled.getInFlightActionIndex());
        assertEquals(2_000_000, cancelled.getLastCancellationResponseNanos());
    }

    @Test
    public void disconnectInvalidatesTheCurrentAction() {
        SessionProgressTracker tracker = runningTracker(1);
        SessionProgressTracker.ActionTicket ticket =
            tracker.beginAction(0, 1, "CHARACTER", 30);

        assertTrue(tracker.disconnect(40));
        assertFalse(tracker.submit(ticket, 1, 500_000, 50));
        SessionProgressTracker.Snapshot disconnected = tracker.snapshot(60);
        assertEquals(SessionProgressTracker.State.DISCONNECTED, disconnected.getState());
        assertEquals(1, disconnected.getUnsubmittedActions());
        assertEquals(-1, disconnected.getInFlightActionIndex());
    }

    @Test
    public void pauseAndResumePreserveCursorAndRejectOldTickets() {
        SessionProgressTracker tracker = runningTracker(2);
        SessionProgressTracker.ActionTicket first =
            tracker.beginAction(0, 1, "CHARACTER", 30);
        assertTrue(tracker.submit(first, 1, 700_000, 40));
        assertTrue(tracker.pause(50, 1_000_000));

        assertNull(tracker.beginAction(1, 2, "CHARACTER", 60));
        assertEquals(1, tracker.snapshot(60).getSourcePosition());
        assertTrue(tracker.resume(70, 2_000_000));

        SessionProgressTracker.ActionTicket resumed =
            tracker.beginAction(1, 2, "CHARACTER", 80);
        assertNotNull(resumed);
        assertEquals(1, resumed.getActionIndex());
        assertFalse(tracker.submit(first, 1, 700_000, 90));
        assertTrue(tracker.submit(resumed, 2, 900_000, 100));
        SessionProgressTracker.Snapshot snapshot = tracker.snapshot(110);
        assertEquals(SessionProgressTracker.State.RUNNING, snapshot.getState());
        assertEquals(2_000_000, snapshot.getLastResumeResponseNanos());
        assertEquals(900_000, snapshot.getLastActionNanos());
    }

    @Test
    public void capturesActionStateAndBluetoothTimingSamples() {
        SessionProgressTracker tracker = runningTracker(1);
        tracker.recordBluetoothCallback("connected", 30);
        tracker.recordBluetoothCallback("disconnected", 45);
        SessionProgressTracker.ActionTicket ticket =
            tracker.beginAction(0, 1, "ENTER", 50);
        assertTrue(tracker.submit(ticket, 1, 1_250_000, 60));

        SessionProgressTracker.Snapshot snapshot = tracker.snapshot(70);
        assertEquals(15, snapshot.getLastBluetoothCallbackGapNanos());
        assertEquals("disconnected", snapshot.getLastBluetoothCallback());
        assertEquals(2, snapshot.getBluetoothCallbackCount());
        assertEquals(1_250_000, snapshot.getLastActionNanos());
        assertEquals(1_250_000, snapshot.getAverageActionNanos());
        assertEquals(1_250_000, snapshot.getMaxActionNanos());
        assertEquals(1, snapshot.getRecentActionTimings().size());
        assertEquals(0, snapshot.getRecentActionTimings().get(0).getActionIndex());
    }

    @Test
    public void capturesTheGapBetweenLocalActionStarts() {
        SessionProgressTracker tracker = runningTracker(2);
        SessionProgressTracker.ActionTicket first =
            tracker.beginAction(0, 1, "CHARACTER", 30_000_000);
        assertTrue(tracker.submit(first, 1, 40_000_000, 70_000_000));
        SessionProgressTracker.ActionTicket second =
            tracker.beginAction(1, 2, "CHARACTER", 430_000_000);

        assertNotNull(second);
        assertEquals(400_000_000,
            tracker.snapshot(431_000_000).getLastActionStartGapNanos());
    }

    private SessionProgressTracker runningTracker(int totalActions) {
        SessionProgressTracker tracker = new SessionProgressTracker();
        tracker.configure(totalActions, 10);
        assertTrue(tracker.start(20));
        return tracker;
    }
}
