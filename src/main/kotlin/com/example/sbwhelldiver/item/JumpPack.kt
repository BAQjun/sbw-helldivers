package com.example.sbwhelldiver.item

import com.example.sbwhelldiver.client.JumpPackVisual
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions
import java.util.function.Consumer

open class JumpPack(properties: Properties) : BackpackStratagemItem(properties) {
    override fun initializeClient(consumer: Consumer<IClientItemExtensions>) {
        consumer.accept(object : IClientItemExtensions {
            override fun getCustomRenderer() = JumpPackVisual.itemRenderer
        })
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip.add(Component.translatable("tooltip.sbw_helldiver.jump_pack"))
    }
}
