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
    // Empirical V37 trajectory, measured against the untransformed clip 95.
    // Normalized seconds of clip 2, with a monotone Hermite interpolation to
    // avoid jumps in scale or camera velocity at the sample boundaries.
    private static final float[] RETURN_T = {
        0f, .0625f, .125f, .1875f, .25f, .3125f, .375f,
        .5f, .625f, .75f, .875f, .96875f, 1f
    };
    private static final float[] RETURN_RATIO = {
        1f, 1f, .933f, .840f, .761f, .744f, .691f,
        .540f, .355f, .182f, .049f, .002f, 0f
    };
    private static float v37Return(float t) {
        t=Math.max(0f,Math.min(1f,t));
        int j=0;
        while(j<RETURN_T.length-2 && t>RETURN_T[j+1])j++;
        float x0=RETURN_T[j],x1=RETURN_T[j+1];
        float y0=RETURN_RATIO[j],y1=RETURN_RATIO[j+1];
        float h=x1-x0, d=(y1-y0)/h;
        // Interior derivatives: zero at flats, otherwise monotone harmonic mean.
        float m0=derivative(j),m1=derivative(j+1);
        float u=(t-x0)/h,u2=u*u,u3=u2*u;
        float y=(2*u3-3*u2+1)*y0+(u3-2*u2+u)*h*m0
                +(-2*u3+3*u2)*y1+(u3-u2)*h*m1;
        return Math.max(0f,Math.min(1f,y));
    }
    private static float derivative(int i) {
        if(i==0||i==RETURN_T.length-1)return 0f;
        float left=(RETURN_RATIO[i]-RETURN_RATIO[i-1])/(RETURN_T[i]-RETURN_T[i-1]);
        float right=(RETURN_RATIO[i+1]-RETURN_RATIO[i])/(RETURN_T[i+1]-RETURN_T[i]);
        if(left*right<=0f)return 0f;
        float a=RETURN_T[i]-RETURN_T[i-1],b=RETURN_T[i+1]-RETURN_T[i];
        float w1=2*b+a,w2=b+2*a;
        return (w1+w2)/(w1/left+w2/right);
    }
    public float scaleAt(long presentationTimeUs) {
        long absolute=sourceStartUs+Math.max(0L,presentationTimeUs);
        if(approach) {
            float t=(absolute-(sourceDurationUs-rampUs))/(float)rampUs;
            return 1f+(peak-1f)*smooth(t);
        }
        // Full-duration V37 recede: no midway reset, no separate release.
        // The peak is shared exactly by both sides of the cut.
        float t=absolute/(float)sourceDurationUs;
        return 1f+(peak-1f)*v37Return(t);
    }
    @Override public Matrix getMatrix(long presentationTimeUs) {
        float z=scaleAt(presentationTimeUs);
        Matrix m=new Matrix();
        m.setValues(new float[]{z,0,0,0,z,0,0,0,1});
        return m;
    }
}
