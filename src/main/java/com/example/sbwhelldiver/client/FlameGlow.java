package com.example.sbwhelldiver.client;

import org.lwjgl.system.MemoryStack;
import java.io.IOException;
import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL33C.*;

/** Isolated HDR flame layer and quarter-resolution bloom. Shader packs have
 * already finished their bloom at AFTER_LEVEL, so emission needs this pass.
 * Only depth-tested flame contributes; smoke and the surrounding scene do not. */
final class FlameGlow {
    private static int program,vao,width,height;
    private static final int[] textures=new int[3],targets=new int[3];
    static void release(){
        if(program!=0)glDeleteProgram(program);if(vao!=0)glDeleteVertexArrays(vao);
        for(int i=0;i<3;i++){
            if(textures[i]!=0)glDeleteTextures(textures[i]);if(targets[i]!=0)glDeleteFramebuffers(targets[i]);
            textures[i]=targets[i]=0;
        }
        program=vao=width=height=0;
    }
    private static void initialize(int w,int h)throws IOException {
        if(program==0){
            int vs=FieldVolumeRenderer.shader(GL_VERTEX_SHADER,FieldVolumeRenderer.source("field.vsh")),fs=0,candidate=0;
            try{
                fs=FieldVolumeRenderer.shader(GL_FRAGMENT_SHADER,FieldVolumeRenderer.source("flame_glow.fsh"));candidate=glCreateProgram();
                glAttachShader(candidate,vs);glAttachShader(candidate,fs);glLinkProgram(candidate);
                if(glGetProgrami(candidate,GL_LINK_STATUS)==0)throw new IllegalStateException(glGetProgramInfoLog(candidate));
                program=candidate;candidate=0;
            }finally{glDeleteShader(vs);if(fs!=0)glDeleteShader(fs);if(candidate!=0)glDeleteProgram(candidate);}
            vao=glGenVertexArrays();
            for(int i=0;i<3;i++){textures[i]=glGenTextures();targets[i]=glGenFramebuffers();}
        }
        if(width==w&&height==h)return;
        for(int i=0;i<3;i++){
            glBindTexture(GL_TEXTURE_2D,textures[i]);
            glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA16F,i==0?w:Math.max(1,w/4),i==0?h:Math.max(1,h/4),0,GL_RGBA,GL_HALF_FLOAT,(ByteBuffer)null);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,targets[i]);
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,textures[i],0);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            if(glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete flame glow target");
        }
        width=w;height=h;
    }
    interface Draw {void run()throws IOException;}
    static void render(int w,int h,Draw flame)throws IOException {
        render(w,h,.52f,flame);
    }
    static void render(int w,int h,float bloomStrength,Draw flame)throws IOException {
        int destination=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),active=glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);int previous0=glGetInteger(GL_TEXTURE_BINDING_2D);
        glActiveTexture(GL_TEXTURE1);int previous1=glGetInteger(GL_TEXTURE_BINDING_2D);
        try{
            glActiveTexture(GL_TEXTURE0);initialize(w,h);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,targets[0]);glViewport(0,0,w,h);
            try(var stack=MemoryStack.stackPush()){glClearBufferfv(GL_COLOR,0,stack.floats(0f,0f,0f,0f));}
            glBindTexture(GL_TEXTURE_2D,previous0);
            flame.run();
            glUseProgram(program);glBindVertexArray(vao);glDisable(GL_BLEND);
            glUniform1i(glGetUniformLocation(program,"Flame"),0);glUniform1i(glGetUniformLocation(program,"Bloom"),1);
            glUniform1f(glGetUniformLocation(program,"BloomStrength"),bloomStrength);
            int bw=Math.max(1,w/4),bh=Math.max(1,h/4);
            pass(targets[1],textures[0],bw,bh,0,0f,0f);
            pass(targets[2],textures[1],bw,bh,1,1.6f/bw,0f);
            pass(targets[1],textures[2],bw,bh,1,0f,1.6f/bh);
            glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,textures[1]);glActiveTexture(GL_TEXTURE0);
            glEnable(GL_BLEND);glBlendFuncSeparate(GL_ONE,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
            pass(destination,textures[0],w,h,2,0f,0f);
        }finally{
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,destination);glViewport(0,0,w,h);
            glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,previous0);
            glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,previous1);glActiveTexture(active);
        }
    }
    private static void pass(int target,int texture,int w,int h,int mode,float dx,float dy){
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,target);glViewport(0,0,w,h);glBindTexture(GL_TEXTURE_2D,texture);
        glUniform1i(glGetUniformLocation(program,"Mode"),mode);glUniform2f(glGetUniformLocation(program,"Step"),dx,dy);
        glDrawArrays(GL_TRIANGLES,0,3);
    }
}
