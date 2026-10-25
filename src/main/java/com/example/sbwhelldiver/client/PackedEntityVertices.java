package com.example.sbwhelldiver.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;

/** Optional Embeddium bulk writer. Keep the normal consumer fallback when absent.
 * Iris' own serializer still generates all extended shader attributes. */
public final class PackedEntityVertices {
    private static final int STRIDE=36, CAPACITY=4096;
    private static final ThreadLocal<ByteBuffer> SCRATCH=ThreadLocal.withInitial(()->org.lwjgl.BufferUtils.createByteBuffer(STRIDE*CAPACITY));
    private static MethodHandle of,push;
    private static Object format;
    static {
        try {
            var writer=Class.forName("net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter");
            var description=Class.forName("net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription");
            var lookup=MethodHandles.publicLookup();
            of=lookup.unreflect(writer.getMethod("tryOf",VertexConsumer.class)).asType(MethodType.methodType(Object.class,VertexConsumer.class));
            push=lookup.unreflect(writer.getMethod("push",MemoryStack.class,long.class,int.class,description)).asType(MethodType.methodType(void.class,Object.class,MemoryStack.class,long.class,int.class,Object.class));
            format=Class.forName("net.caffeinemc.mods.sodium.api.vertex.format.common.ModelVertex").getField("FORMAT").get(null);
        } catch(ReflectiveOperationException|LinkageError ignored) {of=null;push=null;format=null;}
    }
    private final Object writer;
    private final ByteBuffer storage=SCRATCH.get();
    private final long address=MemoryUtil.memAddress(storage);
    private int count;
    private PackedEntityVertices(Object writer){this.writer=writer;}
    public static PackedEntityVertices create(VertexConsumer consumer){
        if(of==null)return null;
        try {Object writer=(Object)of.invokeExact(consumer);return writer==null?null:new PackedEntityVertices(writer);}
        catch(Throwable error){throw new IllegalStateException("Cannot obtain Embeddium vertex writer",error);}
    }
    public void vertex(float x,float y,float z,int color,float u,float v,int overlay,int light,float nx,float ny,float nz){
        vertexPackedNormal(x,y,z,color,u,v,overlay,light,packNormal(nx,ny,nz));
    }
    public static int packNormal(float nx,float ny,float nz){
        return ((int)(nx*127)&255)|(((int)(ny*127)&255)<<8)|(((int)(nz*127)&255)<<16);
    }
    public void vertexPackedNormal(float x,float y,float z,int color,float u,float v,int overlay,int light,int normal){
        long p=address+(long)count*STRIDE;
        MemoryUtil.memPutFloat(p,x);MemoryUtil.memPutFloat(p+4,y);MemoryUtil.memPutFloat(p+8,z);
        MemoryUtil.memPutInt(p+12,color);MemoryUtil.memPutFloat(p+16,u);MemoryUtil.memPutFloat(p+20,v);
        MemoryUtil.memPutInt(p+24,overlay);MemoryUtil.memPutInt(p+28,light);
        MemoryUtil.memPutInt(p+32,normal);
        if(++count==CAPACITY)flush();
    }
    public void flush(){
        if(count==0)return;
        try(MemoryStack stack=MemoryStack.stackPush()){
            push.invokeExact(writer,stack,address,count,format);
            count=0;
        } catch(Throwable error){throw new IllegalStateException("Cannot submit Embeddium entity vertices",error);}
    }
}
