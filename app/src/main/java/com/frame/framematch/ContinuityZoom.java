package com.frame.framematch;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.MatrixTransformation;

/**
 * V39 transition geometry.
 *
 * A cut is represented by two effects:
 *  - beforeCut: anticipates the alignment on the tail of the previous clip;
 *  - afterCut: starts at the aligned frame and returns slowly to the source frame.
 *
 * It only moves existing pixels. It never synthesizes content or changes audio.
 */
@UnstableApi
public final class ContinuityZoom implements MatrixTransformation {
    private final FaceTimeline track;
    private final AlignmentMath.Transform faceTransform;
    private final RegionMath.FaceBox targetStart;
    private final RegionMath.FaceBox referenceEnd;
    private final float ratio;
    private final long releaseTimeUs;
    private final long clipDurationUs;
    private final long windowUs;
    private final boolean staticPlan;
    private final boolean preCut;

    public static final class CutPlan {
        public final ContinuityZoom beforeCut;
        public final ContinuityZoom afterCut;

        private CutPlan(ContinuityZoom beforeCut, ContinuityZoom afterCut) {
            this.beforeCut = beforeCut;
            this.afterCut = afterCut;
        }
    }

    private ContinuityZoom(FaceTimeline t, RegionMath.FaceBox reference,
                           RegionMath.FaceBox target, float ratio, long releaseUs) {
        track = t;
        referenceEnd = reference;
        targetStart = target;
        this.ratio = ratio;
        releaseTimeUs = releaseUs;
        clipDurationUs = 0L;
        windowUs = releaseUs;
        faceTransform = AlignmentMath.Transform.identity();
        staticPlan = false;
        preCut = false;
    }

    private ContinuityZoom(AlignmentMath.Transform transform, long windowUs,
                           long clipDurationUs, boolean preCut) {
        track = null;
        referenceEnd = null;
        targetStart = null;
        ratio = transform.zoom;
        faceTransform = transform;
        releaseTimeUs = Math.max(1L, windowUs);
        this.windowUs = Math.max(1L, windowUs);
        this.clipDurationUs = Math.max(0L, clipDurationUs);
        staticPlan = true;
        this.preCut = preCut;
    }

    /** Compatibility helper for callers that need only the post-cut behavior. */
    public static ContinuityZoom facePlan(AlignmentMath.Pose reference,
                                          AlignmentMath.Pose target,
                                          float strength, long durationMs) {
        AlignmentMath.Transform change = AlignmentMath.estimate(reference, target, strength);
        if (isIdentity(change)) return null;
        return new ContinuityZoom(change, clampWindow(durationMs), 0L, false);
    }

    /**
     * Creates the complete V39 transition. The lead-in and retreat are derived
     * from the measured transform; the 33% example is never used as a constant.
     */
    public static CutPlan cutPlan(AlignmentMath.Pose reference,
                                  AlignmentMath.Pose target,
                                  float strength,
                                  long retreatMs,
                                  long previousDurationUs) {
        AlignmentMath.Transform change = AlignmentMath.estimate(reference, target, strength);
        if (isIdentity(change)) return null;

        long retreatUs = clampWindow(retreatMs) * 1000L;
        // Reference V39 timing: the anticipation starts exactly three seconds
        // before the cut. Short clips are shortened only when necessary to avoid
        // creating an invalid/negative window.
        long leadUs = 3_000_000L;

        // A short previous scene cannot safely host an anticipation ramp.
        long safeLead = Math.min(leadUs, Math.max(0L, previousDurationUs - 100_000L));
        if (safeLead < 500_000L) return null;

        return new CutPlan(
            new ContinuityZoom(change, safeLead, previousDurationUs, true),
            new ContinuityZoom(change, retreatUs, 0L, false));
    }

    private static long clampWindow(long durationMs) {
        return Math.max(500L, Math.min(3000L, durationMs));
    }

    private static boolean isIdentity(AlignmentMath.Transform transform) {
        return transform == null
            || (Math.abs(transform.zoom - 1f) < .002f
                && Math.abs(transform.rotation) < .001f
                && transform.displacement() < .003f);
    }

    private AlignmentMath.Transform transformAt(long timeUs) {
        if (preCut) {
            long startUs = Math.max(0L, clipDurationUs - windowUs);
            float p = RegionMath.clamp((timeUs - startUs) / (float) Math.max(1L, windowUs), 0f, 1f);
            p = p * p * (3f - 2f * p);
            return scaled(faceTransform, p);
        }

        float p = RegionMath.clamp(timeUs / (float) Math.max(1L, releaseTimeUs), 0f, 1f);
        p = 1f - p * p * (3f - 2f * p);
        return scaled(faceTransform, p);
    }

    private static AlignmentMath.Transform scaled(AlignmentMath.Transform source, float p) {
        return new AlignmentMath.Transform(
            1f + (source.zoom - 1f) * p,
            source.dx * p,
            source.dy * p,
            source.rotation * p);
    }

    public float initialZoom() {
        return staticPlan ? faceTransform.zoom : ratio;
    }

    public float zoomAt(long timeUs) {
        if (staticPlan) return transformAt(timeUs).zoom;
        if (timeUs >= releaseTimeUs) return 1f;
        float p = RegionMath.clamp(timeUs / (float) releaseTimeUs, 0f, 1f);
        float fade = 1f - (p * p * (3f - 2f * p));
        RegionMath.FaceBox current = track.at(timeUs);
        float currentRatio = current == null
            ? 1f
            : RegionMath.clamp(referenceEnd.w / Math.max(.001f, current.w), 1f, ratio);
        return 1f + (currentRatio - 1f) * fade;
    }

    /** Preview at the effect's natural boundary: end for beforeCut, start for afterCut. */
    public Bitmap preview(Bitmap source) {
        long timeUs = preCut ? Math.max(0L, clipDurationUs - 1L) : 0L;
        return previewAt(source, timeUs);
    }

    /** Preview the same transform used by Media3 at a specific local timestamp. */
    public Bitmap previewAt(Bitmap source, long presentationTimeUs) {
        if (source == null) return null;

        if (staticPlan) {
            return render(source, transformAt(presentationTimeUs));
        }

        float zoom = zoomAt(presentationTimeUs);
        if (zoom <= 1.001f) return source;
        RegionMath.FaceBox current = track.at(presentationTimeUs);
        if (current == null) return source;

        float tx = (current.x + current.w * .5f) * source.getWidth();
        float ty = (current.y + current.h * .5f) * source.getHeight();
        float rx = (referenceEnd.x + referenceEnd.w * .5f) * source.getWidth();
        float ry = (referenceEnd.y + referenceEnd.h * .5f) * source.getHeight();
        float dx = RegionMath.clamp(rx - zoom * tx, source.getWidth() * (1f - zoom), 0f);
        float dy = RegionMath.clamp(ry - zoom * ty, source.getHeight() * (1f - zoom), 0f);

        Matrix matrix = new Matrix();
        matrix.setValues(new float[]{zoom, 0f, dx, 0f, zoom, dy, 0f, 0f, 1f});
        return render(source, matrix);
    }

    private static Bitmap render(Bitmap source, AlignmentMath.Transform transform) {
        float cos = (float) Math.cos(transform.rotation);
        float sin = (float) Math.sin(transform.rotation);
        float w = source.getWidth();
        float h = source.getHeight();
        float a = transform.zoom * cos;
        float b = transform.zoom * sin;
        float c = -transform.zoom * sin;
        float d = transform.zoom * cos;
        float dx = (1f - a - c) * w * .5f + transform.dx * w * .5f;
        float dy = (1f - b - d) * h * .5f - transform.dy * h * .5f;

        Matrix matrix = new Matrix();
        matrix.setValues(new float[]{a, c * w / h, dx, b * h / w, d, dy, 0f, 0f, 1f});
        return render(source, matrix);
    }

    private static Bitmap render(Bitmap source, Matrix matrix) {
        Bitmap result = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(result);
        canvas.drawColor(android.graphics.Color.BLACK);
        canvas.drawBitmap(source, matrix, new Paint(Paint.FILTER_BITMAP_FLAG));
        return result;
    }

    @Override
    public Matrix getMatrix(long presentationTimeUs) {
        if (staticPlan) {
            AlignmentMath.Transform transform = transformAt(presentationTimeUs);
            float cos = (float) Math.cos(transform.rotation);
            float sin = (float) Math.sin(transform.rotation);
            Matrix matrix = new Matrix();
            matrix.setValues(new float[]{
                transform.zoom * cos, -transform.zoom * sin, transform.dx,
                transform.zoom * sin, transform.zoom * cos, transform.dy,
                0f, 0f, 1f});
            return matrix;
        }

        Matrix matrix = new Matrix();
        float zoom = zoomAt(presentationTimeUs);
        if (zoom <= 1.001f) return matrix;

        RegionMath.FaceBox current = track.at(presentationTimeUs);
        if (current == null) return matrix;
        float tx = 2f * (current.x + current.w * .5f) - 1f;
        float ty = 1f - 2f * (current.y + current.h * .5f);
        float rx = 2f * (referenceEnd.x + referenceEnd.w * .5f) - 1f;
        float ry = 1f - 2f * (referenceEnd.y + referenceEnd.h * .5f);
        float shiftX = RegionMath.clamp(rx - zoom * tx, 1f - zoom, zoom - 1f);
        float shiftY = RegionMath.clamp(ry - zoom * ty, 1f - zoom, zoom - 1f);
        matrix.setValues(new float[]{zoom, 0f, shiftX, 0f, zoom, shiftY, 0f, 0f, 1f});
        return matrix;
    }

    /** Legacy face-box plan retained as a conservative fallback. */
    public static ContinuityZoom plan(FaceTimeline track, RegionMath.FaceBox end) {
        if (end == null || track == null || track.count() < 4 || track.first() == null) return null;
        RegionMath.FaceBox first = track.first();
        float ratio = end.w / Math.max(.0001f, first.w);
        RegionMath.FaceBox later = track.at(2_000_000L);
        if (later == null || ratio < 1.20f || ratio > 1.85f || later.w < first.w * 1.12f) return null;
        return new ContinuityZoom(track, end, first, ratio, 2_800_000L);
    }
}
