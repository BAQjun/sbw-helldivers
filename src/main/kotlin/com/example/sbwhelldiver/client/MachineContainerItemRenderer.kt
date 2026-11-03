package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.survival.MachineContainerItem
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

/** Closed delivery shells, including dropped items, without spawning an entity. */
class MachineContainerItemRenderer:BlockEntityWithoutLevelRenderer(Minecraft.getInstance().blockEntityRenderDispatcher,Minecraft.getInstance().entityModels) {
    override fun renderByItem(stack:ItemStack,context:ItemDisplayContext,pose:PoseStack,buffers:MultiBufferSource,light:Int,overlay:Int) {
        val item=stack.item as? MachineContainerItem?:return
        MachineMesh.drawContainerItem(item.machine.id,pose,buffers,light)
    }
    companion object {val INSTANCE by lazy{MachineContainerItemRenderer()}}
}

class TacticalTerminalItemRenderer:BlockEntityWithoutLevelRenderer(Minecraft.getInstance().blockEntityRenderDispatcher,Minecraft.getInstance().entityModels) {
    override fun renderByItem(stack:ItemStack,context:ItemDisplayContext,pose:PoseStack,buffers:MultiBufferSource,light:Int,overlay:Int) {
        MachineMesh.drawModelItem("tactical_terminal",pose,buffers,light)
    }
    companion object {val INSTANCE by lazy{TacticalTerminalItemRenderer()}}
}
