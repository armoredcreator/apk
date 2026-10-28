package com.frame.framematch;

/**
 * Single continuous camera trajectory for Android's experimental V39-target zoom.
 * Rendering parity with the approved desktop V39 has NOT been established.
 *
 * Pure Java: Android-free regression tests can sample every output frame.
 */
public final class ZoomTimeline {
    private ZoomTimeline() {}

    // Quintic smoothstep: continuous position, velocity and acceleration.
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

    public static float recede(long sourceUs, long durationUs, float peak) {
        double progress = sourceUs / (double) Math.max(1L, durationUs);
        return 1f + (peak - 1f) * (float) (1d - ease(progress));
    }
}
