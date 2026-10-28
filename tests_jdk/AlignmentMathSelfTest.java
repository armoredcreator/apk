import com.frame.framematch.AlignmentMath;
public final class AlignmentMathSelfTest {
    private static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);}
    public static void main(String[] args){
        AlignmentMath.Pose reference=new AlignmentMath.Pose(.50f,.30f,.22f,0f);
        AlignmentMath.Pose target=new AlignmentMath.Pose(.52f,.31f,.20f,0.02f);
        AlignmentMath.Transform t=AlignmentMath.estimate(reference,target,.7f);
        check(t.zoom>1&&t.zoom<1.1f,"bounded zoom toward eye scale");
        check(Math.abs(t.dx)<.16&&Math.abs(t.dy)<.16,"small translation");
        check(Math.abs(t.rotation)<.03,"subtle rotation");
        check(AlignmentMath.fade(0,2_000_000L)==1f,"starts aligned");
        check(AlignmentMath.fade(2_000_000L,2_000_000L)==0f,"returns to source by deadline");
        check(AlignmentMath.estimate(reference,new AlignmentMath.Pose(.1f,.2f,.07f,0f),1).zoom==1f,
              "extreme scale rejected");
        check(AlignmentMath.estimate(reference,new AlignmentMath.Pose(.8f,.8f,.22f,0f),1).zoom==1f,
              "large shift rejected");
        check(AlignmentMath.estimate(reference,null,1).zoom==1f,"missing landmarks safe");
        check(AlignmentMath.estimate(reference,target,0).zoom==1f,"zero strength identity");
        System.out.println("AlignmentMath: 9 checks OK");
    }
}