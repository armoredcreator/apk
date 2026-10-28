import com.frame.framematch.RegionMath;
import com.frame.framematch.MasterColorMatch;
import com.frame.framematch.MatchChoices;
import com.frame.framematch.NaturalSelector;
import java.util.Arrays;

public final class NaturalSelectorSelfTest {
    private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static MasterColorMatch.Sample gray(int color){
        int[] a=new int[96*96];
        Arrays.fill(a,0xff000000|(color<<16)|(color<<8)|color);
        return new MasterColorMatch.Sample(a,96,96,null);
    }
    private static RegionMath.Plan[] variants(MasterColorMatch.Sample a,MasterColorMatch.Sample b){
        return MatchChoices.candidates(MasterColorMatch.calibrate(a,a,b,b,.65f));
    }
    public static void main(String[] args){
        MasterColorMatch.Sample ref=gray(100),same=gray(100),different=gray(114);
        int sameChoice=NaturalSelector.best(ref,ref,same,same,variants(ref,same));
        check(sameChoice==MatchChoices.ORIGINAL,"do not color grade when already matching");
        int best=NaturalSelector.best(ref,ref,different,different,variants(ref,different));
        check(best>=0&&best<MatchChoices.COUNT,"selector should choose valid mode");
        int repeat=NaturalSelector.best(ref,ref,different,different,variants(ref,different));
        check(best==repeat,"selection deterministic");
        check(Math.abs(variants(ref,different)[best].gains[0][0]-1f)<=.15f,
              "corrector stays bounded");
        boolean rejected=false;
        try{NaturalSelector.best(ref,ref,different,different,new RegionMath.Plan[]{RegionMath.Plan.identity()});}
        catch(IllegalArgumentException e){rejected=true;}
        check(rejected,"invalid candidate list rejected");
        System.out.println("NaturalSelector: 5 checks OK");
    }
}
