package com.example.sbwhelldiver.item

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.TriState
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import top.theillusivec4.curios.api.CuriosApi
import top.theillusivec4.curios.api.SlotContext
import top.theillusivec4.curios.api.event.CurioCanEquipEvent
import top.theillusivec4.curios.api.type.capability.ICurioItem

internal object BackpackCurios {
    private const val BACK = "back"

    fun register() {
        NeoForge.EVENT_BUS.addListener(::restrictBackpack)
        NeoForge.EVENT_BUS.addListener(::returnForcedDuplicates)
    }

    fun createGuardDog(properties: Item.Properties, laser: Boolean, stun: Boolean, flame: Boolean, gas: Boolean): GuardDogBackpack = CurioGuardDogBackpack(properties, laser, stun, flame, gas)

    private fun slots(player: Player) =
        CuriosApi.getCuriosInventory(player).orElse(null)?.getStacksHandler(BACK)?.orElse(null)?.stacks

    fun equipped(player: Player): ItemStack? {
        val slots = slots(player) ?: return null
        for (index in 0 until slots.slots) {
            val stack = slots.getStackInSlot(index)
            if (stack.`is`(StratagemBackpacks.tag)) return stack
        }
        return null
    }

    private fun restrictBackpack(event: CurioCanEquipEvent) {
        if (!event.stack.`is`(StratagemBackpacks.tag)) return
        val slot = event.slotContext
        val player = slot.entity() as? Player
        val back = player?.let(::slots)
        if (slot.identifier() != BACK || slot.cosmetic() || player == null || back == null ||
            StratagemBackpacks.chest(player) != null) {
            event.equipResult = TriState.FALSE
            return
        }
        for (index in 0 until back.slots) {
            if (index != slot.index() && back.getStackInSlot(index).`is`(StratagemBackpacks.tag)) {
                event.equipResult = TriState.FALSE
                return
            }
        }
    }

    private fun returnForcedDuplicates(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.tickCount % 10 != 0) return
        val slots = slots(player) ?: return
        var equipped = false
        for (index in 0 until slots.slots) {
            val stack = slots.getStackInSlot(index)
            if (!stack.`is`(StratagemBackpacks.tag)) continue
            if (equipped) {
                slots.setStackInSlot(index, ItemStack.EMPTY)
                player.inventory.placeItemBackInInventory(stack)
            }
            equipped = true
        }
        val chest = StratagemBackpacks.chest(player)
        if (equipped && chest != null) {
            player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY)
            player.inventory.placeItemBackInInventory(chest)
        }
    }
}

private class CurioGuardDogBackpack(properties: Item.Properties, laser: Boolean, stun: Boolean, flame: Boolean, gas: Boolean) : GuardDogBackpack(properties, laser, stun, flame, gas), ICurioItem {
    override fun canEquipFromUse(slotContext: SlotContext, stack: ItemStack) = true

    override fun curioTick(slotContext: SlotContext, stack: ItemStack) {
        val player = slotContext.entity() as? ServerPlayer ?: return
        service(stack, player.serverLevel(), player)
    }
}
