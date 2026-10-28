package com.example.sbwhelldiver.client;

import com.example.sbwhelldiver.entity.SentryHellpodEntity;
import com.example.sbwhelldiver.entity.ShieldGeneratorEntity;
import com.mojang.blaze3d.vertex.*;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import java.io.IOException;
import java.util.List;
import static org.lwjgl.opengl.GL33C.*;

/** Shader-pack independent drawing of the original energy meshes. Geometry,
 * charge envelopes and hit normals come from the same code as vanilla rendering.
 * Only material submission changes: no pack can replace these GL programs. */
final class EnergyEffectGeometry implements MultiBufferSource {
    private final ByteBufferBuilder storage=new ByteBufferBuilder(262144);
    private BufferBuilder builder;
    private int program,vao,vbo,indices,indexCapacity;
    private final String fragment;
    private boolean building;
    private Matrix4f viewProjection;
    private int width,height;
    private static final EnergyEffectGeometry shield=new EnergyEffectGeometry("shield_energy");
    private static final EnergyEffectGeometry arc=new EnergyEffectGeometry("tesla_arc");
    private static final EnergyEffectGeometry plasma=new EnergyEffectGeometry("tesla_plasma");
    private static long shieldFrames,teslaFrames;
    private EnergyEffectGeometry(String fragment){this.fragment=fragment;}
    static String diagnostics(){return "energyShield="+shieldFrames+" energyTesla="+teslaFrames;}
    static void release(){shield.close();arc.close();plasma.close();}
    private void close(){
        if(building){builder.buildOrThrow().close();building=false;}
        if(program!=0)glDeleteProgram(program);if(vao!=0)glDeleteVertexArrays(vao);
        if(vbo!=0)glDeleteBuffers(vbo);if(indices!=0)glDeleteBuffers(indices);
        program=vao=vbo=indices=indexCapacity=0;
    }
    private void initialize() throws IOException {
        if(program!=0)return;
        int vs=FieldVolumeRenderer.shader(GL_VERTEX_SHADER,FieldVolumeRenderer.source("energy.vsh"));
        int fs=0,candidate=0;
        try {
            // Compile the same fragment source as the vanilla material, enabling
            // only explicit scene-depth testing and premultiplied emission.
            String source=FieldVolumeRenderer.sourcePath("shaders/core/"+fragment+".fsh")
                .replace("#version 150","#version 150\n#define SBW_ENERGY_POST");
            fs=FieldVolumeRenderer.shader(GL_FRAGMENT_SHADER,source);
            candidate=glCreateProgram();glAttachShader(candidate,vs);glAttachShader(candidate,fs);
            glBindAttribLocation(candidate,0,"Position");glBindAttribLocation(candidate,1,"Color");glBindAttribLocation(candidate,2,"UV0");
            glLinkProgram(candidate);
            if(glGetProgrami(candidate,GL_LINK_STATUS)==0)throw new IllegalStateException(glGetProgramInfoLog(candidate));
            program=candidate;candidate=0;
        } finally {glDeleteShader(vs);if(fs!=0)glDeleteShader(fs);if(candidate!=0)glDeleteProgram(candidate);}
        vao=glGenVertexArrays();vbo=glGenBuffers();indices=glGenBuffers();
        glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);glBindBuffer(GL_ELEMENT_ARRAY_BUFFER,indices);
        // POSITION_TEX_COLOR: three position floats, two texture floats, four normalized color bytes.
        glEnableVertexAttribArray(0);glVertexAttribPointer(0,3,GL_FLOAT,false,24,0L);
        glEnableVertexAttribArray(1);glVertexAttribPointer(1,4,GL_UNSIGNED_BYTE,true,24,20L);
        glEnableVertexAttribArray(2);glVertexAttribPointer(2,2,GL_FLOAT,false,24,12L);
    }
    @Override public VertexConsumer getBuffer(RenderType ignored){
        if(!building){builder=new BufferBuilder(storage,VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);building=true;}
        return builder;
    }
    private int uniform(String name){return glGetUniformLocation(program,name);}
    private void flush() {
        if(!building)return;
        building=false;
        var mesh=builder.build();
        if(mesh==null)return;
        try {
            int count=mesh.drawState().vertexCount();if(count==0)return;
            glUseProgram(program);glBindVertexArray(vao);glBindBuffer(GL_ARRAY_BUFFER,vbo);
            glBufferData(GL_ARRAY_BUFFER,mesh.vertexBuffer(),GL_STREAM_DRAW);
            if(count>indexCapacity){
                indexCapacity=(count+1023)/1024*1024;
                var data=org.lwjgl.BufferUtils.createIntBuffer(indexCapacity/4*6);
                for(int i=0;i<indexCapacity;i+=4)data.put(i).put(i+1).put(i+2).put(i).put(i+2).put(i+3);
                data.flip();glBufferData(GL_ELEMENT_ARRAY_BUFFER,data,GL_STATIC_DRAW);
            }
            try(var stack=org.lwjgl.system.MemoryStack.stackPush()){
                glUniformMatrix4fv(uniform("ViewProjection"),false,viewProjection.get(stack.mallocFloat(16)));
            }
            glUniform1i(uniform("SceneDepth"),0);glUniform2f(uniform("Viewport"),width,height);
            glUniform4f(uniform("ColorModulator"),1,1,1,1);
            glUniform1f(uniform("GameTime"),RenderSystem.getShaderGameTime());
            glUniform1f(uniform("FogStart"),RenderSystem.getShaderFogStart());
            glUniform1f(uniform("FogEnd"),RenderSystem.getShaderFogEnd());
            if(this==shield){
                var original=ShieldRenderTypes.shader();
                if(original==null)return;
                for(String name:new String[]{"ShieldRadius","Strength","EffectTime","BreakFlash","EffectMode","ViewVector","Hit0","Hit1","Hit2","Hit3","Hit4","Hit5","Hit6","Hit7"}){
                    var source=original.getUniform(name);if(source==null)continue;
                    var f=source.getFloatBuffer();int location=uniform(name);
                    if(name.startsWith("Hit"))glUniform4f(location,f.get(0),f.get(1),f.get(2),f.get(3));
                    else if(name.equals("ViewVector"))glUniform3f(location,f.get(0),f.get(1),f.get(2));
                    else glUniform1f(location,f.get(0));
                }
                glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE_MINUS_SRC_ALPHA,GL_ZERO,GL_ONE);
            }else glBlendFuncSeparate(GL_ONE,GL_ONE,GL_ZERO,GL_ONE);
            glColorMask(true,true,true,false);
            glDrawElements(GL_TRIANGLES,count/4*6,GL_UNSIGNED_INT,0L);
        }finally{mesh.close();}
    }
    private void prepare(Matrix4f matrix,int w,int h)throws IOException{initialize();viewProjection=matrix;width=w;height=h;}
    static void draw(List<net.minecraft.world.entity.Entity> entities,float partial,Vec3 camera,Matrix4f projection,Matrix3f inverseView,int w,int h)throws IOException {
        if(entities.isEmpty())return;
        Matrix4f matrix=new Matrix4f(projection).mul(new Matrix4f().set3x3(new Matrix3f(inverseView).invert()));
        if(entities.stream().anyMatch(e->e instanceof ShieldGeneratorEntity)){
            shield.prepare(matrix,w,h);
            ShieldGeneratorClient.INSTANCE.renderScene(partial,camera,new PoseStack(),shield,shield::flush);
            shieldFrames++;
        }
        if(entities.stream().anyMatch(e->e instanceof SentryHellpodEntity)){
            arc.prepare(matrix,w,h);plasma.prepare(matrix,w,h);
            glColorMask(true,true,true,true);
            FlameGlow.renderAdditive(w,h,.24f,()->{
                MultiBufferSource buffers=type->(type==TeslaRenderTypes.ARC?arc:plasma).getBuffer(type);
                for(var entity:entities)if(entity instanceof SentryHellpodEntity tower){
                    Vec3 at=tower.getPosition(partial).subtract(camera);PoseStack pose=new PoseStack();pose.translate(at.x,at.y,at.z);
                    TeslaTowerClient.INSTANCE.render(tower,partial,pose,buffers);
                }
                arc.flush();plasma.flush();
            });
            teslaFrames++;
        }
        glColorMask(true,true,true,true);
        glBlendFuncSeparate(GL_ONE,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
    }
}
