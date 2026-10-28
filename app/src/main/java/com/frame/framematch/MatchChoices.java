package com.frame.framematch;

/**
 * Five deterministic alternatives for each edit point.
 * No trained model and no claim of perceptual optimality: the user selects the look.
 * Every alternative remains constrained by the safety bounds in RegionMath/RegionEffect.
 */
public final class MatchChoices {
    public static final int ORIGINAL=0, SOFT=1, BALANCED=2, SCENERY=3, SKIN=4;
    public static final int COUNT=5;
    public static final String[] LABELS={
        "Original", "Suave", "Equilibrada", "Priorizar cenário", "Priorizar pele"
    };
    private MatchChoices(){}

    public static boolean valid(int mode){return mode>=0&&mode<COUNT;}

    /** Scale gains toward identity; enables selected region corrections only. */
    private static RegionMath.Plan scaled(RegionMath.Plan base, float background,
                                           float sky, float skin, float lips) {
        float[][] gains=new float[4][3];
        boolean[] calibrated=base.calibrated.clone();
        float[] scale={background,sky,skin,lips};
        for(int k=0;k<4;k++) {
            if(k>0 && scale[k]<=0f)calibrated[k]=false;
            for(int c=0;c<3;c++) {
                float t=RegionMath.clamp(scale[k],0f,1f);
                gains[k][c]=1f+(base.gains[k][c]-1f)*t;
            }
        }
        return new RegionMath.Plan(gains,calibrated,base.strength);
    }

    public static RegionMath.Plan[] candidates(RegionMath.Plan calibrated) {
        if(calibrated==null)throw new IllegalArgumentException("Calibração ausente");
        return new RegionMath.Plan[]{
            RegionMath.Plan.identity(),
            scaled(calibrated,.45f,.45f,.45f,.45f),
            scaled(calibrated,1f,1f,1f,1f),
            scaled(calibrated,.9f,1f,0f,0f),
            scaled(calibrated,.3f,0f,1f,1f)
        };
    }
}
