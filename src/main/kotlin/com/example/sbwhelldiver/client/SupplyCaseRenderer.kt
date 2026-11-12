package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.SupplyCaseEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation

class SupplyCaseRenderer(context: EntityRendererProvider.Context): EntityRenderer<SupplyCaseEntity>(context) {
    init { shadowRadius=.3f }
    override fun getTextureLocation(entity: SupplyCaseEntity) = ResourceLocation.fromNamespaceAndPath(MOD_ID,"textures/mesh/supply_case_0.png")
    override fun render(entity: SupplyCaseEntity, yaw: Float, partial: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        pose.pushPose()
        pose.mulPose(Axis.YP.rotationDegrees(180f-yaw))
        MachineMesh.draw("supply_case","",0f,pose,buffers,light)
        pose.popPose()
        super.render(entity,yaw,partial,pose,buffers,light)
    }
}
