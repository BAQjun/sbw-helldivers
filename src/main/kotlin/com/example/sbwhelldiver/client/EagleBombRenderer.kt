package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.entity.SentryCannonShellEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.*

class EagleBombRenderer<T:SentryCannonShellEntity>(c:EntityRendererProvider.Context,private val radius:Double=.18,private val length:Double=1.0):EntityRenderer<T>(c){
 override fun getTextureLocation(e:T)=BeaconModel.WHITE
 override fun render(e:T,yaw:Float,partial:Float,p:PoseStack,b:MultiBufferSource,light:Int){
  p.pushPose()
  val v=if(e is com.example.sbwhelldiver.entity.Eagle500kgBombEntity&&e.embedded)e.embeddedDirection else if(e.deltaMovement.lengthSqr()>1e-8)e.deltaMovement.normalize() else net.minecraft.world.phys.Vec3.directionFromRotation(e.xRot,e.yRot)
  p.mulPose(Quaternionf().rotationTo(Vector3f(0f,1f,0f),Vector3f(v.x.toFloat(),v.y.toFloat(),v.z.toFloat())))
  val vc=b.getBuffer(RenderType.entityCutoutNoCull(BeaconModel.WHITE));val tr=p.last()
  fun face(points:List<Vector3f>,red:Float,green:Float,blue:Float){
   val n=Vector3f(points[1]).sub(points[0]).cross(Vector3f(points[2]).sub(points[0])).normalize()
   for(q in points)vc.vertex(tr.pose(),q.x,q.y,q.z).color(red,green,blue,1f).uv(0f,0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(tr.normal(),n.x,n.y,n.z).endVertex()
  }
  fun q(a:Double,y:Double,r:Double)=Vector3f((cos(a)*r).toFloat(),(y*length).toFloat(),(sin(a)*r).toFloat())
  for(i in 0..11){
   val t=i*PI/6;val u=(i+1)*PI/6
   face(listOf(q(t,-.5,radius),q(u,-.5,radius),q(u,.35,radius),q(t,.35,radius)),.28f,.30f,.27f)
   face(listOf(q(t,.35,radius),q(u,.35,radius),q(u,.65,0.0),q(t,.65,0.0)),.38f,.40f,.35f)
   face(listOf(q(u,-.5,radius),q(t,-.5,radius),q(t,-.5,0.0),q(u,-.5,0.0)),.18f,.20f,.18f)
  }
  for(i in 0..3){val a=i*PI/2;face(listOf(q(a,-.5,radius*.7),q(a,-.5,radius*1.9),q(a,-.12,radius*.85),q(a,-.12,radius*.7)),.35f,.36f,.30f)}
  p.popPose()
 }
}
