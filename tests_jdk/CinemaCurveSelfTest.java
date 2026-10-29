import com.frame.framematch.CinemaCurve;
import com.frame.framematch.CinematicCameraPivot;

public final class CinemaCurveSelfTest {
    private static void near(float actual,float expected,float epsilon,String description) {
        if(Math.abs(actual-expected)>epsilon)
            throw new AssertionError(description+" actual="+actual+" expected="+expected);
    }
    private static void check(boolean c,String message) {
        if(!c)throw new AssertionError(message);
    }
    public static void main(String[] args){
        long eight=8_000_000L;
        CinematicCameraPivot p=CinematicCameraPivot.validatedPortrait();
        float x=p.ndcX(),y=p.ndcY();
        CinemaCurve.Frame first=CinemaCurve.evaluate(0,eight,0,33,x,y,x,y);
        CinemaCurve.Frame atCutBefore=CinemaCurve.evaluate(eight,eight,0,33,x,y,x,y);
        CinemaCurve.Frame atCutAfter=CinemaCurve.evaluate(0,eight,33,0,x,y,x,y);
        CinemaCurve.Frame finalFrame=CinemaCurve.evaluate(eight,eight,33,0,x,y,x,y);
        near(first.scale,1f,1e-6f,"scene one begins unzoomed");
        near(atCutBefore.scale,1.33f,1e-6f,"first scene ends at V37 zoom");
        near(atCutAfter.scale,atCutBefore.scale,1e-6f,"no zoom step at cut");
        near(atCutAfter.dx,atCutBefore.dx,1e-6f,"no horizontal shift at cut");
        near(atCutAfter.dy,atCutBefore.dy,1e-6f,"no vertical shift at cut");
        near(finalFrame.scale,1f,1e-6f,"scene two recedes completely");
        near(finalFrame.dx,0f,1e-6f,"scene two no residual pan");
        // The 33 percent reference is not imposed on unknown cuts.
        near(CinemaCurve.evaluate(1_000_000,eight,0,0,x,y,x,y).scale,1f,1e-6f,"no effect needed");
        float last=1.33f;
        for(int frame=1;frame<=192;frame++){
            CinemaCurve.Frame point=CinemaCurve.evaluate(Math.round(frame*eight/192d),
                eight,33,0,x,y,x,y);
            check(point.scale<=last+1e-6f,"no reversal after cut");
            check(last-point.scale<.005f,"no abrupt zoom reset");
            last=point.scale;
        }
        // A middle clip has two boundaries: one unified transformation, no
        // multiplication of separately applied matrices.
        for(int frame=1;frame<=192;frame++){
            float t=frame/192f;
            CinemaCurve.Frame f=CinemaCurve.evaluate(Math.round(frame*eight/192d),
                eight,22,33,x,y,x,y);
            check(f.scale>=1f&&f.scale<=1.40f,"overlap bounded");
            if(t>.02f&&t<.98f)check(Float.isFinite(f.dx)&&Float.isFinite(f.dy),"finite camera pan");
        }
        near(CinemaCurve.evaluate(0,eight,22,33,x,y,x,y).scale,1.22f,1e-6f,"middle clip arriving cut");
        near(CinemaCurve.evaluate(eight,eight,22,33,x,y,x,y).scale,1.33f,1e-6f,"middle clip leaving cut");
        System.out.println("CinemaCurveSelfTest OK: V37 boundary, continuous recede, multi-cut geometry");
    }
}
