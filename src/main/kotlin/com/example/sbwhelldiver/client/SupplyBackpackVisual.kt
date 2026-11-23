package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.item.SupplyBackpack
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.item.ItemStack

internal object SupplyBackpackVisual {
    private val hiddenByCount = (0..SupplyBackpack.CAPACITY).map { count ->
        ((count + 1)..SupplyBackpack.CAPACITY).map { "case_0$it" }.toSet()
    }

    fun draw(stack: ItemStack, pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        MachineMesh.draw("supply_backpack", "", 0f, pose, buffers, light,
            hiddenNodeNames = hiddenByCount[SupplyBackpack.cases(stack)])
    }
}
