package com.frame.framematch;

/**
 * Continuous two-scene camera trajectory, calibrated from the APPROVED
 * desktop V37 (94.mp4 / 95.mp4). Not a claim of V39 pixel parity.
 *
 * The approach is 3 seconds before the cut and both sides share the same
 * peak (e.g. 1.33). Recede keyframes are dimensionless, duration-normalized
 * and interpolated with a monotone C1 Hermite spline: no sudden zoom reset.
 */
public final class ZoomTimeline {
    private ZoomTimeline(){}

    private static float clamp01(double t) {
        return (float)Math.max(0d,Math.min(1d,t));
    }
    /** Desktop V37's pre-cut approach matches cubic smoothstep, not quintic. */
    private static float smoothstep(double p) {
        float t=clamp01(p);
        return t*t*(3f-2f*t);
    }
    public static float approach(long sourceUs,long durationUs,long rampUs,float peak) {
        long duration=Math.max(1L,durationUs);
        long start=Math.max(0L,duration-Math.max(1L,rampUs));
        return 1f+(peak-1f)*smoothstep((sourceUs-start)/(double)Math.max(1L,duration-start));
    }

    // Measured V37 recede at t=0,1,2,...,8s; normalized against 33% zoom.
    // Strong change during the second scene is deliberately gradual.
    private static final float[] V37_RECEDE={1f,.930f,.762f,.695f,.545f,.358f,.180f,.048f,0f};

    private static float derivative(int i) {
        if(i<=0||i>=V37_RECEDE.length-1)return 0f;
        float left=V37_RECEDE[i]-V37_RECEDE[i-1];
        float right=V37_RECEDE[i+1]-V37_RECEDE[i];
        if(left*right<=0f)return 0f;
        return 2f*left*right/(left+right);
    }
    /** Smooth C1, monotone profile; same ratio regardless of clip duration. */
    public static float recede(long sourceUs,long durationUs,float peak) {
        float p=clamp01(sourceUs/(double)Math.max(1L,durationUs));
        float pos=p*(V37_RECEDE.length-1);
        int i=Math.min(V37_RECEDE.length-2,(int)Math.floor(pos));
        float t=pos-i;
        float y0=V37_RECEDE[i],y1=V37_RECEDE[i+1];
        float m0=derivative(i),m1=derivative(i+1);
        float t2=t*t,t3=t2*t;
        float factor=(2*t3-3*t2+1)*y0+(t3-2*t2+t)*m0
                     +(-2*t3+3*t2)*y1+(t3-t2)*m1;
        factor=Math.max(0f,Math.min(1f,factor));
        return 1f+(peak-1f)*factor;
    }
}
