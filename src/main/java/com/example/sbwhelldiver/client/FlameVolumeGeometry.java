package com.example.sbwhelldiver.client;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import net.minecraft.world.phys.Vec3;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL31C.*;

/** Instanced conservative boxes bound real 3D combustion. Each fragment
 * integrates a view ray through an ellipsoid, clipped by copied world depth. */
public final class FlameVolumeGeometry {
    private static int program,vao,vbo,instanceTexture;
    private static long frames;
    private static int instances;
    private static FloatBuffer data=org.lwjgl.BufferUtils.createFloatBuffer(1600*12);
    public static long renderedFrames(){return frames;}
    public static String diagnostics(){return "flameFrames="+frames+" flameInstances="+instances+" flameProgram="+program;}
    static void release(){
        if(program!=0)glDeleteProgram(program);if(vao!=0)glDeleteVertexArrays(vao);if(vbo!=0)glDeleteBuffers(vbo);
        if(instanceTexture!=0)glDeleteTextures(instanceTexture);
        program=vao=vbo=instanceTexture=0;
    }
    private static void initialize() throws java.io.IOException {
        if(program!=0)return;
        int vs=FieldVolumeRenderer.shader(GL_VERTEX_SHADER,FieldVolumeRenderer.source("flame.vsh"));
        int fs=0,candidate=0;
        try {
            fs=FieldVolumeRenderer.shader(GL_FRAGMENT_SHADER,FieldVolumeRenderer.source("flame.fsh"));candidate=glCreateProgram();
            glAttachShader(candidate,vs);glAttachShader(candidate,fs);
            glLinkProgram(candidate);if(glGetProgrami(candidate,GL_LINK_STATUS)==0)throw new IllegalStateException(glGetProgramInfoLog(candidate));
            program=candidate;candidate=0;
        } finally {glDeleteShader(vs);if(fs!=0)glDeleteShader(fs);if(candidate!=0)glDeleteProgram(candidate);}
        vao=glGenVertexArrays();
        vbo=glGenBuffers();
        instanceTexture=glGenTextures();
    }
    static void draw(float[] volumes,Matrix4f projection,Matrix3f inverseView,Vec3 camera,int width,int height,float time) throws java.io.IOException {
        instances=volumes.length/12;if(instances==0)return;
        if(data.capacity()<volumes.length)data=org.lwjgl.BufferUtils.createFloatBuffer(volumes.length);
        data.clear();data.put(volumes);data.flip();initialize();
        glUseProgram(program);glBindVertexArray(vao);
        glUniform1i(glGetUniformLocation(program,"FlameInstances"),2);
        glUniform1i(glGetUniformLocation(program,"SceneDepth"),0);glUniform1i(glGetUniformLocation(program,"DensityNoise"),1);
        glUniform2f(glGetUniformLocation(program,"Viewport"),width,height);glUniform1f(glGetUniformLocation(program,"Time"),time);
        glUniform3f(glGetUniformLocation(program,"CameraPosition"),(float)camera.x,(float)camera.y,(float)camera.z);
        try(var stack=MemoryStack.stackPush()){
            var mat=stack.mallocFloat(16);
            new Matrix4f(projection).mul(new Matrix4f().set3x3(new Matrix3f(inverseView).invert())).get(mat);
            glUniformMatrix4fv(glGetUniformLocation(program,"ViewProjection"),false,mat);
            new Matrix4f(projection).invert().get(mat);glUniformMatrix4fv(glGetUniformLocation(program,"InverseProjection"),false,mat);
            var rot=stack.mallocFloat(9);inverseView.get(rot);glUniformMatrix3fv(glGetUniformLocation(program,"InverseViewRotation"),false,rot);
        }
        int face=glGetInteger(GL_CULL_FACE_MODE);boolean cull=glIsEnabled(GL_CULL_FACE);
        int activeTexture=glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE2);
        int previousTexture=glGetInteger(GL_TEXTURE_BINDING_BUFFER);
        int previousBuffer=glGetInteger(GL_TEXTURE_BUFFER);
        try {
            glBindBuffer(GL_TEXTURE_BUFFER,vbo);
            glBufferData(GL_TEXTURE_BUFFER,data,GL_STREAM_DRAW);
            glBindTexture(GL_TEXTURE_BUFFER,instanceTexture);
            glTexBuffer(GL_TEXTURE_BUFFER,GL_RGBA32F,vbo);
            glEnable(GL_CULL_FACE);glCullFace(GL_FRONT);
            glBlendFuncSeparate(GL_ONE,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
            glDrawArraysInstanced(GL_TRIANGLES,0,36,instances);frames++;
        } finally {
            glBindTexture(GL_TEXTURE_BUFFER,previousTexture);
            glBindBuffer(GL_TEXTURE_BUFFER,previousBuffer);
            glActiveTexture(activeTexture);
            glCullFace(face);if(!cull)glDisable(GL_CULL_FACE);
        }
    }
}
