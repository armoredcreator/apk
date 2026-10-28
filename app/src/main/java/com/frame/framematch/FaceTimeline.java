package com.frame.framematch;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Sampled on the worker thread, never on the UI thread. No face identification. */
public final class FaceTimeline {
    public static final class Sample {
        public final long timeUs;public final RegionMath.FaceBox box;
        public Sample(long t,RegionMath.FaceBox b){timeUs=t;box=b;}
    }
    private final List<Sample> samples=new ArrayList<>();
    public void add(long timeUs,RegionMath.FaceBox b){if(b!=null&&b.valid())samples.add(new Sample(timeUs,b));}
    public int count(){return samples.size();}
    public RegionMath.FaceBox at(long timeUs){
        if(samples.isEmpty())return null;
        if(timeUs<=samples.get(0).timeUs)return samples.get(0).box;
        for(int i=1;i<samples.size();i++){
            Sample right=samples.get(i),left=samples.get(i-1);
            if(timeUs<=right.timeUs){
                float p=RegionMath.clamp((timeUs-left.timeUs)/(float)Math.max(1,right.timeUs-left.timeUs),0,1);
                return new RegionMath.FaceBox(mix(left.box.x,right.box.x,p),mix(left.box.y,right.box.y,p),
                        mix(left.box.w,right.box.w,p),mix(left.box.h,right.box.h,p));
            }
        }
        // Don't assume the last detected position is valid forever; disable face masks later.
        return timeUs-samples.get(samples.size()-1).timeUs>350_000L?null:samples.get(samples.size()-1).box;
    }
    private static float mix(float a,float b,float p){return a+(b-a)*p;}
    public RegionMath.FaceBox first(){return samples.isEmpty()?null:samples.get(0).box;}
    public RegionMath.FaceBox last(){return samples.isEmpty()?null:samples.get(samples.size()-1).box;}

    public static RegionMath.FaceBox detect(FaceDetector detector,Bitmap bitmap) throws Exception {
        if(bitmap==null)return null;
        List<Face> faces=Tasks.await(detector.process(InputImage.fromBitmap(bitmap,0)),12,TimeUnit.SECONDS);
        Face biggest=null;int area=0;
        for(Face f:faces){Rect r=f.getBoundingBox();int a=r.width()*r.height();if(a>area){biggest=f;area=a;}}
        if(biggest==null)return null;
        Rect r=biggest.getBoundingBox();
        return new RegionMath.FaceBox(RegionMath.clamp(r.left/(float)bitmap.getWidth(),0,1),
            RegionMath.clamp(r.top/(float)bitmap.getHeight(),0,1),
            RegionMath.clamp(r.width()/(float)bitmap.getWidth(),0,1),
            RegionMath.clamp(r.height()/(float)bitmap.getHeight(),0,1));
    }
    public static FaceDetector createDetector(){
        return FaceDetection.getClient(new FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setMinFaceSize(.10f).build());
    }
    public static FaceTimeline scan(Context ctx,Uri video,long maxDurationUs,FaceDetector detector) throws Exception {
        FaceTimeline result=new FaceTimeline();
        MediaMetadataRetriever mm=new MediaMetadataRetriever();
        try{
            mm.setDataSource(ctx,video);
            long lengthUs=Long.parseLong(mm.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))*1000L;
            long max=Math.min(lengthUs-1,Math.max(0,maxDurationUs));
            long step=Math.max(300_000L, (max+49L)/50L);
            for(long t=0;t<=max;t+=step){
                Bitmap bmp=mm.getFrameAtTime(t,MediaMetadataRetriever.OPTION_CLOSEST);
                if(bmp==null)continue;
                try{result.add(t,detect(detector,bmp));}finally{bmp.recycle();}
            }
        }finally{mm.release();}
        return result;
    }
}
