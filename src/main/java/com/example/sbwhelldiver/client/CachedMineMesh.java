package com.example.sbwhelldiver.client;

import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BakedModelInstance;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import java.util.Arrays;

/** Immutable model-space quads. Bake once, rather than walking the Bedrock
 * hierarchy and converting every triangle for every mine, every frame. */
public final class CachedMineMesh {
    private final float[] vertices, emission;
    private CachedMineMesh(float[] vertices,float[] emission){this.vertices=vertices;this.emission=emission;}
    public int vertexCount(){return vertices.length/8;}
    public int emissiveVertexCount(){return emission.length/6;}
    public static CachedMineMesh bake(BakedModelInstance model,NativeImage texture,int kind){
        Recorder recorder=new Recorder(texture,kind);
        model.renderToBuffer(new PoseStack(),recorder,RenderType.entityCutoutNoCull(net.minecraft.resources.ResourceLocation.tryParse("minecraft:missingno")),
            com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes.polyMeshCutout(net.minecraft.resources.ResourceLocation.tryParse("minecraft:missingno")),0,OverlayTexture.NO_OVERLAY);
        recorder.flush();return new CachedMineMesh(recorder.data.finish(),recorder.glow.finish());
    }
    public void render(PoseStack pose,VertexConsumer out,int light,boolean glow){
        var p=pose.last().pose();var n=pose.last().normal();
        var bulk=PackedEntityVertices.create(out);
        for(int i=0;i<vertices.length;i+=8){
            float x=vertices[i],y=vertices[i+1],z=vertices[i+2],nx=vertices[i+5],ny=vertices[i+6],nz=vertices[i+7];
            float px=p.m00()*x+p.m10()*y+p.m20()*z+p.m30(),py=p.m01()*x+p.m11()*y+p.m21()*z+p.m31(),pz=p.m02()*x+p.m12()*y+p.m22()*z+p.m32();
            float xx=n.m00()*nx+n.m10()*ny+n.m20()*nz,yy=n.m01()*nx+n.m11()*ny+n.m21()*nz,zz=n.m02()*nx+n.m12()*ny+n.m22()*nz;
            if(bulk!=null)bulk.vertex(px,py,pz,-1,vertices[i+3],vertices[i+4],OverlayTexture.NO_OVERLAY,light,xx,yy,zz);
            else out.addVertex(px,py,pz,-1,vertices[i+3],vertices[i+4],OverlayTexture.NO_OVERLAY,light,xx,yy,zz);
        }
        if(bulk!=null)bulk.flush();
        if(glow&&!ShaderPackCompat.INSTANCE.shadowPass())MineGlowGeometry.capture(emission,p);
    }
    private static final class Floats{
        float[] data=new float[4096];int size;
        void add(float... values){if(size+values.length>data.length)data=Arrays.copyOf(data,Math.max(data.length*2,size+values.length));System.arraycopy(values,0,data,size,values.length);size+=values.length;}
        float[] finish(){return Arrays.copyOf(data,size);}
    }
    private static final class Recorder implements MultiBufferSource,VertexConsumer{
        final Floats data=new Floats(),glow=new Floats();final NativeImage texture;final int kind;
        final float[][] pending=new float[4][8];int size,stride;
        Recorder(NativeImage texture,int kind){this.texture=texture;this.kind=kind;}
        public VertexConsumer getBuffer(RenderType type){flush();stride=type.mode()==VertexFormat.Mode.TRIANGLES?3:4;return this;}
        void flush(){if(size!=0)throw new IllegalStateException("Incomplete cached mine primitive");}
        public void addVertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz){
            float[] p=pending[size++];p[0]=x;p[1]=y;p[2]=z;p[3]=u;p[4]=v;p[5]=nx;p[6]=ny;p[7]=nz;
            if(size<stride)return;
            float u0=0,v0=0;for(int k=0;k<stride;k++){u0+=pending[k][3];v0+=pending[k][4];}
            int pixel=texture.getPixelRGBA(Math.max(0,Math.min(texture.getWidth()-1,(int)(u0/stride*texture.getWidth()))),Math.max(0,Math.min(texture.getHeight()-1,(int)(v0/stride*texture.getHeight()))));
            int red=pixel&255,green=pixel>>8&255,blue=pixel>>16&255;
            boolean lamp=kind==0&&red>200&&green<70 || kind==1&&(red>220&&green<80 || red>235&&green>170&&blue>130) || kind==2&&green>200&&red<80&&blue<80 || kind==3&&red>220&&green>70&&green<150&&blue<80;
            for(int k=0;k<4;k++)data.add(pending[Math.min(k,stride-1)]);
            if(lamp){float cr=kind==2?.08f:1f,cg=kind==2?1f:.19f,cb=kind==2?.015f:.025f;
                int[] indices=stride==3?new int[]{0,1,2}:new int[]{0,1,2,0,2,3};
                for(int k:indices){var p0=pending[k];glow.add(p0[0],p0[1],p0[2],cr,cg,cb);}
            }
            size=0;
        }
        // Bedrock emits complete vertices; attribute calls indicate an unsupported path.
        public VertexConsumer addVertex(float x,float y,float z){throw new UnsupportedOperationException();}
        public VertexConsumer setColor(int r,int g,int b,int a){return this;}
        public VertexConsumer setUv(float u,float v){return this;}
        public VertexConsumer setUv1(int u,int v){return this;}
        public VertexConsumer setUv2(int u,int v){return this;}
        public VertexConsumer setNormal(float x,float y,float z){return this;}
        public void endVertex(){throw new UnsupportedOperationException();}
        public void defaultColor(int r,int g,int b,int a){}
        public void unsetDefaultColor(){}
    }
}
