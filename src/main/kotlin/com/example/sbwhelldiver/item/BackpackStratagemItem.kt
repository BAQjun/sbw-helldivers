package com.example.sbwhelldiver.item

import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Equipable
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

abstract class BackpackStratagemItem(properties: Properties) : Item(properties), Equipable {
    override fun getEquipmentSlot() = EquipmentSlot.CHEST

    override fun canEquip(stack: ItemStack, slot: EquipmentSlot, entity: LivingEntity) =
        slot == EquipmentSlot.CHEST && entity is Player && StratagemBackpacks.back(entity) == null

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (!canEquip(stack, EquipmentSlot.CHEST, player)) return InteractionResultHolder.fail(stack)
        return swapWithEquipmentSlot(this, level, player, hand)
    }
}
