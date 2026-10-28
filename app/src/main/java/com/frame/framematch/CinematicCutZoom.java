package com.frame.framematch;

import android.graphics.Matrix;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.MatrixTransformation;

/**
 * Experimental V39-target camera movement.
 * The entrance is zoomed before the cut. Scene 2 starts at the exact peak
 * and recedes along the full retained scene while tracking a detected face.
 * Never reset position or scale at an intermediate timestamp.
 *
 * Pixel parity with the approved desktop V39 is not yet demonstrated.
 */
@UnstableApi
public final class CinematicCutZoom implements MatrixTransformation {
    private final boolean approaching;
    private final CinematicTimebase timebase;
    private final long clipStartUs;
    private final long durationUs;
    private final float peak;
    private final FaceTimeline trackedFace;
    private final RegionMath.FaceBox previousFace;

    private CinematicCutZoom(boolean approaching, long startMs, long durationMs,
                             float zoomPercent, RegionMath.FaceBox anchor,
                             FaceTimeline faceTrack) {
        this.approaching = approaching;
        this.timebase = new CinematicTimebase(startMs);
        this.clipStartUs = Math.max(0L,startMs) * 1000L;
        this.durationUs = Math.max(1L,durationMs) * 1000L;
        this.peak = 1f + Math.max(0f, Math.min(0.5f, zoomPercent / 100f));
        this.previousFace = anchor;
        this.trackedFace = faceTrack;
    }

    public static CinematicCutZoom before(long startMs, long durationMs, float zoomPercent) {
        return new CinematicCutZoom(true,startMs,durationMs,zoomPercent,null,null);
    }

    public static CinematicCutZoom after(long startMs, long durationMs, float zoomPercent) {
        return new CinematicCutZoom(false,startMs,durationMs,zoomPercent,null,null);
    }

    public static CinematicCutZoom after(long startMs, long durationMs, float zoomPercent,
                                          RegionMath.FaceBox anchor, FaceTimeline track) {
        return new CinematicCutZoom(false,startMs,durationMs,zoomPercent,anchor,track);
    }

    private long sceneUs(long presentationTimeUs) {
        long sourceUs=timebase.sourceTimeUs(presentationTimeUs);
        return approaching ? sourceUs : Math.max(0L,sourceUs-clipStartUs);
    }

    private long visibleDurationUs() { return Math.max(1L,durationUs-clipStartUs); }

    public float scaleAt(long presentationTimeUs) {
        long timeUs=sceneUs(presentationTimeUs);
        return approaching
            ? ZoomTimeline.approach(timeUs,durationUs,3_000_000L,peak)
            : ZoomTimeline.recede(timeUs,visibleDurationUs(),peak);
    }

    private float[] facePosition(long sceneTimeUs) {
        RegionMath.FaceBox first=trackedFace.atHolding(0L);
        if(first==null) return null;
        RegionMath.FaceBox prev=trackedFace.atHolding(Math.max(0L,sceneTimeUs-250_000L));
        RegionMath.FaceBox now=trackedFace.atHolding(sceneTimeUs);
        RegionMath.FaceBox next=trackedFace.atHolding(sceneTimeUs+250_000L);
        if(prev==null||now==null||next==null) return null;
        float averageX=.25f*(prev.x+prev.w*.5f)+.5f*(now.x+now.w*.5f)
            +.25f*(next.x+next.w*.5f);
        float averageY=.25f*(prev.y+prev.h*.5f)+.5f*(now.y+now.h*.5f)
            +.25f*(next.y+next.h*.5f);
        // Exact source centre on first frame, then a gentle measurement filter.
        float u=Math.min(1f,sceneTimeUs/400_000f);
        float smooth=u*u*(3f-2f*u);
        return new float[]{
            first.x+first.w*.5f+(averageX-first.x-first.w*.5f)*smooth,
            first.y+first.h*.5f+(averageY-first.y-first.h*.5f)*smooth
        };
    }

    @Override public Matrix getMatrix(long presentationTimeUs) {
        long localUs=sceneUs(presentationTimeUs);
        float scale=approaching
            ? ZoomTimeline.approach(localUs,durationUs,3_000_000L,peak)
            : ZoomTimeline.recede(localUs,visibleDurationUs(),peak);
        Matrix matrix=new Matrix();
        if(!approaching&&previousFace!=null&&previousFace.valid()&&trackedFace!=null){
            float[] current=facePosition(localUs);
            if(current!=null){
                float progress=Math.min(1f,localUs/(float)visibleDurationUs());
                float[] shift=CinematicFollowMath.translation(scale,progress,
                    previousFace.x+previousFace.w*.5f,
                    previousFace.y+previousFace.h*.5f,current[0],current[1]);
                matrix.setValues(new float[]{
                    scale,0f,shift[0], 0f,scale,shift[1], 0f,0f,1f
                });
                return matrix;
            }
        }
        matrix.setScale(scale,scale);
        return matrix;
    }
}
