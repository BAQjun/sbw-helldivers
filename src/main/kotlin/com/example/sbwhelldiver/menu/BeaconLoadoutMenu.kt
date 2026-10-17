package com.example.sbwhelldiver.menu

import com.example.sbwhelldiver.init.ModMenus
import com.example.sbwhelldiver.item.BeaconItem
import com.example.sbwhelldiver.stratagem.BeaconData
import com.example.sbwhelldiver.stratagem.Stratagems
import net.minecraft.world.InteractionHand
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class BeaconLoadoutMenu(id: Int, val inventory: Inventory, val hand: InteractionHand) : AbstractContainerMenu(ModMenus.BEACON_LOADOUT.get(), id) {
    private val source = inventory.player.getItemInHand(hand)
    private val sourceSlot = if (hand == InteractionHand.MAIN_HAND) inventory.selected else 40
    private var loading = true
    val cards = object : SimpleContainer(4) {
        override fun setChanged() {
            super.setChanged()
            if (!loading && !inventory.player.level().isClientSide && stillValid(inventory.player)) {
                BeaconData.setItems(source, (0..3).map(::getItem))
                inventory.setChanged()
            }
        }
    }
    init {
        BeaconData.storedItems(source).forEachIndexed(cards::setItem)
        for (index in 0..3) addSlot(object : Slot(cards, index, 27 + 56 * index, 40) {
            override fun getMaxStackSize() = 1
            override fun mayPlace(stack: ItemStack): Boolean {
                val def = Stratagems.fromItem(stack) ?: return false
                return (0..3).none { it != index && Stratagems.fromItem(cards.getItem(it))?.id == def.id }
            }
        })
        for (row in 0..2) for (col in 0..8) addSlot(playerSlot(col + row * 9 + 9, 44 + col * 18, 101 + row * 18))
        for (col in 0..8) addSlot(playerSlot(col, 44 + col * 18, 159))
        loading = false
    }
    private fun playerSlot(index: Int, x: Int, y: Int) = object : Slot(inventory, index, x, y) {
        override fun mayPickup(player: Player) = index != sourceSlot && super.mayPickup(player)
        override fun mayPlace(stack: ItemStack) = index != sourceSlot && super.mayPlace(stack)
    }
    override fun stillValid(player: Player) = player.isAlive && inventory.getItem(sourceSlot) === source && source.item is BeaconItem
    override fun clicked(slotId: Int, button: Int, type: ClickType, player: Player) {
        if (type == ClickType.SWAP && button == sourceSlot) return
        super.clicked(slotId, button, type, player)
    }
    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        val slot = slots.getOrNull(index) ?: return ItemStack.EMPTY
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY
        val item = slot.item
        val copy = item.copy()
        if (index < 4) {
            if (!moveItemStackTo(item, 4, slots.size, true)) return ItemStack.EMPTY
        } else {
            if (Stratagems.fromItem(item) == null || !moveItemStackTo(item, 0, 4, false)) return ItemStack.EMPTY
        }
        if (item.isEmpty) slot.setByPlayer(ItemStack.EMPTY) else slot.setChanged()
        slot.onTake(player, item)
        return copy
    }
}
