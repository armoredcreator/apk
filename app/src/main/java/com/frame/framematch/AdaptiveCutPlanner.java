package com.frame.framematch;

/**
 * Conservative per-cut evidence gate for a cinematic zoom.
 *
 * This is a diagnostic HEURISTIC, not a V39-equivalent renderer. A zoom can
 * conceal shoulder/background differences but cannot repair a different face.
 * When face evidence is absent, unreliable or the clips are too short,
 * the automatic mode must not invent an unsafe edit.
 */
public final class AdaptiveCutPlanner {
    private AdaptiveCutPlanner() {}

    public enum Action { KEEP, ZOOM, REVIEW }

    public static final class Decision {
        public final Action action;
        public final float zoomPercent;
        public final float visualDifference;
        public final String reason;
        private Decision(Action action,float percent,float difference,String reason) {
            this.action=action; this.zoomPercent=percent;
            this.visualDifference=difference; this.reason=reason;
        }
        public boolean useZoom(){return action==Action.ZOOM && zoomPercent>0f;}
    }

    /**
     * Compare same-size, decoded boundary frames. Face coordinates are
     * normalized to [0,1]. The output MUST be used together with an exported
     * preview check; no pixel score alone establishes perceptual quality.
     */
    public static Decision decide(int[] last,int width,int height,int[] first,int targetWidth,
                                  int targetHeight,RegionMath.FaceBox previousFace,
                                  RegionMath.FaceBox nextFace,long previousDurationMs,
                                  long nextDurationMs) {
        if(last==null||first==null||width<16||height<16||targetWidth<16||targetHeight<16||
           last.length!=width*height||first.length!=targetWidth*targetHeight)
            return new Decision(Action.REVIEW,0,1f,"Quadros de fronteira inválidos");
        float mismatch=frameDifference(last,width,height,first,targetWidth,targetHeight);
        if(previousFace==null||nextFace==null) {
            if(mismatch>=.065f)
                return new Decision(Action.REVIEW,0,mismatch,"Sem rosto rastreável; verificar o corte");
            return new Decision(Action.KEEP,0,mismatch,"Sem rosto confiável: preservar enquadramento");
        }
        if(!valid(previousFace)||!valid(nextFace))
            return new Decision(Action.REVIEW,0,mismatch,"Geometria facial inválida");
        float px=previousFace.x+previousFace.w/2f,py=previousFace.y+previousFace.h/2f;
        float nx=nextFace.x+nextFace.w/2f,ny=nextFace.y+nextFace.h/2f;
        float shiftX=Math.abs(px-nx),shiftY=Math.abs(py-ny);
        float scaleGap=Math.abs((float)Math.log(previousFace.w/nextFace.w));
        if(shiftX>.16f||shiftY>.19f||scaleGap>.37f)
            return new Decision(Action.REVIEW,0,mismatch,"Rostos incompatíveis: zoom não é correção segura");
        // An incomplete 3s approach or recede may produce an abrupt trajectory.
        if(previousDurationMs<4200L||nextDurationMs<4200L)
            return new Decision(Action.REVIEW,0,mismatch,"Duração insuficiente para movimento seguro");
        float score=Math.max(0f,mismatch-.020f)*5.1f + scaleGap*.50f +
            shiftX*.42f + shiftY*.28f;
        if(score<.075f)
            return new Decision(Action.KEEP,0,mismatch,"Quadros próximos: sem zoom desnecessário");
        float pct=RegionMath.clamp(12f + (score-.075f)*110f,12f,33f);
        // Never hardcode 33% for every clip: use bounded evidence from this cut.
        return new Decision(Action.ZOOM,pct,mismatch,"Diferença visual detectada; zoom conservador");
    }

    private static boolean valid(RegionMath.FaceBox f) {
        return f.x>=0&&f.y>=0&&f.w>.05f&&f.h>.05f&&
            f.x+f.w<=1.01f&&f.y+f.h<=1.01f;
    }
    /** Robust low-resolution paired samples, in an RGB domain, no global exposure assumptions. */
    public static float frameDifference(int[] a,int aw,int ah,int[] b,int bw,int bh) {
        double difference=0;
        final int nx=24,ny=36;
        for(int j=0;j<ny;j++){
            int ya=Math.min(ah-1,(j*2+1)*ah/(ny*2));
            int yb=Math.min(bh-1,(j*2+1)*bh/(ny*2));
            for(int i=0;i<nx;i++){
                int xa=Math.min(aw-1,(i*2+1)*aw/(nx*2));
                int xb=Math.min(bw-1,(i*2+1)*bw/(nx*2));
                int p=a[ya*aw+xa],q=b[yb*bw+xb];
                difference+=Math.abs(((p>>>16)&255)-((q>>>16)&255));
                difference+=Math.abs(((p>>>8)&255)-((q>>>8)&255));
                difference+=Math.abs((p&255)-(q&255));
            }
        }
        return (float)(difference/(nx*ny*3d*255d));
    }
}
