package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.item.WarpPackFlight
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.LightTexture
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.loading.FMLEnvironment
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal object WarpPackVisual {
    private val sphereType by lazy { RenderType.entityTranslucentEmissive(BeaconModel.WHITE) }
    private val coronaType by lazy { RenderType.entityTranslucentEmissive(warpTexture("warp_orb")) }
    private const val LATITUDES = 6
    private const val LONGITUDES = 12
    private val sphere = Array(LATITUDES + 1) { y ->
        val latitude = PI * y / LATITUDES
        Array(LONGITUDES) { x ->
            val longitude = 2.0 * PI * x / LONGITUDES
            Vec3(sin(latitude) * cos(longitude), cos(latitude), sin(latitude) * sin(longitude))
        }
    }

    fun displayEnergy(stack: ItemStack): Int =
        WarpPackFlight.energy(stack, if (FMLEnvironment.dist == Dist.CLIENT) Minecraft.getInstance().level?.gameTime else null)

    fun draw(stack: ItemStack, pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        val ring = MachineMesh.drawWarpPack(pose, buffers, light) ?: return
        if (ShaderPackCompat.shadowPass()) return
        val minecraft = Minecraft.getInstance()
        val time = (minecraft.level?.gameTime?.rem(2400L) ?: 0L).toFloat()
        val tint = WarpPackFlight.phase(displayEnergy(stack)).color
        val radius = .069 + .004 * sin(time * .29)
        pose.pushPose()
        pose.translate(ring.x, ring.y, ring.z)
        pose.mulPose(Axis.YP.rotationDegrees(time * 3f))
        val geometry = buffers.getBuffer(sphereType)
        for (y in 0 until LATITUDES) {
            for (x in 0 until LONGITUDES) {
                val next = (x + 1) % LONGITUDES
                val a = sphere[y][x]
                val b = sphere[y + 1][x]
                val c = sphere[y + 1][next]
                val d = sphere[y][next]
                val brightness = (.52 + .40 * ((a.y + b.y) * .5 + 1.0) / 2.0 +
                    .08 * sin(x * 1.3 + y * .8)).toFloat()
                for (vertex in arrayOf(d, c, b, a)) {
                    warpVertex(geometry, pose.last(), vertex.scale(radius), .5f, .5f, tint, .94f, brightness)
                }
            }
        }

        val glow = buffers.getBuffer(coronaType)
        for (plane in 0..2) {
            pose.pushPose()
            pose.mulPose(Axis.YP.rotationDegrees(plane * 60f))
            pose.mulPose(Axis.XP.rotationDegrees((plane - 1) * 32f))
            val right = Vec3(.117, 0.0, 0.0)
            val up = Vec3(0.0, .117, 0.0)
            warpQuad(glow, pose.last(), Vec3.ZERO, right, up, tint, .72f)
            warpQuad(glow, pose.last(), Vec3.ZERO, right.scale(-1.0), up, tint, .72f)
            pose.popPose()
        }
        pose.popPose()
    }

    val itemRenderer by lazy {
        val minecraft = Minecraft.getInstance()
        object : BlockEntityWithoutLevelRenderer(minecraft.blockEntityRenderDispatcher, minecraft.entityModels) {
            override fun renderByItem(stack: ItemStack, context: ItemDisplayContext, pose: PoseStack,
                                      buffers: MultiBufferSource, light: Int, overlay: Int) {
                pose.pushPose()
                pose.translate(.5, .5, .5)
                pose.scale(1.65f, 1.65f, 1.65f)
                pose.translate(0.0, -.211, 0.0)
                draw(stack, pose, buffers, light)
                pose.popPose()
            }
        }
    }
}

internal fun warpTexture(name: String) = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/effect/$name.png")

internal fun warpQuad(vertices: VertexConsumer, pose: PoseStack.Pose, at: Vec3, right: Vec3, up: Vec3,
                      color: Int, alpha: Float) {
    warpVertex(vertices, pose, at.subtract(right).subtract(up), 0f, 1f, color, alpha)
    warpVertex(vertices, pose, at.add(right).subtract(up), 1f, 1f, color, alpha)
    warpVertex(vertices, pose, at.add(right).add(up), 1f, 0f, color, alpha)
    warpVertex(vertices, pose, at.subtract(right).add(up), 0f, 0f, color, alpha)
}

internal fun warpVertex(vertices: VertexConsumer, pose: PoseStack.Pose, point: Vec3, u: Float, v: Float,
                        color: Int, alpha: Float, brightness: Float = 1f) {
    val red = (color shr 16 and 255) / 255f * brightness
    val green = (color shr 8 and 255) / 255f * brightness
    val blue = (color and 255) / 255f * brightness
    vertices.vertex(pose.pose(), point.x.toFloat(), point.y.toFloat(), point.z.toFloat())
        .color(red, green, blue, alpha).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
        .uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(), 0f, 0f, 1f).endVertex()
}
