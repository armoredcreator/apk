package com.frame.framematch;

import android.graphics.Matrix;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.MatrixTransformation;

/**
 * Experimental, uniform camera zoom on both sides of the same cut.
 * The desktop V39 remains the visual reference; this shader has not been
 * verified as pixel-equivalent to V39 on a physical Android device.
 *
 * One C2-continuous recede trajectory replaces the old segmented lookup.
 * Timestamps are anchored to each clip's first frame, including for clipped previews.
 * No hard release, no midway scale reset, and no secondary zoom effect.
 */
@UnstableApi
public final class CinematicCutZoom implements MatrixTransformation {
    private final boolean approaching;
    private final CinematicTimebase timebase;
    private final long durationUs;
    private final float peak;

    private CinematicCutZoom(boolean approaching, long startMs, long durationMs,
                             float zoomPercent) {
        this.approaching = approaching;
        this.timebase = new CinematicTimebase(startMs);
        this.durationUs = Math.max(1L, durationMs) * 1000L;
        this.peak = 1f + Math.max(0f, Math.min(0.5f, zoomPercent / 100f));
    }

    public static CinematicCutZoom before(long startMs, long durationMs, float zoomPercent) {
        return new CinematicCutZoom(true, startMs, durationMs, zoomPercent);
    }

    public static CinematicCutZoom after(long startMs, long durationMs, float zoomPercent) {
        return new CinematicCutZoom(false, startMs, durationMs, zoomPercent);
    }

    public float scaleAt(long presentationTimeUs) {
        // Never pass a composition-global timestamp into an item-local timeline.
        // Otherwise clip 2 can enter at scale 1.0 and skip the entire recede.
        long sourceUs = timebase.sourceTimeUs(presentationTimeUs);
        return approaching
            ? ZoomTimeline.approach(sourceUs, durationUs, 3_000_000L, peak)
            : ZoomTimeline.recede(sourceUs, durationUs, peak);
    }

    @Override public Matrix getMatrix(long presentationTimeUs) {
        float scale = scaleAt(presentationTimeUs);
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        return matrix;
    }
}
