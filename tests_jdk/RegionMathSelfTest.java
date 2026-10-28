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
        System.out.println("RegionMath: 7 checks OK");
    }
}
