package com.example.sbwhelldiver.client;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import java.io.IOException;
import java.nio.ByteBuffer;
import static org.lwjgl.opengl.GL33C.*;

/** Ray march at half width/height, then reconstruct using scene depth. The
 * expensive three-dimensional noise is evaluated for one quarter of the pixels;
 * depth rejection keeps the cheaper full-resolution resolve off foreground edges. */
final class FieldFogLayer {
    private static int program,vao,texture,target,width,height;
    private static int depthUniform,fogUniform,projectionUniform;
    static void release(){
        if(program!=0)glDeleteProgram(program);if(vao!=0)glDeleteVertexArrays(vao);
        if(texture!=0)glDeleteTextures(texture);if(target!=0)glDeleteFramebuffers(target);
        program=vao=texture=target=width=height=0;
    }
    private static void initialize(int w,int h)throws IOException {
        if(program==0){
            int vs=FieldVolumeRenderer.shader(GL_VERTEX_SHADER,FieldVolumeRenderer.source("field.vsh")),fs=0,candidate=0;
            try{
                fs=FieldVolumeRenderer.shader(GL_FRAGMENT_SHADER,FieldVolumeRenderer.source("field_resolve.fsh"));candidate=glCreateProgram();
                glAttachShader(candidate,vs);glAttachShader(candidate,fs);glLinkProgram(candidate);
                if(glGetProgrami(candidate,GL_LINK_STATUS)==0)throw new IllegalStateException(glGetProgramInfoLog(candidate));
                program=candidate;candidate=0;
            }finally{glDeleteShader(vs);if(fs!=0)glDeleteShader(fs);if(candidate!=0)glDeleteProgram(candidate);}
            depthUniform=glGetUniformLocation(program,"SceneDepth");fogUniform=glGetUniformLocation(program,"Fog");projectionUniform=glGetUniformLocation(program,"InverseProjection");
            vao=glGenVertexArrays();texture=glGenTextures();target=glGenFramebuffers();
        }
        if(width==w&&height==h)return;
        glBindTexture(GL_TEXTURE_2D,texture);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA16F,w,h,0,GL_RGBA,GL_HALF_FLOAT,(ByteBuffer)null);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,target);glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,texture,0);glDrawBuffer(GL_COLOR_ATTACHMENT0);
        if(glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete fog layer");
        width=w;height=h;
    }
    static void begin(int w,int h)throws IOException {
        glActiveTexture(GL_TEXTURE0);initialize(w,h);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,target);glViewport(0,0,w,h);
        try(var stack=MemoryStack.stackPush()){glClearBufferfv(GL_COLOR,0,stack.floats(0,0,0,0));}
    }
    static void composite(int destination,int sceneDepth,int w,int h,Matrix4f projection){
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,destination);glViewport(0,0,w,h);
        glUseProgram(program);glBindVertexArray(vao);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,sceneDepth);
        glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,texture);
        glUniform1i(depthUniform,0);glUniform1i(fogUniform,1);
        try(var stack=MemoryStack.stackPush()){glUniformMatrix4fv(projectionUniform,false,new Matrix4f(projection).invert().get(stack.mallocFloat(16)));}
        glDrawArrays(GL_TRIANGLES,0,3);
        glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,sceneDepth);
    }
}
