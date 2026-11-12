package com.example.sbwhelldiver.item



import net.minecraft.server.level.ServerPlayer

import net.minecraft.world.entity.EquipmentSlot

import net.minecraft.world.entity.player.Player

import net.minecraft.world.item.Item

import net.minecraft.world.item.ItemStack

import net.minecraftforge.common.MinecraftForge

import net.minecraftforge.eventbus.api.Event.Result

import net.minecraftforge.event.TickEvent

import top.theillusivec4.curios.api.CuriosApi

import top.theillusivec4.curios.api.SlotContext

import top.theillusivec4.curios.api.event.CurioEquipEvent

import top.theillusivec4.curios.api.type.capability.ICurioItem



internal object BackpackCurios {

    private const val BACK = "back"



    fun register() {

        MinecraftForge.EVENT_BUS.addListener(::restrictBackpack)

        MinecraftForge.EVENT_BUS.addListener(::returnForcedDuplicates)

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



    private fun restrictBackpack(event: CurioEquipEvent) {

        if (!event.stack.`is`(StratagemBackpacks.tag)) return

        val slot = event.slotContext

        val player = slot.entity() as? Player

        val back = player?.let(::slots)

        if (slot.identifier() != BACK || slot.cosmetic() || player == null || back == null ||

            StratagemBackpacks.chest(player) != null) {

            event.result = Result.DENY

            return

        }

        for (index in 0 until back.slots) {

            if (index != slot.index() && back.getStackInSlot(index).`is`(StratagemBackpacks.tag)) {

                event.result = Result.DENY

                return

            }

        }

    }



    private fun returnForcedDuplicates(event: TickEvent.PlayerTickEvent) {
        if(event.phase != TickEvent.Phase.END) return

        val player = event.player as? ServerPlayer ?: return

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
