package com.frame.framematch;

/**
 * Full transition preview window. Inspect the complete camera approach before
 * the cut and do not truncate the succeeding scene: exposure and zoom failures
 * often appear several seconds AFTER a visually correct boundary.
 */
public final class PreviewWindow {
    public static final long APPROACH_MS = 3000L;
    private PreviewWindow() {}

    public static long beforeStart(long previousDurationMs, long minimumAllowedMs) {
        if (previousDurationMs < 0 || minimumAllowedMs < 0) {
            throw new IllegalArgumentException("Negative media timestamp");
        }
        long start = Math.max(0L, previousDurationMs - APPROACH_MS);
        return Math.min(previousDurationMs, Math.max(start, minimumAllowedMs));
    }

    public static long afterEnd(long nextDurationMs) {
        if (nextDurationMs <= 0L) {
            throw new IllegalArgumentException("Empty clip");
        }
        return nextDurationMs;
    }
}
