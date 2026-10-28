import com.frame.framematch.RegionMath;
import com.frame.framematch.MatchChoices;
public final class MatchChoicesSelfTest {
    static void ok(boolean c,String message){if(!c)throw new AssertionError(message);}
    static void near(float a,float b,String message){ok(Math.abs(a-b)<.00001f,message);}
    public static void main(String[] args){
        float[][] gains={{1.12f,.91f,1.04f},{1.1f,.9f,1.12f},
            {1.08f,.95f,1.05f},{1.09f,.91f,1.03f}};
        RegionMath.Plan p=new RegionMath.Plan(gains,new boolean[]{true,true,true,true},.65f);
        RegionMath.Plan[] choices=MatchChoices.candidates(p);
        ok(choices.length==5,"five alternatives");
        ok(MatchChoices.LABELS.length==MatchChoices.COUNT,"all labels");
        for(int i=0;i<MatchChoices.COUNT;i++)ok(MatchChoices.valid(i),"mode "+i);
        ok(!MatchChoices.valid(-1)&&!MatchChoices.valid(5),"reject invalid choices");
        near(choices[MatchChoices.ORIGINAL].gains[0][0],1f,"original no grade");
        near(choices[MatchChoices.SOFT].gains[0][0],1.054f,"soft reduced");
        near(choices[MatchChoices.BALANCED].gains[0][0],1.12f,"balanced original plan");
        near(choices[MatchChoices.SCENERY].gains[RegionMath.SKY][0],1.1f,"scenery maintains blue");
        ok(!choices[MatchChoices.SCENERY].calibrated[RegionMath.SKIN],"scenery protects skin");
        ok(!choices[MatchChoices.SCENERY].calibrated[RegionMath.LIPS],"scenery protects lips");
        ok(!choices[MatchChoices.SKIN].calibrated[RegionMath.SKY],"skin protects sky");
        near(choices[MatchChoices.SKIN].gains[0][0],1.036f,"skin dampens global shift");
        near(choices[MatchChoices.SKIN].gains[RegionMath.LIPS][0],1.09f,"skin matches lips");
        ok(choices[MatchChoices.BALANCED]!=p,"no alias on plan");
        near(p.gains[0][0],1.12f,"input unchanged");
        RegionMath.Plan[] noGrade=MatchChoices.candidates(p,0);
        near(noGrade[MatchChoices.BALANCED].gains[0][0],1f,"zero intensity disables grade");
        RegionMath.Plan[] half=MatchChoices.candidates(p,50);
        near(half[MatchChoices.BALANCED].gains[0][0],1.06f,"per-cut strength 50");
        RegionMath.Plan[] full=MatchChoices.candidates(p,100);
        near(full[MatchChoices.BALANCED].gains[0][0],1.12f,"per-cut strength 100");
        RegionMath.Plan[] strong=MatchChoices.candidates(p,150);
        near(strong[MatchChoices.BALANCED].gains[0][0],1.18f,"per-cut strength 150");
        near(MatchChoices.candidates(p,250)[MatchChoices.BALANCED].gains[0][0],1.18f,"bounded intensity");
        near(MatchChoices.candidates(p,-99)[MatchChoices.BALANCED].gains[0][0],1f,"negative intensity clamped");
        near(p.gains[0][0],1.12f,"source gains preserved");
        System.out.println("MatchChoices: 20 checks OK");
    }
}