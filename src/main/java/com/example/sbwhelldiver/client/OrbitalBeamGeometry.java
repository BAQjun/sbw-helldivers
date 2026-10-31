package com.example.sbwhelldiver.client;

import com.example.sbwhelldiver.entity.OrbitalLaserEntity;
import com.example.sbwhelldiver.entity.StratagemMarker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import static org.lwjgl.opengl.GL33C.*;

/** Camera rays against the actual ship-to-contact segment, after clouds and
 * shader-pack compositing. No dimension fog or far-plane clipping is applied;
 * scene depth still occludes the light at its real distance, including clouds.
 * The ship's sky projection preserves angles, so no artificial beam endpoint
 * or compressed depth is needed to connect to its belly. */
final class OrbitalBeamGeometry {
    record Beam(Vec3 contact,Vec3 origin,float strength,boolean trail) {}
    private static int program,vao;
    static List<Beam> collect(float partial,Vec3 camera) {
        var result=new ArrayList<Beam>();var level=Minecraft.getInstance().level;
        if(level==null)return result;
        for(var entity:level.entitiesForRendering())if(entity instanceof OrbitalLaserEntity e&&e.getActive()&&e.shouldRenderAtSqrDistance(e.distanceToSqr(camera))){
            float strength=(float)Math.max(0,Math.min(1,Math.min((level.getGameTime()-e.getStartedAt()+partial)/3.0,(e.getEndsAt()-level.getGameTime()-partial)/5.0)));
            result.add(new Beam(e.getPosition(partial).subtract(camera),e.getOrigin().subtract(camera),strength,false));
        }
        // The projectile is far outside terrain's render distance at launch.
        // Composite its real segment with scene depth, like the orbital laser;
        // an entity-pass ribbon is clipped/fogged or replaced by shader packs.
        for(var entity:level.entitiesForRendering())if(entity instanceof StratagemMarker marker){
            if(marker.isOrbitalLaser()||marker.isOrbitalGatling()||marker.isOrbitalBarrage()||marker.isOrbitalAirburst())continue;
            var origin=marker.getOrbitalOrigin();if(origin==null)continue;
            double progress=marker.orbitalProgress(partial);
            if(progress<=0||progress>1)continue;
            var target=marker.orbitalTarget(partial);
            result.add(new Beam(origin.lerp(target,progress).subtract(camera),
                origin.lerp(target,Math.max(0,progress-2.0/marker.getOrbitalFlightTicks())).subtract(camera),1,true));
        }
        return result;
    }
    static void release(){if(program!=0)glDeleteProgram(program);if(vao!=0)glDeleteVertexArrays(vao);program=vao=0;}
    private static void initialize()throws IOException {
        if(program!=0)return;
        int vs=FieldVolumeRenderer.shader(GL_VERTEX_SHADER,FieldVolumeRenderer.source("field.vsh")),fs=0,candidate=0;
        try{
            fs=FieldVolumeRenderer.shader(GL_FRAGMENT_SHADER,FieldVolumeRenderer.source("orbital_beam.fsh"));candidate=glCreateProgram();
            glAttachShader(candidate,vs);glAttachShader(candidate,fs);glLinkProgram(candidate);
            if(glGetProgrami(candidate,GL_LINK_STATUS)==0)throw new IllegalStateException(glGetProgramInfoLog(candidate));
            program=candidate;candidate=0;
        }finally{glDeleteShader(vs);if(fs!=0)glDeleteShader(fs);if(candidate!=0)glDeleteProgram(candidate);}
        vao=glGenVertexArrays();
    }
    static void draw(List<Beam> beams,Matrix4f projection,Matrix3f inverseView,int width,int height)throws IOException {
        initialize();glUseProgram(program);glBindVertexArray(vao);
        glUniform1i(glGetUniformLocation(program,"SceneDepth"),0);glUniform2f(glGetUniformLocation(program,"Viewport"),width,height);
        try(var stack=MemoryStack.stackPush()){
            var matrix=stack.mallocFloat(16);new Matrix4f(projection).invert().get(matrix);glUniformMatrix4fv(glGetUniformLocation(program,"InverseProjection"),false,matrix);
            var rotation=stack.mallocFloat(9);inverseView.get(rotation);glUniformMatrix3fv(glGetUniformLocation(program,"InverseViewRotation"),false,rotation);
        }
        for(var beam:beams){
            var a=beam.contact();var b=beam.origin();
            glUniform3f(glGetUniformLocation(program,"Contact"),(float)a.x,(float)a.y,(float)a.z);
            glUniform3f(glGetUniformLocation(program,"Origin"),(float)b.x,(float)b.y,(float)b.z);
            glUniform1i(glGetUniformLocation(program,"Trail"),beam.trail()?1:0);
            glUniform1f(glGetUniformLocation(program,"Strength"),beam.strength());glDrawArrays(GL_TRIANGLES,0,3);
        }
    }
}
