package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.item.SupplyBackpack
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

internal object SupplyBackpackVisual {
    private val hiddenByCount = (0..SupplyBackpack.CAPACITY).map { count ->
        ((count + 1)..SupplyBackpack.CAPACITY).map { "case_0$it" }.toSet()
    }

    fun draw(stack: ItemStack, pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        MachineMesh.draw("supply_backpack", "", 0f, pose, buffers, light,
            hiddenNodeNames = hiddenByCount[SupplyBackpack.cases(stack)])
    }

    val itemRenderer by lazy {
        val minecraft = Minecraft.getInstance()
        object : BlockEntityWithoutLevelRenderer(minecraft.blockEntityRenderDispatcher, minecraft.entityModels) {
            override fun renderByItem(stack: ItemStack, context: ItemDisplayContext, pose: PoseStack,
                                      buffers: MultiBufferSource, light: Int, overlay: Int) {
                pose.pushPose()
                pose.translate(.5, .5, .5)
                pose.scale(1.65f, 1.65f, 1.65f)
                pose.translate(0.0, -.0965, -.0195)
                draw(stack, pose, buffers, light)
                pose.popPose()
            }
        }
    }
}
