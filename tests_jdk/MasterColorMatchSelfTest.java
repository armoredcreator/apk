import com.frame.framematch.RegionMath;
import com.frame.framematch.MasterColorMatch;
import com.frame.framematch.MatchChoices;
import java.util.Arrays;

/** Verifies that Clip 1, not Clip 2 or a cumulative grade, anchors every later scene. */
public final class MasterColorMatchSelfTest {
    private static void require(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    private static MasterColorMatch.Sample sample(int gray) {
        int[] argb=new int[64*64];
        Arrays.fill(argb,0xff000000 | (gray<<16) | (gray<<8) | gray);
        return new MasterColorMatch.Sample(argb,64,64,null);
    }
    private static float gain(RegionMath.Plan p){return p.gains[0][0];}
    public static void main(String[] args) {
        MasterColorMatch.Sample master=sample(100), masterNear=sample(100);
        MasterColorMatch.Sample a=sample(160), b=sample(90), c=sample(95);
        RegionMath.Plan aGrade=MasterColorMatch.calibrate(master,masterNear,a,a,1f);
        RegionMath.Plan bGrade=MasterColorMatch.calibrate(master,masterNear,b,b,1f);
        RegionMath.Plan cGrade=MasterColorMatch.calibrate(master,masterNear,c,c,1f);
        require(gain(bGrade)>1.09f&&gain(bGrade)<1.13f,"clip B follows master 100, not clip A 160");
        require(gain(cGrade)>1f&&gain(cGrade)<1.08f,"clip C follows same master");
        require(gain(aGrade)<1f,"clip A is separately corrected toward master");
        // Different choices for A MUST NOT change B's calibration.
        for(int mode=0;mode<MatchChoices.COUNT;mode++){
            RegionMath.Plan chosenA=MatchChoices.candidates(aGrade)[mode];
            require(chosenA!=null,"mode exists");
            RegionMath.Plan invariant=MasterColorMatch.calibrate(master,masterNear,b,b,1f);
            require(Math.abs(gain(invariant)-gain(bGrade))<.00001f,"clip B independent of A choice "+mode);
        }
        RegionMath.Plan wrongCascade=MasterColorMatch.calibrate(a,a,b,b,1f);
        require(Math.abs(gain(wrongCascade)-gain(bGrade))>.02f,"test distinguishes cascaded source");
        require(Math.abs(gain(MasterColorMatch.calibrate(master,masterNear,b,b,0f))-1f)<.00001f,
                "strength zero identity");
        System.out.println("MasterColorMatch: fixed Clip 1, no cascade checks OK");
    }
}
