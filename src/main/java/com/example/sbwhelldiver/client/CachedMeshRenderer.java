package com.example.sbwhelldiver.client;

import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedGeometryChunk;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedVertexData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Vector3f;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Arrays;
import java.util.HashMap;
import java.util.WeakHashMap;

/** Shared immutable mesh topology; bone poses, visibility and animation stay with SBW.
 * Each distinct position/normal is transformed once per bone instead of once per
 * triangle corner. Caches have weak model keys, so resource reloads can reclaim them.
 * Access is confined to the render thread, including Oculus shadow passes. */
public final class CachedMeshRenderer {
    private CachedMeshRenderer() {}
    public interface Consumer {
        void renderMesh(BakedGeometryChunk chunk, PoseStack pose, int light, int overlay,
                        float red, float green, float blue, float alpha);
    }
    public interface QuadConsumer {
        void renderQuads(BakedGeometryChunk chunk, PoseStack pose, int light, int overlay,
                         float red, float green, float blue, float alpha, boolean skipCull);
    }
    private static final WeakHashMap<BakedGeometryChunk, QuadMesh> QUAD_CACHE = new WeakHashMap<>();
    private static final WeakHashMap<BakedGeometryChunk, Mesh> CACHE = new WeakHashMap<>();
    public static void clear() { CACHE.clear(); QUAD_CACHE.clear(); }

    public static void render(BakedGeometryChunk chunk, PoseStack pose, VertexConsumer out,
                              PackedEntityVertices bulk, boolean quads, int light, int overlay,
                              float red, float green, float blue, float alpha) {
        renderOccluded(chunk, pose, out, bulk, quads, null, light, overlay, red, green, blue, alpha);
    }
    public static void renderOccluded(BakedGeometryChunk chunk, PoseStack pose, VertexConsumer out,
                                     PackedEntityVertices bulk, boolean quads, double[] occlusion,
                                     int light, int overlay, float red, float green, float blue, float alpha) {
        var matrix = pose.last();
        if(collapsed(matrix.pose())) return;
        Mesh mesh = CACHE.computeIfAbsent(chunk, Mesh::new);
        Selection selection = mesh.selection;
        boolean filtered = occlusion != null && selection.update(matrix.pose(), occlusion);
        if (filtered && selection.visibleCount == 0) return;
        Vector3f v = mesh.scratch;
        for (int i=0; i<mesh.positions.length; i+=3) {
            if (filtered && !selection.usedPositions[i/3]) continue;
            v.set(mesh.positions[i],mesh.positions[i+1],mesh.positions[i+2]).mulPosition(matrix.pose());
            mesh.transformed[i]=v.x; mesh.transformed[i+1]=v.y; mesh.transformed[i+2]=v.z;
        }
        for (int i=0; i<mesh.normals.length; i+=3) {
            v.set(mesh.normals[i],mesh.normals[i+1],mesh.normals[i+2]).mul(matrix.normal());
            if (v.lengthSquared()>1.0E-12f) v.normalize();
            mesh.transformedNormals[i]=v.x; mesh.transformedNormals[i+1]=v.y; mesh.transformedNormals[i+2]=v.z;
            mesh.packedNormals[i/3]=PackedEntityVertices.packNormal(v.x,v.y,v.z);
        }
        int color=((int)(red*255)&255)|(((int)(green*255)&255)<<8)|(((int)(blue*255)&255)<<16)|(((int)(alpha*255)&255)<<24);
        for (int i=0; i<mesh.positionIndices.length; i+=3) {
            if (filtered && !selection.visible[i/3]) continue;
            // A collapsed animation bone must not submit degenerate primitives to Iris.
            int a=mesh.positionIndices[i], b=mesh.positionIndices[i+1], c=mesh.positionIndices[i+2];
            float[] t=mesh.transformed;
            float ux=t[b]-t[a],uy=t[b+1]-t[a+1],uz=t[b+2]-t[a+2];
            float vx=t[c]-t[a],vy=t[c+1]-t[a+1],vz=t[c+2]-t[a+2];
            float cx=uy*vz-uz*vy,cy=uz*vx-ux*vz,cz=ux*vy-uy*vx;
            if (quads && cx*cx+cy*cy+cz*cz<=1.0E-16f) continue;
            emit(mesh,i,out,bulk,color,red,green,blue,alpha,overlay,light);
            emit(mesh,i+1,out,bulk,color,red,green,blue,alpha,overlay,light);
            emit(mesh,i+2,out,bulk,color,red,green,blue,alpha,overlay,light);
            if (quads) emit(mesh,i+2,out,bulk,color,red,green,blue,alpha,overlay,light);
        }
    }
    private static void emit(Mesh m,int index,VertexConsumer out,PackedEntityVertices bulk,int color,
                             float r,float g,float b,float a,int overlay,int light) {
        int p=m.positionIndices[index],n=m.normalIndices[index],u=index*2;
        float[] t=m.transformed;
        if(bulk!=null) bulk.vertexPackedNormal(t[p],t[p+1],t[p+2],color,m.uvs[u],m.uvs[u+1],overlay,light,m.packedNormals[n/3]);
        else out.addVertex(t[p],t[p+1],t[p+2],net.minecraft.util.FastColor.ARGB32.color((int)(a*255),(int)(r*255),(int)(g*255),(int)(b*255)),m.uvs[u],m.uvs[u+1],overlay,light,
                        m.transformedNormals[n],m.transformedNormals[n+1],m.transformedNormals[n+2]);
    }
    // Animations hide entire groups with zero scale. They cannot contribute pixels,
    // but the native renderer still transforms and submits all their cube faces.
    private static boolean collapsed(org.joml.Matrix4f m) {
        return m.m00()==0 && m.m01()==0 && m.m02()==0 && m.m10()==0 && m.m11()==0 && m.m12()==0 && m.m20()==0 && m.m21()==0 && m.m22()==0;
    }
    public static void renderQuads(BakedGeometryChunk chunk,PoseStack pose,VertexConsumer out,PackedEntityVertices bulk,
                                   int light,int overlay,float r,float g,float b,float a,boolean skipCull) {
        renderQuadsOccluded(chunk, pose, out, bulk, null, light, overlay, r, g, b, a, skipCull);
    }
    public static void renderQuadsOccluded(BakedGeometryChunk chunk, PoseStack pose, VertexConsumer out,
                                          PackedEntityVertices bulk, double[] occlusion, int light,
                                          int overlay, float r, float g, float b, float a, boolean skipCull) {
        var state=pose.last();var matrix=state.pose();var normal=state.normal();
        if(collapsed(matrix))return;
        QuadMesh mesh=QUAD_CACHE.computeIfAbsent(chunk,QuadMesh::new);
        Selection selection=mesh.selection;
        boolean filtered=occlusion!=null && selection.update(matrix,occlusion);
        if(filtered && selection.visibleCount==0)return;
        Vector3f v=mesh.scratch;
        for(int i=0;i<mesh.positions.length;i+=3) {
            if(filtered && !selection.usedPositions[i/3])continue;
            v.set(mesh.positions[i],mesh.positions[i+1],mesh.positions[i+2]).mulPosition(matrix);
            mesh.transformed[i]=v.x;mesh.transformed[i+1]=v.y;mesh.transformed[i+2]=v.z;
        }
        for(int i=0;i<mesh.normals.length;i+=3) {
            float nx=mesh.normals[i],ny=mesh.normals[i+1],nz=mesh.normals[i+2];
            // Native cube normals intentionally do not get normalized a second time.
            float x=normal.m00()*nx+normal.m10()*ny+normal.m20()*nz;
            float y=normal.m01()*nx+normal.m11()*ny+normal.m21()*nz;
            float z=normal.m02()*nx+normal.m12()*ny+normal.m22()*nz;
            mesh.transformedNormals[i]=x;mesh.transformedNormals[i+1]=y;mesh.transformedNormals[i+2]=z;
            mesh.packedNormals[i/3]=PackedEntityVertices.packNormal(x,y,z);
        }
        boolean cull=!skipCull && RenderSystem.getModelViewMatrix().m32()==0;
        int color=((int)(r*255)&255)|(((int)(g*255)&255)<<8)|(((int)(b*255)&255)<<16)|(((int)(a*255)&255)<<24);
        float[] t=mesh.transformed;
        for(int i=0;i<mesh.normalIndices.length;i++) {
            if(filtered && !selection.visible[i])continue;
            int corner=i*4,n=mesh.normalIndices[i],p=mesh.positionIndices[corner],q=mesh.positionIndices[corner+2];
            if(cull) {
                float cx=mesh.positions[p]+mesh.positions[q],cy=mesh.positions[p+1]+mesh.positions[q+1],cz=mesh.positions[p+2]+mesh.positions[q+2];
                float tx=matrix.m00()*cx+matrix.m10()*cy+matrix.m20()*cz+matrix.m30()+matrix.m30();
                float ty=matrix.m01()*cx+matrix.m11()*cy+matrix.m21()*cz+matrix.m31()+matrix.m31();
                float tz=matrix.m02()*cx+matrix.m12()*cy+matrix.m22()*cz+matrix.m32()+matrix.m32();
                if(tx*mesh.transformedNormals[n]+ty*mesh.transformedNormals[n+1]+tz*mesh.transformedNormals[n+2]>0)continue;
            }
            if(degenerate(t,p,mesh.positionIndices[corner+1],q) && degenerate(t,p,q,mesh.positionIndices[corner+3]))continue;
            for(int j=corner;j<corner+4;j++) {
                int at=mesh.positionIndices[j],u=j*2;
                if(bulk!=null)bulk.vertexPackedNormal(t[at],t[at+1],t[at+2],color,mesh.uvs[u],mesh.uvs[u+1],overlay,light,mesh.packedNormals[n/3]);
                else out.addVertex(t[at],t[at+1],t[at+2],net.minecraft.util.FastColor.ARGB32.color((int)(a*255),(int)(r*255),(int)(g*255),(int)(b*255)),mesh.uvs[u],mesh.uvs[u+1],overlay,light,mesh.transformedNormals[n],mesh.transformedNormals[n+1],mesh.transformedNormals[n+2]);
            }
        }
    }
    private static boolean degenerate(float[] t,int a,int b,int c) {
        float ux=t[b]-t[a],uy=t[b+1]-t[a+1],uz=t[b+2]-t[a+2],vx=t[c]-t[a],vy=t[c+1]-t[a+1],vz=t[c+2]-t[a+2];
        float x=uy*vz-uz*vy,y=uz*vx-ux*vz,z=ux*vy-uy*vx;
        return x*x+y*y+z*z<=1.0E-16f;
    }
    private static final class QuadMesh {
        final int[] positionIndices,normalIndices,packedNormals;
        final float[] positions,normals,uvs,transformed,transformedNormals;
        final Vector3f scratch=new Vector3f();
        final Selection selection;
        QuadMesh(BakedGeometryChunk chunk) {
            var data=chunk.quads();positionIndices=new int[data.quadCount()*4];normalIndices=new int[data.quadCount()];
            positions=unique(data.positions(),positionIndices);normals=unique(data.normals(),normalIndices);uvs=data.uvs();
            selection=new Selection(positions,positionIndices,4);
            transformed=new float[positions.length];transformedNormals=new float[normals.length];packedNormals=new int[normals.length/3];
        }
    }
    /** Conservative batches keep source primitive order and never clip a visible face.
     * The block volume is recalculated from the live world on each render pass: digging exposes
     * the complete retained hull again. Local geometry and the resource are untouched. */
    private static final class Selection {
        private static final int RUN_SIZE=32;
        final int[] indices;
        final int corners;
        final float[][] bounds;
        final boolean[] visible,usedPositions;
        int visibleCount;
        Selection(float[] positions,int[] indices,int corners) {
            this.indices=indices;this.corners=corners;
            visible=new boolean[indices.length/corners];
            usedPositions=new boolean[positions.length/3];
            bounds=new float[(visible.length+RUN_SIZE-1)/RUN_SIZE][6];
            for(int run=0;run<bounds.length;run++) {
                float[] b=bounds[run];Arrays.fill(b,0,3,Float.POSITIVE_INFINITY);
                Arrays.fill(b,3,6,Float.NEGATIVE_INFINITY);
                int end=Math.min(indices.length,(run+1)*RUN_SIZE*corners);
                for(int i=run*RUN_SIZE*corners;i<end;i++) {
                    int at=indices[i];
                    for(int a=0;a<3;a++){b[a]=Math.min(b[a],positions[at+a]);b[a+3]=Math.max(b[a+3],positions[at+a]);}
                }
            }
        }
        boolean update(org.joml.Matrix4f matrix,double[] box) {
            visibleCount=0;
            for(int run=0;run<bounds.length;run++) {
                boolean shown=!inside(bounds[run],matrix,box);
                int start=run*RUN_SIZE,end=Math.min(visible.length,start+RUN_SIZE);
                Arrays.fill(visible,start,end,shown);
                if(shown)visibleCount+=end-start;
            }
            if(visibleCount==visible.length)return false;
            Arrays.fill(usedPositions,false);
            for(int i=0;i<visible.length;i++)if(visible[i])
                for(int c=0;c<corners;c++)usedPositions[indices[i*corners+c]/3]=true;
            return true;
        }
        private static boolean inside(float[] b,org.joml.Matrix4f m,double[] box) {
            // Include a margin against rounding at block boundaries. All eight
            // transformed corners must be strictly inside the confirmed solid box.
            for(int i=0;i<8;i++) {
                float x=b[(i&1)==0?0:3],y=b[(i&2)==0?1:4],z=b[(i&4)==0?2:5];
                float px=m.m00()*x+m.m10()*y+m.m20()*z+m.m30();
                float py=m.m01()*x+m.m11()*y+m.m21()*z+m.m31();
                float pz=m.m02()*x+m.m12()*y+m.m22()*z+m.m32();
                double wx=px,wy=py,wz=pz;
                if(box.length>=15) {
                    wx=box[6]*px+box[9]*py+box[12]*pz;
                    wy=box[7]*px+box[10]*py+box[13]*pz;
                    wz=box[8]*px+box[11]*py+box[14]*pz;
                }
                if(wx<=box[0]+.001 || wy<=box[1]+.001 || wz<=box[2]+.001 ||
                   wx>=box[3]-.001 || wy>=box[4]-.001 || wz>=box[5]-.001)return false;
            }
            return true;
        }
    }
    private record Key(int x,int y,int z) {}
    private static float[] unique(float[] source,int[] indices) {
        HashMap<Key,Integer> seen=new HashMap<>();
        float[] unique=new float[source.length];int size=0;
        for(int i=0;i<indices.length;i++) {
            int p=i*3;
            Key key=new Key(Float.floatToIntBits(source[p]),Float.floatToIntBits(source[p+1]),Float.floatToIntBits(source[p+2]));
            Integer existing=seen.get(key);
            if(existing==null) {
                existing=size;seen.put(key,size);unique[size++]=source[p];unique[size++]=source[p+1];unique[size++]=source[p+2];
            }
            indices[i]=existing;
        }
        return Arrays.copyOf(unique,size);
    }
    private static final class Mesh {
        final int[] positionIndices,normalIndices,packedNormals;
        final float[] positions,normals,uvs,transformed,transformedNormals;
        final Vector3f scratch=new Vector3f();
        final Selection selection;
        Mesh(BakedGeometryChunk chunk) {
            BakedVertexData data=chunk.vertices();
            if(data.vertexCount()%3!=0)throw new IllegalArgumentException("Incomplete Bedrock mesh triangle");
            positionIndices=new int[data.vertexCount()];normalIndices=new int[data.vertexCount()];
            positions=unique(data.positions(),positionIndices);normals=unique(data.normals(),normalIndices);uvs=data.uvs();
            selection=new Selection(positions,positionIndices,3);
            transformed=new float[positions.length];transformedNormals=new float[normals.length];packedNormals=new int[normals.length/3];
        }
    }
}
