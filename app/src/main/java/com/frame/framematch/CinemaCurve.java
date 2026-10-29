package com.frame.framematch;

/**
 * V37 camera contract for every clip of a multi-clip composition.
 * Each adjacent boundary has its own decision, intensity and pivot.
 * A clip can recede from its incoming boundary AND approach its outgoing
 * boundary without stacking two MatrixTransformations.
 *
 * Pure Java so its math is testable without Android or GPU.
 */
public final class CinemaCurve {
    private CinemaCurve() {}

    public static final class Frame {
        public final float scale, dx, dy;
        Frame(float scale,float dx,float dy){
            this.scale=scale;this.dx=dx;this.dy=dy;
        }
    }
    private static float bound(float n,float low,float high){
        return Math.max(low,Math.min(high,n));
    }
    public static Frame evaluate(long sourceTimeUs,long durationUs,
                                 float incomingPercent,float outgoingPercent,
                                 float incomingPivotNdcX,float incomingPivotNdcY,
                                 float outgoingPivotNdcX,float outgoingPivotNdcY){
        long duration=Math.max(1L,durationUs);
        long t=Math.max(0L,Math.min(duration,sourceTimeUs));
        float in=bound(incomingPercent,0f,40f);
        float out=bound(outgoingPercent,0f,40f);
        float remaining=ZoomTimeline.recede(t,duration,1f+in/100f)-1f;
        float approaching=ZoomTimeline.approach(t,duration,3_000_000L,1f+out/100f)-1f;
        // Continuous composition, not two separately multiplying zoom effects.
        float change=bound((float)Math.hypot(remaining,approaching),0f,.40f);
        if(change<=.000001f)return new Frame(1f,0f,0f);
        float total=remaining+approaching;
        float cx=(remaining*incomingPivotNdcX+approaching*outgoingPivotNdcX)/total;
        float cy=(remaining*incomingPivotNdcY+approaching*outgoingPivotNdcY)/total;
        float zoom=1f+change;
        return new Frame(zoom,(1f-zoom)*cx,(1f-zoom)*cy);
    }
}
