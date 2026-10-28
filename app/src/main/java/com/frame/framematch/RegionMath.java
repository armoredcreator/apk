package com.frame.framematch;

import java.util.Arrays;

/** Region-aware matching: global background + blue sky + face skin + lips.
 * Pure Java so math can be exercised with JDK tests, without Android runtime.
 * The same masks/thresholds are approximated by RegionEffect's GLSL shader.
 */
public final class RegionMath {
    private RegionMath() {}
    private static final float[] ONE={1,1,1};
    public static final int BACKGROUND=0, SKY=1, SKIN=2, LIPS=3;
    public static final String[] LABELS={"cenário","céu azul","pele","lábios"};
    public static float clamp(float v,float lo,float hi){return Math.max(lo,Math.min(hi,v));}

    /** Coordinates normalized to the UNROTATED/ORIENTED decoded Bitmap bounds [0..1]. */
    public static final class FaceBox {
        public final float x,y,w,h;
        public FaceBox(float x,float y,float w,float h) {
            this.x=x;this.y=y;this.w=w;this.h=h;
        }
        public boolean valid(){return w>.03f&&h>.03f;}
    }
    public static final class Plan {
        public final float[][] gains; // [background/sky/skin/lips][R/G/B]
        public final boolean[] calibrated;
        public final float strength;
        public Plan(float[][] gains,boolean[] calibrated,float strength){
            this.gains=gains;this.calibrated=calibrated;this.strength=clamp(strength,0,1);
        }
        public static Plan identity(){return new Plan(new float[][]{{1,1,1},{1,1,1},{1,1,1},{1,1,1}},new boolean[4],1);}
    }
    public static boolean sky(int color){
        float r=(color>>16)&255,g=(color>>8)&255,b=color&255;
        return b>35 && b>r*1.12f && b>g*1.06f && b-r>12;
    }
    public static boolean skin(int color){
        float r=(color>>16)&255,g=(color>>8)&255,b=color&255;
        float cr=128f+(r-(.299f*r+.587f*g+.114f*b))*.713f;
        float cb=128f+(b-(.299f*r+.587f*g+.114f*b))*.564f;
        return cr>132 && cr<178 && cb>77 && cb<139 && r>38;
    }
    private static boolean ellipse(float x,float y,float cx,float cy,float rx,float ry){
        if(rx<=.0001f||ry<=.0001f)return false;
        float dx=(x-cx)/rx,dy=(y-cy)/ry;
        return dx*dx+dy*dy<=1;
    }
    public static int classify(int pixel,float x,float y,FaceBox face){
        if(face!=null && face.valid()) {
            boolean lip=ellipse(x,y,face.x+face.w*.50f,face.y+face.h*.75f,face.w*.23f,face.h*.10f);
            if(lip && (skin(pixel)||((pixel>>16&255)>(pixel>>8&255)*1.08f)))return LIPS;
            boolean faceSkin=ellipse(x,y,face.x+face.w*.5f,face.y+face.h*.49f,face.w*.48f,face.h*.48f);
            if(faceSkin && skin(pixel))return SKIN;
        }
        if(sky(pixel))return SKY;
        return BACKGROUND;
    }
    private static final class Stat {
        private final int[][] values=new int[3][8192];
        int count=0;
        void accept(int argb) {
            if(count>=8192)return;
            values[0][count]=(argb>>16)&255;
            values[1][count]=(argb>>8)&255;
            values[2][count]=argb&255;count++;
        }
        float[] robust(){
            float[] out=new float[]{128,128,128};
            if(count==0)return out;
            for(int c=0;c<3;c++){
                Arrays.sort(values[c],0,count);
                int a=count/5,b=count-a;double sum=0;
                for(int i=a;i<b;i++)sum+=values[c][i];
                out[c]=(float)(sum/Math.max(1,b-a));
            }
            return out;
        }
    }
    private static Stat[] stats(int[] image,int width,int height,FaceBox face){
        Stat[] s={new Stat(),new Stat(),new Stat(),new Stat()};
        int max=Math.min(8192,image.length);
        for(int i=0;i<max;i++){
            int ix=(int)((long)i*image.length/max);
            int pixel=image[ix]; int x=ix%width,y=ix/width;
            int light=((pixel>>16)&255)*54+((pixel>>8)&255)*183+(pixel&255)*19;
            if(light/256<22||light/256>241)continue;
            int k=classify(pixel,(x+.5f)/width,(y+.5f)/height,face);
            s[k].accept(pixel);
            // Stable global sample includes all pixels, but is calibrated to a neutral fallback.
        }
        return s;
    }
    public static Plan analyze(int[] ref,int rw,int rh,FaceBox rface,
                               int[] tar,int tw,int th,FaceBox tface,float strength){
        if(ref.length!=rw*rh||tar.length!=tw*th)throw new IllegalArgumentException("Dimensões inválidas");
        if(rw<32||rh<32||tw<32||th<32)throw new IllegalArgumentException("Quadro muito pequeno");
        Stat[] a=stats(ref,rw,rh,rface),b=stats(tar,tw,th,tface);
        float[][] out=new float[4][3];boolean[] calibrated=new boolean[4];
        calibrated[0]=a[0].count>=50&&b[0].count>=50;
        float[] ra=calibrated[0]?a[0].robust():global(ref);
        float[] ta=calibrated[0]?b[0].robust():global(tar);
        float s=clamp(strength,0f,1f);
        for(int c=0;c<3;c++)out[0][c]=1+s*(clamp(ra[c]/Math.max(25,ta[c]),.85f,1.15f)-1);
        for(int k=1;k<4;k++){
            calibrated[k]=a[k].count>=(k==LIPS?12:32) && b[k].count>=(k==LIPS?12:32);
            float[] aa=a[k].robust(),bb=b[k].robust();
            for(int c=0;c<3;c++){
                float extra=calibrated[k] ? aa[c]/Math.max(24,bb[c]*out[0][c]) : 1;
                out[k][c]=1+s*(clamp(extra,k==SKY?.85f:.90f,k==SKY?1.15f:1.10f)-1);
            }
        }
        return new Plan(out,calibrated,s);
    }
    private static float[] global(int[] a){
        Stat s=new Stat();int max=Math.min(4096,a.length);
        for(int i=0;i<max;i++)s.accept(a[(int)((long)i*a.length/max)]);
        return s.robust();
    }
    /**
     * Blend two early color estimates only when they agree. A passing flashlight,
     * exposure pulse or actor movement must not redefine the grade for an entire clip.
     */
    public static Plan blendStable(Plan first,Plan followup) {
        float[][] gains=new float[4][3];
        boolean[] calibrated=first.calibrated.clone();
        for(int k=0;k<4;k++)for(int c=0;c<3;c++) {
            float a=first.gains[k][c], b=followup.gains[k][c];
            gains[k][c]=(k==BACKGROUND||followup.calibrated[k]) && Math.abs(a-b)<=.075f
                ? .8f*a+.2f*b : a;
        }
        return new Plan(gains,calibrated,first.strength);
    }
    private static float smoothstep(float a,float b,float x) {
        float t=clamp((x-a)/(b-a),0f,1f);
        return t*t*(3f-2f*t);
    }
    private static float ovalWeight(float x,float y,float cx,float cy,float rx,float ry) {
        if(rx<=0.0001f||ry<=0.0001f)return 0f;
        float dx=(x-cx)/rx,dy=(y-cy)/ry;
        return 1f-smoothstep(.75f,1.18f,dx*dx+dy*dy);
    }
    private static int adjusted(int pixel,float x,float y,FaceBox f,
                                float[] base,float[] sky,float[] skin,float[] lips) {
        float r=((pixel>>16)&255)/255f,g=((pixel>>8)&255)/255f,b=(pixel&255)/255f;
        float lum=.299f*r+.587f*g+.114f*b;
        float cr=.5f+.713f*(r-lum),cb=.5f+.564f*(b-lum);
        float isSkin=smoothstep(.51f,.54f,cr)*(1-smoothstep(.69f,.72f,cr))
            *smoothstep(.29f,.32f,cb)*(1-smoothstep(.53f,.56f,cb));
        float isSky=smoothstep(.035f,.10f,b-r)*smoothstep(.014f,.07f,b-g)
            *smoothstep(.12f,.20f,b);
        float face=0f,lip=0f;
        if(f!=null && f.valid()) {
            face=ovalWeight(x,y,f.x+.5f*f.w,f.y+.49f*f.h,.48f*f.w,.48f*f.h)*isSkin;
            lip=ovalWeight(x,y,f.x+.5f*f.w,f.y+.75f*f.h,.23f*f.w,.10f*f.h)*isSkin;
        }
        float skyWeight=isSky*(1f-face), skinWeight=face*(1f-lip);
        float fade=.35f+.65f*smoothstep(.02f,.12f,lum)
                *(1f-smoothstep(.84f,.98f,lum));
        int argb=pixel&0xff000000;
        for(int c=0;c<3;c++) {
            int bit=(2-c)*8;
            float raw=(pixel>>bit)&255;
            float gain=base[c]
                    *(1f+(sky[c]-1f)*skyWeight)
                    *(1f+(skin[c]-1f)*skinWeight)
                    *(1f+(lips[c]-1f)*lip);
            // Avoid strongly multiplying global and regional color corrections.
            gain=clamp(gain,.84f,1.18f);
            int result=Math.round(clamp(raw*(1f+(gain-1f)*fade),0,255));
            argb|=result<<bit;
        }
        return argb;
    }

    /** CPU preview uses the same soft masks, gain limits and tonal protection as the shader. */
    public static int[] preview(int[] source,int width,int height,FaceBox face,Plan plan){
        int[] dst=source.clone();
        for(int i=0;i<dst.length;i++){
            float x=(i%width+.5f)/width,y=(i/width+.5f)/height;
            dst[i]=adjusted(source[i],x,y,face,plan.gains[0],
                    plan.calibrated[SKY]?plan.gains[SKY]:ONE,
                    plan.calibrated[SKIN]?plan.gains[SKIN]:ONE,
                    plan.calibrated[LIPS]?plan.gains[LIPS]:ONE);
        }
        return dst;
    }

}
