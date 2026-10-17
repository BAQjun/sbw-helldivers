package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.entity.EagleBombEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.*
class EagleBombRenderer(c:EntityRendererProvider.Context):EntityRenderer<EagleBombEntity>(c) {
 override fun getTextureLocation(e:EagleBombEntity)=BeaconModel.WHITE
 override fun render(e:EagleBombEntity,yaw:Float,partial:Float,p:PoseStack,b:MultiBufferSource,light:Int) {
  p.pushPose();val v=e.deltaMovement.normalize();p.mulPose(Quaternionf().rotationTo(Vector3f(0f,1f,0f),Vector3f(v.x.toFloat(),v.y.toFloat(),v.z.toFloat())))
  val vc=b.getBuffer(RenderType.entityCutoutNoCull(BeaconModel.WHITE));val tr=p.last()
  for(i in 0..11){val t=i*PI/6;val u=(i+1)*PI/6
   val points=arrayOf(Vector3f((cos(t)*.18).toFloat(),-.5f,(sin(t)*.18).toFloat()),Vector3f((cos(u)*.18).toFloat(),-.5f,(sin(u)*.18).toFloat()),Vector3f((cos(u)*.18).toFloat(),.35f,(sin(u)*.18).toFloat()),Vector3f((cos(t)*.18).toFloat(),.35f,(sin(t)*.18).toFloat()))
   for(vv in points)vc.vertex(tr.pose(),vv.x,vv.y,vv.z).color(.28f,.30f,.27f,1f).uv(0f,0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(tr.normal(),cos(t).toFloat(),0f,sin(t).toFloat()).endVertex()
   for(k in intArrayOf(0,1,2,2)){val vv=if(k==2)Vector3f(0f,.65f,0f) else points[k+2];vc.vertex(tr.pose(),vv.x,vv.y,vv.z).color(.38f,.4f,.35f,1f).uv(0f,0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(tr.normal(),cos(t).toFloat(),.5f,sin(t).toFloat()).endVertex()}
  };p.popPose()
 }
}
