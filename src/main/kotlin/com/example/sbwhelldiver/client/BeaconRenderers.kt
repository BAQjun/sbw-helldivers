package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.entity.StratagemBeacon
import com.example.sbwhelldiver.entity.StratagemMarker
import com.example.sbwhelldiver.stratagem.BeaconData
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

class BeaconProjectileRenderer(context: EntityRendererProvider.Context) : EntityRenderer<StratagemBeacon>(context) {
    init { shadowRadius = 0.12f }
    override fun getTextureLocation(entity: StratagemBeacon) = BeaconModel.TEXTURE
    override fun render(entity: StratagemBeacon, yaw: Float, partial: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        pose.pushPose()
        // Both collision and visual rotation use the centre of the closed ball.
        pose.translate(0.0, entity.ballRadius, 0.0)
        pose.mulPose(Quaternionf(entity.previousRoll).slerp(entity.roll, partial))
        pose.scale(2.2f, 2.2f, 2.2f)
        pose.translate(0.000077, -0.062874, -0.001263)
        val color = BeaconData.selected(entity.item)?.category?.beamColor ?: 0x27BAFF
        BeaconModel.render(pose, buffers, light, null, !entity.deploymentFailed, color)
        pose.popPose()
        super.render(entity, yaw, partial, pose, buffers, light)
    }
}

class BeaconMarkerRenderer(context: EntityRendererProvider.Context) : EntityRenderer<StratagemMarker>(context) {
    override fun getTextureLocation(entity: StratagemMarker) = BeaconModel.TEXTURE
    override fun shouldRender(entity: StratagemMarker, frustum: Frustum, x: Double, y: Double, z: Double) =
        frustum.isVisible(entity.boundingBox.expandTowards(0.0, com.example.sbwhelldiver.entity.AbstractHellpodEntity.descentSpawnHeight().toDouble(), 0.0).inflate(1.0))
    override fun render(entity: StratagemMarker, yaw: Float, partial: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        val seconds = (entity.elapsedTicks + partial) / 20f
        pose.pushPose()
        val normal = entity.face.normal
        pose.mulPose(Quaternionf().rotationTo(Vector3f(0f, 1f, 0f), Vector3f(normal.x.toFloat(), normal.y.toFloat(), normal.z.toFloat())))
        pose.scale(2.2f, 2.2f, 2.2f)
        BeaconModel.render(pose, buffers, light, seconds, true, entity.color)
        pose.popPose()
        beam(entity, pose, buffers, (seconds / 0.7f).coerceIn(0f, 1f))
        super.render(entity, yaw, partial, pose, buffers, light)
    }
    private fun beam(entity: StratagemMarker, pose: PoseStack, buffers: MultiBufferSource, strength: Float) {
        if (!entity.hasBeaconBeam || strength <= 0) return
        val vertex = buffers.getBuffer(RenderType.lightning())
        val transform = pose.last().pose()
        val r = ((entity.color shr 16) and 255) / 255f
        val g = ((entity.color shr 8) and 255) / 255f
        val b = (entity.color and 255) / 255f
        val height = com.example.sbwhelldiver.entity.AbstractHellpodEntity.descentSpawnHeight().toFloat()
        for ((radius, alpha) in listOf(0.018f to 0.9f, 0.045f to 0.32f, 0.10f to 0.10f)) {
            for (i in 0..7) {
                val a = i * Math.PI / 4
                val c = (i + 1) * Math.PI / 4
                val x1 = (cos(a) * radius).toFloat(); val z1 = (sin(a) * radius).toFloat()
                val x2 = (cos(c) * radius).toFloat(); val z2 = (sin(c) * radius).toFloat()
                vertex.vertex(transform, x1, 0.2f, z1).color(r, g, b, alpha * strength).endVertex()
                vertex.vertex(transform, x1, height, z1).color(r, g, b, alpha * strength).endVertex()
                vertex.vertex(transform, x2, height, z2).color(r, g, b, alpha * strength).endVertex()
                vertex.vertex(transform, x2, 0.2f, z2).color(r, g, b, alpha * strength).endVertex()
            }
        }
    }
}
