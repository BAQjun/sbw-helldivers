package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.EagleEntity
import com.example.sbwhelldiver.entity.EagleFlight
import com.google.gson.JsonParser
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import org.joml.Vector3f

class EagleRenderer(context:EntityRendererProvider.Context):EntityRenderer<EagleEntity>(context) {
 private val aircraftBuffers=MultiBufferSource.immediate(com.mojang.blaze3d.vertex.ByteBufferBuilder(262144))
 override fun shouldRender(e:EagleEntity,f:net.minecraft.client.renderer.culling.Frustum,x:Double,y:Double,z:Double):Boolean {
  val p=e.positionAt(e.age);return e.age<EagleFlight.end(e.attack)&&f.isVisible(net.minecraft.world.phys.AABB(p.x-10,p.y-9,p.z-10,p.x+10,p.y+9,p.z+10))
 }
 override fun getTextureLocation(entity:EagleEntity)=Model.textures[0]
 override fun render(e:EagleEntity,yaw:Float,partial:Float,p:PoseStack,sharedBuffers:MultiBufferSource,light:Int) {
  val b=aircraftBuffers
  val fogStart=com.mojang.blaze3d.systems.RenderSystem.getShaderFogStart();val fogEnd=com.mojang.blaze3d.systems.RenderSystem.getShaderFogEnd()
  com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(maxOf(fogStart,300f));com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(maxOf(fogEnd,800f))
  p.pushPose()
  try {
  val t=e.age+partial;val exact=e.positionAt(t);val rendered=net.minecraft.world.phys.Vec3(net.minecraft.util.Mth.lerp(partial.toDouble(),e.xo,e.x),net.minecraft.util.Mth.lerp(partial.toDouble(),e.yo,e.y),net.minecraft.util.Mth.lerp(partial.toDouble(),e.zo,e.z))
  p.translate(exact.x-rendered.x,exact.y-rendered.y,exact.z-rendered.z)
  p.mulPose(Axis.YP.rotationDegrees(180-e.yRot));p.mulPose(Axis.XP.rotationDegrees(-EagleFlight.pitch(t,e.attack,e.flightHeight)))
  val tr=p.last()
  val transformedPosition=Vector3f();val transformedNormal=Vector3f()
  Model.parts.forEachIndexed { i,triangles ->
   val vc=b.getBuffer(RenderType.entityCutoutNoCull(Model.textures[i]))
   val bulk=PackedEntityVertices.create(vc)
   for(a in triangles){
    transformedNormal.set(a.n).mul(tr.normal())
    for(corner in 0..3){
     val k=if(corner==3)2 else corner;val v=a.p[k];val uv=a.uv[k]
     transformedPosition.set(v).mulPosition(tr.pose())
     if(bulk!=null)bulk.vertex(transformedPosition.x,transformedPosition.y,transformedPosition.z,-1,uv[0],uv[1],OverlayTexture.NO_OVERLAY,light,transformedNormal.x,transformedNormal.y,transformedNormal.z)
     else vc.vertex(transformedPosition.x,transformedPosition.y,transformedPosition.z,1f,1f,1f,1f,uv[0],uv[1],OverlayTexture.NO_OVERLAY,light,transformedNormal.x,transformedNormal.y,transformedNormal.z)
    }
   }
   bulk?.flush()
  }
  // Two short blue exhaust cones at the original engine nodes, in the same transformed aircraft pose.
  val glow=b.getBuffer(RenderType.entityTranslucentEmissive(BeaconModel.WHITE))
  fun cone(cx:Double,r:Double,length:Double,red:Float,green:Float,blue:Float,alpha:Float){
   for(i in 0..11){val a=i*kotlin.math.PI/6;val c=(i+1)*kotlin.math.PI/6
    val points=arrayOf(Vector3f((cx+r*kotlin.math.cos(a)).toFloat(),(1.313+r*kotlin.math.sin(a)).toFloat(),4.912f),Vector3f((cx+r*kotlin.math.cos(c)).toFloat(),(1.313+r*kotlin.math.sin(c)).toFloat(),4.912f),Vector3f(cx.toFloat(),1.313f,(4.912+length).toFloat()))
    for(k in intArrayOf(0,1,2,2)){val v=points[k];glow.vertex(tr.pose(),v.x,v.y,v.z).color(red,green,blue,alpha).uv(0f,0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(tr.normal(),0f,0f,1f).endVertex()}
   }
  }
  val flicker=1.0+.08*kotlin.math.sin(t*2.7)
  for(cx in listOf(-1.545,1.545)){cone(cx,.48,2.2*flicker,.12f,.44f,1f,.45f);cone(cx,.22,.9*flicker,.7f,.88f,1f,.9f)}
  b.endBatch()
  } finally {p.popPose();com.mojang.blaze3d.systems.RenderSystem.setShaderFogStart(fogStart);com.mojang.blaze3d.systems.RenderSystem.setShaderFogEnd(fogEnd)}
 }
 object Model:ResourceManagerReloadListener {
  data class Tri(val p:Array<Vector3f>,val uv:Array<FloatArray>,val n:Vector3f)
  val textures=List(4){ResourceLocation.fromNamespaceAndPath(MOD_ID,"textures/mesh/eagle_$it.png")}
  var parts=List(4){emptyList<Tri>()};private set
  override fun onResourceManagerReload(r:ResourceManager) {
   val lists=List(4){mutableListOf<Tri>()}
   r.openAsReader(ResourceLocation.fromNamespaceAndPath(MOD_ID,"models/mesh/eagle.mesh.json")).use {reader ->
    JsonParser.parseReader(reader).asJsonObject.getAsJsonArray("triangles").forEach {row ->
     val a=row.asJsonArray;val vv=a[1].asJsonArray.map {it.asJsonArray};val pp=vv.map {Vector3f(it[0].asFloat,it[1].asFloat,it[2].asFloat)}.toTypedArray();val n=Vector3f(pp[1]).sub(pp[0]).cross(Vector3f(pp[2]).sub(pp[0]))
     if(n.lengthSquared()>1e-15f){n.normalize();lists[a[0].asInt].add(Tri(pp,vv.map {floatArrayOf(it[3].asFloat,it[4].asFloat)}.toTypedArray(),n))}
    }
   };parts=lists
  }
 }
}
