package com.frame.framematch;

/**
 * Smooth subject-aware translation for the cinematic recede.
 *
 * All coordinates are normalized to [0..1] in the displayed (upright) frame.
 * Translation is in NDC [-1..1], compatible with Media3 MatrixTransformation.
 *
 * The movement starts with the previous clip's subject anchor, follows an
 * observed subject rather than a stationary screen center, and gives back all
 * translation as scale approaches 1. No black canvas can be uncovered.
 *
 * Geometric continuity is a necessary condition, not proof of V39 equivalence.
 */
public final class CinematicFollowMath {
    private CinematicFollowMath() {}

    private static float clamp(float x, float a, float b) {
        return Math.max(a, Math.min(b, x));
    }

    public static float releaseWeight(float progress) {
        float t = clamp(progress, 0f, 1f);
        // Quintic fade: velocity and acceleration vanish at both ends.
        float eased = t*t*t*(t*(6f*t - 15f) + 10f);
        return 1f - eased;
    }

    /** C1 soft saturation against the available zoom overscan. */
    public static float limitedShift(float desired, float margin) {
        if (margin <= 0f) return 0f;
        float sign = Math.signum(desired);
        float amount = Math.abs(desired);
        float threshold = margin * 0.70f;
        if (amount <= threshold) return desired;
        if (amount >= margin) return sign * margin;
        float span = margin - threshold;
        float t = (amount - threshold) / span;
        // y(0)=0, y'(0)=1, y(1)=1, y'(1)=0.
        float y = t + t*t - t*t*t;
        return sign * (threshold + span*y);
    }

    /**
     * Previous-cut face centre vs current observed centre. Eye/face geometry
     * can be absent; caller should then request a centered (0,0) zoom.
     */
    public static float[] translation(
            float scale, float progress, float previousX, float previousY,
            float observedX, float observedY) {
        if (!Float.isFinite(scale) || !Float.isFinite(previousX)
                || !Float.isFinite(previousY) || !Float.isFinite(observedX)
                || !Float.isFinite(observedY) || scale <= 1f) {
            return new float[]{0f,0f};
        }
        float weight = releaseWeight(progress);
        float margin = scale - 1f;
        // NDC Y is positive upwards; bitmap coordinates are downwards.
        float dx = 2f * scale * (previousX-observedX) * weight;
        float dy = -2f * scale * (previousY-observedY) * weight;
        return new float[]{limitedShift(dx,margin),limitedShift(dy,margin)};
    }
}
