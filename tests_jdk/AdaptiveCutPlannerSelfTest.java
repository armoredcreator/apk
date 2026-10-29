import com.frame.framematch.AdaptiveCutPlanner;
import com.frame.framematch.RegionMath;

public final class AdaptiveCutPlannerSelfTest {
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
    static int[] solid(int w,int h,int rgb){
        int[] result=new int[w*h];
        java.util.Arrays.fill(result,0xff000000 | rgb);
        return result;
    }
    public static void main(String[] args){
        int w=96,h=172;
        int[] base=solid(w,h,0x484848);
        int[] different=solid(w,h,0x747474);
        RegionMath.FaceBox face=new RegionMath.FaceBox(.25f,.15f,.48f,.45f);
        AdaptiveCutPlanner.Decision clean=AdaptiveCutPlanner.decide(
            base,w,h,base,w,h,face,face,8000,8000);
        check(clean.action==AdaptiveCutPlanner.Action.KEEP,"Already matching must remain untouched");
        AdaptiveCutPlanner.Decision manual=AdaptiveCutPlanner.decide(
            base,w,h,different,w,h,face,face,8000,8000);
        check(manual.action==AdaptiveCutPlanner.Action.ZOOM,"Detect visible mismatch");
        check(manual.zoomPercent>=12 && manual.zoomPercent<=33,"Zoom must be bounded/adaptive");
        check(manual.visualDifference>.04f,"Actual decoded frames drive choice");
        AdaptiveCutPlanner.Decision unknown=AdaptiveCutPlanner.decide(
            base,w,h,different,w,h,null,null,8000,8000);
        check(unknown.action!=AdaptiveCutPlanner.Action.ZOOM,
              "Unknown face cannot trigger unsafe automatic zoom");
        RegionMath.FaceBox displaced=new RegionMath.FaceBox(.65f,.7f,.28f,.20f);
        AdaptiveCutPlanner.Decision unstable=AdaptiveCutPlanner.decide(
            base,w,h,different,w,h,face,displaced,8000,8000);
        check(unstable.action==AdaptiveCutPlanner.Action.REVIEW,"Mismatch of geometry must be reviewed");
        AdaptiveCutPlanner.Decision shortClip=AdaptiveCutPlanner.decide(
            base,w,h,different,w,h,face,face,1900,8000);
        check(shortClip.action==AdaptiveCutPlanner.Action.REVIEW,"Avoid unsafe short zoom ramp");
        AdaptiveCutPlanner.Decision malformed=AdaptiveCutPlanner.decide(
            new int[3],w,h,different,w,h,face,face,8000,8000);
        check(malformed.action==AdaptiveCutPlanner.Action.REVIEW,"Invalid frames must be rejected");
        check(AdaptiveCutPlanner.frameDifference(base,w,h,base,w,h)==0f,
              "Zero mismatch on identical frames");
        System.out.println("AdaptiveCutPlannerSelfTest PASS");
    }
}
