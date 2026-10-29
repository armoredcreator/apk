package com.frame.framematch;

/**
 * V39 empirical reference profile, VALID ONLY for the documented 183/95 pair.
 *
 * Numeric calibration from the approved 181.mp4. NOT a general automatic
 * continuity algorithm and not the original desktop rendering source.
 * All frame positions are defined on the 24fps source timeline.
 */
public final class V39ReferenceProfile {
    private V39ReferenceProfile() {}
    public static final String CLIP1_SHA256 =
        "91250d98002e01b958357113ea1c9fc19950ab554e4c07359650786c7f2a2531";
    public static final String CLIP2_SHA256 =
        "298f38b28ad1b3072d5e0e5f025122e909eae21c7441922344ea8fa8a2d0098d";
    public static final float FOCUS_X=240f/480f;
    public static final float FOCUS_Y=320f/864f;

    private static final String RECEDING =
        "1.34751,1.33980,1.33729,1.33511,1.32602,1.32426,1.31481,1.31211,1.30673,1.30077,1.29411,1.28543,1.27821,1.26907,1.26186,1.25192,1.25192,1.25070,1.24922,1.24715,1.24481,1.24175,1.23828,1.23500,1.22905,1.22383,1.21867,1.21201,1.20639,1.20125,1.19369,1.18702,1.18004,1.17314,1.16555,1.15788,1.14964,1.14167,1.13469,1.12658,1.11808,1.11081,1.10250,1.09584,1.08797,1.08055,1.07279,1.06646,1.05961,1.05302,1.04647,1.04041,1.03466,1.02868,1.02436,1.01961,1.01534,1.01218,1.00873,1.00592,1.00332,1.00194,1.00144,1.00043,1.00000";
    private static final String FIRST_GRADE =
        "0.988,-7.421,0.993,-6.499,0.985,-7.727;0.990,-7.126,0.990,-6.586,0.978,-7.838;0.990,-6.865,0.992,-6.499,0.982,-7.831;0.989,-6.887,0.995,-6.448,0.985,-7.855;0.989,-7.179,0.996,-6.489,0.987,-8.181;0.992,-7.468,0.997,-6.490,0.990,-8.349;0.993,-7.178,0.998,-6.471,0.993,-8.132;0.991,-6.750,0.999,-6.531,0.994,-8.039;0.990,-6.601,1.001,-6.566,0.995,-8.037;0.991,-7.420,1.002,-7.139,0.993,-8.655;0.989,-10.153,0.999,-9.186,0.979,-10.814;0.987,-14.045,0.995,-12.030,0.959,-13.610;0.992,-18.349,0.998,-15.229,0.942,-16.611;0.995,-19.724,0.999,-16.298,0.935,-17.935;0.993,-19.659,0.993,-16.017,0.927,-17.889;0.993,-19.730,0.991,-15.751,0.925,-17.889;0.995,-19.911,0.992,-15.523,0.927,-17.890";
    private static final String SECOND_GRADE =
        "0.916,-14.327,0.881,-10.109,0.803,-10.956;0.938,-21.015,0.909,-14.962,0.808,-14.559;0.948,-25.118,0.919,-18.143,0.828,-18.639;0.946,-25.985,0.926,-19.335,0.852,-20.598;0.930,-25.270,0.930,-19.608,0.869,-20.820;0.915,-24.703,0.929,-19.931,0.874,-20.702;0.908,-24.318,0.926,-20.006,0.876,-20.882;0.906,-23.966,0.923,-19.703,0.875,-20.796;0.908,-23.517,0.923,-19.129,0.876,-20.328;0.909,-22.741,0.923,-18.363,0.877,-19.520;0.911,-21.960,0.924,-17.977,0.880,-19.108;0.910,-21.002,0.924,-17.772,0.878,-18.630;0.912,-20.099,0.924,-17.086,0.880,-18.022;0.913,-19.066,0.925,-16.162,0.885,-17.265;0.916,-18.159,0.931,-15.317,0.894,-16.568;0.921,-17.580,0.937,-14.561,0.904,-16.094;0.924,-17.469,0.941,-14.249,0.909,-16.067";

    private static float[] parse(String csv) {
        String[] items=csv.replace(';',',').split(",");
        float[] values=new float[items.length];
        for(int i=0;i<items.length;i++)values[i]=Float.parseFloat(items[i]);
        return values;
    }
    private static final float[] Z=parse(RECEDING), C1=parse(FIRST_GRADE), C2=parse(SECOND_GRADE);

    public static float cubicApproach(long sourceUs,long durationUs) {
        double begin=Math.max(0L,durationUs-3_000_000L);
        double t=Math.min(1d,Math.max(0d,(sourceUs-begin)/Math.max(1d,durationUs-begin)));
        return (float)(1+.33*(3*t*t-2*t*t*t));
    }

    private static float slope(int i) {
        if(i<=0)return Z[1]-Z[0];
        if(i>=Z.length-1)return Z[Z.length-1]-Z[Z.length-2];
        float a=Z[i]-Z[i-1],b=Z[i+1]-Z[i];
        if(a*b<=0f)return 0f;
        return 2f*a*b/(a+b);
    }
    /** PCHIP-like monotone Hermite interpolation, indexed by eighth-seconds. */
    public static float recede(long clipElapsedUs) {
        float pos=Math.max(0f,Math.min(Z.length-1,clipElapsedUs/125_000f));
        int index=Math.min(Z.length-2,(int)Math.floor(pos));
        float t=pos-index, a=Z[index],b=Z[index+1];
        float m0=slope(index),m1=slope(index+1);
        float h00=2*t*t*t-3*t*t+1,h10=t*t*t-2*t*t+t;
        float h01=-2*t*t*t+3*t*t,h11=t*t*t-t*t;
        return Math.max(1f,Math.min(1.35f,h00*a+h10*m0+h01*b+h11*m1));
    }

    /** RGB gains/offsets in 8-bit channel units; sample every 0.5s. */
    public static float[] gradeAt(int scene,long sourceElapsedUs) {
        float[] data=scene==0?C1:C2;
        float p=Math.max(0f,Math.min(16f,sourceElapsedUs/500_000f));
        int i=Math.min(15,(int)Math.floor(p));
        float t=p-i;
        // Last calibration point represents frame 191 (7.958s).
        if(i==15)t=Math.min(1f, (p-15f));
        float[] result=new float[6];
        for(int k=0;k<6;k++)
            result[k]=data[i*6+k]*(1-t)+data[(i+1)*6+k]*t;
        return result;
    }
}
