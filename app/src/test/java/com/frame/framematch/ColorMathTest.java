package com.frame.framematch;

import org.junit.Test;
import static org.junit.Assert.*;

public class ColorMathTest {
    @Test public void identicalFramesHaveUnitGains() {
        int[] p=new int[144]; java.util.Arrays.fill(p, 0xff8050a0);
        ColorMath.Gains gains=ColorMath.match(p,p,1f);
        assertEquals(1,gains.r,1e-4f);assertEquals(1,gains.g,1e-4f);assertEquals(1,gains.b,1e-4f);
    }
    @Test public void strengthZeroDoesNotChangeColors() {
        int[] a=new int[144],b=new int[144];
        java.util.Arrays.fill(a,0xff446688);java.util.Arrays.fill(b,0xff887755);
        ColorMath.Gains gains=ColorMath.match(a,b,0f);
        assertEquals(1,gains.r,1e-4f);assertEquals(1,gains.g,1e-4f);assertEquals(1,gains.b,1e-4f);
    }
    @Test public void gainsAreCappedForSafety() {
        int[] a=new int[144],b=new int[144];
        java.util.Arrays.fill(a,0xffe0e0e0);java.util.Arrays.fill(b,0xff282828);
        ColorMath.Gains gains=ColorMath.match(a,b,1f);
        assertTrue(gains.r<=1.29f);assertTrue(gains.b<=1.29f);
    }
}
