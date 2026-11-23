package com.example.sbwhelldiver.item

import com.example.sbwhelldiver.client.WarpPackVisual
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions
import java.util.function.Consumer

open class WarpPack(properties: Properties) : BackpackStratagemItem(properties) {
    override fun isBarVisible(stack: ItemStack) = WarpPackVisual.displayEnergy(stack) < WarpPackFlight.FULL

    override fun getBarWidth(stack: ItemStack) =
        (13f * WarpPackVisual.displayEnergy(stack) / WarpPackFlight.FULL).roundToInt().coerceIn(0, 13)

    override fun getBarColor(stack: ItemStack) = WarpPackFlight.phase(WarpPackVisual.displayEnergy(stack)).color

    override fun initializeClient(consumer: Consumer<IClientItemExtensions>) {
        consumer.accept(object : IClientItemExtensions {
            override fun getCustomRenderer() = WarpPackVisual.itemRenderer
        })
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip.add(Component.translatable("tooltip.sbw_helldiver.warp_pack"))
    }
}
