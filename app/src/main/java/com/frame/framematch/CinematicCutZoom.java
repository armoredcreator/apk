package com.frame.framematch;

import android.graphics.Matrix;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.MatrixTransformation;

/**
 * Camera-only continuous zoom across a cut. No resampling of faces independent
 * of the image, no synthetic frames, no abrupt release at the transition.
 * A single exact peak scale is shared by both adjacent edited media items.
 */
@UnstableApi
public final class CinematicCutZoom implements MatrixTransformation {
    private final boolean approach;
    private final long sourceStartUs;
    private final long sourceDurationUs;
    private final long rampUs;
    private final float peak;

    private CinematicCutZoom(boolean approach,long startMs,long durationMs,long rampMs,float peak) {
        this.approach=approach;
        this.sourceStartUs=Math.max(0,startMs)*1000L;
        this.sourceDurationUs=Math.max(1,durationMs)*1000L;
        this.rampUs=Math.max(1,rampMs)*1000L;
        this.peak=peak;
    }
    public static CinematicCutZoom before(long sourceStartMs,long totalDurationMs,float zoomPercent) {
        return new CinematicCutZoom(true,sourceStartMs,totalDurationMs,3000L,
            1f+Math.max(0f,Math.min(0.5f,zoomPercent/100f)));
    }
    public static CinematicCutZoom after(long sourceStartMs,long totalDurationMs,float zoomPercent) {
        return new CinematicCutZoom(false,sourceStartMs,totalDurationMs,3000L,
            1f+Math.max(0f,Math.min(0.5f,zoomPercent/100f)));
    }
    private static float smooth(float t) {
        t=Math.max(0f,Math.min(1f,t));
        return t*t*(3f-2f*t);
    }
    public float scaleAt(long presentationTimeUs) {
        long absolute=sourceStartUs+Math.max(0L,presentationTimeUs);
        if(approach) {
            float t=(absolute-(sourceDurationUs-rampUs))/(float)rampUs;
            return 1f+(peak-1f)*smooth(t);
        }
        // Recede through the remainder of clip 2. At its last frame the
        // framing reaches exactly 1.0; never drop to 1.0 mid-clip.
        float t=(absolute)/(float)sourceDurationUs;
        return peak-(peak-1f)*smooth(t);
    }
    @Override public Matrix getMatrix(long presentationTimeUs) {
        float z=scaleAt(presentationTimeUs);
        Matrix m=new Matrix();
        m.setValues(new float[]{z,0,0,0,z,0,0,0,1});
        return m;
    }
}
