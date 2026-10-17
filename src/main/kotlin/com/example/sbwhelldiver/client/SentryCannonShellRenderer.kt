package com.example.sbwhelldiver.client

import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import com.atsuishio.superbwarfare.entity.vehicle.utils.VehicleVecUtils
import com.example.sbwhelldiver.entity.SentryCannonShellEntity
import com.example.sbwhelldiver.LOGGER
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceLocation
import org.joml.Vector3f

/** The native shell model follows the real projectile, independent of the owner's held weapon. */
class SentryCannonShellRenderer(context: EntityRendererProvider.Context) : EntityRenderer<SmallCannonShellEntity>(context) {
    override fun getTextureLocation(entity: SmallCannonShellEntity) = TEXTURE
    override fun shouldRender(entity: SmallCannonShellEntity, frustum: Frustum, x: Double, y: Double, z: Double) = true
    override fun getBlockLightLevel(entity: SmallCannonShellEntity, pos: BlockPos) = 15
    override fun render(entity: SmallCannonShellEntity, yaw: Float, partial: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        if (java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest") && entity is SentryCannonShellEntity)
            ClientSmokeTest.verifyCannonSpawn(entity)
        val model = entity.modelInstance ?: return
        val velocity = entity.deltaMovement
        if (velocity.lengthSqr() < 1e-8) return
        val launch = (entity as? SentryCannonShellEntity)?.launchPosition ?: entity.position()
        val travelled = entity.getPosition(partial).subtract(launch).dot(velocity.normalize()).coerceAtLeast(0.0)
        val length = minOf(.35 * velocity.length(), travelled.coerceAtLeast(.15)).toFloat()
        val tipForward = (length - travelled).coerceAtLeast(0.0).toFloat()
        val centerShift = tipForward - length / 2f
        val expectedCenter = pose.last().pose().transformPosition(Vector3f(velocity.normalize().scale(centerShift.toDouble()).toVector3f()))
        pose.pushPose()
        try {
            val shift = velocity.normalize().scale(centerShift.toDouble())
            pose.translate(shift.x, shift.y, shift.z)
            pose.mulPose(Axis.YP.rotationDegrees(VehicleVecUtils.getYRotFromVector(velocity).toFloat()))
            pose.mulPose(Axis.XP.rotationDegrees(-VehicleVecUtils.getXRotFromVector(velocity).toFloat()))
            pose.scale(.3f, .3f, length * 2f)
            // Native cubes share a Y=4 centre and have a half-block depth.
            pose.translate(0.0, -4.0 / 16.0, 0.0)
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.energySwirl(TEXTURE, 15f, 15f)),
                light, OverlayTexture.NO_OVERLAY, 1f, .87058824f, .15294118f, 1f)
            if (java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest") && checkedVertices < 24) {
                val vertices = BoundsConsumer()
                model.renderToBuffer(pose, vertices, light, OverlayTexture.NO_OVERLAY, 1f, 1f, 1f, 1f, true)
                val error = vertices.center().distance(expectedCenter)
                check(error < .00003f) { "Cannon shell is off its actual path by $error blocks" }
                checkedVertices++
                if (checkedVertices == 24) LOGGER.info("CANNON_GEOMETRY_PASSED: 24 live shell frames stay on the ballistic path despite a displaced handheld render offset")
            }
        } finally { pose.popPose() }
    }
    private class BoundsConsumer : VertexConsumer {
        private val min = Vector3f(Float.POSITIVE_INFINITY)
        private val max = Vector3f(Float.NEGATIVE_INFINITY)
        override fun vertex(x: Double, y: Double, z: Double): VertexConsumer {
            val p = Vector3f(x.toFloat(), y.toFloat(), z.toFloat()); min.min(p); max.max(p); return this
        }
        fun center() = Vector3f(min).add(max).mul(.5f)
        override fun color(r: Int, g: Int, b: Int, a: Int): VertexConsumer = this
        override fun uv(u: Float, v: Float): VertexConsumer = this
        override fun overlayCoords(u: Int, v: Int): VertexConsumer = this
        override fun uv2(u: Int, v: Int): VertexConsumer = this
        override fun normal(x: Float, y: Float, z: Float): VertexConsumer = this
        override fun endVertex() {}
        override fun defaultColor(r: Int, g: Int, b: Int, a: Int) {}
        override fun unsetDefaultColor() {}
    }
    companion object {
        private val TEXTURE = ResourceLocation("superbwarfare", "textures/bedrock/projectile/small_cannon_shell.png")
        var checkedVertices = 0
            private set
    }
}
