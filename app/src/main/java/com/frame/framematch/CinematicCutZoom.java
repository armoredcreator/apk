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
    public float scaleAt(long presentationTimeUs) {
        long absolute=sourceStartUs+Math.max(0L,presentationTimeUs);
        if(approach) {
            return CinematicMotionCurve.approach(absolute,sourceDurationUs,rampUs,peak);
        }
        // Same reference trajectory as before; no new animation or cutoff.
        return CinematicMotionCurve.recede(absolute,sourceDurationUs,peak);
    }
    @Override public Matrix getMatrix(long presentationTimeUs) {
        float z=scaleAt(presentationTimeUs);
        Matrix m=new Matrix();
        m.setValues(new float[]{z,0,0,0,z,0,0,0,1});
        return m;
    }
}
