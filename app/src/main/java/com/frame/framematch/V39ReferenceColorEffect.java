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

/**
 * Calibrated grading of the historical reference pair. The source files
 * must be SHA256-verified BEFORE attaching this effect. No colors are baked
 * into a newly generated scene; the program only remaps the source RGB.
 */
@UnstableApi
public final class V39ReferenceColorEffect implements GlEffect {
    private final int scene;
    private final CinematicTimebase clock;
    private final long clipStartUs;
    public V39ReferenceColorEffect(int scene,long clipStartMs) {
        this.scene=scene;
        this.clock=new CinematicTimebase(clipStartMs);
        this.clipStartUs=Math.max(0,clipStartMs)*1000L;
    }
    @Override public boolean isNoOp(int width,int height) {return false;}
    @Override public BaseGlShaderProgram toGlShaderProgram(Context context,boolean useHdr)
            throws VideoFrameProcessingException {
        if(useHdr)throw new VideoFrameProcessingException("Calibração V39 disponível somente em SDR");
        return new Program(scene,clock,clipStartUs);
    }
    private static final class Program extends BaseGlShaderProgram {
        private static final String VERT=
            "attribute vec2 aPos;varying vec2 uv;void main(){uv=(aPos+1.0)*0.5;gl_Position=vec4(aPos,0.0,1.0);}";
        private static final String FRAG=
            "precision mediump float;varying vec2 uv;uniform sampler2D uTex;"+
            "uniform vec3 uGain,uOffset;void main(){vec4 c=texture2D(uTex,uv);"+
            "gl_FragColor=vec4(clamp(c.rgb*uGain+uOffset,0.0,1.0),c.a);}";
        private final int scene;
        private final CinematicTimebase clock;
        private final long clipStartUs;
        private int program,tex,pos,gain,offset;
        private final FloatBuffer quad=ByteBuffer.allocateDirect(8*4)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();
        Program(int scene,CinematicTimebase clock,long clipStartUs) {
            super(false,1);
            this.scene=scene;this.clock=clock;this.clipStartUs=clipStartUs;
            quad.put(new float[]{-1,-1,1,-1,-1,1,1,1});quad.position(0);
        }
        private static int compile(int type,String code) throws VideoFrameProcessingException {
            int s=GLES20.glCreateShader(type);
            GLES20.glShaderSource(s,code);GLES20.glCompileShader(s);
            int[] status=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,status,0);
            if(status[0]==0) {
                String msg=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);
                throw new VideoFrameProcessingException("V39 color shader: "+msg);
            }
            return s;
        }
        @Override public Size configure(int width,int height) throws VideoFrameProcessingException {
            if(program!=0)GLES20.glDeleteProgram(program);
            int vs=compile(GLES20.GL_VERTEX_SHADER,VERT),fs=compile(GLES20.GL_FRAGMENT_SHADER,FRAG);
            program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);
            GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);
            GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);
            int[] ok=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,ok,0);
            if(ok[0]==0)throw new VideoFrameProcessingException("V39 color shader link: "+
                GLES20.glGetProgramInfoLog(program));
            tex=GLES20.glGetUniformLocation(program,"uTex");
            pos=GLES20.glGetAttribLocation(program,"aPos");
            gain=GLES20.glGetUniformLocation(program,"uGain");
            offset=GLES20.glGetUniformLocation(program,"uOffset");
            return new Size(width,height);
        }
        @Override public void drawFrame(int inputTexId,long presentationTimeUs) {
            long source=clock.sourceTimeUs(presentationTimeUs);
            long t=Math.max(0,source-(scene==0?0L:clipStartUs));
            float[] c=V39ReferenceProfile.gradeAt(scene,t);
            GLES20.glUseProgram(program);
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,inputTexId);
            GLES20.glUniform1i(tex,0);
            GLES20.glUniform3f(gain,c[0],c[2],c[4]);
            GLES20.glUniform3f(offset,c[1]/255f,c[3]/255f,c[5]/255f);
            quad.position(0);
            GLES20.glEnableVertexAttribArray(pos);
            GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,quad);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
            GLES20.glDisableVertexAttribArray(pos);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,0);
        }
        @Override public void release() throws VideoFrameProcessingException {
            if(program!=0)GLES20.glDeleteProgram(program);program=0;super.release();
        }
    }
}
