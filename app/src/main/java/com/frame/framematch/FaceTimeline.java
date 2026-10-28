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
import com.google.mlkit.vision.face.FaceLandmark;
import android.graphics.PointF;
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
    /** Adjust face-mask sample timestamps after MediaItem source clipping.
     * The first retained sample is at t=0; samples before trim are discarded.
     */
    public FaceTimeline shifted(long clipStartUs){
        if(clipStartUs<=0)return this;
        FaceTimeline result=new FaceTimeline();
        RegionMath.FaceBox atCut=at(clipStartUs);
        result.add(0,atCut);
        for(Sample sample:samples){
            if(sample.timeUs>clipStartUs)result.add(sample.timeUs-clipStartUs,sample.box);
        }
        return result;
    }
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
    /**
     * Subject tracking needs a continuous centre even past the final valid
     * detection. Do not alter at(): the color mask still times out normally.
     * This never introduces a new face position when the detector is missing.
     */
    public RegionMath.FaceBox atHolding(long timeUs) {
        if(samples.isEmpty())return null;
        if(timeUs>=samples.get(samples.size()-1).timeUs)
            return samples.get(samples.size()-1).box;
        return at(timeUs);
    }
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
    /** Eye midpoint and inter-eye angle at the cut boundary. Falls back to box when landmarks unavailable. */
    public static AlignmentMath.Pose pose(FaceDetector detector,Bitmap bitmap) throws Exception {
        if(bitmap==null)return null;
        List<Face> faces=Tasks.await(detector.process(InputImage.fromBitmap(bitmap,0)),12,TimeUnit.SECONDS);
        Face biggest=null;int best=0;
        for(Face face:faces){
            Rect rect=face.getBoundingBox();int area=rect.width()*rect.height();
            if(area>best){best=area;biggest=face;}
        }
        if(biggest==null)return null;
        FaceLandmark left=biggest.getLandmark(FaceLandmark.LEFT_EYE);
        FaceLandmark right=biggest.getLandmark(FaceLandmark.RIGHT_EYE);
        if(left==null||right==null)return null;
        PointF a=left.getPosition(),b=right.getPosition();
        float w=bitmap.getWidth(),h=bitmap.getHeight();
        float distance=(float)Math.hypot(b.x-a.x,b.y-a.y)/w;
        float angle=(float)Math.atan2(-(b.y-a.y),b.x-a.x);
        AlignmentMath.Pose pose=new AlignmentMath.Pose(
            RegionMath.clamp((a.x+b.x)/(2*w),0,1),
            RegionMath.clamp((a.y+b.y)/(2*h),0,1),distance,angle);
        return pose.valid()?pose:null;
    }

    public static FaceDetector createDetector(){
        return FaceDetection.getClient(new FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
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
