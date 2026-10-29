package com.frame.framematch;

import android.graphics.Matrix;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.MatrixTransformation;

/** Boundary facial alignment: applied to scene 2 BEFORE cinematic zoom. */
@UnstableApi
public final class CutFaceAlignment implements MatrixTransformation {
    private final CutFaceMath.Plan plan;
    private final CinematicTimebase timebase;
    private final long durationUs;

    private CutFaceAlignment(CutFaceMath.Plan p,long startMs,long durationMs) {
        plan=p;
        timebase=new CinematicTimebase(startMs);
        durationUs=Math.max(1L,durationMs)*1000L;
    }
    public static CutFaceAlignment between(RegionMath.FaceBox referenceEnd,
                                           RegionMath.FaceBox incomingStart,
                                           long clipStartMs,long fullDurationMs) {
        if(referenceEnd==null||incomingStart==null)return null;
        CutFaceMath.Plan p=CutFaceMath.estimate(referenceEnd.x,referenceEnd.y,
            referenceEnd.w,referenceEnd.h,incomingStart.x,incomingStart.y,
            incomingStart.w,incomingStart.h);
        return p.active()?new CutFaceAlignment(p,clipStartMs,fullDurationMs):null;
    }
    @Override public Matrix getMatrix(long presentationTimeUs) {
        float[] a=plan.at(timebase.sourceTimeUs(presentationTimeUs)-Math.max(0L,
            // Correct for clip source offset; preview/full share timeline.
            // Source timebase at first retained frame already includes this offset.
            0L),durationUs);
        Matrix matrix=new Matrix();
        matrix.setValues(new float[]{a[0],0f,a[2],0f,a[1],a[3],0f,0f,1f});
        return matrix;
    }
}
