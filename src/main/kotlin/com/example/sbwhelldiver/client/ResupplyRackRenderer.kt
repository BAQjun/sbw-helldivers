package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.entity.ResupplyHellpodEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource

object ResupplyRackRenderer {
    fun render(pod: ResupplyHellpodEntity, partial: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        val rack = ItemDeliveryRackRenderer.sample(pod, partial) ?: return
        val offsets = mutableMapOf("rack_lift" to rack.liftY,
            "front_shield" to rack.liftY + rack.frontShutter * (25.7f/36f),
            "rear_shield" to rack.liftY + rack.rearShutter * (25.7f/36f))
        for (i in 0..3) offsets["case_0${i+1}"] = rack.liftY
        val hidden = (0..3).filterNot(pod::hasCase).map { "case_0${it+1}" }.toSet()
        MachineMesh.draw("resupply_rack", "deployed", 0f, pose, buffers, light, nodeYOffsets=offsets, hiddenNodeNames=hidden)
    }
}
