package com.frame.framematch;

import android.graphics.Matrix;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.MatrixTransformation;

/**
 * V37 cinematic camera curve. Only uniform zoom; no squashing of faces.
 * Incoming: starts at the EXACT same zoom as previous clip ended; slowly
 * restores original framing by the end of the clip. Outgoing: starts before
 * the next cut and reaches the same zoom at its final frame.
 *
 * Pairwise effects can be composed on an intermediate clip. Each cut is
 * independent. This effect intentionally does not alter color.
 */
@UnstableApi
public final class CinematicCutZoom implements MatrixTransformation {
    public enum Phase { APPROACH, RETREAT }
    private final Phase phase;
    private final long lengthUs;
    private final float maxZoom;
    private final long approachUs;
    private final float centerX, centerY;
    private CinematicCutZoom(Phase phase, long lengthUs, float maxZoom,
                             long approachUs, float cx, float cy) {
        this.phase=phase; this.lengthUs=Math.max(1,lengthUs);
        this.maxZoom=Math.max(1f,Math.min(1.5f,maxZoom));
        this.approachUs=Math.min(this.lengthUs,Math.max(1,approachUs));
        this.centerX=Math.max(-.7f,Math.min(.7f,cx));
        this.centerY=Math.max(-.7f,Math.min(.7f,cy));
    }
    public static CinematicCutZoom outgoing(long lengthUs,float zoom,float cx,float cy) {
        return new CinematicCutZoom(Phase.APPROACH,lengthUs,zoom,3_000_000L,cx,cy);
    }
    public static CinematicCutZoom incoming(long lengthUs,float zoom,float cx,float cy) {
        return new CinematicCutZoom(Phase.RETREAT,lengthUs,zoom,3_000_000L,cx,cy);
    }
    private static float smooth(float v) {
        float t=Math.max(0f,Math.min(1f,v));
        return t*t*(3f-2f*t);
    }
    public float zoomAt(long timeUs) {
        if(phase==Phase.APPROACH) {
            long start=lengthUs-approachUs;
            return 1f+(maxZoom-1f)*smooth((timeUs-start)/(float)approachUs);
        }
        // The zoom is already at its maximum at t=0. The retreat is continuous
        // for the full second scene; it never jumps or switches at 2 seconds.
        return 1f+(maxZoom-1f)*(1f-smooth(timeUs/(float)lengthUs));
    }
    @Override public Matrix getMatrix(long presentationTimeUs) {
        float z=zoomAt(presentationTimeUs);
        Matrix m=new Matrix();
        m.setValues(new float[]{z,0f,(1f-z)*centerX,
                                 0f,z,(1f-z)*centerY,
                                 0f,0f,1f});
        return m;
    }
}
