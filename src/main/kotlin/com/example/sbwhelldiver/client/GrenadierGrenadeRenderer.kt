package com.example.sbwhelldiver.client
import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleVecUtils
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation

/** Compact 40 mm projectile; do not stretch it into the cannon's tracer streak. */
class GrenadierGrenadeRenderer(context:EntityRendererProvider.Context):EntityRenderer<SmallCannonShellEntity>(context) {
    private val texture=ResourceLocation.fromNamespaceAndPath("superbwarfare","textures/bedrock/projectile/small_cannon_shell.png")
    override fun getTextureLocation(entity:SmallCannonShellEntity)=texture
    override fun render(entity:SmallCannonShellEntity,yaw:Float,partial:Float,pose:PoseStack,buffers:MultiBufferSource,light:Int) {
        val model=entity.modelInstance ?: return
        val velocity=entity.deltaMovement
        if(velocity.lengthSqr()<1e-8)return
        pose.pushPose()
        try {
            pose.mulPose(Axis.YP.rotationDegrees(VehicleVecUtils.getYRotFromVector(velocity).toFloat()))
            pose.mulPose(Axis.XP.rotationDegrees(-VehicleVecUtils.getXRotFromVector(velocity).toFloat()))
            pose.scale(.22f,.22f,.32f);pose.translate(0.0,-4.0/16,0.0)
            model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),light,OverlayTexture.NO_OVERLAY,.80f,.76f,.31f,1f)
        } finally {pose.popPose()}
    }
}
