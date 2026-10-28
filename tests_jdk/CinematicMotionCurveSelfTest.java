import com.frame.framematch.CinematicMotionCurve;

public final class CinematicMotionCurveSelfTest {
    private static final long SEC=1000000L;
    private static void near(float a,float b,float tolerance,String label) {
        if(Math.abs(a-b)>tolerance) {
            throw new AssertionError(label+" expected "+a+" got "+b);
        }
    }

    public static void main(String[] args) {
        final long duration=8*SEC,ramp=3*SEC;
        final float peak=1.33f;
        near(1f,CinematicMotionCurve.approach(0,duration,ramp,peak),.00001f,"original first frame");
        near(1f,CinematicMotionCurve.approach(5*SEC,duration,ramp,peak),.00001f,"3s before cut");
        near(peak,CinematicMotionCurve.approach(duration,duration,ramp,peak),.00001f,"final frame");
        near(peak,CinematicMotionCurve.recede(0,duration,peak),.00001f,"first frame after cut");
        near(1f,CinematicMotionCurve.recede(duration,duration,peak),.00001f,"last frame full composition");
        near(CinematicMotionCurve.approach(duration,duration,ramp,peak),
            CinematicMotionCurve.recede(0,duration,peak),.00001f,"cut zoom must be identical");

        float previous=peak,maxStep=0f;
        for(int frame=1;frame<=192;frame++){
            float z=CinematicMotionCurve.recede(frame*SEC/24L,duration,peak);
            if (z>previous+.000001f) throw new AssertionError("unexpected zoom-forward at "+frame);
            if (z<.99999f||z>peak+.00001f)throw new AssertionError("zoom out of safe range");
            maxStep=Math.max(maxStep,Math.abs(z-previous));
            previous=z;
        }
        if(maxStep>.012f)throw new AssertionError("camera jump: "+maxStep);

        // A short clip cannot hard-reset to 1x before its end either.
        near(peak,CinematicMotionCurve.recede(0,2*SEC,peak),.00001f,"short clip start");
        near(1f,CinematicMotionCurve.recede(2*SEC,2*SEC,peak),.00001f,"short clip end");
        System.out.println("CinematicMotionCurveSelfTest PASS; max scale change/frame="+maxStep);
    }
}
