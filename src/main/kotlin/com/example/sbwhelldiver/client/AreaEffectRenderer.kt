package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.GroundFireEntity
import com.example.sbwhelldiver.entity.MortarFieldEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/** Clients animate bounded geometry locally; authoritative area damage remains server-owned. */
abstract class AreaEffectRenderer<T:Entity>(context:EntityRendererProvider.Context):EntityRenderer<T>(context) {
    override fun getTextureLocation(entity:T)=ResourceLocation(MOD_ID,"textures/particle/gas_cloud.png")
    protected abstract fun radius(entity:T):Double
    override fun shouldRender(entity:T,frustum:Frustum,x:Double,y:Double,z:Double):Boolean =
        entity.shouldRenderAtSqrDistance(entity.distanceToSqr(x,y,z)) && frustum.isVisible(AABB(entity.x-radius(entity),entity.y,entity.z-radius(entity),entity.x+radius(entity),entity.y+4,entity.z+radius(entity)))
    protected fun quad(pose:PoseStack,buffer:VertexConsumer,center:Vec3,width:Double,height:Double,color:FloatArray,billboard:Boolean=true) {
        val camera=Minecraft.getInstance().entityRenderDispatcher.cameraOrientation()
        val right=org.joml.Vector3f(1f,0f,0f).rotate(camera)
        val up=if(billboard)org.joml.Vector3f(0f,1f,0f).rotate(camera) else org.joml.Vector3f(0f,1f,0f)
        if(!billboard) { right.y=0f;right.normalize() }
        val matrix=pose.last().pose()
        for((dx,dy,u,v) in arrayOf(floatArrayOf(-1f,-1f,0f,0f),floatArrayOf(1f,-1f,1f,0f),floatArrayOf(1f,1f,1f,1f),floatArrayOf(-1f,1f,0f,1f))) {
            val p=org.joml.Vector3f(center.x.toFloat(),center.y.toFloat(),center.z.toFloat()).fma((dx*width*.5).toFloat(),right).fma((dy*height*.5).toFloat(),up)
            buffer.vertex(matrix,p.x,p.y,p.z).color(color[0],color[1],color[2],color[3]).uv(u,v).endVertex()
        }
    }
}
class GroundFireRenderer(context:EntityRendererProvider.Context):AreaEffectRenderer<GroundFireEntity>(context) {
    override fun radius(entity:GroundFireEntity)=entity.radius
    // Ground fire is rendered by the chunk renderer as real sentry_fire blocks.
}

class MortarFieldRenderer(context:EntityRendererProvider.Context):AreaEffectRenderer<MortarFieldEntity>(context) {
    override fun radius(entity:MortarFieldEntity)=entity.radius
    override fun render(e:MortarFieldEntity,yaw:Float,partial:Float,pose:PoseStack,buffers:MultiBufferSource,light:Int) {
        if(ShaderPackCompat.shadowPass())return
        if(e.kind==MortarFieldEntity.Kind.EMS){EmsFieldVisual.render(e,partial,pose,buffers);return}
        // Gas volume is integrated after the world (FieldVolumeRenderer).
    }
}
