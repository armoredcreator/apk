package com.frame.framematch;

/**
 * Camera zoom timeline. Keep the approved approach unchanged.
 * The recede uses a continuous, near-uniform pace with short smooth ramps,
 * instead of concentrating the movement in the middle of clip two.
 */
public final class ZoomTimeline {
    private ZoomTimeline() {}

    private static double ease(double value) {
        double t = Math.max(0d, Math.min(1d, value));
        return t * t * t * (t * (t * 6d - 15d) + 10d);
    }

    public static float approach(long sourceUs, long durationUs, long rampUs, float peak) {
        long duration = Math.max(1L, durationUs);
        long start = Math.max(0L, duration - Math.max(1L, rampUs));
        double progress = (sourceUs - start) / (double) Math.max(1L, duration - start);
        return 1f + (peak - 1f) * (float) ease(progress);
    }

    // Integral of the quintic easing function. I(0)=0 and I(1)=1/2.
    private static double integralEase(double t) {
        return t * t * t * t * (2.5d + t * (-3d + t));
    }

    /**
     * Slow, uninterrupted zoom out: smooth takeoff, near-uniform middle,
     * smooth landing. The scale is exactly peak at frame zero and 1 at the
     * final frame, with no artificial release/reset.
     */
    public static float recede(long sourceUs, long durationUs, float peak) {
        double t = Math.max(0d, Math.min(1d,
                sourceUs / (double) Math.max(1L, durationUs)));
        final double ramp = 0.14d;
        final double area;
        if (t < ramp) {
            area = ramp * integralEase(t / ramp);
        } else if (t <= 1d - ramp) {
            area = ramp * 0.5d + (t - ramp);
        } else {
            double tail = (1d - t) / ramp;
            area = 1d - ramp - ramp * integralEase(tail);
        }
        double progress = area / (1d - ramp);
        return 1f + (peak - 1f) * (float) (1d - progress);
    }
}
