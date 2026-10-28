package com.frame.framematch;

/**
 * Pure Java camera curve for repeatable frame-by-frame regression checks.
 * This is the V37 motion reference, not proof of V39 export equivalence.
 * One continuous scale is shared at the cut; the second clip returns to 1x.
 */
public final class CinematicMotionCurve {
    private CinematicMotionCurve() {}

    private static final float[] RETURN_T = {
        0f, .0625f, .125f, .1875f, .25f, .3125f, .375f,
        .5f, .625f, .75f, .875f, .96875f, 1f
    };
    private static final float[] RETURN_RATIO = {
        1f, 1f, .933f, .840f, .761f, .744f, .691f,
        .540f, .355f, .182f, .049f, .002f, 0f
    };

    private static float clamp(float value) { return Math.max(0f, Math.min(1f, value)); }
    private static float smooth(float value) {
        float t=clamp(value);
        return t*t*(3f-2f*t);
    }

    public static float approach(long originalTimeUs, long clipDurationUs,
                                 long rampUs, float peak) {
        if (clipDurationUs<=0L||rampUs<=0L||peak<1f)
            throw new IllegalArgumentException("Invalid camera curve");
        long availableRamp=Math.min(clipDurationUs,rampUs);
        float t=(originalTimeUs-(clipDurationUs-availableRamp))/(float)availableRamp;
        return 1f+(peak-1f)*smooth(t);
    }

    public static float recede(long originalTimeUs, long clipDurationUs, float peak) {
        if (clipDurationUs<=0L||peak<1f)
            throw new IllegalArgumentException("Invalid camera curve");
        return 1f+(peak-1f)*returnWeight(clamp(originalTimeUs/(float)clipDurationUs));
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

    private static float returnWeight(float t) {
        int j=0;
        while(j<RETURN_T.length-2 && t>RETURN_T[j+1])j++;
        float x0=RETURN_T[j],x1=RETURN_T[j+1];
        float y0=RETURN_RATIO[j],y1=RETURN_RATIO[j+1];
        float h=x1-x0;
        float m0=derivative(j),m1=derivative(j+1);
        float u=(t-x0)/h,u2=u*u,u3=u2*u;
        float y=(2*u3-3*u2+1)*y0+(u3-2*u2+u)*h*m0
                +(-2*u3+3*u2)*y1+(u3-u2)*h*m1;
        return clamp(y);
    }
}
