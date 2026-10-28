import com.frame.framematch.TemporalColor;
import com.frame.framematch.RegionMath;
public final class TemporalColorSelfTest{
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static RegionMath.Plan plan(float g){
        float[][] a=new float[4][3];
        for(int k=0;k<4;k++)for(int c=0;c<3;c++)a[k][c]=g;
        return new RegionMath.Plan(a,new boolean[]{true,true,true,true},.65f);
    }
    public static void main(String[] args){
        RegionMath.Plan stable=TemporalColor.consolidate(plan(1.08f),plan(1.07f),plan(1.09f));
        check(Math.abs(stable.gains[0][0]-1.08f)<.02f,"stable tint retained");
        RegionMath.Plan flash=TemporalColor.consolidate(plan(1.01f),plan(1.22f),plan(.86f));
        check(Math.abs(flash.gains[0][0]-1f)<.08f,"changing lighting damped");
        RegionMath.Plan unchanged=TemporalColor.consolidate(plan(1),plan(1),plan(1));
        check(unchanged.gains[0][0]==1,"identity");
        System.out.println("TemporalColor: 3 checks OK");
    }
}