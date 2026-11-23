package com.example.sbwhelldiver.item

import com.example.sbwhelldiver.survival.ResupplyAmmo
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.CustomData

open class SupplyBackpack(properties: Properties) : BackpackStratagemItem(properties) {
    override fun initializeClient(consumer: java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions>) {
        consumer.accept(object : net.neoforged.neoforge.client.extensions.common.IClientItemExtensions {
            override fun getCustomRenderer() = com.example.sbwhelldiver.client.SupplyBackpackVisual.itemRenderer
        })
    }

    override fun appendHoverText(stack: ItemStack, context: TooltipContext, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip.add(Component.translatable("tooltip.sbw_helldiver.supply_backpack_cases", cases(stack), CAPACITY))
    }

    companion object {
        const val CAPACITY = 4
        private const val CASES = "SupplyCases"

        fun cases(stack: ItemStack): Int {
            val tag = stack.get(DataComponents.CUSTOM_DATA)?.copyTag() ?: return CAPACITY
            return if (tag.contains(CASES)) tag.getInt(CASES).coerceIn(0, CAPACITY) else CAPACITY
        }

        private fun setCases(stack: ItemStack, count: Int) {
            CustomData.update(DataComponents.CUSTOM_DATA, stack) { it.putInt(CASES, count) }
        }

        internal fun useCase(player: ServerPlayer, stack: ItemStack) {
            val count = cases(stack)
            if (count == 0) {
                player.displayClientMessage(Component.translatable("message.sbw_helldiver.supply_backpack_empty"), true)
                return
            }
            setCases(stack, count - 1)
            ResupplyAmmo.refill(player, refillGuardDogs = false)
            ResupplyAmmo.playPickupSound(player.level(), player.x, player.y, player.z)
            player.displayClientMessage(Component.translatable("tooltip.sbw_helldiver.supply_backpack_cases", count - 1, CAPACITY), true)
        }

        internal fun restockEquipped(player: ServerPlayer) {
            val stack = StratagemBackpacks.equipped(player)?.takeIf { it.item is SupplyBackpack } ?: return
            val count = cases(stack)
            if (count < CAPACITY) setCases(stack, count + 1)
        }
    }
}
