package com.example.sbwhelldiver.client;

import java.util.*;

/** Joins only flat triangles along their existing diagonal. No vertex, UV,
 * winding, normal, bone or material changes; curved faces remain triangles. */
public final class PlanarMeshQuads {
    private PlanarMeshQuads() {}
    public record Compiled(int[] corners, int[] sourceTriangles) {}
    private record Corner(int x,int y,int z,int u,int v,int nx,int ny,int nz) {}
    private record Edge(int a,int b) {}
    public static final boolean ENABLED=!Boolean.getBoolean("sbw_helldiver.qaDisableNearMeshes");
    public static long inputTriangles, outputQuads;
    /** Existing true quads stay intact. Only triangle-quads enter the compiler. */
    public static float[] packQuads(float[] source) {
        if(!ENABLED)return source;
        int count=0;
        for(int k=0;k<source.length;k+=32)if(sameCorner(source,k+16,k+24))count++;
        if(count<2)return source;
        float[] p=new float[count*9],n=new float[count*9],uv=new float[count*6];
        int[] original=new int[count*3];int at=0;
        for(int k=0;k<source.length;k+=32)if(sameCorner(source,k+16,k+24))
            for(int j=0;j<3;j++) {
                int v=k+j*8;original[at]=v;
                System.arraycopy(source,v,p,at*3,3);System.arraycopy(source,v+3,uv,at*2,2);
                System.arraycopy(source,v+5,n,at*3,3);at++;
            }
        var compiled=compile(p,n,uv);if(compiled.corners.length==count*4)return source;
        float[] result=new float[source.length-(count*4-compiled.corners.length)*8];at=0;
        for(int k=0;k<source.length;k+=32)if(!sameCorner(source,k+16,k+24)) {System.arraycopy(source,k,result,at,32);at+=32;}
        for(int corner:compiled.corners){System.arraycopy(source,original[corner],result,at,8);at+=8;}
        return result;
    }
    private static boolean sameCorner(float[] v,int a,int b) {for(int i=0;i<8;i++)if(v[a+i]!=v[b+i])return false;return true;}

    public static Compiled compile(float[] p,float[] n,float[] uv) {
        int size=p.length/9;
        if(p.length%9!=0 || n.length!=p.length || uv.length!=size*6)
            throw new IllegalArgumentException("Incomplete mesh attributes");
        var canonical=new HashMap<Corner,Integer>();int[] ids=new int[size*3];
        for(int i=0;i<ids.length;i++) {
            int a=i*3,b=i*2;
            var key=new Corner(bits(p[a]),bits(p[a+1]),bits(p[a+2]),bits(uv[b]),bits(uv[b+1]),bits(n[a]),bits(n[a+1]),bits(n[a+2]));
            ids[i]=canonical.computeIfAbsent(key,k->canonical.size());
        }
        // Ambiguous/non-manifold edges are left unchanged.
        var edges=new HashMap<Edge,Integer>();
        for(int i=0;i<size;i++)for(int k=0;k<3;k++) {
            int a=i*3+k,b=i*3+(k+1)%3;
            edges.merge(new Edge(ids[a],ids[b]),a,(old,next)->-1);
        }
        boolean[] used=new boolean[size];int[] corners=new int[size*4],sources=new int[size*2];int at=0;
        for(int i=0;i<size;i++) {
            if(used[i])continue;
            int a=i*3,b=a+1,c=a+2,d=c,paired=-1;
            for(int k=0;k<3;k++) {
                int x=i*3+k,y=i*3+(k+1)%3,z=i*3+(k+2)%3;
                Integer reverse=edges.get(new Edge(ids[y],ids[x]));
                if(reverse==null || reverse<0 || reverse/3<=i || used[reverse/3])continue;
                int w=reverse/3*3+(reverse%3+2)%3;
                if(compatible(p,n,uv,y,z,x,w)) {a=y;b=z;c=x;d=w;paired=reverse/3;break;}
            }
            int face=at/4;
            corners[at++]=a;corners[at++]=b;corners[at++]=c;corners[at++]=d;
            sources[face*2]=i;sources[face*2+1]=paired;used[i]=true;if(paired>=0)used[paired]=true;
        }
        inputTriangles+=size;outputQuads+=at/4;
        return new Compiled(Arrays.copyOf(corners,at),Arrays.copyOf(sources,at/2));
    }
    private static int bits(float v){return v==0?0:Float.floatToIntBits(v);}
    private static boolean compatible(float[] p,float[] n,float[] uv,int a,int b,int c,int d) {
        // Source normals must agree exactly, including flat-shaded detail edges.
        for(int v:new int[]{b,c,d})for(int k=0;k<3;k++)if(n[a*3+k]!=n[v*3+k])return false;
        double[] ab=sub(p,b,a),ac=sub(p,c,a),ad=sub(p,d,a),normal=cross(ab,ac);
        double length=dot(normal,normal);if(length<1e-24)return false;
        double scale=Math.max(dot(ab,ab),Math.max(dot(ac,ac),dot(ad,ad)));
        double plane=dot(normal,ad);
        if(plane*plane>length*scale*1e-14)return false;
        int[] vertices={a,b,c,d};
        for(int i=0;i<4;i++)if(dot(cross(sub(p,vertices[(i+1)%4],vertices[i]),sub(p,vertices[(i+2)%4],vertices[(i+1)%4])),normal)<=length*1e-10)return false;
        // Keep tangents identical too: the UV field must be affine across the pair.
        double aa=dot(ab,ab),bb=dot(ab,ac),cc=dot(ac,ac),den=aa*cc-bb*bb;
        if(den<=1e-24)return false;
        double x=(dot(ad,ab)*cc-dot(ad,ac)*bb)/den,y=(dot(ad,ac)*aa-dot(ad,ab)*bb)/den;
        for(int k=0;k<2;k++)if(Math.abs(uv[a*2+k]+x*(uv[b*2+k]-uv[a*2+k])+y*(uv[c*2+k]-uv[a*2+k])-uv[d*2+k])>1e-6)return false;
        return true;
    }
    private static double[] sub(float[] p,int a,int b){return new double[]{(double)p[a*3]-p[b*3],(double)p[a*3+1]-p[b*3+1],(double)p[a*3+2]-p[b*3+2]};}
    private static double[] cross(double[] a,double[] b){return new double[]{a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]};}
    private static double dot(double[] a,double[] b){return a[0]*b[0]+a[1]*b[1]+a[2]*b[2];}
}
