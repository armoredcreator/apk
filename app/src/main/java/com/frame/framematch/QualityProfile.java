package com.frame.framematch;

/**
 * Output specification is derived ONLY from clip 1. This class plans export settings,
 * not perceptual restoration. Upscaling or higher bitrate cannot recover lost details.
 * The Android video encoder may further limit resolution/bitrate on some devices.
 */
public final class QualityProfile {
    public final int width, height, requestedBitrate, maxFrameRate;
    public final boolean resolutionLimited, bitrateEstimated;

    private QualityProfile(int width,int height,int requestedBitrate,int maxFrameRate,
                           boolean resolutionLimited,boolean bitrateEstimated){
        this.width=width;this.height=height;this.requestedBitrate=requestedBitrate;
        this.maxFrameRate=maxFrameRate;this.resolutionLimited=resolutionLimited;
        this.bitrateEstimated=bitrateEstimated;
    }

    public static int even(int value){return Math.max(2,value&~1);}

    /** Pixel dimensions adjusted for display rotation (90/270 degrees). */
    public static QualityProfile fromMaster(int encodedWidth,int encodedHeight,int degrees,
                                             int videoBitrate,int frameRate) {
        if(encodedWidth<2||encodedHeight<2)throw new IllegalArgumentException("Dimensões ausentes no Clip 1");
        int w=encodedWidth,h=encodedHeight;
        if(Math.abs(degrees)%180==90){int temp=w;w=h;h=temp;}
        // Safe ceiling for common Android encoders to avoid 4K failures on older devices.
        float factor=Math.min(1f,Math.min(1920f/w,1920f/h));
        int outputW=even((int)Math.floor(w*factor));
        int outputH=even((int)Math.floor(h*factor));
        boolean limited=(outputW!=even(w)||outputH!=even(h));

        int fps=frameRate>=1&&frameRate<=120?frameRate:0; // 0 = do not cap.
        boolean estimated=videoBitrate<200_000;
        double guessed=(double)outputW*outputH*(fps>0?fps:30)*0.12;
        int bitrate=(int)Math.round(estimated?guessed:videoBitrate);
        bitrate=Math.max(600_000,Math.min(16_000_000,bitrate));
        return new QualityProfile(outputW,outputH,bitrate,fps,limited,estimated);
    }

    public int[] outputSize(boolean followMaster) {
        return followMaster?new int[]{width,height}:new int[]{540,960};
    }

    /** Frame cap only: never synthesizes additional frames for a lower-FPS source. */
    public int frameRateCeiling(boolean followMaster){return followMaster?maxFrameRate:0;}

    public String describe() {
        String fps=maxFrameRate==0?"FPS não identificado":maxFrameRate+" FPS (limite)";
        return width+"×"+height+" · "+fps+" · "+requestedBitrate/1_000_000f+" Mb/s"+
                (bitrateEstimated?" (bitrate estimado)":"")+
                (resolutionLimited?" · reduzido para compatibilidade":"");
    }
}
