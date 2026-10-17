package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.stratagem.BeaconData
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

class BeaconItemRenderer : BlockEntityWithoutLevelRenderer(Minecraft.getInstance().blockEntityRenderDispatcher, Minecraft.getInstance().entityModels) {
    override fun renderByItem(stack: ItemStack, context: ItemDisplayContext, pose: PoseStack, buffers: MultiBufferSource, light: Int, overlay: Int) {
        val selected = BeaconData.selected(stack)
        pose.pushPose()
        // The extracted mesh is centred at y=0.062874, not the item model's 0.5.
        pose.translate(0.5, 0.5, 0.5)
        val size = when (context) {
            ItemDisplayContext.GUI -> 5.2f
            ItemDisplayContext.GROUND -> 2f
            ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND -> 3f
            else -> 3.8f
        }
        pose.scale(size, size, size)
        if (context == ItemDisplayContext.GUI) {
            pose.mulPose(Axis.XP.rotationDegrees(30f))
            pose.mulPose(Axis.YP.rotationDegrees(-25f))
        } else {
            pose.mulPose(Axis.XP.rotationDegrees(90f))
            pose.mulPose(Axis.YP.rotationDegrees(-15f))
        }
        pose.translate(0.000077, -0.062874, -0.001263)
        BeaconModel.render(pose, buffers, light, null, selected != null, selected?.category?.beamColor ?: 0x27BAFF)
        pose.popPose()
    }
    companion object { val INSTANCE by lazy { BeaconItemRenderer() } }
}
