package com.frame.framematch;

import java.util.Arrays;

/** Deterministic, conservative RGB frame matching. No network or ML. */
public final class ColorMath {
    private ColorMath() {}

    public static final class Gains {
        public final float r, g, b;
        public Gains(float r, float g, float b) {
            this.r = r; this.g = g; this.b = b;
        }
        @Override public String toString() {
            return String.format(java.util.Locale.US, "R %.3f  G %.3f  B %.3f", r,g,b);
        }
    }

    static float clamp(float n, float lo, float hi) { return Math.max(lo, Math.min(hi,n)); }

    /**
     * Compares the last decoded frame of video N (already graded) to the first of N+1.
     * Samples midtones and returns global RGB scale factors. Refuses to exaggerate grades.
     * These are not spatial/semantic corrections or a replacement for pro color grading.
     */
    public static Gains match(int[] reference, int[] target, float strength) {
        if(reference.length < 64 || target.length < 64) throw new IllegalArgumentException("Empty frames");
        float[] a = robustRgb(reference), b = robustRgb(target);
        float s = clamp(strength, 0f, 1f);
        return new Gains(
            1 + (clamp(a[0]/Math.max(18,b[0]), .76f,1.29f)-1)*s,
            1 + (clamp(a[1]/Math.max(18,b[1]), .76f,1.29f)-1)*s,
            1 + (clamp(a[2]/Math.max(18,b[2]), .76f,1.29f)-1)*s
        );
    }

    private static float[] robustRgb(int[] argb) {
        // Equal sampling density for all clips; skip extreme black/white areas.
        int max = Math.min(4096,argb.length);
        int[][] px = new int[3][max]; int n=0;
        for(int i=0;i<max;i++) {
            int color=argb[(int)((long)i*argb.length/max)];
            int r=(color>>>16)&255, g=(color>>>8)&255, b=color&255;
            int y=(54*r+183*g+19*b)>>8;
            if(y<20 || y>242) continue;
            px[0][n]=r; px[1][n]=g;px[2][n]=b; n++;
        }
        if(n<50) { // Very dark or bright: remain conservative by using all pixels.
            n=max;
            for(int i=0;i<max;i++) {
                int c=argb[(int)((long)i*argb.length/max)];
                px[0][i]=(c>>>16)&255;px[1][i]=(c>>>8)&255;px[2][i]=c&255;
            }
        }
        float[] v=new float[3];
        for(int c=0;c<3;c++) {
            Arrays.sort(px[c],0,n);
            int low=n/5,high=n-low;
            double sum=0;
            for(int i=low;i<high;i++)sum+=px[c][i];
            v[c]=(float)(sum/Math.max(1,high-low));
        }
        return v;
    }

    public static int[] apply(int[] argb, Gains gains) {
        int[] output=argb.clone();
        for(int i=0;i<output.length;i++) {
            int c=output[i];
            int r=Math.round(((c>>>16)&255)*gains.r);
            int g=Math.round(((c>>>8)&255)*gains.g);
            int b=Math.round((c&255)*gains.b);
            output[i]=(c&0xff000000) | ((int)clamp(r,0,255)<<16) |
                    ((int)clamp(g,0,255)<<8) | (int)clamp(b,0,255);
        }
        return output;
    }
}
