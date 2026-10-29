package com.frame.framematch;

/**
 * Conservative cut-boundary correspondence, in normalized coordinates.
 *
 * The LAST visible frame of scene 1 is the reference. The FIRST retained
 * frame of scene 2 is aligned at t=0 before the cinematic zoom is applied.
 * Independent X/Y scale allows small width mismatches, but refuses large
 * distortions, big translations and unreliable/missing faces.
 */
public final class CutFaceMath {
    private CutFaceMath() {}

    public static final class Plan {
        public final float sx, sy, dx, dy;
        private Plan(float sx, float sy, float dx, float dy) {
            this.sx=sx;this.sy=sy;this.dx=dx;this.dy=dy;
        }
        public boolean active() {
            return Math.abs(sx-1f)>.0005f || Math.abs(sy-1f)>.0005f
                || Math.abs(dx)>.0005f || Math.abs(dy)>.0005f;
        }
        /** C2 smooth fade: cut is fully aligned, scene end is original. */
        public float[] at(long elapsedUs,long fullDurationUs) {
            // Alignment should finish before the camera completes its recede.
            long releaseUs=Math.max(400_000L,
                Math.min(6_000_000L,Math.round(fullDurationUs*.80)));
            float p=Math.max(0f,Math.min(1f,elapsedUs/(float)releaseUs));
            float smooth=p*p*p*(p*(p*6f-15f)+10f);
            float a=1f-smooth;
            return new float[]{1f+(sx-1f)*a,1f+(sy-1f)*a,dx*a,dy*a};
        }
    }
    public static Plan identity(){return new Plan(1f,1f,0f,0f);}
    private static boolean valid(float x,float y,float w,float h) {
        return Float.isFinite(x)&&Float.isFinite(y)&&Float.isFinite(w)&&Float.isFinite(h)
            &&x>=0&&y>=0&&w>=.08f&&h>=.08f
            &&x+w<=1.001f&&y+h<=1.001f;
    }
    private static float clip(float n,float min,float max) {
        return Math.min(max,Math.max(min,n));
    }
    public static Plan estimate(float rx,float ry,float rw,float rh,
                                float tx,float ty,float tw,float th) {
        if(!valid(rx,ry,rw,rh)||!valid(tx,ty,tw,th))return identity();
        float ratioX=rw/tw,ratioY=rh/th;
        // Large differences likely mean another pose or face. Do not stretch.
        if(ratioX<.88f||ratioX>1.12f||ratioY<.88f||ratioY>1.12f)
            return identity();
        float sx=clip(ratioX,.95f,1.06f);
        float sy=clip(ratioY,.95f,1.06f);
        // MatrixTransformation is in normalized device coordinates (NDC).
        float refX=2f*(rx+rw*.5f)-1f;
        float refY=1f-2f*(ry+rh*.5f);
        float targetX=2f*(tx+tw*.5f)-1f;
        float targetY=1f-2f*(ty+th*.5f);
        float dx=refX-sx*targetX,dy=refY-sy*targetY;
        // The 33% cinematic zoom hides modest border displacement at cut.
        if(Math.abs(dx)>.12f||Math.abs(dy)>.12f)return identity();
        return new Plan(sx,sy,dx,dy);
    }
}
