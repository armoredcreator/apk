package com.frame.framematch;

import android.graphics.Matrix;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.MatrixTransformation;

/**
 * Exact-reference-pair motion in the Media3 normalized device coordinates.
 * The measured focal point (240,320) differs from image center (240,432).
 *
 * This effect must NEVER be applied to unknown future scenes: their correct
 * approach/recede geometry must be diagnosed independently.
 */
@UnstableApi
public final class V39ReferenceMotion implements MatrixTransformation {
    private final boolean before;
    private final CinematicTimebase clock;
    private final long clipStartUs,durationUs;
    private V39ReferenceMotion(boolean before,long clipStartMs,long durationMs) {
        this.before=before;
        this.clock=new CinematicTimebase(clipStartMs);
        clipStartUs=Math.max(0,clipStartMs)*1000L;
        durationUs=Math.max(1,durationMs)*1000L;
    }
    public static V39ReferenceMotion before(long clipStartMs,long durationMs){
        return new V39ReferenceMotion(true,clipStartMs,durationMs);
    }
    public static V39ReferenceMotion after(long clipStartMs,long durationMs){
        return new V39ReferenceMotion(false,clipStartMs,durationMs);
    }
    public float scaleAt(long timestampUs){
        long sourceUs=clock.sourceTimeUs(timestampUs);
        return before ? V39ReferenceProfile.cubicApproach(sourceUs,durationUs)
             : V39ReferenceProfile.recede(Math.max(0,sourceUs-clipStartUs));
    }
    @Override public Matrix getMatrix(long timestampUs) {
        float z=scaleAt(timestampUs);
        // Translate about (240,320) on original 480x864 upright source.
        float x=2f*V39ReferenceProfile.FOCUS_X-1f;
        float y=1f-2f*V39ReferenceProfile.FOCUS_Y;
        Matrix result=new Matrix();
        result.setValues(new float[]{
            z,0,(1f-z)*x, 0,z,(1f-z)*y, 0,0,1f
        });
        return result;
    }
}
