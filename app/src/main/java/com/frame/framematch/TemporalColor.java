package com.frame.framematch;

/**
 * Temporal robustness: three estimates against the SAME Clip 1.
 * Small divergences are smoothed; large disagreements are ignored rather than
 * imposing unstable, globally exaggerated color changes.
 */
public final class TemporalColor {
    private TemporalColor(){}
    public static RegionMath.Plan consolidate(RegionMath.Plan a,RegionMath.Plan b,RegionMath.Plan c) {
        float[][] gains=new float[4][3];
        boolean[] valid=new boolean[4];
        for(int k=0;k<4;k++){
            valid[k]=(k==0)||(a.calibrated[k]&&b.calibrated[k]);
            for(int ch=0;ch<3;ch++){
                float x=a.gains[k][ch],y=b.gains[k][ch],z=c.gains[k][ch];
                float mid=Math.max(Math.min(x,y),Math.min(Math.max(x,y),z));
                float max=Math.max(x,Math.max(y,z)),min=Math.min(x,Math.min(y,z));
                // Strong temporal fluctuations likely represent motion or changing illumination.
                gains[k][ch]=max-min>.14f ? 1f+(mid-1f)*.45f : .55f*mid+.25f*x+.20f*y;
            }
            if(k!=0 && !c.calibrated[k]&&!b.calibrated[k])valid[k]=false;
        }
        return new RegionMath.Plan(gains,valid,a.strength);
    }
}
