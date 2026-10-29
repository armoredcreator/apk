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
    private final long fadeOriginUs;

    private CutFaceAlignment(CutFaceMath.Plan p,long previewStartMs,long originMs,long durationMs) {
        plan=p;
        timebase=new CinematicTimebase(previewStartMs);
        fadeOriginUs=Math.max(0L,originMs)*1000L;
        durationUs=Math.max(1L,durationMs)*1000L;
    }
    public static CutFaceAlignment between(RegionMath.FaceBox referenceEnd,
                                           RegionMath.FaceBox incomingStart,
                                           long previewStartMs,long originMs,long fullDurationMs) {
        if(referenceEnd==null||incomingStart==null)return null;
        CutFaceMath.Plan p=CutFaceMath.estimate(referenceEnd.x,referenceEnd.y,
            referenceEnd.w,referenceEnd.h,incomingStart.x,incomingStart.y,
            incomingStart.w,incomingStart.h);
        return p.active()?new CutFaceAlignment(p,previewStartMs,originMs,fullDurationMs):null;
    }
    @Override public Matrix getMatrix(long presentationTimeUs) {
        // Align to the original retained-frame origin even in a clipped preview.
        float[] a=plan.at(Math.max(0L,timebase.sourceTimeUs(presentationTimeUs)-fadeOriginUs),
            durationUs);
        Matrix matrix=new Matrix();
        matrix.setValues(new float[]{a[0],0f,a[2],0f,a[1],a[3],0f,0f,1f});
        return matrix;
    }
}
