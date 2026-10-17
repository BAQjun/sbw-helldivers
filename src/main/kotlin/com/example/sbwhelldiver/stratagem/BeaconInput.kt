package com.example.sbwhelldiver.stratagem

import com.example.sbwhelldiver.init.ModSounds
import com.example.sbwhelldiver.item.BeaconItem
import com.example.sbwhelldiver.menu.BeaconLoadoutMenu
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import java.util.UUID

/** The server chooses the stratagem from the actual item, never from a client entity ID. */
object BeaconInput {
    private data class Session(val beacon: UUID, val prefix: MutableList<Int>, var lastTick: Long, var countAtTick: Int = 0)
    private val sessions = mutableMapOf<UUID, Session>()
    private data class HeldState(val slot: Int, val main: ItemStack, val off: ItemStack)
    private val heldStates = mutableMapOf<UUID, HeldState>()
    fun reset(player: UUID) { sessions.remove(player) }
    fun logout(player: UUID) { reset(player); heldStates.remove(player) }
    /** Also run before packets: a hand switch must invalidate stale activation on the server. */
    fun observeHands(player: ServerPlayer) {
        if (player.isPassenger) reset(player.uuid)
        val now = HeldState(player.inventory.selected, player.mainHandItem, player.offhandItem)
        val before = heldStates.put(player.uuid, now)
        if (before != null && (before.slot != now.slot || before.main !== now.main || before.off !== now.off)) {
            reset(player.uuid)
            var changed = false
            for (i in 0 until player.inventory.containerSize) {
                val stack = player.inventory.getItem(i)
                if (stack.item is BeaconItem && stack.tag?.contains(BeaconData.SELECTED) == true) {
                    BeaconData.clear(stack); changed = true
                }
            }
            if (changed) { player.inventory.setChanged(); player.containerMenu.broadcastChanges() }
        }
    }
    fun accept(player: ServerPlayer, hand: InteractionHand, identity: UUID, direction: Int): Boolean {
        observeHands(player)
        val stack = player.getItemInHand(hand)
        if (stack.item !is BeaconItem || BeaconData.identity(stack) != identity || player.isSpectator || player.isPassenger || player.containerMenu is BeaconLoadoutMenu) return false
        if (direction == -1) { reset(player.uuid); return false }
        if (direction == -2) {
            reset(player.uuid)
            BeaconData.clear(stack)
            player.inventory.setChanged(); player.containerMenu.broadcastChanges()
            return false
        }
        if (direction !in 0..3 || BeaconData.selected(stack) != null) return false
        val tick = player.level().gameTime
        var session = sessions[player.uuid]
        if (session == null || session.beacon != identity || tick - session.lastTick > 100) {
            session = Session(identity, mutableListOf(), tick)
            sessions[player.uuid] = session
        }
        if (session.lastTick != tick) session.countAtTick = 0
        if (++session.countAtTick > 8) return false
        session.lastTick = tick
        session.prefix.add(direction)
        val candidates = Stratagems.matching(BeaconData.loadout(stack), session.prefix)
        if (candidates.isEmpty()) {
            session.prefix.clear()
            player.playNotifySound(ModSounds.EVENTS.getValue("beacon_input_error").get(), SoundSource.PLAYERS, 1f, 1f)
            return false
        }
        // The reviewed recordings follow input progress; each event has one fixed sample.
        val step = session.prefix.size.coerceIn(1, 8)
        player.playNotifySound(ModSounds.EVENTS.getValue("beacon_arrow_input_$step").get(), SoundSource.PLAYERS, 1f, 1f)
        val complete = candidates.firstOrNull { it.code.size == session.prefix.size } ?: return false
        BeaconData.activate(stack, complete)
        player.inventory.setChanged()
        player.containerMenu.broadcastChanges()
        player.playNotifySound(ModSounds.EVENTS.getValue("beacon_ready").get(), SoundSource.PLAYERS, 1f, 1f)
        reset(player.uuid)
        return true
    }
}
