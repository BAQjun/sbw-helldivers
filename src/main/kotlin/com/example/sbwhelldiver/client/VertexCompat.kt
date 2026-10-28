package com.example.sbwhelldiver.client

import com.mojang.blaze3d.vertex.VertexConsumer
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f

/** Shared attribute bridge for the mod's procedural effects and cached Bedrock geometry. */
internal fun VertexConsumer.vertex(x:Double,y:Double,z:Double)=addVertex(x.toFloat(),y.toFloat(),z.toFloat())
internal fun VertexConsumer.vertex(matrix:Matrix4f,x:Float,y:Float,z:Float)=addVertex(matrix,x,y,z)
internal fun VertexConsumer.vertex(x:Float,y:Float,z:Float,r:Float,g:Float,b:Float,a:Float,u:Float,v:Float,overlay:Int,light:Int,nx:Float,ny:Float,nz:Float) =
 addVertex(x,y,z,net.minecraft.util.FastColor.ARGB32.color((a*255).toInt(),(r*255).toInt(),(g*255).toInt(),(b*255).toInt()),u,v,overlay,light,nx,ny,nz)
internal fun VertexConsumer.color(r:Int,g:Int,b:Int,a:Int)=setColor(r,g,b,a)
internal fun VertexConsumer.color(r:Float,g:Float,b:Float,a:Float)=setColor(r,g,b,a)
internal fun VertexConsumer.uv(u:Float,v:Float)=setUv(u,v)
internal fun VertexConsumer.overlayCoords(value:Int)=setOverlay(value)
internal fun VertexConsumer.uv2(value:Int)=setLight(value)
internal fun VertexConsumer.normal(x:Float,y:Float,z:Float)=setNormal(x,y,z)
internal fun VertexConsumer.normal(matrix:Matrix3f,x:Float,y:Float,z:Float):VertexConsumer {
 val n=matrix.transform(Vector3f(x,y,z));return setNormal(n.x,n.y,n.z)
}
internal fun VertexConsumer.endVertex() {}
