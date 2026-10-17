package com.example.sbwhelldiver.item

import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

/** Physical loadout cards; only an activated beacon can call a drop. */
class StratagemItem(properties: Properties, val dropEntityId: ResourceLocation) : Item(properties) {
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        if (!level.isClientSide) player.displayClientMessage(Component.translatable("item.sbw_helldiver.stratagem_card_hint"), true)
        return InteractionResultHolder.fail(player.getItemInHand(hand))
    }
}
