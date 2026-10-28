package com.frame.framematch;

/**
 * Conservative, deterministic ranking of candidate grades against Clip 1.
 * Compare distribution of background, sky, face skin and lips in two frames
 * near the transition. The ranking is HEURISTIC, not a quality guarantee.
 * Avoids preferring extreme gain if two candidates have comparable matches.
 * Pure Java and bounded sampling keep it workable without a GPU.
 */
public final class NaturalSelector {
    private NaturalSelector(){}
    private static final float[] REGION_WEIGHTS={1.0f,1.25f,1.6f,1.2f};

    public static int best(MasterColorMatch.Sample masterLast,
                           MasterColorMatch.Sample masterNear,
                           MasterColorMatch.Sample targetFirst,
                           MasterColorMatch.Sample targetEarly,
                           RegionMath.Plan[] candidates) {
        if(candidates==null||candidates.length!=MatchChoices.COUNT)
            throw new IllegalArgumentException("Tentativas de cor inválidas");
        MasterColorMatch.Sample a=reduce(masterLast),b=reduce(masterNear),
            c=reduce(targetFirst),d=reduce(targetEarly);
        // Clip 1 is the ONLY color reference. Different camera positions do not
        // provide pixel correspondences; compare robust region color statistics.
        double bestCost=Double.POSITIVE_INFINITY;
        int bestIndex=MatchChoices.SOFT;
        for(int index=0;index<candidates.length;index++){
            RegionMath.Plan candidate=candidates[index];
            int[] first=RegionMath.preview(c.argb,c.width,c.height,c.face,candidate);
            int[] early=RegionMath.preview(d.argb,d.width,d.height,d.face,candidate);
            RegionMath.Plan residualA=RegionMath.analyze(
                a.argb,a.width,a.height,a.face,first,c.width,c.height,c.face,1f);
            RegionMath.Plan residualB=RegionMath.analyze(
                b.argb,b.width,b.height,b.face,early,d.width,d.height,d.face,1f);
            double cost=0,weight=0;
            for(int k=0;k<4;k++){
                // Do not reward matching masks that have insufficient matching evidence.
                if(k!=RegionMath.BACKGROUND &&
                   (!residualA.calibrated[k]||!residualB.calibrated[k]))continue;
                for(int ch=0;ch<3;ch++){
                    double da=Math.log(Math.max(.01f,residualA.gains[k][ch]));
                    double db=Math.log(Math.max(.01f,residualB.gains[k][ch]));
                    cost+=REGION_WEIGHTS[k]*(da*da+db*db);
                    weight+=REGION_WEIGHTS[k]*2;
                }
            }
            if(weight>0)cost/=weight;
            else cost=.05; // no comparable regions -> prefer lighter correction
            double penalty=0;
            for(int k=0;k<4;k++){
                if(k>0&&!candidate.calibrated[k])continue;
                for(int ch=0;ch<3;ch++){
                    double deviation=candidate.gains[k][ch]-1d;
                    penalty+=deviation*deviation*(k==0?2:1);
                }
            }
            cost+=.28*penalty/12;
            // The scene's source may already be natural; original is valid.
            if(cost<bestCost-1e-10){bestCost=cost;bestIndex=index;}
        }
        return bestIndex;
    }

    private static MasterColorMatch.Sample reduce(MasterColorMatch.Sample src){
        final int w=Math.min(96,src.width);
        final int h=Math.max(32,Math.min(192,
            Math.round(w*(float)src.height/src.width)));
        int[] pixels=new int[w*h];
        for(int y=0;y<h;y++)for(int x=0;x<w;x++){
            int px=Math.min(src.width-1,(int)((x+.5f)*src.width/w));
            int py=Math.min(src.height-1,(int)((y+.5f)*src.height/h));
            pixels[y*w+x]=src.argb[py*src.width+px];
        }
        return new MasterColorMatch.Sample(pixels,w,h,src.face);
    }
}
