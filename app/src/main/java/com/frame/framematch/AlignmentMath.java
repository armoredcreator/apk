package com.frame.framematch;

/** Conservative face-to-face affine alignment, pure Java for regression tests. */
public final class AlignmentMath {
    private AlignmentMath(){}
    public static final class Pose {
        public final float x,y,eyeDistance,angle;
        public Pose(float x,float y,float eyeDistance,float angle){
            this.x=x;this.y=y;this.eyeDistance=eyeDistance;this.angle=angle;
        }
        public boolean valid(){return x>=0&&x<=1&&y>=0&&y<=1&&eyeDistance>.025f&&eyeDistance<.8f;}
    }
    public static final class Transform {
        public final float zoom,dx,dy,rotation;
        Transform(float z,float dx,float dy,float angle){zoom=z;this.dx=dx;this.dy=dy;rotation=angle;}
        public static Transform identity(){return new Transform(1,0,0,0);}
        public float displacement(){return (float)Math.hypot(dx,dy);}
    }
    public static Transform estimate(Pose reference,Pose target,float strength){
        if(reference==null||target==null||!reference.valid()||!target.valid())return Transform.identity();
        float factor=RegionMath.clamp(strength,0,1);
        float desired=reference.eyeDistance/target.eyeDistance;
        // prevent aggressive zoom and distorted crops
        if(desired<.82f||desired>1.20f)return Transform.identity();
        float angle=reference.angle-target.angle;
        if(Math.abs(angle)>.11f)return Transform.identity(); // ~6 degrees
        float z=1+(desired-1)*factor;
        float rot=angle*factor;
        float cos=(float)Math.cos(rot),sin=(float)Math.sin(rot);
        float targetX=2*target.x-1,targetY=1-2*target.y;
        float refX=2*reference.x-1,refY=1-2*reference.y;
        float tx=refX-z*(cos*targetX-sin*targetY);
        float ty=refY-z*(sin*targetX+cos*targetY);
        // Large translations cause black edges, slipping background and unnatural movement.
        if(Math.abs(tx)>.16f||Math.abs(ty)>.16f)return Transform.identity();
        return new Transform(z,tx,ty,rot);
    }
    public static float fade(long timeUs,long releaseUs){
        if(timeUs>=releaseUs)return 0;
        float t=RegionMath.clamp(timeUs/(float)Math.max(1,releaseUs),0,1);
        return 1-t*t*(3-2*t);
    }
}
