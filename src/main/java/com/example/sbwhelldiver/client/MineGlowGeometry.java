package com.example.sbwhelldiver.client;

import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL33C.*;

/** A single depth-tested, batched lamp pass, independent of shader-pack entity
 * materials. Only the original lamp faces contribute light and bloom. */
public final class MineGlowGeometry {
    private static int program,vao,vbo,count;
    private static FloatBuffer data=org.lwjgl.BufferUtils.createFloatBuffer(65536);
    public static void beginFrame(){data.clear();count=0;}
    public static int vertexCount(){return count;}
    public static void capture(float[] vertices,Matrix4f m){
        if(data.remaining()<vertices.length){var next=org.lwjgl.BufferUtils.createFloatBuffer(Math.max(data.capacity()*2,data.position()+vertices.length));data.flip();next.put(data);data=next;}
        for(int i=0;i<vertices.length;i+=6){float x=vertices[i],y=vertices[i+1],z=vertices[i+2];
            data.put(m.m00()*x+m.m10()*y+m.m20()*z+m.m30()).put(m.m01()*x+m.m11()*y+m.m21()*z+m.m31()).put(m.m02()*x+m.m12()*y+m.m22()*z+m.m32());
            data.put(vertices[i+3]).put(vertices[i+4]).put(vertices[i+5]);count++;
        }
    }
    static void release(){if(program!=0)glDeleteProgram(program);if(vao!=0)glDeleteVertexArrays(vao);if(vbo!=0)glDeleteBuffers(vbo);program=vao=vbo=0;}
    static void draw(Matrix4f projection,int w,int h)throws java.io.IOException{
        if(count==0)return;
        if(program==0){
            int vs=FieldVolumeRenderer.shader(GL_VERTEX_SHADER,FieldVolumeRenderer.source("mine.vsh")),fs=0,candidate=0;
            try{fs=FieldVolumeRenderer.shader(GL_FRAGMENT_SHADER,FieldVolumeRenderer.source("mine.fsh"));candidate=glCreateProgram();glAttachShader(candidate,vs);glAttachShader(candidate,fs);glBindAttribLocation(candidate,0,"Position");glBindAttribLocation(candidate,1,"Color");glLinkProgram(candidate);if(glGetProgrami(candidate,GL_LINK_STATUS)==0)throw new IllegalStateException(glGetProgramInfoLog(candidate));program=candidate;candidate=0;}
            finally{glDeleteShader(vs);if(fs!=0)glDeleteShader(fs);if(candidate!=0)glDeleteProgram(candidate);}
            vao=glGenVertexArrays();vbo=glGenBuffers();glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glEnableVertexAttribArray(0);glVertexAttribPointer(0,3,GL_FLOAT,false,24,0L);glEnableVertexAttribArray(1);glVertexAttribPointer(1,3,GL_FLOAT,false,24,12L);
        }
        glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
        var view=data.duplicate();view.flip();glBufferData(GL_ARRAY_BUFFER,view,GL_STREAM_DRAW);
        glUniform1i(glGetUniformLocation(program,"SceneDepth"),0);glUniform2f(glGetUniformLocation(program,"Viewport"),w,h);
        try(var stack=MemoryStack.stackPush()){glUniformMatrix4fv(glGetUniformLocation(program,"Projection"),false,projection.get(stack.mallocFloat(16)));}
        glBlendFuncSeparate(GL_ONE,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);glDrawArrays(GL_TRIANGLES,0,count);
    }
}
