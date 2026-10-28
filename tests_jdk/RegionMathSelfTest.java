import com.frame.framematch.RegionMath;
public final class RegionMathSelfTest {
    private static void check(boolean okay,String s){if(!okay)throw new AssertionError(s);}
    private static int color(int r,int g,int b){return 0xff000000|(r<<16)|(g<<8)|b;}
    public static void main(String[] args){
        check(RegionMath.sky(color(40,90,170)),"detect blue sky");
        check(!RegionMath.sky(color(170,90,40)),"ignore furniture brown");
        RegionMath.FaceBox f=new RegionMath.FaceBox(.3f,.2f,.4f,.4f);
        check(f.valid(),"face bbox");
        int[] ref=new int[120*120],tar=new int[120*120];
        for(int y=0;y<120;y++)for(int x=0;x<120;x++){
            int i=y*120+x;
            if(x<40){ref[i]=color(31,75,148);tar[i]=color(45,70,109);}
            else if(x<80){ref[i]=color(150,98,85);tar[i]=color(120,80,70);}
            else {ref[i]=color(90,94,101);tar[i]=color(84,90,93);}
        }
        RegionMath.Plan p=RegionMath.analyze(ref,120,120,null,tar,120,120,null,1);
        check(p.calibrated[RegionMath.SKY],"sky calibration");
        check(!p.calibrated[RegionMath.LIPS],"no lip without face");
        int[] corrected=RegionMath.preview(tar,120,120,null,p);
        check(corrected.length==tar.length,"preview size");
        float before=Math.abs((tar[10]&255)-(ref[10]&255));
        float after=Math.abs((corrected[10]&255)-(ref[10]&255));
        check(after<before,"blue should improve");
        RegionMath.Plan zero=RegionMath.analyze(ref,120,120,null,tar,120,120,null,0);
        check(java.util.Arrays.equals(tar,RegionMath.preview(tar,120,120,null,zero)),"zero strength identity");
        float[][] hi={{1.30f,1.30f,1.30f},{1.30f,1.30f,1.30f},
                      {1.30f,1.30f,1.30f},{1.30f,1.30f,1.30f}};
        float[][] low={{.70f,.70f,.70f},{1f,1f,1f},{1f,1f,1f},{1f,1f,1f}};
        RegionMath.Plan boosted=new RegionMath.Plan(hi,new boolean[]{true,true,true,true},1f);
        RegionMath.Plan darkened=new RegionMath.Plan(low,new boolean[]{true,false,false,false},1f);
        int mid=RegionMath.preview(new int[]{color(125,125,125)},1,1,null,boosted)[0];
        check(((mid>>16)&255)<=149,"compound gain capped at 18%");
        int shadow=RegionMath.preview(new int[]{color(10,10,10)},1,1,null,boosted)[0];
        check(((shadow>>16)&255)<=12,"dark shadow protected");
        int highlight=RegionMath.preview(new int[]{color(248,248,248)},1,1,null,darkened)[0];
        check(((highlight>>16)&255)>=226,"bright highlights protected");
        RegionMath.Plan firstGrade=new RegionMath.Plan(
            new float[][]{{1.05f,1.05f,1.05f},{1,1,1},{1,1,1},{1,1,1}},
            new boolean[]{true,false,false,false},.65f);
        RegionMath.Plan stable=new RegionMath.Plan(
            new float[][]{{1.06f,1.06f,1.06f},{1,1,1},{1,1,1},{1,1,1}},
            new boolean[]{true,false,false,false},.65f);
        RegionMath.Plan flash=new RegionMath.Plan(
            new float[][]{{1.18f,1.18f,1.18f},{1,1,1},{1,1,1},{1,1,1}},
            new boolean[]{true,false,false,false},.65f);
        check(RegionMath.blendStable(firstGrade,stable).gains[0][0]>1.05f,"stable exposure estimate blended");
        check(Math.abs(RegionMath.blendStable(firstGrade,flash).gains[0][0]-1.05f)<.001f,
              "temporary exposure pulse must not dominate matching");
        int[] blues={color(25,55,155),color(70,80,140)};
        check(RegionMath.preview(blues,2,1,null,firstGrade).length==2,"soft mask works");
        System.out.println("RegionMath: 13 checks OK");
    }
}
