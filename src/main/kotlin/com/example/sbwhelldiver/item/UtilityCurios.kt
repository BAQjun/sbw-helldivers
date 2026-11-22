package com.example.sbwhelldiver.item

import com.example.sbwhelldiver.survival.SampleContainerItem
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.common.util.TriState
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import top.theillusivec4.curios.api.CuriosApi
import top.theillusivec4.curios.api.event.CurioCanEquipEvent

enum class UtilitySlot(val id: String) {
    BEACON("sbw_stratagem_beacon"),
    SAMPLES("sbw_sample_container");

    fun accepts(stack: ItemStack) = !stack.isEmpty && when (this) {
        BEACON -> stack.item is BeaconItem
        SAMPLES -> stack.item is SampleContainerItem
    }

    fun equipped(player: Player): ItemStack? =
        if (StratagemBackpacks.usesCurios) UtilityCurios.equipped(player, this) else null
}

/** Main and off hand retain their network ordinals. */
enum class ItemSource(val hand: InteractionHand?) {
    MAIN_HAND(InteractionHand.MAIN_HAND),
    OFF_HAND(InteractionHand.OFF_HAND),
    CURIO(null);

    fun stack(player: Player, slot: UtilitySlot): ItemStack =
        hand?.let(player::getItemInHand) ?: slot.equipped(player) ?: ItemStack.EMPTY

    fun inventoryIndex(player: Player): Int? = when (this) {
        MAIN_HAND -> player.inventory.selected
        OFF_HAND -> 40
        CURIO -> null
    }

    companion object {
        fun of(hand: InteractionHand) = if (hand == InteractionHand.MAIN_HAND) MAIN_HAND else OFF_HAND
    }
}

/** Keep all Curios API class references in the optional integration. */
internal object UtilityCurios {
    fun register() {
        NeoForge.EVENT_BUS.addListener(::restrictUtilitySlots)
        NeoForge.EVENT_BUS.addListener(::tick)
    }

    fun equipped(player: Player, slot: UtilitySlot): ItemStack? {
        val stacks = slots(player, slot) ?: return null
        for (index in 0 until stacks.slots) {
            val stack = stacks.getStackInSlot(index)
            if (slot.accepts(stack)) return stack
        }
        return null
    }

    private fun slots(player: Player, slot: UtilitySlot) =
        CuriosApi.getCuriosInventory(player).orElse(null)?.getStacksHandler(slot.id)?.orElse(null)?.stacks

    fun takeBeacon(player: ServerPlayer): ItemStack? {
        val slots = slots(player, UtilitySlot.BEACON) ?: return null
        val index = (0 until slots.slots).firstOrNull { UtilitySlot.BEACON.accepts(slots.getStackInSlot(it)) } ?: return null
        return slots.extractItem(index, 1, false).takeUnless(ItemStack::isEmpty)
    }

    fun returnBeacon(player: ServerPlayer, beacon: ItemStack): Boolean {
        val slots = slots(player, UtilitySlot.BEACON) ?: return false
        val index = (0 until slots.slots).firstOrNull { slots.getStackInSlot(it).isEmpty } ?: return false
        slots.setStackInSlot(index, beacon)
        return true
    }

    private fun restrictUtilitySlots(event: CurioCanEquipEvent) {
        val expected = UtilitySlot.entries.firstOrNull { it.accepts(event.stack) }
        val context = event.slotContext
        if (expected != null) {
            if (context.identifier() != expected.id || context.cosmetic() || context.entity() !is Player)
                event.equipResult = TriState.FALSE
        } else if (UtilitySlot.entries.any { it.id == context.identifier() }) {
            event.equipResult = TriState.FALSE
        }
    }

    private fun tick(event: PlayerTickEvent.Pre) {
        val player = event.entity as? ServerPlayer ?: return
        if (player.level().gameTime % 10L == 0L) {
            val stack = equipped(player, UtilitySlot.SAMPLES) ?: return
            (stack.item as SampleContainerItem).collectAutomatically(player, stack)
        }
    }
}
