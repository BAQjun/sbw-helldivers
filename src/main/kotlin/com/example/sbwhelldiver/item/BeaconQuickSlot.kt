package com.example.sbwhelldiver.item

import com.example.sbwhelldiver.network.BeaconNetwork
import com.example.sbwhelldiver.network.BeaconSlotStatePacket
import com.example.sbwhelldiver.stratagem.BeaconData
import com.example.sbwhelldiver.stratagem.BeaconInput
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent
import net.minecraftforge.network.PacketDistributor
import java.util.Collections
import java.util.WeakHashMap

/** Curios owns the stack. Only the player's active hotbar selection changes. */
object BeaconQuickSlot {
    private val activeSlots = Collections.synchronizedMap(WeakHashMap<Player, Int>())

    fun register() {
        MinecraftForge.EVENT_BUS.addListener(::tick)
        MinecraftForge.EVENT_BUS.addListener<LivingSwapItemsEvent.Hands> { event ->
            val player = event.entity as? Player ?: return@addListener
            if (selected(player) != null) event.isCanceled = true
        }
    }

    fun select(player: ServerPlayer, normalSlot: Int) {
        if (normalSlot !in 0..8 || player.inventory.selected != normalSlot || !player.isAlive || player.isSpectator ||
            player.isPassenger || player.containerMenu !== player.inventoryMenu) return
        val beacon = UtilitySlot.BEACON.equipped(player) ?: return
        BeaconData.ensureIdentity(beacon)
        BeaconData.clear(beacon)
        BeaconInput.reset(player.uuid)
        activeSlots[player] = normalSlot
        sendState(player, normalSlot)
    }

    private fun anchor(player: Player): Int? = activeSlots[player]

    fun updateClient(player: Player, slot: Int) {
        if (slot in 0..8 && player.inventory.selected == slot) activeSlots[player] = slot
        else activeSlots.remove(player)
    }

    private fun sendState(player: ServerPlayer, slot: Int) {
        BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { player }, BeaconSlotStatePacket(slot))
    }

    fun selected(player: Player): ItemStack? {
        val anchor = anchor(player) ?: return null
        if ((!player.level().isClientSide || player.isLocalPlayer) && player.inventory.selected != anchor) return null
        return UtilitySlot.BEACON.equipped(player)
    }

    fun deselect(player: Player) {
        if (anchor(player) == null) return
        activeSlots.remove(player)
        if (player is ServerPlayer) {
            UtilitySlot.BEACON.equipped(player)?.let(BeaconData::clear)
            BeaconInput.reset(player.uuid)
            sendState(player, -1)
        }
    }

    fun removeSelected(player: Player): ItemStack? {
        val selected = selected(player) ?: return null
        val removed = UtilityCurios.takeBeacon(player, selected)
        deselect(player)
        return removed
    }

    fun replaceSelected(player: Player, replacement: ItemStack): Boolean {
        if (player.level().isClientSide && !player.isLocalPlayer) return false
        val beacon = selected(player) ?: return false
        if (!replacement.isEmpty && !UtilitySlot.BEACON.accepts(replacement)) {
            deselect(player)
            return false
        }
        if (beacon !== replacement) UtilityCurios.replaceBeacon(player, beacon, replacement)
        if (replacement.isEmpty) deselect(player)
        return true
    }

    private fun tick(event: TickEvent.PlayerTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        val player = event.player as? ServerPlayer ?: return
        if (anchor(player) != null && (!player.isAlive || player.isSpectator || player.isPassenger || selected(player) == null))
            deselect(player)
    }
}
