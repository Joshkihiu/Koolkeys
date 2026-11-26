package com.koolkeys.app.session;

import java.util.List;

final class TypingTiming {
    static final int MIN_WORDS_PER_MINUTE = 1;
    static final int DEFAULT_WORDS_PER_MINUTE = 5;
    static final int MAX_WORDS_PER_MINUTE = 30;

    private TypingTiming() { }

    static int clampWordsPerMinute(int wordsPerMinute) {
        return Math.max(MIN_WORDS_PER_MINUTE,
            Math.min(MAX_WORDS_PER_MINUTE, wordsPerMinute));
    }

    static long actionIntervalMillis(int wordsPerMinute) {
        int boundedWordsPerMinute = clampWordsPerMinute(wordsPerMinute);
        long intervalsPerMinute = boundedWordsPerMinute * 5L;
        return (60_000L + intervalsPerMinute - 1) / intervalsPerMinute;
    }

    static long delayAfterActionMillis(long actionDurationMillis, int wordsPerMinute) {
        return Math.max(0,
            actionIntervalMillis(wordsPerMinute) - Math.max(0, actionDurationMillis));
    }

    static long estimateRemainingMillis(List<TypingAction> actions, int nextAction,
                                        long averageActionMillis, long delayUntilNext,
                                        int wordsPerMinute) {
        int remainingActions = Math.max(0, actions.size() - nextAction);
        long actionEstimate = Math.max(
            actionIntervalMillis(wordsPerMinute), averageActionMillis);
        return Math.max(0, delayUntilNext) + remainingActions * actionEstimate;
    }
}
