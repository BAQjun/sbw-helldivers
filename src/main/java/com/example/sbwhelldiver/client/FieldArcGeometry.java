package com.example.sbwhelldiver.client;

import com.example.sbwhelldiver.entity.MortarFieldEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.FloatBuffer;
import static org.lwjgl.opengl.GL33C.*;

/** Soft additive electric filaments. The fragment profile reaches zero at its
 * boundary, with no opaque rectangular lightning material for a pack to shade. */
public final class FieldArcGeometry {
    public interface Sink {
        void segment(Vec3 a,Vec3 b,double width,double alpha);
        void flash(Vec3 at,double size,double alpha);
    }
    private static int program,vao,vbo;
    private static final FloatBuffer data=org.lwjgl.BufferUtils.createFloatBuffer(262144);
    static void release(){
        if(program!=0)glDeleteProgram(program);if(vao!=0)glDeleteVertexArrays(vao);if(vbo!=0)glDeleteBuffers(vbo);
        program=vao=vbo=0;
    }
    private static void initialize() throws java.io.IOException {
        if(program!=0)return;
        int vs=FieldVolumeRenderer.shader(GL_VERTEX_SHADER,FieldVolumeRenderer.source("arc.vsh"));
        int fs=0,candidate=0;
        try {
            fs=FieldVolumeRenderer.shader(GL_FRAGMENT_SHADER,FieldVolumeRenderer.source("arc.fsh"));candidate=glCreateProgram();
            glAttachShader(candidate,vs);glAttachShader(candidate,fs);
            glBindAttribLocation(candidate,0,"Position");glBindAttribLocation(candidate,1,"Coord");glBindAttribLocation(candidate,2,"Color");
            glLinkProgram(candidate);if(glGetProgrami(candidate,GL_LINK_STATUS)==0)throw new IllegalStateException(glGetProgramInfoLog(candidate));
            program=candidate;candidate=0;
        } finally {glDeleteShader(vs);if(fs!=0)glDeleteShader(fs);if(candidate!=0)glDeleteProgram(candidate);}
        vao=glGenVertexArrays();vbo=glGenBuffers();glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
        glEnableVertexAttribArray(0);glVertexAttribPointer(0,3,GL_FLOAT,false,40,0L);
        glEnableVertexAttribArray(1);glVertexAttribPointer(1,3,GL_FLOAT,false,40,12L);
        glEnableVertexAttribArray(2);glVertexAttribPointer(2,4,GL_FLOAT,false,40,24L);
    }
    private static void vertex(Vec3 p,float u,float v,float mode,float alpha){
        data.put((float)p.x).put((float)p.y).put((float)p.z).put(u).put(v).put(mode).put(.30f).put(.65f).put(1f).put(alpha);
    }
    private static void quad(Vec3 a,Vec3 b,Vec3 c,Vec3 d,float mode,double alpha){
        if(data.remaining()<60)return;
        float f=(float)alpha;
        vertex(a,0,0,mode,f);vertex(b,0,1,mode,f);vertex(c,1,1,mode,f);
        vertex(a,0,0,mode,f);vertex(c,1,1,mode,f);vertex(d,1,0,mode,f);
    }
    static void draw(java.util.List<MortarFieldEntity> fields,float partial,Vec3 camera,Matrix4f projection,Matrix3f inverseView,int width,int height) throws java.io.IOException {
        if(!ShaderPackCompat.INSTANCE.active())return;
        data.clear();
        Vector3f right3=new Vector3f(1,0,0).mul(inverseView),up3=new Vector3f(0,1,0).mul(inverseView);
        Vec3 right=new Vec3(right3.x,right3.y,right3.z),up=new Vec3(up3.x,up3.y,up3.z);
        for(var field:fields)if(field.getKind()==MortarFieldEntity.Kind.EMS){
            Vec3 origin=field.position().subtract(camera);
            Sink sink=new Sink(){
                public void segment(Vec3 a,Vec3 b,double width,double alpha){
                    a=a.add(origin);b=b.add(origin);Vec3 axis=b.subtract(a);
                    if(axis.lengthSqr()<1e-8)return;
                    Vec3 side=axis.normalize().cross(a.scale(-1)).normalize().scale(width);
                    quad(a.subtract(side),a.add(side),b.add(side),b.subtract(side),0,alpha);
                }
                public void flash(Vec3 at,double size,double alpha){
                    Vec3 center=at.add(origin),r=right.scale(size),u=up.scale(size);
                    quad(center.subtract(r).subtract(u),center.subtract(r).add(u),center.add(r).add(u),center.add(r).subtract(u),1,alpha);
                }
            };
            EmsFieldVisual.INSTANCE.collect(field,partial,sink);
        }
        data.flip();if(!data.hasRemaining())return;
        initialize();glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,data,GL_STREAM_DRAW);
        glUniform1i(glGetUniformLocation(program,"SceneDepth"),0);glUniform2f(glGetUniformLocation(program,"Viewport"),width,height);
        try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
            var matrix=stack.mallocFloat(16);
            new Matrix4f(projection).mul(new Matrix4f().set3x3(new Matrix3f(inverseView).invert())).get(matrix);
            glUniformMatrix4fv(glGetUniformLocation(program,"ViewProjection"),false,matrix);
        }
        glColorMask(true,true,true,false);glBlendFuncSeparate(GL_ONE,GL_ONE,GL_ZERO,GL_ONE);
        glDrawArrays(GL_TRIANGLES,0,data.remaining()/10);
        glColorMask(true,true,true,true);glBlendFuncSeparate(GL_ONE,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
    }
}
