package com.example.sbwhelldiver.client;

import com.example.sbwhelldiver.entity.MortarFieldEntity;
import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
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
import java.util.HashMap;
import static org.lwjgl.opengl.GL33C.*;

/** True per-pixel ray integration of world-space fog. AFTER_LEVEL is after
 * Oculus/Iris final compositing and before vanilla hands/HUD. An isolated GL
 * program avoids the shader-pack replacement of custom entity shaders. Scene
 * depth is copied before drawing (no framebuffer texture feedback), and all
 * modified GL state is restored. No fog planes or enclosing meshes are drawn.
 */
public final class FieldVolumeRenderer {
    private static int program,vao,noise,depth,depthFbo,width,height,depthFormat,terrain;
    private static final HashMap<String,Integer> uniforms=new HashMap<>();
    private static boolean failed;
    private static volatile boolean reload;
    private static long frames,events;
    private static int visible;
    public static String diagnostics(){return "events="+events+" frames="+frames+" fields="+visible+" ready="+isReady()+" depthFormat="+depthFormat+" "+FlameVolumeGeometry.diagnostics()+" "+FlameSurfaceLighting.diagnostics()+" "+LaserEffectGeometry.diagnostics()+" "+EnergyEffectGeometry.diagnostics();}
    public static void requestReload(){reload=true;}
    public static boolean isReady(){return program!=0&&!failed;}
    public static long renderedFrames(){return frames;}
    private static void release(){
        FieldFogLayer.release();FieldTerrainMask.clear();uniforms.clear();MineGlowGeometry.release();EnergyEffectGeometry.release();FieldArcGeometry.release();FlameVolumeGeometry.release();FlameGlow.release();FlameSurfaceLighting.release();LaserEffectGeometry.release();OrbitalBeamGeometry.release();
        if(program!=0)glDeleteProgram(program);if(vao!=0)glDeleteVertexArrays(vao);
        if(terrain!=0)glDeleteTextures(terrain);if(noise!=0)glDeleteTextures(noise);if(depth!=0)glDeleteTextures(depth);if(depthFbo!=0)glDeleteFramebuffers(depthFbo);
        program=vao=noise=depth=depthFbo=width=height=depthFormat=terrain=0;failed=false;reload=false;
    }
    static String source(String name) throws IOException {
        return sourcePath("shaders/volume/"+name);
    }
    static String sourcePath(String path) throws IOException {
        try(var input=Minecraft.getInstance().getResourceManager().open(ResourceLocation.fromNamespaceAndPath("sbw_helldiver",path))) {
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
        terrain=glGenTextures();
        glBindTexture(GL_TEXTURE_2D,terrain);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,GL_NEAREST);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        // Radius is capped at 16 blocks: one fixed 33x33 floor texture avoids
        // reallocating GPU storage for each cloud or frame.
        glTexImage2D(GL_TEXTURE_2D,0,GL_R32F,33,33,0,GL_RED,GL_FLOAT,(ByteBuffer)null);
        glBindTexture(GL_TEXTURE_3D,noise);
        glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MIN_FILTER,GL_LINEAR);glTexParameteri(GL_TEXTURE_3D,GL_TEXTURE_MAG_FILTER,GL_LINEAR);
        for(int axis:new int[]{GL_TEXTURE_WRAP_S,GL_TEXTURE_WRAP_T,GL_TEXTURE_WRAP_R})glTexParameteri(GL_TEXTURE_3D,axis,GL_REPEAT);
        // Hardware trilinear interpolation of a repeatable 3D noise volume.
        ByteBuffer data=org.lwjgl.BufferUtils.createByteBuffer(64*64*64);
        Random random=new Random(0x534257464f47L);
        while(data.hasRemaining())data.put((byte)random.nextInt(256));data.flip();
        glTexImage3D(GL_TEXTURE_3D,0,GL_R8,64,64,64,0,GL_RED,GL_UNSIGNED_BYTE,data);
    }
    private static float canopyLift(MortarFieldEntity field) {
        return field.getKind()==MortarFieldEntity.Kind.GAS ?
            (float)Math.max(0,Math.min(2.0,10.0-field.getFieldHeight())) : 0;
    }
    /** Includes the soft upper plume, above the server-owned effect height. */
    public static float visualHeight(MortarFieldEntity field) {
        return (float)field.getFieldHeight()+canopyLift(field);
    }
    // Restrict each ray-march pass to its projected bounds. Inside/near-plane cases use the full screen.
    private static void fieldScissor(MortarFieldEntity field,Vec3 camera,Matrix4f projection,int w,int h,float down) {
        Matrix4f view=new Matrix4f(new Matrix3f(new org.joml.Matrix3f().rotation(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation())).transpose());
        Matrix4f clip=new Matrix4f(projection).mul(view);
        float minX=1,minY=1,maxX=-1,maxY=-1;
        for(int dx:new int[]{-1,1})for(int dz:new int[]{-1,1})for(int dy:new int[]{0,1}) {
            var v=new org.joml.Vector4f((float)(field.getX()+dx*field.getRadius()-camera.x),
                (float)(field.getY()+(dy==0?-down:visualHeight(field))-camera.y),(float)(field.getZ()+dz*field.getRadius()-camera.z),1);
            clip.transform(v);
            if(v.w<=.01f){glScissor(0,0,w,h);return;}
            float x=v.x/v.w,y=v.y/v.w;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        }
        int x=Math.max(0,(int)Math.floor((minX+1)*.5*w)-2),y=Math.max(0,(int)Math.floor((minY+1)*.5*h)-2);
        int right=Math.min(w,(int)Math.ceil((maxX+1)*.5*w)+2),top=Math.min(h,(int)Math.ceil((maxY+1)*.5*h)+2);
        glScissor(x,y,Math.max(0,right-x),Math.max(0,top-y));
    }
    private static int uniform(String name){return uniforms.computeIfAbsent(name,n->glGetUniformLocation(program,n));}
    public static void render(RenderLevelStageEvent event) {
        if(event.getStage()==RenderLevelStageEvent.Stage.AFTER_SKY&&!ShaderPackCompat.INSTANCE.shadowPass())MineGlowGeometry.beginFrame();
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL||ShaderPackCompat.INSTANCE.shadowPass())return;
        events++;Minecraft mc=Minecraft.getInstance();if(mc.level==null)return;
        Vec3 camera=event.getCamera().getPosition();
        var fields=new ArrayList<MortarFieldEntity>();
        for(var entity:mc.level.entitiesForRendering())if(entity instanceof MortarFieldEntity f&&f.isAlive()&&f.distanceToSqr(camera)<128*128&&event.getFrustum().isVisible(new net.minecraft.world.phys.AABB(f.getX()-f.getRadius(),f.getY()-FieldTerrainMask.maximumDepth(f),f.getZ()-f.getRadius(),f.getX()+f.getRadius(),f.getY()+visualHeight(f),f.getZ()+f.getRadius())))fields.add(f);
        visible=fields.size();
        var energy=new ArrayList<net.minecraft.world.entity.Entity>();
        for(var entity:mc.level.entitiesForRendering()){
            if(!entity.isAlive()||entity.distanceToSqr(camera)>192*192)continue;
            if(entity instanceof com.example.sbwhelldiver.entity.ShieldGeneratorEntity ||
                ShaderPackCompat.INSTANCE.active()&&entity instanceof com.example.sbwhelldiver.entity.SentryHellpodEntity s&&s.getProfile().isTesla())energy.add(entity);
        }
        float[] fuelFlames=FlameJetVisual.INSTANCE.volumeInstances(event.getPartialTick().getGameTimeDeltaPartialTick(false),camera,event.getFrustum());
        float[] impacts=OrbitalLaserImpact.INSTANCE.volumeInstances(event.getPartialTick().getGameTimeDeltaPartialTick(false),camera,event.getFrustum());
        final float[] flames;
        if(impacts.length>0){int previous=fuelFlames.length;flames=java.util.Arrays.copyOf(fuelFlames,previous+impacts.length);System.arraycopy(impacts,0,flames,previous,impacts.length);}else flames=fuelFlames;
        var orbitalBeams=OrbitalBeamGeometry.collect(event.getPartialTick().getGameTimeDeltaPartialTick(false),camera);
        float[] lasers=LaserPostVisual.INSTANCE.collect(event.getPartialTick().getGameTimeDeltaPartialTick(false),camera,event.getFrustum());
        if((fields.isEmpty()&&flames.length==0&&lasers.length==0&&orbitalBeams.isEmpty()&&energy.isEmpty()&&MineGlowGeometry.vertexCount()==0)||(failed&&!reload))return;
        fields.sort(Comparator.comparingDouble((MortarFieldEntity f)->f.distanceToSqr(camera)).reversed());
        try(var stack=MemoryStack.stackPush()) {
            // Raw GL calls are paired with exact restoration, leaving Mojang's
            // cached state unchanged and avoiding loader-specific shader hooks.
            int oldProgram=glGetInteger(GL_CURRENT_PROGRAM),oldVao=glGetInteger(GL_VERTEX_ARRAY_BINDING),oldBuffer=glGetInteger(GL_ARRAY_BUFFER_BINDING);
            int oldDraw=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),oldRead=glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
            int oldActive=glGetInteger(GL_ACTIVE_TEXTURE);
            int oldSampler0=glGetIntegeri(GL_SAMPLER_BINDING,0),oldSampler1=glGetIntegeri(GL_SAMPLER_BINDING,1),oldSampler2=glGetIntegeri(GL_SAMPLER_BINDING,2);
            glActiveTexture(GL_TEXTURE0);int old2D=glGetInteger(GL_TEXTURE_BINDING_2D),old3D=glGetInteger(GL_TEXTURE_BINDING_3D);
            glActiveTexture(GL_TEXTURE1);int old1=glGetInteger(GL_TEXTURE_BINDING_3D),old12D=glGetInteger(GL_TEXTURE_BINDING_2D);
            glActiveTexture(GL_TEXTURE2);int old22D=glGetInteger(GL_TEXTURE_BINDING_2D),old23D=glGetInteger(GL_TEXTURE_BINDING_3D);
            boolean test=glIsEnabled(GL_DEPTH_TEST),blend=glIsEnabled(GL_BLEND),cull=glIsEnabled(GL_CULL_FACE),scissor=glIsEnabled(GL_SCISSOR_TEST);
            boolean writeDepth=glGetBoolean(GL_DEPTH_WRITEMASK);
            int src=glGetInteger(GL_BLEND_SRC_RGB),dst=glGetInteger(GL_BLEND_DST_RGB),srcA=glGetInteger(GL_BLEND_SRC_ALPHA),dstA=glGetInteger(GL_BLEND_DST_ALPHA);
            int eq=glGetInteger(GL_BLEND_EQUATION_RGB),eqA=glGetInteger(GL_BLEND_EQUATION_ALPHA);
            IntBuffer viewport=stack.mallocInt(4);glGetIntegerv(GL_VIEWPORT,viewport);
            IntBuffer scissorBox=stack.mallocInt(4);glGetIntegerv(GL_SCISSOR_BOX,scissorBox);
            ByteBuffer mask=stack.malloc(4);glGetBooleanv(GL_COLOR_WRITEMASK,mask);
            try {
                if(Boolean.getBoolean("sbw_helldiver.energyShaderSmokeTest")||Boolean.getBoolean("sbw_helldiver.shaderFieldSmokeTest")||Boolean.getBoolean("sbw_helldiver.flameVolumeSmokeTest")||Boolean.getBoolean("sbw_helldiver.laserShaderSmokeTest"))glGetError();
                if(reload)release();
                glBindSampler(0,0);glBindSampler(1,0);glBindSampler(2,0);
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
                FieldArcGeometry.draw(fields,event.getPartialTick().getGameTimeDeltaPartialTick(false),camera,event.getProjectionMatrix(),new Matrix3f(new org.joml.Matrix3f().rotation(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation())),w,h);
                int fogW=Math.max(1,(w+1)/2),fogH=Math.max(1,(h+1)/2);
                if(!fields.isEmpty())FieldFogLayer.begin(fogW,fogH);
                glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,depth);
                glUseProgram(program);glBindVertexArray(vao);
                glUniform1i(uniform("SceneDepth"),0);glUniform1i(uniform("DensityNoise"),1);glUniform1i(uniform("TerrainFloor"),2);
                glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_3D,noise);
                FloatBuffer matrix=stack.mallocFloat(16);
                new Matrix4f(event.getProjectionMatrix()).invert().get(matrix);glUniformMatrix4fv(uniform("InverseProjection"),false,matrix);
                FloatBuffer rotation=stack.mallocFloat(9);
                new Matrix3f(new org.joml.Matrix3f().rotation(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation())).get(rotation);glUniformMatrix3fv(uniform("InverseViewRotation"),false,rotation);
                glUniform2f(uniform("Viewport"),fogW,fogH);
                glEnable(GL_SCISSOR_TEST);
                for(var field:fields) {
                    var floor=FieldTerrainMask.get(field);
                    fieldScissor(field,camera,event.getProjectionMatrix(),fogW,fogH,floor.depth());
                    glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,terrain);
                    glTexSubImage2D(GL_TEXTURE_2D,0,0,0,floor.width(),floor.height(),GL_RED,GL_FLOAT,floor.floors());
                    glUniform2f(uniform("TerrainOrigin"),(float)(floor.x()-field.getX()),(float)(floor.z()-field.getZ()));
                    glUniform2f(uniform("TerrainSize"),33,33);
                    glUniform1f(uniform("DownwardDepth"),floor.depth());
                    Vec3 eye=camera.subtract(field.position());boolean gas=field.getKind()==MortarFieldEntity.Kind.GAS,smoke=field.getKind()==MortarFieldEntity.Kind.SMOKE;
                    float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
                    float fade=(float)Math.max(0,Math.min(1,Math.min((field.tickCount+partial)/(gas?7:6),field.getRemainingTicks()/(gas?20.0:12.0))));
                    glUniform3f(uniform("Eye"),(float)eye.x,(float)eye.y,(float)eye.z);
                    glUniform1f(uniform("Radius"),(float)field.getRadius());glUniform1f(uniform("Time"),(field.tickCount+partial)/20f);
                    glUniform1f(uniform("Seed"),(float)((field.getX()*.731+field.getZ()*.173)%91));
                    glUniform1f(uniform("Height"),visualHeight(field));
                    glUniform1f(uniform("CanopyLift"),canopyLift(field));
                    // Every field uses the same billowing density profile, including the light EMS fog.
                    glUniform1i(uniform("Smoke"),1);
                    glUniform3f(uniform("SmokeNoiseOrigin"),(float)((field.getX()*.22)%64),
                        (float)((field.getY()*.30-(mc.level.getGameTime()+(double)partial)*.055/20)%64),(float)((field.getZ()*.22)%64));
                    glUniform1f(uniform("Extinction"),(smoke?.95f:gas?.20f:.028f)*fade);
                    glUniform3f(uniform("FogColor"),smoke?.82f:gas?.48f:.38f,smoke?.84f:gas?.55f:.60f,smoke?.86f:gas?.15f:.84f);
                    glDrawArrays(GL_TRIANGLES,0,3);
                }
                glDisable(GL_SCISSOR_TEST);
                if(!fields.isEmpty())FieldFogLayer.composite(target.frameBufferId,depth,w,h,event.getProjectionMatrix());
                if(flames.length>0){
                    FlameSurfaceLighting.draw(flames,camera,event.getProjectionMatrix(),new Matrix3f(new org.joml.Matrix3f().rotation(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation())),w,h);
                    FlameGlow.render(w,h,()->FlameVolumeGeometry.draw(flames,event.getProjectionMatrix(),new Matrix3f(new org.joml.Matrix3f().rotation(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation())),camera,w,h,(mc.level.getGameTime()+event.getPartialTick().getGameTimeDeltaPartialTick(false))/20f));
                }
                if(!orbitalBeams.isEmpty())FlameGlow.renderAdditive(w,h,.18f,()->OrbitalBeamGeometry.draw(orbitalBeams,event.getProjectionMatrix(),new Matrix3f(new org.joml.Matrix3f().rotation(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation())),w,h));
                glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,depth);
                if(lasers.length>0)FlameGlow.render(w,h,.18f,()->LaserEffectGeometry.draw(lasers,event.getProjectionMatrix(),new Matrix3f(new org.joml.Matrix3f().rotation(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation())),w,h));
                glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,depth);
                EnergyEffectGeometry.draw(energy,event.getPartialTick().getGameTimeDeltaPartialTick(false),camera,event.getProjectionMatrix(),new Matrix3f(new org.joml.Matrix3f().rotation(net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().rotation())),w,h);
                glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,depth);
                // 1.21 entity poses contain camera-relative positions, without camera rotation.
                // Use the same view matrix as the base mesh for the depth-tested lamp pass.
                if(MineGlowGeometry.vertexCount()>0)FlameGlow.render(w,h,.28f,()->MineGlowGeometry.draw(new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()),w,h));
                if(Boolean.getBoolean("sbw_helldiver.energyShaderSmokeTest")||Boolean.getBoolean("sbw_helldiver.shaderFieldSmokeTest")||Boolean.getBoolean("sbw_helldiver.flameVolumeSmokeTest")||Boolean.getBoolean("sbw_helldiver.laserShaderSmokeTest")) {
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
                glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_3D,old1);glBindTexture(GL_TEXTURE_2D,old12D);
                glActiveTexture(GL_TEXTURE2);glBindTexture(GL_TEXTURE_2D,old22D);glBindTexture(GL_TEXTURE_3D,old23D);glActiveTexture(oldActive);
                glBindSampler(0,oldSampler0);glBindSampler(1,oldSampler1);glBindSampler(2,oldSampler2);
                glScissor(scissorBox.get(0),scissorBox.get(1),scissorBox.get(2),scissorBox.get(3));
                set(GL_DEPTH_TEST,test);set(GL_BLEND,blend);set(GL_CULL_FACE,cull);set(GL_SCISSOR_TEST,scissor);glDepthMask(writeDepth);
                glBlendFuncSeparate(src,dst,srcA,dstA);glBlendEquationSeparate(eq,eqA);
                glColorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);
            }
        }
    }
    private static void set(int state,boolean enabled){if(enabled)glEnable(state);else glDisable(state);}
}
