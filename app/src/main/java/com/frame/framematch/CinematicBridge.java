package com.frame.framematch;

import android.graphics.Matrix;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.MatrixTransformation;

/**
 * Opt-in cinematic bridge: identical geometry on both sides of the cut.
 * Never inserts a replacement frame or a jump in scale at the boundary.
 * Each adjacent pair is evaluated independently before enabling this effect.
 */
@UnstableApi
public final class CinematicBridge implements MatrixTransformation {
    private static final float MAX_ZOOM = 1.33f; // V37 reference, not a universal requirement.
    private static final long APPROACH_US = 3_000_000L;
    private final boolean approaching;
    private final long durationUs;
    private final float peakZoom;

    private CinematicBridge(boolean approaching, long durationUs, float peakZoom) {
        this.approaching = approaching;
        this.durationUs = Math.max(1L, durationUs);
        this.peakZoom = RegionMath.clamp(peakZoom, 1f, MAX_ZOOM);
    }
    public static CinematicBridge before(long durationMs, float zoom) {
        return new CinematicBridge(true, durationMs * 1000L, zoom);
    }
    public static CinematicBridge after(long durationMs, float zoom) {
        return new CinematicBridge(false, durationMs * 1000L, zoom);
    }
    private static float smooth(float x) {
        float t = RegionMath.clamp(x, 0f, 1f);
        return t * t * (3f - 2f*t);
    }
    public float scale(long presentationTimeUs) {
        float weight;
        if (approaching) {
            long start = Math.max(0L, durationUs - APPROACH_US);
            weight = smooth((presentationTimeUs - start) / (float)Math.max(1L, durationUs - start));
        } else {
            // Hold the exact cut geometry, then release all zoom by the final frame.
            weight = 1f - smooth(presentationTimeUs / (float)durationUs);
        }
        return 1f + (peakZoom - 1f) * weight;
    }
    @Override public Matrix getMatrix(long presentationTimeUs) {
        float s = scale(presentationTimeUs);
        Matrix result = new Matrix();
        // Media3 matrix uses normalized device coordinates; scale about the image center.
        result.setScale(s, s);
        return result;
    }
}
