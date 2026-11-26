package com.koolkeys.app.session;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class TypingTimingTest {
    @Test
    public void estimatesFromMeasuredActionTime() {
        List<TypingAction> actions = SessionManager.buildSessionActions("a\nb");

        assertEquals(actions.size() * 2_400L,
            TypingTiming.estimateRemainingMillis(actions, 0, 0, 0, 5));
        assertEquals(7_203,
            TypingTiming.estimateRemainingMillis(actions, 1, 5, 3, 5));
    }

    @Test
    public void enforcesSelectedRatesAndAccountsForTheKeyPressDuration() {
        assertEquals(2_400, TypingTiming.actionIntervalMillis(5));
        assertEquals(1_200, TypingTiming.actionIntervalMillis(10));
        assertEquals(400, TypingTiming.actionIntervalMillis(30));
        assertEquals(2_360, TypingTiming.delayAfterActionMillis(40, 5));
        assertEquals(360, TypingTiming.delayAfterActionMillis(40, 30));
        assertEquals(0, TypingTiming.delayAfterActionMillis(450, 30));
    }
}
