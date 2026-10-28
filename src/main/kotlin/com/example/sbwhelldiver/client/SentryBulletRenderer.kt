package com.example.sbwhelldiver.client

import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleVecUtils
import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import com.example.sbwhelldiver.LOGGER
import net.minecraft.core.BlockPos
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3f

/** Native ballistics and tracer colours, with the visible geometry centred on the real flight path. */
class SentryBulletRenderer(context: EntityRendererProvider.Context) : EntityRenderer<ProjectileEntity>(context) {
    override fun getTextureLocation(entity: ProjectileEntity) = TEXTURE
    override fun shouldRender(entity: ProjectileEntity, frustum: Frustum, x: Double, y: Double, z: Double) = true
    override fun getBlockLightLevel(entity: ProjectileEntity, pos: BlockPos) = 15
    override fun render(entity: ProjectileEntity, yaw: Float, partial: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        val model = entity.modelInstance ?: return
        val velocity = entity.deltaMovement
        if (velocity.lengthSqr() < 1e-8) return
        val origin = pose.last().pose().transformPosition(Vector3f())
        val direction = pose.last().pose().transformDirection(Vector3f(velocity.normalize().toVector3f()))
        val launch = (entity as? com.example.sbwhelldiver.entity.SentryBulletEntity)?.launchPosition ?: entity.position()
        val travelled = entity.getPosition(partial).subtract(launch).dot(velocity.normalize()).coerceAtLeast(0.0)
        // The tracer trails the bullet tip; it cannot already extend through the gun at birth.
        val length = minOf(0.7 * velocity.length() / 4.0, travelled.coerceAtLeast(0.15)).toFloat()
        val tipForward = (length - travelled).coerceAtLeast(0.0).toFloat()
        val expectedCenter = Vector3f(origin).add(Vector3f(direction).mul(tipForward-length/2))
        pose.pushPose()
        try {
            // A short newborn tracer starts at the muzzle and extends only forward.
            val forward = velocity.normalize().scale(tipForward.toDouble())
            pose.translate(forward.x,forward.y,forward.z)
            // Turret tracers must never inherit the owner's handheld-weapon render offset.
            pose.mulPose(Axis.YP.rotationDegrees(VehicleVecUtils.getYRotFromVector(velocity).toFloat()))
            pose.mulPose(Axis.XP.rotationDegrees(-VehicleVecUtils.getXRotFromVector(velocity).toFloat()))
            pose.scale(0.12f, 0.12f, length * 4)
            // Both cubes in SBW 0.8.9.1's projectile model are centred at Y=2, not Y=0.
            // Apply this in model space after scaling so the correction follows barrel pitch.
            pose.translate(0.0, -2.0 / 16.0, -2.0 / 16.0)
            val data = entity.entityData
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.energySwirl(TEXTURE,15f,15f)),
                light, OverlayTexture.NO_OVERLAY, data.get(ProjectileEntity.COLOR_R),
                data.get(ProjectileEntity.COLOR_G), data.get(ProjectileEntity.COLOR_B), 1f)
            // Inspect actual baked vertices, including all renderer transforms, in the opt-in client test.


        } finally {
            pose.popPose()
        }
    }

    companion object {
        private val TEXTURE = ResourceLocation.fromNamespaceAndPath("superbwarfare", "textures/bedrock/projectile/projectile.png")
        var checkedVertices = 0
            private set
        val checkedSpawnMotion = mutableSetOf<Int>()
    }
}
