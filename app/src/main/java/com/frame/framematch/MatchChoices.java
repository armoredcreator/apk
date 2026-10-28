package com.frame.framematch;

/**
 * Five deterministic alternatives for each edit point.
 * No trained model and no claim of perceptual optimality: the user selects the look.
 * Every alternative remains constrained by the safety bounds in RegionMath/RegionEffect.
 */
public final class MatchChoices {
    public static final int ORIGINAL=0, SOFT=1, BALANCED=2, SCENERY=3, SKIN=4, CUSTOM=5;
    public static final int COUNT=6;
    public static final String[] LABELS={
        "Original", "Suave", "Equilibrada", "Priorizar cenário", "Priorizar pele", "Ajuste fino"
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
                float t=RegionMath.clamp(scale[k],0f,1.5f);
                gains[k][c]=1f+(base.gains[k][c]-1f)*t;
            }
        }
        return new RegionMath.Plan(gains,calibrated,base.strength);
    }

    /** Per-cut intensity: 0=unaltered, 100=selected look, 150=amplified conservatively.
     * The shared GPU+CPU safeguards still clamp the total output gains.
     */
    public static RegionMath.Plan intensity(RegionMath.Plan source,int percent) {
        float factor=RegionMath.clamp(percent,0,150)/100f;
        float[][] adjusted=new float[4][3];
        for(int k=0;k<4;k++)for(int c=0;c<3;c++) {
            adjusted[k][c]=1f+(source.gains[k][c]-1f)*factor;
        }
        return new RegionMath.Plan(adjusted,source.calibrated.clone(),source.strength);
    }

    public static RegionMath.Plan[] candidates(RegionMath.Plan calibrated,int percent) {
        return candidates(calibrated,percent,new int[]{100,100,100,100});
    }
    public static RegionMath.Plan[] candidates(RegionMath.Plan calibrated,int percent,int[] levels) {
        if(levels==null||levels.length!=4)throw new IllegalArgumentException("Quatro controles de cor necessários");
        RegionMath.Plan[] result=candidates(calibrated);
        result[CUSTOM]=scaled(calibrated,levels[0]/100f,levels[1]/100f,
                             levels[2]/100f,levels[3]/100f);
        for(int i=0;i<result.length;i++)result[i]=intensity(result[i],percent);
        return result;
    }

    public static RegionMath.Plan[] candidates(RegionMath.Plan calibrated) {
        if(calibrated==null)throw new IllegalArgumentException("Calibração ausente");
        return new RegionMath.Plan[]{
            RegionMath.Plan.identity(),
            scaled(calibrated,.45f,.45f,.45f,.45f),
            scaled(calibrated,1f,1f,1f,1f),
            scaled(calibrated,.9f,1f,0f,0f),
            scaled(calibrated,.3f,0f,1f,1f),
            scaled(calibrated,1f,1f,1f,1f)
        };
    }
}
