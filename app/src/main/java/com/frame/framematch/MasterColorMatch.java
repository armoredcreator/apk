package com.frame.framematch;

/**
 * Global master-color policy: Clip 1 is the only palette reference.
 * Each later clip is calibrated independently against it, never against
 * the previous clip or a previous clip's grade.
 *
 * Scene alignment and movement are intentionally outside this class.
 */
public final class MasterColorMatch {
    private MasterColorMatch() {}

    public static final class Sample {
        public final int[] argb;
        public final int width, height;
        public final RegionMath.FaceBox face;

        public Sample(int[] argb,int width,int height,RegionMath.FaceBox face){
            if(argb==null||width<32||height<32||argb.length!=width*height)
                throw new IllegalArgumentException("Quadro inválido");
            this.argb=argb;
            this.width=width;
            this.height=height;
            this.face=face;
        }
    }

    /** Both master samples MUST originate from Clip 1. */
    public static RegionMath.Plan calibrate(Sample masterLast,Sample masterNearEnd,
                                             Sample targetFirst,Sample targetEarly,
                                             float strength){
        RegionMath.Plan boundary=RegionMath.analyze(
            masterLast.argb,masterLast.width,masterLast.height,masterLast.face,
            targetFirst.argb,targetFirst.width,targetFirst.height,targetFirst.face,strength);
        RegionMath.Plan nearby=RegionMath.analyze(
            masterNearEnd.argb,masterNearEnd.width,masterNearEnd.height,masterNearEnd.face,
            targetEarly.argb,targetEarly.width,targetEarly.height,targetEarly.face,strength);
        return RegionMath.blendStable(boundary,nearby);
    }
}
