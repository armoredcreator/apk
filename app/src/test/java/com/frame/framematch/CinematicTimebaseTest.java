package com.frame.framematch;

import org.junit.Test;
import static org.junit.Assert.*;

/** Regression: V39 must never lose its recede when clip 2 starts after clip 1. */
public class CinematicTimebaseTest {
    private static final long SECOND = 1_000_000L;
    private static final long DURATION = 8 * SECOND;
    private static final float PEAK = 1.33f;

    @Test public void secondClipGlobalTimestampStartsAtFullZoom() {
        CinematicTimebase timebase = new CinematicTimebase(0);
        assertEquals(0, timebase.sourceTimeUs(8 * SECOND));
        assertEquals(4 * SECOND, timebase.sourceTimeUs(12 * SECOND));
        assertEquals(8 * SECOND, timebase.sourceTimeUs(16 * SECOND));
        assertEquals(PEAK, ZoomTimeline.recede(0, DURATION, PEAK), 0.0001f);
        assertEquals(1f, ZoomTimeline.recede(timebase.sourceTimeUs(16 * SECOND),
                DURATION, PEAK), 0.0001f);
    }

    @Test public void sourceRelativeTimestampHasSameRecede() {
        CinematicTimebase timebase = new CinematicTimebase(0);
        float first = ZoomTimeline.recede(timebase.sourceTimeUs(0), DURATION, PEAK);
        float middle = ZoomTimeline.recede(timebase.sourceTimeUs(4 * SECOND),
                DURATION, PEAK);
        float last = ZoomTimeline.recede(timebase.sourceTimeUs(8 * SECOND),
                DURATION, PEAK);
        assertEquals(PEAK, first, .0001f);
        // At 4s V37_RECEDE[4] = 0.545, hence scale = 1 + 0.33 * 0.545.
        assertEquals(1.17985f, middle, .001f);
        assertEquals(1f, last, .0001f);
    }

    @Test public void trimOffsetDoesNotSkipReturnOrFinishEarly() {
        CinematicTimebase timebase = new CinematicTimebase(2000);
        assertEquals(2 * SECOND, timebase.sourceTimeUs(8 * SECOND));
        assertEquals(8 * SECOND, timebase.sourceTimeUs(14 * SECOND));
    }

    @Test public void v39ApproachAndRecedeAreContinuousAndMonotonic() {
        float previous = 1f;
        for (long t = 0; t <= DURATION; t += SECOND / 24) {
            float current = ZoomTimeline.approach(t, DURATION, 3 * SECOND, PEAK);
            assertTrue("approach must not shrink at t=" + t, current >= previous - .00001f);
            previous = current;
        }
        assertEquals(PEAK, ZoomTimeline.approach(DURATION, DURATION,
                3 * SECOND, PEAK), .0001f);
        previous = PEAK;
        for (long t = 0; t <= DURATION; t += SECOND / 24) {
            float current = ZoomTimeline.recede(t, DURATION, PEAK);
            assertTrue("recede must never zoom forward at t=" + t,
                    current <= previous + .00001f);
            assertTrue("recede must not fall below original scale", current >= .99999f);
            previous = current;
        }
        assertEquals(1f, ZoomTimeline.recede(DURATION, DURATION, PEAK), .0001f);
        assertEquals(PEAK, ZoomTimeline.recede(0, DURATION, PEAK), .0001f);
    }
}
