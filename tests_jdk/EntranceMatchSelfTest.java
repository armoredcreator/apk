import com.frame.framematch.AlignmentMath;
import com.frame.framematch.EntranceMatch;
public class EntranceMatchSelfTest{
    static void check(boolean yes,String msg){if(!yes)throw new AssertionError(msg);}
    public static void main(String[] args){
        AlignmentMath.Pose reference=new AlignmentMath.Pose(.5f,.4f,.2f,0);
        long[] times={0,250,500,750,1000,1250,1500};
        AlignmentMath.Pose[] choices=new AlignmentMath.Pose[times.length];
        for(int i=0;i<choices.length;i++)choices[i]=new AlignmentMath.Pose(.7f,.5f,.12f,0.07f);
        choices[3]=new AlignmentMath.Pose(.505f,.401f,.198f,.003f);
        EntranceMatch.Choice best=EntranceMatch.choose(reference,choices,times);
        check(best.offsetMs==750,"closest facial frame found");
        check(EntranceMatch.safe(best),"valid match safe");
        check(!EntranceMatch.safe(EntranceMatch.choose(null,choices,times)),"missing face preserves entrance");
        check(!EntranceMatch.safe(new EntranceMatch.Choice(1700,0)),"trim max enforced");
        System.out.println("EntranceMatch: 4 checks OK");
    }
}