import com.frame.framematch.CinematicFollowMath;
import com.frame.framematch.ZoomTimeline;

/**
 * Pure-Java behavioural tests. These are NOT visual V39 certification.
 */
public final class CinematicFollowMathSelfTest {
    private static void ok(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }
    private static void near(float a,float b,float tolerance,String message) {
        ok(Math.abs(a-b)<=tolerance,message+" actual="+a+" wanted="+b);
    }
    public static void main(String[] args) {
        float peak=1.33f;
        float[] cut=CinematicFollowMath.translation(peak,0f,.55f,.50f,.50f,.50f);
        near(cut[0],.133f,.00001f,"align face X on first scene-2 frame");
        near(cut[1],0f,.00001f,"no accidental face Y shift");
        float[] identical=CinematicFollowMath.translation(peak,0f,.50f,.50f,.50f,.50f);
        near(identical[0],0f,1e-6f,"do not add unnecessary offset");

        for(int k=0;k<=192;k++){
            float t=k/192f;
            float scale=ZoomTimeline.recede((long)(8_000_000L*t),8_000_000L,peak);
            // Subject drifts horizontally, then returns naturally.
            float motion=(float)Math.sin(Math.PI*t);
            float observedX=.5f-.18f*motion;
            float[] shift=CinematicFollowMath.translation(scale,t,.5f,.5f,observedX,.5f);
            float margin=scale-1f;
            ok(Math.abs(shift[0])<=margin+1e-6f,"x exposes black edge "+k);
            ok(Math.abs(shift[1])<=margin+1e-6f,"y exposes black edge "+k);
            ok(shift[0]>=-1e-6f,"tracked movement wrong direction "+k);
            ok(Float.isFinite(shift[0]),"non-finite matrix "+k);
            // Tracking must reduce framing error while respecting the crop.
            float before=Math.abs(2f*scale*(observedX-.5f));
            float after=Math.abs(2f*scale*(observedX-.5f)+shift[0]);
            ok(after<=before+1e-5f,"tracking worsens framing "+k);
            if(k==192)near(shift[0],0f,1e-6f,"full-frame position restored");
        }
        float previous=0f;
        for(int k=0;k<=192;k++){
            float t=k/192f;
            float scale=ZoomTimeline.recede((long)(8_000_000L*t),8_000_000L,peak);
            float observedX=.5f-.14f*(float)Math.sin(Math.PI*t);
            float current=CinematicFollowMath.translation(scale,t,.5f,.5f,observedX,.5f)[0];
            if(k>0)ok(Math.abs(current-previous)<.015f,"frame shift jump at "+k);
            previous=current;
        }
        near(CinematicFollowMath.translation(1f,1f,.5f,.5f,.1f,.2f)[0],0f,1e-6f,"identity at end");
        near(CinematicFollowMath.translation(1.2f,.5f,Float.NaN,.5f,.1f,.2f)[0],0f,1e-6f,
             "fail closed if landmarks invalid");
        System.out.println("CinematicFollowMathSelfTest OK: tracked, bounded, smooth, returns to identity");
    }
}
