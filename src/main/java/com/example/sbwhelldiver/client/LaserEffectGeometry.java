package com.example.sbwhelldiver.client;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import java.nio.FloatBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import static org.lwjgl.opengl.GL33C.*;

/** Opaque display backing plus HDR display pixels, beams and hot sparks. */
public final class LaserEffectGeometry {
    private static int program,vao,vbo;
    private static long frames;
    private static int vertices;
    private static FloatBuffer data=org.lwjgl.BufferUtils.createFloatBuffer(65536);
    public static long renderedFrames(){return frames;}
    static String diagnostics(){return "laserFrames="+frames+" laserVertices="+vertices;}
    static void release(){if(program!=0)glDeleteProgram(program);if(vao!=0)glDeleteVertexArrays(vao);if(vbo!=0)glDeleteBuffers(vbo);program=vao=vbo=0;}
    private static void initialize()throws java.io.IOException {
        if(program!=0)return;
        int vs=FieldVolumeRenderer.shader(GL_VERTEX_SHADER,FieldVolumeRenderer.source("arc.vsh")),fs=0,candidate=0;
        try{
            fs=FieldVolumeRenderer.shader(GL_FRAGMENT_SHADER,FieldVolumeRenderer.source("laser.fsh"));candidate=glCreateProgram();
            glAttachShader(candidate,vs);glAttachShader(candidate,fs);glBindAttribLocation(candidate,0,"Position");glBindAttribLocation(candidate,1,"Coord");glBindAttribLocation(candidate,2,"Color");glLinkProgram(candidate);
            if(glGetProgrami(candidate,GL_LINK_STATUS)==0)throw new IllegalStateException(glGetProgramInfoLog(candidate));program=candidate;candidate=0;
        }finally{glDeleteShader(vs);if(fs!=0)glDeleteShader(fs);if(candidate!=0)glDeleteProgram(candidate);}
        vao=glGenVertexArrays();vbo=glGenBuffers();glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glEnableVertexAttribArray(0);glVertexAttribPointer(0,3,GL_FLOAT,false,40,0L);
        glEnableVertexAttribArray(1);glVertexAttribPointer(1,3,GL_FLOAT,false,40,12L);
        glEnableVertexAttribArray(2);glVertexAttribPointer(2,4,GL_FLOAT,false,40,24L);
    }
    static void draw(float[] geometry,Matrix4f projection,Matrix3f inverseView,int width,int height)throws java.io.IOException {
        vertices=geometry.length/10;if(vertices==0)return;
        if(data.capacity()<geometry.length)data=org.lwjgl.BufferUtils.createFloatBuffer(geometry.length);
        data.clear();data.put(geometry);data.flip();initialize();
        glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,data,GL_STREAM_DRAW);
        glUniform1i(glGetUniformLocation(program,"SceneDepth"),0);glUniform1i(glGetUniformLocation(program,"DisplayTexture"),1);glUniform2f(glGetUniformLocation(program,"Viewport"),width,height);
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
            var matrix=stack.mallocFloat(16);new Matrix4f(projection).mul(new Matrix4f().set3x3(new Matrix3f(inverseView).invert())).get(matrix);glUniformMatrix4fv(glGetUniformLocation(program,"ViewProjection"),false,matrix);
        }
        int active=glGetInteger(GL_ACTIVE_TEXTURE);glActiveTexture(GL_TEXTURE1);int previous=glGetInteger(GL_TEXTURE_BINDING_2D);
        try{
            int texture=Minecraft.getInstance().getTextureManager().getTexture(ResourceLocation.fromNamespaceAndPath("sbw_helldiver","textures/bedrock/vehicle/laser_hellpod_v1.png")).getId();
            glBindTexture(GL_TEXTURE_2D,texture);glBlendFuncSeparate(GL_ONE,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);glDrawArrays(GL_TRIANGLES,0,vertices);frames++;
        }finally{glBindTexture(GL_TEXTURE_2D,previous);glActiveTexture(active);}
    }
}
