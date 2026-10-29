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
        final long duration = 8_000_000L, ramp = 3_000_000L;
        final float peak = 1.33f;
        near(ZoomTimeline.approach(0,duration,ramp,peak),1f,1e-6f,"unmodified beginning");
        near(ZoomTimeline.approach(5_000_000L,duration,ramp,peak),1f,1e-6f,"approach starts 3s before cut");
        near(ZoomTimeline.approach(duration,duration,ramp,peak),peak,1e-6f,"left side at cut");
        near(ZoomTimeline.recede(0,duration,peak),peak,1e-6f,"right side at cut");
        near(ZoomTimeline.recede(duration,duration,peak),1f,1e-6f,"full frame restored");
        float previous = peak;
        float maxStep = 0f;
        for(int frame=1;frame<=192;frame++) {
            long time=Math.round(frame * duration / 192d);
            float current=ZoomTimeline.recede(time,duration,peak);
            ok(current <= previous + 1e-6f,"recede must be monotone at frame " + frame);
            ok(current >= 1f - 1e-6f,"recede must never overshoot");
            maxStep=Math.max(maxStep, Math.abs(current-previous));
            previous=current;
        }
        ok(maxStep < 0.004f,"no single-frame zoom release, max=" + maxStep);
        // Same absolute source moment must produce same scale for cropped previews.
        long previewStart=1_000_000L;
        long elapsed=2_000_000L;
        near(ZoomTimeline.recede(previewStart+elapsed,duration,peak),
             ZoomTimeline.recede(3_000_000L,duration,peak),1e-6f,
             "clipped preview and full export source timeline");
        // Check speed and acceleration near the ends to avoid a stop/start jerk.
        float frame1=ZoomTimeline.recede(41_667L,duration,peak);
        float frame2=ZoomTimeline.recede(83_333L,duration,peak);
        ok(Math.abs((peak-frame1)-(frame1-frame2)) < 0.0002f,
           "recede must begin without a velocity step");
        float end1=ZoomTimeline.recede(duration-41_667L,duration,peak);
        float end2=ZoomTimeline.recede(duration-83_333L,duration,peak);
        ok(Math.abs((end2-end1)-(end1-1f)) < 0.0001f,
           "recede must end without a velocity step");
        // Independent optical measurement of approved V37 94->95 (24fps).
        near(ZoomTimeline.approach(6_000_000L,duration,ramp,peak),
             1.0857f,.001f,"V37 scale at 6s");
        near(ZoomTimeline.approach(7_000_000L,duration,ramp,peak),
             1.2449f,.001f,"V37 scale at 7s");
        near(ZoomTimeline.recede(2_000_000L,duration,peak),
             1.2514f,.001f,"V37 scale at 10s global");
        near(ZoomTimeline.recede(4_000_000L,duration,peak),
             1.1799f,.001f,"V37 scale at 12s global");
        System.out.println("ZoomTimelineSelfTest OK: measured V37 motion, monotone camera, full return");
    }
}
