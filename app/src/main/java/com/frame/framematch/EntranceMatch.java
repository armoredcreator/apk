package com.frame.framematch;

/** Rank candidate entrance frames by adjacent face pose, before color matching. */
public final class EntranceMatch {
    private EntranceMatch(){}
    public static final class Choice {
        public final long offsetMs;
        public final float score;
        Choice(long time,float score){offsetMs=time;this.score=score;}
    }
    public static Choice choose(AlignmentMath.Pose reference,
                                AlignmentMath.Pose[] targets,long[] offsetsMs) {
        if(targets==null||offsetsMs==null||targets.length!=offsetsMs.length||targets.length==0)
            throw new IllegalArgumentException("Amostras inválidas");
        Choice best=new Choice(0,Float.POSITIVE_INFINITY);
        for(int i=0;i<targets.length;i++){
            float score=score(reference,targets[i]);
            if(score<best.score)best=new Choice(offsetsMs[i],score);
        }
        return best;
    }
    public static float score(AlignmentMath.Pose a,AlignmentMath.Pose b){
        if(a==null||b==null||!a.valid()||!b.valid())return Float.POSITIVE_INFINITY;
        float pos=(float)Math.hypot(a.x-b.x,a.y-b.y);
        float size=Math.abs((float)Math.log(a.eyeDistance/b.eyeDistance));
        float angle=Math.abs(a.angle-b.angle);
        return pos*2.5f+size*.65f+angle*.55f;
    }
    public static boolean safe(Choice choice){
        // If no trustworthy correspondence exists, do not trim a potentially good scene.
        return choice!=null&&choice.offsetMs>=0&&choice.offsetMs<=1500&&choice.score<.32f;
    }
}
