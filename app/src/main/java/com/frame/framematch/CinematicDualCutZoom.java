package com.frame.framematch;

import android.graphics.Matrix;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.MatrixTransformation;

/**
 * Exactly ONE camera transform per media item. Handles an arriving recede and
 * a departing approach on the same source timeline without discrete resets.
 *
 * The approved V37 94->95 zoom is a reference, not a guarantee of pixel parity.
 */
@UnstableApi
public final class CinematicDualCutZoom implements MatrixTransformation {
    private final CinematicTimebase timebase;
    private final long durationUs;
    private final float entering, leaving;
    private final CinematicCameraPivot enteringPivot, leavingPivot;

    public CinematicDualCutZoom(long clipStartMs,long fullDurationMs,
                                float enteringPercent,float leavingPercent,
                                CinematicCameraPivot enteringPivot,
                                CinematicCameraPivot leavingPivot) {
        timebase=new CinematicTimebase(clipStartMs);
        durationUs=Math.max(1L,fullDurationMs)*1000L;
        entering=Math.max(0f,enteringPercent);
        leaving=Math.max(0f,leavingPercent);
        this.enteringPivot=enteringPivot==null?
            CinematicCameraPivot.validatedPortrait():enteringPivot;
        this.leavingPivot=leavingPivot==null?
            CinematicCameraPivot.validatedPortrait():leavingPivot;
    }

    @Override public Matrix getMatrix(long presentationTimeUs) {
        CinemaCurve.Frame f=CinemaCurve.evaluate(
            timebase.sourceTimeUs(presentationTimeUs),durationUs,
            entering,leaving,enteringPivot.ndcX(),enteringPivot.ndcY(),
            leavingPivot.ndcX(),leavingPivot.ndcY());
        Matrix m=new Matrix();
        m.setValues(new float[]{f.scale,0f,f.dx,0f,f.scale,f.dy,0f,0f,1f});
        return m;
    }
}
