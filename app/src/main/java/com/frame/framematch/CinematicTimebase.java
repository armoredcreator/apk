package com.frame.framematch;

/**
 * Normalizes Media3 presentation timestamps to the local source clip.
 *
 * Media3 may supply timestamps relative to the edited item or relative to the
 * composition. Anchoring at the FIRST frame for this individual item prevents
 * a later clip's recede from being evaluated as already complete at its start.
 *
 * One instance per clip/effect. Frames are processed in chronological order.
 */
public final class CinematicTimebase {
    private final long clipStartUs;
    private long firstPresentationUs = Long.MIN_VALUE;

    public CinematicTimebase(long clipStartMs) {
        clipStartUs = Math.max(0L, clipStartMs) * 1000L;
    }

    public long sourceTimeUs(long presentationTimeUs) {
        long timestamp = Math.max(0L, presentationTimeUs);
        if (firstPresentationUs == Long.MIN_VALUE) firstPresentationUs = timestamp;
        return clipStartUs + Math.max(0L, timestamp - firstPresentationUs);
    }
}
