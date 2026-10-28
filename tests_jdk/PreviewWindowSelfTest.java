import com.frame.framematch.PreviewWindow;

public final class PreviewWindowSelfTest {
    private static void eq(long expected, long actual) {
        if (expected != actual) throw new AssertionError("expected " + expected + ", got " + actual);
    }

    public static void main(String[] args) {
        // The validated 8s+8s cut must preview ALL of the 3s approach and
        // ALL 8s of the receding scene (not the old 1.5s+2.5s fragment).
        eq(5000L, PreviewWindow.beforeStart(8000L, 0L));
        eq(8000L, PreviewWindow.afterEnd(8000L));
        eq(0L, PreviewWindow.beforeStart(2400L, 0L));
        eq(6500L, PreviewWindow.beforeStart(8000L, 6500L));
        eq(1000L, PreviewWindow.beforeStart(1000L, 1500L));
        boolean failed = false;
        try { PreviewWindow.afterEnd(0L); } catch (IllegalArgumentException expected) { failed = true; }
        if (!failed) throw new AssertionError("zero-duration preview must be rejected");
        System.out.println("PreviewWindowSelfTest PASS");
    }
}
