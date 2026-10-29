import com.frame.framematch.CinematicCameraPivot;
import com.frame.framematch.RegionMath;

public final class CinematicCameraPivotSelfTest {
    private static void near(float actual,float expected,float tolerance,String message) {
        if(Math.abs(actual-expected)>tolerance)
            throw new AssertionError(message+" actual="+actual+" expected="+expected);
    }
    public static void main(String[] args) {
        CinematicCameraPivot v37=CinematicCameraPivot.validatedPortrait();
        near(v37.x,.5f,1e-6f,"reference x");
        near(v37.y,320f/864f,1e-6f,"reference y");
        float zoom=1.33f;
        near(v37.dx(zoom),0f,1e-6f,"horizontal framing");
        // Pixel-space output: (320-432)*(1-zoom) = +36.96 pixels.
        // The previous Android scale-about-centre was ~37px too low.
        float offsetInPixels=-v37.dy(zoom)*864f/2f;
        near(offsetInPixels,36.96f,.03f,"V37 camera pivot difference");
        near(zoom*v37.ndcY()+v37.dy(zoom),v37.ndcY(),1e-6f,"point fixed under zoom");
        near(v37.dy(1f),0f,1e-6f,"return to full frame");
        CinematicCameraPivot adaptive=CinematicCameraPivot.fromFace(
            new RegionMath.FaceBox(.25f,.20f,.50f,.25f));
        near(adaptive.x,.5f,.0001f,"adaptive face x");
        near(adaptive.y,.37f,.0001f,"adaptive face y");
        System.out.println("CinematicCameraPivotSelfTest OK");
    }
}
