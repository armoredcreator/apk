import com.frame.framematch.CutFaceMath;

public final class CutFaceMathSelfTest {
    private static void check(boolean ok,String msg){
        if(!ok)throw new AssertionError(msg);
    }
    private static void near(float value,float expected,float tolerance,String msg){
        check(Math.abs(value-expected)<=tolerance,msg+": "+value+" expected "+expected);
    }
    public static void main(String[] args){
        CutFaceMath.Plan p=CutFaceMath.estimate(.20f,.21f,.50f,.28f,
                                                  .22f,.218f,.48f,.28f);
        check(p.active(),"small width mismatch MUST be aligned on first frame");
        check(p.sx>1f&&p.sx<1.06f,"width corrected without extreme stretch");
        // Normalized centre of first face must exactly overlap face from scene 1.
        float targetX=2f*(.22f+.48f/2f)-1f;
        float targetY=1f-2f*(.218f+.28f/2f);
        float referenceX=2f*(.20f+.50f/2f)-1f;
        float referenceY=1f-2f*(.21f+.28f/2f);
        float[] first=p.at(0,8_000_000L);
        near(first[0]*targetX+first[2],referenceX,.00001f,"face centre X AT CUT");
        near(first[1]*targetY+first[3],referenceY,.00001f,"face centre Y AT CUT");
        float[] last=p.at(8_000_000L,8_000_000L);
        near(last[0],1f,1e-6f,"natural width at end");
        near(last[1],1f,1e-6f,"natural height at end");
        near(last[2],0f,1e-6f,"natural X at end");
        near(last[3],0f,1e-6f,"natural Y at end");
        float[] previous=first;
        for(int i=1;i<=192;i++){
            float[] now=p.at(i*8_000_000L/192,8_000_000L);
            check(Math.abs(now[0]-previous[0])<.003f,"no horizontal scale jump");
            check(Math.abs(now[2]-previous[2])<.003f,"no horizontal movement jump");
            check(Math.abs(now[3]-previous[3])<.003f,"no vertical movement jump");
            previous=now;
        }
        CutFaceMath.Plan unsafe=CutFaceMath.estimate(.2f,.2f,.6f,.3f,
                                                   .2f,.2f,.25f,.12f);
        check(!unsafe.active(),"large face difference MUST be rejected");
        CutFaceMath.Plan noFace=CutFaceMath.estimate(.2f,.2f,.5f,.25f,
                                                   .2f,.2f,0f,0f);
        check(!noFace.active(),"missing face MUST be rejected");
        CutFaceMath.Plan sameSize=CutFaceMath.estimate(.2f,.2f,.4f,.2f,
                                                     .21f,.21f,.4f,.2f);
        check(sameSize.active(),"alignment must work when no zoom is needed");
        System.out.println("CutFaceMathSelfTest OK: initial overlap + natural recede");
    }
}
