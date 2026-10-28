package com.frame.framematch;

import android.content.Context;
import android.opengl.GLES20;
import androidx.media3.common.VideoFrameProcessingException;
import androidx.media3.common.util.Size;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.effect.BaseGlShaderProgram;
import androidx.media3.effect.GlEffect;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Per-pixel chroma/skin/lips masking in GLES2, applied throughout the exported video.
 * CPU preview uses RegionMath. Both algorithms retain one stable calibration per clip.
 * Color gains operate on the same gamma-encoded RGB values in preview and export.
 * Never exponentiate RGB gains: that previously amplified color by gain^2.2 on export.
 * Preview and export share soft color masks and shadow/highlight protection.
 */
@UnstableApi
public final class RegionEffect implements GlEffect {
    private final RegionMath.Plan plan;
    private final FaceTimeline track;
    public RegionEffect(RegionMath.Plan plan,FaceTimeline track){this.plan=plan;this.track=track;}
    @Override public boolean isNoOp(int w,int h){return plan.strength<=.001f;}
    @Override public BaseGlShaderProgram toGlShaderProgram(Context context,boolean useHdr)
            throws VideoFrameProcessingException {
        if(useHdr)throw new VideoFrameProcessingException("A correção regional suporta SDR; converta HDR para SDR antes.");
        return new Program(plan,track);
    }
    private static final class Program extends BaseGlShaderProgram {
        private static final float[] NEUTRAL={1f,1f,1f};
        private static final String VERT=
          "attribute vec2 aPos;varying vec2 uv;void main(){uv=(aPos+1.0)*0.5;gl_Position=vec4(aPos,0.0,1.0);}";
        private static final String FRAG=
          "precision mediump float;varying vec2 uv;uniform sampler2D uTex;"+
          "uniform vec3 uBase,uSky,uSkin,uLips;uniform vec4 uFace;uniform float uHasFace;"+
          "float oval(vec2 p,vec2 c,vec2 rad){vec2 d=(p-c)/max(rad,vec2(0.001));"+
          "return 1.0-smoothstep(0.75,1.18,dot(d,d));}"+
          "void main(){vec4 c=texture2D(uTex,uv);vec3 r=clamp(c.rgb,0.0,1.0);"+
          "float cr=0.5+0.713*(r.r-dot(r,vec3(.299,.587,.114)));"+
          "float cb=0.5+0.564*(r.b-dot(r,vec3(.299,.587,.114)));"+
          "float isSkin=smoothstep(.51,.54,cr)*(1.0-smoothstep(.69,.72,cr))*"+
          "smoothstep(.29,.32,cb)*(1.0-smoothstep(.53,.56,cb));"+
          "float isSky=smoothstep(.035,.10,r.b-r.r)*smoothstep(.014,.07,r.b-r.g)*smoothstep(.12,.20,r.b);"+
          "vec2 centre=vec2(uFace.x+.5*uFace.z,1.0-uFace.y-.49*uFace.w);"+
          "float face=uHasFace*oval(uv,centre,vec2(.48*uFace.z,.48*uFace.w))*isSkin;"+
          "vec2 mouth=vec2(centre.x,1.0-uFace.y-.75*uFace.w);"+
          "float lips=uHasFace*oval(uv,mouth,vec2(.23*uFace.z,.10*uFace.w))*isSkin;"+
          "float sky=isSky*(1.0-face);float skin=face*(1.0-lips);"+
          "vec3 gain=uBase;gain*=mix(vec3(1.0),uSky,sky);"+
          "gain*=mix(vec3(1.0),uSkin,skin);gain*=mix(vec3(1.0),uLips,lips);"+
          "gain=clamp(gain,vec3(.84),vec3(1.18));"+
          "float lum=dot(r,vec3(.299,.587,.114));"+
          "float fade=.35+.65*smoothstep(.02,.12,lum)*(1.0-smoothstep(.84,.98,lum));"+
          "vec3 grade=r*mix(vec3(1.0),gain,fade);"+
          "gl_FragColor=vec4(clamp(grade,0.0,1.0),c.a);}";
        private final RegionMath.Plan plan;private final FaceTimeline track;
        private int program,tex,pos,face,hasFace,base,sky,skin,lips;
        private final FloatBuffer quad=ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(new float[]{-1,-1,1,-1,-1,1,1,1});
        Program(RegionMath.Plan p,FaceTimeline t){super(false,1);plan=p;track=t;quad.position(0);}
        private static int compile(int type,String code) throws VideoFrameProcessingException{
            int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,code);GLES20.glCompileShader(s);
            int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
            if(ok[0]==0){String err=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);
                throw new VideoFrameProcessingException("Shader de cor: "+err);}
            return s;
        }
        @Override public Size configure(int width,int height) throws VideoFrameProcessingException{
            if(program!=0)GLES20.glDeleteProgram(program);
            int vs=compile(GLES20.GL_VERTEX_SHADER,VERT),fs=compile(GLES20.GL_FRAGMENT_SHADER,FRAG);
            program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);
            GLES20.glLinkProgram(program);GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);
            int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
            if(ok[0]==0)throw new VideoFrameProcessingException("Falha shader: "+GLES20.glGetProgramInfoLog(program));
            pos=GLES20.glGetAttribLocation(program,"aPos");tex=GLES20.glGetUniformLocation(program,"uTex");
            base=GLES20.glGetUniformLocation(program,"uBase");sky=GLES20.glGetUniformLocation(program,"uSky");
            skin=GLES20.glGetUniformLocation(program,"uSkin");lips=GLES20.glGetUniformLocation(program,"uLips");
            face=GLES20.glGetUniformLocation(program,"uFace");hasFace=GLES20.glGetUniformLocation(program,"uHasFace");
            return new Size(width,height);
        }
        private void uniform(int handle,float[] v){GLES20.glUniform3f(handle,v[0],v[1],v[2]);}
        @Override public void drawFrame(int inputTexId,long presentationTimeUs){
            GLES20.glUseProgram(program);GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,inputTexId);GLES20.glUniform1i(tex,0);
            uniform(base,plan.gains[0]);
            uniform(sky,plan.calibrated[RegionMath.SKY]?plan.gains[RegionMath.SKY]:NEUTRAL);
            uniform(skin,plan.calibrated[RegionMath.SKIN]?plan.gains[RegionMath.SKIN]:NEUTRAL);
            uniform(lips,plan.calibrated[RegionMath.LIPS]?plan.gains[RegionMath.LIPS]:NEUTRAL);
            RegionMath.FaceBox f=track==null?null:track.at(presentationTimeUs);
            GLES20.glUniform1f(hasFace,f==null?0:1);
            if(f!=null)GLES20.glUniform4f(face,f.x,f.y,f.w,f.h);
            quad.position(0);GLES20.glEnableVertexAttribArray(pos);
            GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,quad);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            GLES20.glDisableVertexAttribArray(pos);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);
        }
        @Override public void release() throws VideoFrameProcessingException {
            if(program!=0)GLES20.glDeleteProgram(program);program=0;super.release();
        }
    }
}
