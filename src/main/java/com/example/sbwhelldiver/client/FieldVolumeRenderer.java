package com.example.sbwhelldiver.client;

import com.example.sbwhelldiver.entity.MortarFieldEntity;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Random;
import static org.lwjgl.opengl.GL33C.*;

/** True per-pixel ray integration of world-space fog. AFTER_LEVEL is after
 * Oculus/Iris final compositing and before vanilla hands/HUD. An isolated GL
 * program avoids the shader-pack replacement of custom entity shaders. Scene
 * depth is copied before drawing (no framebuffer texture feedback), and all
 * modified GL state is restored. No fog planes or enclosing meshes are drawn.
 */
public final class FieldVolumeRenderer {
    private static int program,vao,noise,depth,depthFbo,width,height,depthFormat;
    private static boolean failed;
    private static volatile boolean reload;
    private static long frames,events;
    private static int visible;
    public static String diagnostics(){return "events="+events+" frames="+frames+" fields="+visible+" ready="+isReady()+" depthFormat="+depthFormat;}
    public static void requestReload(){reload=true;}
    public static boolean isReady(){return program!=0&&!failed;}
    public static long renderedFrames(){return frames;}
    private static void release(){
        FieldArcGeometry.release();
        if(program!=0)glDeleteProgram(program);if(vao!=0)glDeleteVertexArrays(vao);
        if(noise!=0)glDeleteTextures(noise);if(depth!=0)glDeleteTextures(depth);if(depthFbo!=0)glDeleteFramebuffers(depthFbo);
        program=vao=noise=depth=depthFbo=width=height=depthFormat=0;failed=false;reload=false;
    }
    static String source(String name) throws IOException {
        try(var input=Minecraft.getInstance().getResourceManager().open(new ResourceLocation("sbw_helldiver","shaders/volume/"+name))) {
            return new String(input.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
    static int shader(int type,String source) {
        int shader=glCreateShader(type);
        glShaderSource(shader,source);glCompileShader(shader);
        if(glGetShaderi(shader,GL_COMPILE_STATUS)==GL_FALSE) {
            String error=glGetShaderInfoLog(shader);glDeleteShader(shader);throw new IllegalStateException(error);
        }
        return shader;
    }
    private static void initialize() throws IOException {
        if(program!=0)return;
        int vertex=shader(GL_VERTEX_SHADER,source("field.vsh"));
        int fragment=0,candidate=0;
        try {
            fragment=shader(GL_FRAGMENT_SHADER,source("field.fsh"));candidate=glCreateProgram();
            glAttachShader(candidate,vertex);glAttachShader(candidate,fragment);glLinkProgram(candidate);
            if(glGetProgrami(candidate,GL_LINK_STATUS)==GL_FALSE)throw new IllegalStateException(glGetProgramInfoLog(candidate));
            program=candidate;candidate=0;
        } finally {glDeleteShader(vertex);if(fragment!=0)glDeleteShader(fragment);if(candidate!=0)glDeleteProgram(candidate);}
        vao=glGenVertexArrays();noise=glGenTextures();depth=glGenTextures();depthFbo=glGenFramebuffers();
        glBindTexture(GL_TEXTURE_3D,noise);
        glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        for(int axis:new int[]{GL_TEXTURE_WRAP_S,GL_TEXTURE_WRAP_T,GL_TEXTURE_WRAP_R})glTexParameteri(GL_TEXTURE_3D,axis,GL_REPEAT);
        // Hardware trilinear interpolation of a repeatable 3D noise volume.
        ByteBuffer data=org.lwjgl.BufferUtils.createByteBuffer(64*64*64);
        Random random=new Random(0x534257464f47L);
        while(data.hasRemaining())data.put((byte)random.nextInt(256));data.flip();
        glTexImage3D(GL_TEXTURE_3D,0,GL_R8,64,64,64,0,GL_RED,GL_UNSIGNED_BYTE,data);
    }
    private static int uniform(String name){return glGetUniformLocation(program,name);}
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||ShaderPackCompat.INSTANCE.shadowPass())return;
        events++;Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        Vec3 camera=event.getCamera().getPosition();
        var fields=new ArrayList<MortarFieldEntity>();
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof MortarFieldEntity f&&f.isAlive()&&f.distanceToSqr(camera)<128*128)fields.add(f);
        visible=fields.size();
        if(fields.isEmpty()||(failed&&!reload))return;
        fields.sort(Comparator.comparingDouble((MortarFieldEntity f)->f.distanceToSqr(camera)).reversed());
        try(var stack=MemoryStack.stackPush()) {
            // Raw GL calls are paired with exact restoration, leaving Mojang's
            // cached state unchanged and avoiding loader-specific shader hooks.
            int oldProgram=glGetInteger(GL_CURRENT_PROGRAM),oldVao=glGetInteger(GL_VERTEX_ARRAY_BINDING),oldBuffer=glGetInteger(GL_ARRAY_BUFFER_BINDING);
            int oldDraw=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),oldRead=glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            int oldActive=glGetInteger(GL_ACTIVE_TEXTURE);
            int oldSampler0=glGetIntegeri(GL_SAMPLER_BINDING,0),oldSampler1=glGetIntegeri(GL_SAMPLER_BINDING,1);
            glActiveTexture(GL_TEXTURE0);int old2D=glGetInteger(GL_TEXTURE_BINDING_2D),old3D=glGetInteger(GL_TEXTURE_BINDING_3D);
            glActiveTexture(GL_TEXTURE1);int old1=glGetInteger(GL_TEXTURE_BINDING_3D);
            boolean test=glIsEnabled(GL_DEPTH_TEST),blend=glIsEnabled(GL_BLEND),cull=glIsEnabled(GL_CULL_FACE),scissor=glIsEnabled(GL_SCISSOR_TEST);
            boolean writeDepth=glGetBoolean(GL_DEPTH_WRITEMASK);
            int src=glGetInteger(GL_BLEND_SRC_RGB),dst=glGetInteger(GL_BLEND_DST_RGB),srcA=glGetInteger(GL_BLEND_SRC_ALPHA),dstA=glGetInteger(GL_BLEND_DST_ALPHA);
            int eq=glGetInteger(GL_BLEND_EQUATION_RGB),eqA=glGetInteger(GL_BLEND_EQUATION_ALPHA);
            IntBuffer viewport=stack.mallocInt(4);glGetIntegerv(GL_VIEWPORT,viewport);
            ByteBuffer mask=stack.malloc(4);glGetBooleanv(GL_COLOR_WRITEMASK,mask);
            try {
                if(Boolean.getBoolean("sbw_helldiver.shaderFieldSmokeTest"))glGetError();
                if(reload)release();
                glBindSampler(0,0);glBindSampler(1,0);
                glActiveTexture(GL_TEXTURE0);initialize();
                var target=mc.getMainRenderTarget();int w=target.width,h=target.height;
                glBindTexture(GL_TEXTURE_2D,target.getDepthTextureId());
                int format=glGetTexLevelParameteri(GL_TEXTURE_2D,0,GL_TEXTURE_INTERNAL_FORMAT);
                glBindTexture(GL_TEXTURE_2D,depth);
                if(width!=w||height!=h||depthFormat!=format) {
                    width=w;height=h;depthFormat=format;
                    boolean stencil=format==GL_DEPTH24_STENCIL8||format==GL_DEPTH32F_STENCIL8;
                    int layout=stencil?GL_DEPTH_STENCIL:GL_DEPTH_COMPONENT;
                    int type=format==GL_DEPTH32F_STENCIL8?GL_FLOAT_32_UNSIGNED_INT_24_8_REV:stencil?GL_UNSIGNED_INT_24_8:GL_FLOAT;
                    glTexImage2D(GL_TEXTURE_2D,0,format,w,h,0,layout,type,(ByteBuffer)null);
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
                    glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_COMPARE_MODE,GL_NONE);
                    glBindFramebuffer(GL_FRAMEBUFFER,depthFbo);
                    glFramebufferTexture2D(GL_FRAMEBUFFER,GL_DEPTH_STENCIL_ATTACHMENT,GL_TEXTURE_2D,0,0);
                    glFramebufferTexture2D(GL_FRAMEBUFFER,stencil?GL_DEPTH_STENCIL_ATTACHMENT:GL_DEPTH_ATTACHMENT,GL_TEXTURE_2D,depth,0);
                    glDrawBuffer(GL_NONE);glReadBuffer(GL_NONE);
                    if(glCheckFramebufferStatus(GL_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete volume depth target");
                }
                glBindFramebuffer(GL_READ_FRAMEBUFFER,target.frameBufferId);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER,depthFbo);
                glBlitFramebuffer(0,0,w,h,0,0,w,h,GL_DEPTH_BUFFER_BIT,GL_NEAREST);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER,target.frameBufferId);
                glViewport(0,0,w,h);glDisable(GL_DEPTH_TEST);glDepthMask(false);glDisable(GL_CULL_FACE);glDisable(GL_SCISSOR_TEST);
                glColorMask(true,true,true,true);glEnable(GL_BLEND);glBlendEquationSeparate(GL_FUNC_ADD,GL_FUNC_ADD);
                glBlendFuncSeparate(GL_ONE,GL_ONE_MINUS_SRC_ALPHA,GL_ONE,GL_ONE_MINUS_SRC_ALPHA);
                FieldArcGeometry.draw(fields,event.getPartialTick(),camera,event.getProjectionMatrix(),new Matrix3f(RenderSystem.getInverseViewRotationMatrix()),w,h);
                glUseProgram(program);glBindVertexArray(vao);
                glUniform1i(uniform("SceneDepth"),0);glUniform1i(uniform("DensityNoise"),1);
                glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_3D,noise);
                FloatBuffer matrix=stack.mallocFloat(16);
                new Matrix4f(event.getProjectionMatrix()).invert().get(matrix);glUniformMatrix4fv(uniform("InverseProjection"),false,matrix);
                FloatBuffer rotation=stack.mallocFloat(9);
                new Matrix3f(RenderSystem.getInverseViewRotationMatrix()).get(rotation);glUniformMatrix3fv(uniform("InverseViewRotation"),false,rotation);
                glUniform2f(uniform("Viewport"),w,h);
                for(var field:fields) {
                    Vec3 eye=camera.subtract(field.position());boolean gas=field.getKind()==MortarFieldEntity.Kind.GAS;
                    float partial=event.getPartialTick();
                    float fade=(float)Math.max(0,Math.min(1,Math.min((field.tickCount+partial)/(gas?7:6),field.getRemainingTicks()/(gas?20.0:12.0))));
                    glUniform3f(uniform("Eye"),(float)eye.x,(float)eye.y,(float)eye.z);
                    glUniform1f(uniform("Radius"),(float)field.getRadius());glUniform1f(uniform("Time"),(field.tickCount+partial)/20f);
                    glUniform1f(uniform("Seed"),(float)((field.getX()*.731+field.getZ()*.173)%91));
                    glUniform1f(uniform("Extinction"),(gas?.42f:.16f)*fade);
                    glUniform3f(uniform("FogColor"),gas?.48f:.38f,gas?.55f:.60f,gas?.15f:.84f);
                    glDrawArrays(GL_TRIANGLES,0,3);
                }
                if(Boolean.getBoolean("sbw_helldiver.shaderFieldSmokeTest")) {
                    int error=glGetError();if(error!=GL_NO_ERROR)throw new IllegalStateException("Volume GL error "+error);
                }
                frames++;
            } catch(Exception failure) {
                failed=true;LogUtils.getLogger().error("Unable to render area volume",failure);
            } finally {
                glUseProgram(oldProgram);glBindVertexArray(oldVao);glBindBuffer(GL_ARRAY_BUFFER,oldBuffer);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER,oldDraw);glBindFramebuffer(GL_READ_FRAMEBUFFER,oldRead);
                glViewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
                glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,old2D);glBindTexture(GL_TEXTURE_3D,old3D);
                glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_3D,old1);glActiveTexture(oldActive);
                glBindSampler(0,oldSampler0);glBindSampler(1,oldSampler1);
                set(GL_DEPTH_TEST,test);set(GL_BLEND,blend);set(GL_CULL_FACE,cull);set(GL_SCISSOR_TEST,scissor);glDepthMask(writeDepth);
                glBlendFuncSeparate(src,dst,srcA,dstA);glBlendEquationSeparate(eq,eqA);
                glColorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);
            }
        }
    }
    private static void set(int state,boolean enabled){if(enabled)glEnable(state);else glDisable(state);}
}
