package com.example.sbwhelldiver.client;

import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Comparator;
import static org.lwjgl.opengl.GL33C.*;

/** Deferred moving lights illuminate actual world surfaces, independently of
 * flame emission/bloom. Depth reconstructs blocks and entities alike; screen
 * space shadow rays keep foreground geometry from receiving light through it.
 * No server light blocks or shader-pack-specific program replacements are used. */
public final class FlameSurfaceLighting {
    private static int program,vao,color,fbo,width,height,count;
    private static long frames;
    public static long renderedFrames(){return frames;}
    static String diagnostics(){return "lightFrames="+frames+" flameLights="+count;}
    private record Cell(int x,int y,int z){}
    private static final class Light {
        double x,y,z,weight;float power;
        void add(float[] a,int i){float w=a[i+9];x+=a[i]*w;y+=a[i+1]*w;z+=a[i+2]*w;weight+=w;power=Math.max(power,w);}
        double distance(){return (x*x+y*y+z*z)/(weight*weight);}
    }
    static void release(){
        if(program!=0)glDeleteProgram(program);if(vao!=0)glDeleteVertexArrays(vao);
        if(color!=0)glDeleteTextures(color);if(fbo!=0)glDeleteFramebuffers(fbo);
        program=vao=color=fbo=width=height=count=0;
    }
    private static void initialize(int w,int h)throws IOException {
        if(program==0){
            int vs=FieldVolumeRenderer.shader(GL_VERTEX_SHADER,FieldVolumeRenderer.source("field.vsh")),fs=0,candidate=0;
            try{
                fs=FieldVolumeRenderer.shader(GL_FRAGMENT_SHADER,FieldVolumeRenderer.source("flame_light.fsh"));candidate=glCreateProgram();
                glAttachShader(candidate,vs);glAttachShader(candidate,fs);glLinkProgram(candidate);
                if(glGetProgrami(candidate,GL_LINK_STATUS)==0)throw new IllegalStateException(glGetProgramInfoLog(candidate));
                program=candidate;candidate=0;
            }finally{glDeleteShader(vs);if(fs!=0)glDeleteShader(fs);if(candidate!=0)glDeleteProgram(candidate);}
            vao=glGenVertexArrays();color=glGenTextures();fbo=glGenFramebuffers();
        }
        if(width==w&&height==h)return;
        glBindTexture(GL_TEXTURE_2D,color);
        glTexImage2D(GL_TEXTURE_2D,0,GL_RGBA16F,w,h,0,GL_RGBA,GL_HALF_FLOAT,(ByteBuffer)null);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER,fbo);glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,color,0);glDrawBuffer(GL_COLOR_ATTACHMENT0);
        if(glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete flame light color target");
        width=w;height=h;
    }
    static void draw(float[] flames,Vec3 camera,Matrix4f projection,Matrix3f inverseView,int w,int h)throws IOException {
        // Average nearby fuel parcels into bounded lights, rather than creating
        // hundreds of duplicate light sources or tying illumination to the gun.
        var cells=new HashMap<Cell,Light>();
        for(int i=0;i<flames.length;i+=12){
            if((flames[i+10]>1.5f&&flames[i+10]<3.5f)||flames[i+9]<.07f)continue;
            var key=new Cell((int)Math.floor((flames[i]+camera.x)/3.),(int)Math.floor((flames[i+1]+camera.y)/3.),(int)Math.floor((flames[i+2]+camera.z)/3.));
            cells.computeIfAbsent(key,k->new Light()).add(flames,i);
        }
        var lights=cells.values().stream().sorted(Comparator.comparingDouble(Light::distance)).limit(48).toList();count=lights.size();if(count==0)return;
        int destination=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),read=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),active=glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE1);int previous1=glGetInteger(GL_TEXTURE_BINDING_2D);
        boolean blend=glIsEnabled(GL_BLEND);
        try(var stack=MemoryStack.stackPush()){
            initialize(w,h);
            glBindFramebuffer(GL_READ_FRAMEBUFFER,destination);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,fbo);
            glBlitFramebuffer(0,0,w,h,0,0,w,h,GL_COLOR_BUFFER_BIT,GL_NEAREST);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,destination);glBindTexture(GL_TEXTURE_2D,color);
            glUseProgram(program);glBindVertexArray(vao);glDisable(GL_BLEND);
            glUniform1i(glGetUniformLocation(program,"SceneDepth"),0);glUniform1i(glGetUniformLocation(program,"SceneColor"),1);
            glUniform1i(glGetUniformLocation(program,"LightCount"),count);
            var positions=stack.mallocFloat(count*4);
            for(var light:lights)positions.put((float)(light.x/light.weight)).put((float)(light.y/light.weight)).put((float)(light.z/light.weight)).put(light.power);
            positions.flip();glUniform4fv(glGetUniformLocation(program,"Lights[0]"),positions);
            var matrix=stack.mallocFloat(16);new Matrix4f(projection).invert().get(matrix);glUniformMatrix4fv(glGetUniformLocation(program,"InverseProjection"),false,matrix);
            new Matrix4f(projection).mul(new Matrix4f().set3x3(new Matrix3f(inverseView).invert())).get(matrix);glUniformMatrix4fv(glGetUniformLocation(program,"ProjectionView"),false,matrix);
            var rotation=stack.mallocFloat(9);inverseView.get(rotation);glUniformMatrix3fv(glGetUniformLocation(program,"InverseViewRotation"),false,rotation);
            glDrawArrays(GL_TRIANGLES,0,3);frames++;
        }finally{
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER,destination);glBindFramebuffer(GL_READ_FRAMEBUFFER,read);
            glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,previous1);glActiveTexture(active);
            if(blend)glEnable(GL_BLEND);else glDisable(GL_BLEND);
        }
    }
}
