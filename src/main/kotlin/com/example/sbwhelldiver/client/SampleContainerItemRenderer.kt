package com.example.sbwhelldiver.client

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack

class SampleContainerItemRenderer:BlockEntityWithoutLevelRenderer(Minecraft.getInstance().blockEntityRenderDispatcher,Minecraft.getInstance().entityModels){
    override fun renderByItem(stack:ItemStack,context:ItemDisplayContext,pose:PoseStack,buffers:MultiBufferSource,light:Int,overlay:Int){
        MachineMesh.drawModelItem("sample_container",pose,buffers,light)
    }
    companion object{val INSTANCE by lazy{SampleContainerItemRenderer()}}
}
