package com.example.sbwhelldiver.client;

import com.example.sbwhelldiver.stratagem.DestroyerWarp;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import java.nio.ByteBuffer;
import java.util.List;
import static org.lwjgl.opengl.GL33C.*;

/** Shader packs may replace a cleared sky G-buffer with their own sky later.
 * Draw the distant ship after that composition, with private hull depth and
 * read-only scene depth. The world's color is attached without its depth, so
 * terrain remains an occluder and neither depth feedback nor a world clear is needed.
 */
public final class DestroyerPostRenderer {
    private static volatile float[] mesh=new float[0];
    private static float[] uploaded;
    private static int program,vao,vbo,trailVao,trailVbo,fbo,depth,width,height,vertices;
    private static int viewUniform,projectionUniform,inverseUniform,scaleUniform,tintUniform,viewportUniform;
    private static boolean failed;
    private static long frames;
    private static final ResourceLocation HULL=new ResourceLocation("sbw_helldiver","textures/mesh/super_destroyer.png");
    private static final ResourceLocation WHITE=new ResourceLocation("sbw_helldiver","textures/mesh/white.png");
    private DestroyerPostRenderer(){}
    // Resource reloads can run without a current GL context; upload only on a render below.
    public static void setMesh(float[] data){mesh=data;failed=false;}
    public static long renderedFrames(){return frames;}
    private static void initialize() throws Exception {
        if(program!=0)return;
        int vs=FieldVolumeRenderer.shader(GL_VERTEX_SHADER,FieldVolumeRenderer.source("destroyer.vsh")),fs=0,candidate=0;
        try {
            fs=FieldVolumeRenderer.shader(GL_FRAGMENT_SHADER,FieldVolumeRenderer.source("destroyer.fsh"));
            candidate=glCreateProgram();glAttachShader(candidate,vs);glAttachShader(candidate,fs);
            glBindAttribLocation(candidate,0,"Position");glBindAttribLocation(candidate,1,"UV");glBindAttribLocation(candidate,2,"Shade");glLinkProgram(candidate);
            if(glGetProgrami(candidate,GL_LINK_STATUS)==GL_FALSE)throw new IllegalStateException(glGetProgramInfoLog(candidate));
            program=candidate;candidate=0;
        } finally {glDeleteShader(vs);if(fs!=0)glDeleteShader(fs);if(candidate!=0)glDeleteProgram(candidate);}
        viewUniform=glGetUniformLocation(program,"ViewTransform");projectionUniform=glGetUniformLocation(program,"Projection");
        inverseUniform=glGetUniformLocation(program,"InverseProjection");scaleUniform=glGetUniformLocation(program,"DistanceScale");
        tintUniform=glGetUniformLocation(program,"Tint");viewportUniform=glGetUniformLocation(program,"Viewport");
        vao=glGenVertexArrays();vbo=glGenBuffers();layout(vao,vbo);
        trailVao=glGenVertexArrays();trailVbo=glGenBuffers();layout(trailVao,trailVbo);
        fbo=glGenFramebuffers();depth=glGenRenderbuffers();
    }
    private static void layout(int array,int buffer){
        glBindVertexArray(array);glBindBuffer(GL_ARRAY_BUFFER,buffer);
        glVertexAttribPointer(0,3,GL_FLOAT,false,24,0L);glVertexAttribPointer(1,2,GL_FLOAT,false,24,12L);glVertexAttribPointer(2,1,GL_FLOAT,false,24,20L);
        glEnableVertexAttribArray(0);glEnableVertexAttribArray(1);glEnableVertexAttribArray(2);
    }
    public static void render(Matrix4f projection,Matrix4f view,List<DestroyerWarp.Pose> flights,Vec3 camera){
        if(flights.isEmpty()||mesh.length==0||failed||GLFW.glfwGetCurrentContext()==0L)return;
        final org.lwjgl.opengl.GLCapabilities caps;
        try {caps=GL.getCapabilities();}catch(IllegalStateException absent){return;}
        if(!caps.OpenGL32)return;
        var mc=Minecraft.getInstance();var target=mc.getMainRenderTarget();
        int hullTexture=mc.getTextureManager().getTexture(HULL).getId(),whiteTexture=mc.getTextureManager().getTexture(WHITE).getId();
        boolean samplers=caps.OpenGL33||caps.GL_ARB_sampler_objects;
        try(var stack=MemoryStack.stackPush()){
            int oldProgram=glGetInteger(GL_CURRENT_PROGRAM),oldVao=glGetInteger(GL_VERTEX_ARRAY_BINDING),oldBuffer=glGetInteger(GL_ARRAY_BUFFER_BINDING);
            int oldDraw=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING),oldRead=glGetInteger(GL_READ_FRAMEBUFFER_BINDING),oldRenderbuffer=glGetInteger(GL_RENDERBUFFER_BINDING);
            int oldActive=glGetInteger(GL_ACTIVE_TEXTURE),sampler0=samplers?glGetIntegeri(GL_SAMPLER_BINDING,0):0,sampler1=samplers?glGetIntegeri(GL_SAMPLER_BINDING,1):0;
            glActiveTexture(GL_TEXTURE0);int texture0=glGetInteger(GL_TEXTURE_BINDING_2D);
            glActiveTexture(GL_TEXTURE1);int texture1=glGetInteger(GL_TEXTURE_BINDING_2D);
            boolean test=glIsEnabled(GL_DEPTH_TEST),blend=glIsEnabled(GL_BLEND),cull=glIsEnabled(GL_CULL_FACE),scissor=glIsEnabled(GL_SCISSOR_TEST),writeDepth=glGetBoolean(GL_DEPTH_WRITEMASK);
            int depthFunction=glGetInteger(GL_DEPTH_FUNC),src=glGetInteger(GL_BLEND_SRC_RGB),dst=glGetInteger(GL_BLEND_DST_RGB),srcA=glGetInteger(GL_BLEND_SRC_ALPHA),dstA=glGetInteger(GL_BLEND_DST_ALPHA);
            int eq=glGetInteger(GL_BLEND_EQUATION_RGB),eqA=glGetInteger(GL_BLEND_EQUATION_ALPHA);
            var viewport=stack.mallocInt(4);glGetIntegerv(GL_VIEWPORT,viewport);var mask=stack.malloc(4);glGetBooleanv(GL_COLOR_WRITEMASK,mask);
            try {
                initialize();
                if(samplers){glBindSampler(0,0);glBindSampler(1,0);}
                if(uploaded!=mesh){glBindBuffer(GL_ARRAY_BUFFER,vbo);glBufferData(GL_ARRAY_BUFFER,mesh,GL_STATIC_DRAW);uploaded=mesh;vertices=mesh.length/6;}
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER,fbo);
                glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER,GL_COLOR_ATTACHMENT0,GL_TEXTURE_2D,target.getColorTextureId(),0);
                glBindRenderbuffer(GL_RENDERBUFFER,depth);
                if(width!=target.width||height!=target.height){width=target.width;height=target.height;glRenderbufferStorage(GL_RENDERBUFFER,GL_DEPTH_COMPONENT24,width,height);}
                glFramebufferRenderbuffer(GL_DRAW_FRAMEBUFFER,GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,depth);glDrawBuffer(GL_COLOR_ATTACHMENT0);
                if(glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER)!=GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Incomplete destroyer target");
                glViewport(0,0,width,height);glDisable(GL_SCISSOR_TEST);glDisable(GL_CULL_FACE);glEnable(GL_DEPTH_TEST);glDepthFunc(GL_LEQUAL);glDepthMask(true);
                glColorMask(true,true,true,true);glDisable(GL_BLEND);glClearBufferfv(GL_DEPTH,0,new float[]{1f});
                glUseProgram(program);glUniform1i(glGetUniformLocation(program,"Surface"),0);glUniform1i(glGetUniformLocation(program,"SceneDepth"),1);
                glUniform2f(viewportUniform,width,height);var matrix=stack.mallocFloat(16);
                projection.get(matrix);glUniformMatrix4fv(projectionUniform,false,matrix);
                new Matrix4f(projection).invert().get(matrix);glUniformMatrix4fv(inverseUniform,false,matrix);
                glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,target.getDepthTextureId());glActiveTexture(GL_TEXTURE0);
                for(var flight:flights){
                    Vec3 delta=flight.getPosition().subtract(camera);double distance=delta.length();if(distance<1||distance>16384)continue;
                    float factor=(float)Math.min(1,96/distance);
                    new Matrix4f(view).translate((float)(delta.x*factor),(float)(delta.y*factor),(float)(delta.z*factor)).scale(factor).rotateY((float)Math.toRadians(flight.getYaw())).get(matrix);
                    glUniformMatrix4fv(viewUniform,false,matrix);glUniform1f(scaleUniform,factor);glUniform4f(tintUniform,1,1,1,1);
                    glDisable(GL_BLEND);glDepthMask(true);glBindTexture(GL_TEXTURE_2D,hullTexture);glBindVertexArray(vao);glDrawArrays(GL_TRIANGLES,0,vertices);
                    if(flight.getTrailLength()>0){
                        view.get(matrix);glUniformMatrix4fv(viewUniform,false,matrix);glBindTexture(GL_TEXTURE_2D,whiteTexture);glBindVertexArray(trailVao);glBindBuffer(GL_ARRAY_BUFFER,trailVbo);
                        glEnable(GL_BLEND);glBlendEquationSeparate(GL_FUNC_ADD,GL_FUNC_ADD);glBlendFuncSeparate(GL_SRC_ALPHA,GL_ONE,GL_ZERO,GL_ONE);glDepthMask(false);
                        ribbon(flight,camera,factor,4.5f,.25f,.55f,1f,.4f);ribbon(flight,camera,factor,1.4f,.8f,.93f,1f,.95f);
                    }
                }
                frames++;
            }catch(Exception failure){failed=true;LogUtils.getLogger().error("Unable to render shader-pack destroyer",failure);}
            finally{
                glUseProgram(oldProgram);glBindVertexArray(oldVao);glBindBuffer(GL_ARRAY_BUFFER,oldBuffer);glBindRenderbuffer(GL_RENDERBUFFER,oldRenderbuffer);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER,oldDraw);glBindFramebuffer(GL_READ_FRAMEBUFFER,oldRead);
                glViewport(viewport.get(0),viewport.get(1),viewport.get(2),viewport.get(3));
                glActiveTexture(GL_TEXTURE0);glBindTexture(GL_TEXTURE_2D,texture0);glActiveTexture(GL_TEXTURE1);glBindTexture(GL_TEXTURE_2D,texture1);glActiveTexture(oldActive);
                if(samplers){glBindSampler(0,sampler0);glBindSampler(1,sampler1);}
                set(GL_DEPTH_TEST,test);set(GL_BLEND,blend);set(GL_CULL_FACE,cull);set(GL_SCISSOR_TEST,scissor);glDepthMask(writeDepth);glDepthFunc(depthFunction);
                glBlendFuncSeparate(src,dst,srcA,dstA);glBlendEquationSeparate(eq,eqA);glColorMask(mask.get(0)!=0,mask.get(1)!=0,mask.get(2)!=0,mask.get(3)!=0);
            }
        }
    }
    private static void ribbon(DestroyerWarp.Pose flight,Vec3 camera,float factor,float width,float r,float g,float b,float alpha){
        var forward=flight.getMotion();var head=flight.getPosition().subtract(forward.scale(110));var tail=head.subtract(forward.scale(flight.getTrailLength()));
        var right=forward.cross(head.subtract(camera)).normalize();if(right.lengthSqr()<1e-6)right=new Vec3(1,0,0);
        var edge=right.scale(width);var points=new Vec3[]{head.add(edge),tail.add(edge.scale(.12)),tail.subtract(edge.scale(.12)),head.subtract(edge)};
        float[] data=new float[36];int i=0;
        for(int k:new int[]{0,1,2,0,2,3}){var p=points[k].subtract(camera).scale(factor);data[i++]=(float)p.x;data[i++]=(float)p.y;data[i++]=(float)p.z;data[i++]=0;data[i++]=0;data[i++]=1;}
        glBufferData(GL_ARRAY_BUFFER,data,GL_STREAM_DRAW);glUniform4f(tintUniform,r,g,b,alpha);glDrawArrays(GL_TRIANGLES,0,6);
    }
    private static void set(int state,boolean enabled){if(enabled)glEnable(state);else glDisable(state);}
}
