package com.frame.framematch;

import android.graphics.Matrix;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.MatrixTransformation;

/** Only transforms pixels. Never synthesizes faces, hands, motion or other scene content. */
@UnstableApi
public final class ContinuityZoom implements MatrixTransformation {
    private final FaceTimeline track;
    private final RegionMath.FaceBox targetStart, referenceEnd;
    private final float ratio;
    private final long releaseTimeUs;
    private ContinuityZoom(FaceTimeline t,RegionMath.FaceBox r,RegionMath.FaceBox a,
                           float ratio,long releaseTimeUs){
        track=t;referenceEnd=r;targetStart=a;this.ratio=ratio;this.releaseTimeUs=releaseTimeUs;
    }
    public static ContinuityZoom plan(FaceTimeline track,RegionMath.FaceBox end){
        if(end==null||track==null||track.count()<4||track.first()==null)return null;
        RegionMath.FaceBox first=track.first();
        float ratio=end.w/Math.max(.0001f,first.w);
        // Require evidence of an actual move closer, not just a jump cut to a wider shot.
        RegionMath.FaceBox later=track.at(2_000_000L);
        if(later==null || ratio<1.20f || ratio>1.85f || later.w<first.w*1.12f)return null;
        long release=2_800_000L;
        return new ContinuityZoom(track,end,first,ratio,release);
    }
    public float initialZoom(){return ratio;}
    public float zoomAt(long timeUs){
        if(timeUs>=releaseTimeUs)return 1;
        float p=RegionMath.clamp(timeUs/(float)releaseTimeUs,0,1);
        float fade=1-(p*p*(3-2*p));
        RegionMath.FaceBox f=track.at(timeUs);
        float currentRatio=f==null?1:RegionMath.clamp(referenceEnd.w/Math.max(.001f,f.w),1,ratio);
        return 1+(currentRatio-1)*fade;
    }
    public Bitmap preview(Bitmap source){
        float z=zoomAt(0L);
        if(z<=1.001f)return source;
        RegionMath.FaceBox f=track.at(0);
        if(f==null)return source;
        float tx=(f.x+f.w*.5f)*source.getWidth(),ty=(f.y+f.h*.5f)*source.getHeight();
        float rx=(referenceEnd.x+referenceEnd.w*.5f)*source.getWidth();
        float ry=(referenceEnd.y+referenceEnd.h*.5f)*source.getHeight();
        float dx=RegionMath.clamp(rx-z*tx,source.getWidth()*(1-z),0);
        float dy=RegionMath.clamp(ry-z*ty,source.getHeight()*(1-z),0);
        Matrix m=new Matrix();m.setValues(new float[]{z,0,dx,0,z,dy,0,0,1});
        Bitmap rendered=Bitmap.createBitmap(source.getWidth(),source.getHeight(),Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(rendered);c.drawColor(android.graphics.Color.BLACK);
        c.drawBitmap(source,m,new Paint(Paint.FILTER_BITMAP_FLAG));
        return rendered;
    }
    @Override public Matrix getMatrix(long presentationTimeUs){
        Matrix m=new Matrix();float zoom=zoomAt(presentationTimeUs);
        if(zoom<=1.001f)return m;
        RegionMath.FaceBox curr=track.at(presentationTimeUs);
        if(curr==null)return m;
        // NDC coordinates: x [-1,1], y top positive via conversion here.
        float tx=2*(curr.x+curr.w*.5f)-1,ty=1-2*(curr.y+curr.h*.5f);
        float rx=2*(referenceEnd.x+referenceEnd.w*.5f)-1;
        float ry=1-2*(referenceEnd.y+referenceEnd.h*.5f);
        float shiftX=RegionMath.clamp(rx-zoom*tx,1-zoom,zoom-1);
        float shiftY=RegionMath.clamp(ry-zoom*ty,1-zoom,zoom-1);
        m.setValues(new float[]{zoom,0,shiftX, 0,zoom,shiftY, 0,0,1});
        return m;
    }
}
