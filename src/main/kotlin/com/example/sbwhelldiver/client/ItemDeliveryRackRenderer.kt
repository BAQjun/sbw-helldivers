package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.ItemDeliveryHellpodEntity
import com.example.sbwhelldiver.item.GuardDogBackpack
import com.example.sbwhelldiver.entity.ItemDeliveryHellpodEntity.Side
import com.example.sbwhelldiver.entity.ItemDeliveryHellpodEntity.Companion.LIFT_START
import com.example.sbwhelldiver.entity.ItemDeliveryHellpodEntity.Companion.LIFT_END
import com.example.sbwhelldiver.entity.ItemDeliveryHellpodEntity.Companion.LOWER_MAIN_START
import com.example.sbwhelldiver.entity.ItemDeliveryHellpodEntity.Companion.LOWER_TICKS
import com.example.sbwhelldiver.entity.ItemDeliveryHellpodEntity.Companion.SHUTTER_START
import com.example.sbwhelldiver.entity.ItemDeliveryHellpodEntity.Companion.SHUTTER_TICKS
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import java.util.WeakHashMap

object ItemDeliveryRackRenderer {
    private const val MODEL = "item_delivery_rack"
    private const val SHIELD_TRAVEL = 36f / 16f
    private const val LIFT_TRAVEL = 41.1f / 16f
    private val animationTimes = WeakHashMap<ItemDeliveryHellpodEntity, Double>()

    data class RackPose(val liftY: Float, val frontShutter: Float, val rearShutter: Float)

    fun sample(pod: ItemDeliveryHellpodEntity, partial: Float): RackPose? {
        val phase = pod.phase
        if (phase != Phase.DEPLOYING && phase != Phase.ACTIVE && phase != Phase.RETRACTING) return null
        val now = pod.level().gameTime + partial.toDouble()
        // Corrections to the client world clock can pause motion briefly, but never reverse it.
        val time = maxOf(now, animationTimes[pod] ?: now)
        animationTimes[pod] = time
        val age = (time - pod.phaseStartTime).toFloat().coerceAtLeast(0f)
        val lift = when (phase) {
            Phase.DEPLOYING -> {
                val t = ((age - LIFT_START) / (LIFT_END - LIFT_START)).coerceIn(0f, 1f)
                -LIFT_TRAVEL * (1f - t * (1.1f + t * (.8f - .9f * t)))
            }
            Phase.RETRACTING -> {
                // Keep moving through the start of the fast drop so the lift never pauses.
                val travel = (age / LOWER_TICKS).coerceIn(0f, 1f)
                val fastDrop = ((age - LOWER_MAIN_START) / (LOWER_TICKS - LOWER_MAIN_START)).coerceIn(0f, 1f)
                -LIFT_TRAVEL * (.16f * travel + .84f * fastDrop * fastDrop * (2f - fastDrop))
            }
            else -> 0f
        }

        fun shutter(side: Side): Float {
            val elapsed = pod.shieldAge(side, time) - SHUTTER_START
            return SHIELD_TRAVEL * smooth(elapsed / (SHUTTER_TICKS - SHUTTER_START))
        }
        return RackPose(lift, shutter(Side.FRONT), shutter(Side.REAR))
    }

    private fun smooth(progress: Float): Float {
        val t = progress.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun render(pod: ItemDeliveryHellpodEntity, partial: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        val rack = sample(pod, partial) ?: return
        val offsets = mapOf(
            "rack_lift" to rack.liftY,
            Side.FRONT.shield to (rack.liftY + rack.frontShutter),
            Side.REAR.shield to (rack.liftY + rack.rearShutter),
        )
        MachineMesh.draw(MODEL, "deployed", 0f, pose, buffers, light, nodeYOffsets = offsets)
        for (side in Side.entries) {
            val stack = pod.item(side)
            if (!stack.isEmpty) renderItem(pod, side, stack, rack.liftY, pose, buffers, light)
        }
    }

    private fun renderItem(pod: ItemDeliveryHellpodEntity, side: Side, stack: ItemStack, lift: Float,
        pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        pose.pushPose()
        try {
            val mount = side.mount
            pose.translate(mount.x, mount.y + lift, mount.z)
            if (side == Side.REAR) pose.mulPose(Axis.YP.rotationDegrees(180f))
            if (stack.item is GuardDogBackpack) {
                pose.scale(.85f, .85f, .85f)
                val reduced = ModelLod.reduced(pod.distanceToSqr(Minecraft.getInstance().gameRenderer.mainCamera.position))
                GuardDogVisual.drawPack(pose, buffers, light, true, spares = GuardDogBackpack.spareMags(stack),
                    emptyFold = GuardDogBackpack.emptyFold(stack), laser = GuardDogBackpack.isLaser(stack),
                    stun = GuardDogBackpack.isStun(stack), flameDog = GuardDogBackpack.isFlame(stack),
                    gasDog = GuardDogBackpack.isGas(stack), reducedModel = reduced)
                return
            }
            pose.mulPose(Axis.ZP.rotationDegrees(90f))
            pose.scale(.58f, .58f, .58f)
            Minecraft.getInstance().itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, light,
                OverlayTexture.NO_OVERLAY, pose, buffers, pod.level(), pod.id)
        } finally {
            pose.popPose()
        }
    }
}
