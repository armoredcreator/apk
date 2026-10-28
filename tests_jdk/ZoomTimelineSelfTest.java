import com.frame.framematch.ZoomTimeline;
public final class ZoomTimelineSelfTest {
    private static void ok(boolean b, String message) {
        if (!b) throw new AssertionError(message);
    }
    private static void near(float actual, float expected, float tol, String message) {
        ok(Math.abs(actual - expected) <= tol,
            message + " actual=" + actual + " expected=" + expected);
    }
    public static void main(String[] args) {
        long duration=8_000_000L, ramp=3_000_000L;
        float peak=1.33f;
        near(ZoomTimeline.approach(0,duration,ramp,peak),1f,1e-6f,"beginning unchanged");
        near(ZoomTimeline.approach(5_000_000L,duration,ramp,peak),1f,1e-6f,"start 3s before cut");
        near(ZoomTimeline.approach(duration,duration,ramp,peak),peak,1e-6f,"approach cut");
        near(ZoomTimeline.recede(0,duration,peak),peak,1e-6f,"recede cut");
        near(ZoomTimeline.recede(duration,duration,peak),1f,1e-6f,"recede end");
        float previous=peak,maxStep=0f;
        for(int frame=1;frame<=192;frame++) {
            float now=ZoomTimeline.recede(Math.round(frame*duration/192d),duration,peak);
            ok(now<=previous+1e-6f,"not monotonic at "+frame);
            ok(now>=1f-1e-6f,"overshoot "+frame);
            maxStep=Math.max(maxStep,previous-now);
            previous=now;
        }
        ok(maxStep<0.0022f,"too fast middle frame: "+maxStep);
        float a=ZoomTimeline.recede(41_667L,duration,peak);
        float b=ZoomTimeline.recede(83_333L,duration,peak);
        ok(Math.abs((peak-a)-(a-b))<0.0001f,"start jerk");
        float c=ZoomTimeline.recede(duration-41_667L,duration,peak);
        float e=ZoomTimeline.recede(duration-83_333L,duration,peak);
        ok(Math.abs((e-c)-(c-1f))<0.0001f,"end jerk");
        near(ZoomTimeline.recede(3_000_000L,duration,peak),
             ZoomTimeline.recede(1_000_000L+2_000_000L,duration,peak),1e-6f,
             "preview timeline");
        System.out.println("ZoomTimelineSelfTest OK: smooth, slow and continuous; maxStep="+maxStep);
    }
}
