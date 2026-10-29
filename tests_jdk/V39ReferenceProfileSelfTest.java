import com.frame.framematch.V39ReferenceProfile;

public final class V39ReferenceProfileSelfTest {
    private static void test(boolean condition,String message) {
        if(!condition)throw new AssertionError(message);
    }
    private static void near(float a,float b,float maxError,String message){
        test(Math.abs(a-b)<=maxError,message+" expected="+b+" got="+a);
    }
    public static void main(String[] args){
        long duration=8_000_000L;
        near(V39ReferenceProfile.cubicApproach(0,duration),1f,1e-6f,"early clip");
        near(V39ReferenceProfile.cubicApproach(5_000_000L,duration),1f,1e-6f,"zoom starts at 5s");
        near(V39ReferenceProfile.cubicApproach(6_500_000L,duration),1.165f,1e-5f,"cubic midpoint");
        near(V39ReferenceProfile.cubicApproach(duration,duration),1.33f,1e-5f,"last first-clip frame");
        near(V39ReferenceProfile.recede(0L),1.34751f,1e-5f,"reference frame zero");
        near(V39ReferenceProfile.recede(1_875_000L),1.25192f,1e-5f,"9.875 plateau");
        near(V39ReferenceProfile.recede(2_000_000L),1.25192f,1e-5f,"10s plateau");
        near(V39ReferenceProfile.recede(4_000_000L),1.18004f,1e-5f,"12s reference");
        near(V39ReferenceProfile.recede(8_000_000L),1f,1e-5f,"frame fully restored");
        near(V39ReferenceProfile.recede(9_000_000L),1f,1e-5f,"no overshoot");
        float last=2f;
        float biggestStep=0f;
        for(int k=0;k<=192;k++){
            float value=V39ReferenceProfile.recede(k*1_000_000L/24L);
            test(value<=last+1e-6f,"recede must be monotone at "+k);
            test(value>=1f,"zoom should not undershoot 1x at "+k);
            if(k>0)biggestStep=Math.max(biggestStep,last-value);
            last=value;
        }
        test(biggestStep<.0055f,"unexpected zoom step "+biggestStep);
        float[] c1=V39ReferenceProfile.gradeAt(0,0),c2=V39ReferenceProfile.gradeAt(1,7_958_333L);
        test(c1.length==6&&c2.length==6,"RGB gains and offsets");
        test(c2[0]<1f&&c2[1]<0f,"grade must remain active through final clip");
        test(!V39ReferenceProfile.CLIP1_SHA256.equals(V39ReferenceProfile.CLIP2_SHA256),
            "sources must be independently verified");
        near(V39ReferenceProfile.FOCUS_Y,320f/864f,1e-7f,"V39 focal point");
        System.out.println("V39ReferenceProfileSelfTest OK: reference-only motion and grade");
    }
}
