package com.example.sbwhelldiver.client;



import com.mojang.blaze3d.vertex.VertexConsumer;

import org.joml.Matrix3f;

import org.joml.Matrix4f;

import org.joml.Vector3f;

import java.util.ArrayList;

import java.util.HashMap;



/** Cached indices share only position/normal arithmetic, never UVs or animated nodes.

 * Scratch arrays are render-thread local to this immutable model batch. */

public final class MachineVertexBatch {

    private record Point(int x,int y,int z) {}

    private record Indexed(float[] values,int[] indices) {}

    private final float[] source,positions,normals,transformed,transformedNormals;

    private final int[] positionIndices,normalIndices,packedNormals;

    private final Vector3f scratch=new Vector3f();

    public MachineVertexBatch(float[] source) {

        if(source.length%32!=0)throw new IllegalArgumentException("Incomplete machine quad");

        this.source=source;

        var p=index(source,8,0);var n=index(source,32,5);

        positions=p.values;positionIndices=p.indices;normals=n.values;normalIndices=n.indices;

        transformed=new float[positions.length];transformedNormals=new float[normals.length];

        packedNormals=new int[normals.length/3];

    }

    private static Indexed index(float[] source,int stride,int offset) {

        var map=new HashMap<Point,Integer>();var values=new ArrayList<Float>();

        int[] indices=new int[source.length/stride];

        for(int i=0;i<indices.length;i++) {

            int at=i*stride+offset;

            var key=new Point(bits(source[at]),bits(source[at+1]),bits(source[at+2]));

            Integer id=map.get(key);

            if(id==null){id=values.size();map.put(key,id);for(int k=0;k<3;k++)values.add(source[at+k]);}

            indices[i]=id;

        }

        float[] result=new float[values.size()];for(int i=0;i<result.length;i++)result[i]=values.get(i);

        return new Indexed(result,indices);

    }

    private static int bits(float x){return x==0?0:Float.floatToIntBits(x);}

    public int uniquePositions(){return positions.length/3;}

    public int sourceVertices(){return positionIndices.length;}

    public void draw(Matrix4f matrix,Matrix3f normalMatrix,VertexConsumer out,PackedEntityVertices bulk,int light,int overlay) {

        for(int i=0;i<positions.length;i+=3) {

            scratch.set(positions[i],positions[i+1],positions[i+2]).mulPosition(matrix);

            transformed[i]=scratch.x;transformed[i+1]=scratch.y;transformed[i+2]=scratch.z;

        }

        for(int i=0;i<normals.length;i+=3) {

            scratch.set(normals[i],normals[i+1],normals[i+2]).mul(normalMatrix).normalize();

            transformedNormals[i]=scratch.x;transformedNormals[i+1]=scratch.y;transformedNormals[i+2]=scratch.z;

            packedNormals[i/3]=PackedEntityVertices.packNormal(scratch.x,scratch.y,scratch.z);

        }

        for(int i=0;i<positionIndices.length;i++) {

            int p=positionIndices[i],n=normalIndices[i/4],uv=i*8+3;

            if(bulk!=null)bulk.vertexPackedNormal(transformed[p],transformed[p+1],transformed[p+2],-1,

                source[uv],source[uv+1],overlay,light,packedNormals[n/3]);

            else out.vertex(transformed[p],transformed[p+1],transformed[p+2],1f,1f,1f,1f,source[uv],source[uv+1],

                overlay,light,transformedNormals[n],transformedNormals[n+1],transformedNormals[n+2]);

        }

    }

}
