import com.frame.framematch.QualityProfile;

public final class QualityProfileSelfTest {
    private static void yes(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args){
        QualityProfile p=QualityProfile.fromMaster(464,848,0,3_200_000,24);
        yes(p.width==464&&p.height==848,"portrait follows master dimensions");
        yes(p.requestedBitrate==3_200_000,"uses known source bitrate");
        yes(p.frameRateCeiling(true)==24,"known FPS caps faster clips");
        yes(p.frameRateCeiling(false)==0,"FPS option off");
        yes(p.outputSize(false)[0]==540&&p.outputSize(false)[1]==960,"legacy output available");
        QualityProfile rotated=QualityProfile.fromMaster(1920,1080,90,8_000_000,30);
        yes(rotated.width==1080&&rotated.height==1920,"metadata rotation supported");
        QualityProfile huge=QualityProfile.fromMaster(4096,2160,0,40_000_000,60);
        yes(huge.width<=1920&&huge.height<=1920&&huge.resolutionLimited,"encoder-compatible ceiling");
        yes(huge.requestedBitrate<=16_000_000,"bitrate safety clamp");
        QualityProfile unknown=QualityProfile.fromMaster(853,481,0,0,0);
        yes(unknown.width%2==0&&unknown.height%2==0,"encoder even dimensions");
        yes(unknown.bitrateEstimated,"reports inferred bitrate");
        yes(unknown.maxFrameRate==0,"does not invent FPS");
        boolean threw=false;
        try{QualityProfile.fromMaster(0,0,0,0,0);}catch(IllegalArgumentException ex){threw=true;}
        yes(threw,"invalid master metadata rejected");
        System.out.println("QualityProfile: 12 checks OK");
    }
}