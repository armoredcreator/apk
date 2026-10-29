package com.frame.framematch;

/**
 * Pivot of a single camera movement, shared by both sides of the cut.
 * Coordinates: normalized image space, x=0 left, y=0 top.
 *
 * The validated portrait cut 94->95 (V37) rotated around (240,320) on
 * a 480x864 frame. Media3 MatrixTransformation normally scales around
 * (240,432), which produces the wrong framing even at the correct 33%.
 */
public final class CinematicCameraPivot {
    public final float x, y;
    private CinematicCameraPivot(float x,float y) {
        this.x=x;this.y=y;
    }
    public static CinematicCameraPivot validatedPortrait() {
        return new CinematicCameraPivot(.5f, 320f/864f);
    }
    /** Experimental adaptive camera target for novel cuts. */
    public static CinematicCameraPivot fromFace(RegionMath.FaceBox face) {
        if(face==null || !face.valid())return validatedPortrait();
        // Move the point of interest slightly below the face centre; this
        // preserves a cinematic portrait instead of focusing on the shoulder.
        float cx=RegionMath.clamp(face.x+face.w*.5f,.16f,.84f);
        float cy=RegionMath.clamp(face.y+face.h*.68f,.22f,.56f);
        return new CinematicCameraPivot(cx,cy);
    }
    public float ndcX(){return 2f*x-1f;}
    public float ndcY(){return 1f-2f*y;}
    /** Translate a centred NDC scale so the same chosen image point is fixed. */
    public float dx(float scale){return (1f-scale)*ndcX();}
    public float dy(float scale){return (1f-scale)*ndcY();}
}
