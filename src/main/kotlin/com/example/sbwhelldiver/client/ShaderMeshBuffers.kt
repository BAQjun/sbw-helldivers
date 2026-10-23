package com.example.sbwhelldiver.client

import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation

/** Iris/Oculus extends entity vertices in groups of four. Keep each native mesh
 * triangle as a degenerate quad so vertices from different model parts cannot become
 * one shader primitive. Positions, UVs, normals and animation remain unchanged.
 */
object ShaderMeshBuffers {
 fun wrap(source:MultiBufferSource,texture:ResourceLocation)=Wrapped(source,texture,ShaderPackCompat.active())
 class Wrapped(private val source:MultiBufferSource,private val texture:ResourceLocation,private val enabled:Boolean):MultiBufferSource {
  private val meshType=BedrockModelRenderTypes.polyMeshCutout(texture)
  private var pending:TriangleQuads?=null
  override fun getBuffer(type:RenderType):VertexConsumer {
   finish() // The source can finish its previous RenderType when selecting another.
   // Leave muzzle flashes, emissive passes and other materials on their native paths.
   if(!enabled||type!==meshType)return source.getBuffer(type)
   return TriangleQuads(source.getBuffer(RenderType.entityCutoutNoCull(texture))).also{pending=it}
  }
  fun finish(){pending?.flush();pending=null}
 }
}

private class TriangleQuads(private val out:VertexConsumer):VertexConsumer {
 private val bulk=PackedEntityVertices.create(out)
 private var x=0.0;private var y=0.0;private var z=0.0
 private var r=255;private var g=255;private var b=255;private var a=255
 private var u=0f;private var v=0f;private var ou=0;private var ov=0;private var lu=0;private var lv=0
 private var nx=0f;private var ny=1f;private var nz=0f;private var count=0
 private val vertices=Array(3){FloatArray(14)}
 private val packed=Array(3){IntArray(3)}
 override fun vertex(x:Double,y:Double,z:Double):VertexConsumer{this.x=x;this.y=y;this.z=z;return this}
 override fun color(r:Int,g:Int,b:Int,a:Int):VertexConsumer{this.r=r;this.g=g;this.b=b;this.a=a;return this}
 override fun uv(u:Float,v:Float):VertexConsumer{this.u=u;this.v=v;return this}
 override fun overlayCoords(u:Int,v:Int):VertexConsumer{ou=u;ov=v;return this}
 override fun uv2(u:Int,v:Int):VertexConsumer{lu=u;lv=v;return this}
 override fun normal(x:Float,y:Float,z:Float):VertexConsumer{nx=x;ny=y;nz=z;return this}
 private fun emit(i:Int){val v=vertices[i];val p=packed[i]
  if(bulk==null)out.vertex(v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],p[0],p[1],v[11],v[12],v[13])
  else bulk.vertex(v[0],v[1],v[2],p[2],v[7],v[8],p[0],p[1],v[11],v[12],v[13])
 }
 fun flush(){check(count%3==0){"Incomplete Bedrock mesh triangle"};bulk?.flush()}
 override fun endVertex(){
  val i=count%3;val v=vertices[i];v[0]=x.toFloat();v[1]=y.toFloat();v[2]=z.toFloat();v[3]=r/255f;v[4]=g/255f;v[5]=b/255f;v[6]=a/255f;v[7]=u;v[8]=this.v;v[11]=nx;v[12]=ny;v[13]=nz
  packed[i][0]=ou or (ov shl 16);packed[i][1]=lu or (lv shl 16);packed[i][2]=(r and 255) or ((g and 255) shl 8) or ((b and 255) shl 16) or ((a and 255) shl 24)
  completeVertex(i)
 }
 // The Bedrock bulk renderer supplies complete vertices. Avoid routing each
 // one through the default thirteen virtual attribute calls and color casts.
 override fun vertex(x:Float,y:Float,z:Float,r:Float,g:Float,b:Float,a:Float,u:Float,v:Float,overlay:Int,light:Int,nx:Float,ny:Float,nz:Float){
  val i=count%3;val row=vertices[i]
  row[0]=x;row[1]=y;row[2]=z;row[3]=r;row[4]=g;row[5]=b;row[6]=a;row[7]=u;row[8]=v;row[11]=nx;row[12]=ny;row[13]=nz
  packed[i][0]=overlay;packed[i][1]=light
  packed[i][2]=((r*255).toInt() and 255) or (((g*255).toInt() and 255) shl 8) or (((b*255).toInt() and 255) shl 16) or (((a*255).toInt() and 255) shl 24)
  completeVertex(i)
 }
 private fun completeVertex(i:Int){
  count++
  if(i!=2)return
  val a=vertices[0];val b=vertices[1];val c=vertices[2]
  val ux=b[0]-a[0];val uy=b[1]-a[1];val uz=b[2]-a[2];val vx=c[0]-a[0];val vy=c[1]-a[1];val vz=c[2]-a[2]
  val cx=uy*vz-uz*vy;val cy=uz*vx-ux*vz;val cz=ux*vy-uy*vx
  if(cx*cx+cy*cy+cz*cz<=1e-16f)return
  emit(0);emit(1);emit(2);emit(2)
 }
 override fun defaultColor(r:Int,g:Int,b:Int,a:Int){color(r,g,b,a)}
 override fun unsetDefaultColor(){}
}
