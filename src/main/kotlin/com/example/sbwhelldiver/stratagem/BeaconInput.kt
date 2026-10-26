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
    private data class Session(val beacon: UUID, val prefix: MutableList<Int>, var lastTick: Long, var countAtTick: Int = 0, var stage:Int=0)
    private val sessions = mutableMapOf<UUID, Session>()
    private data class HeldState(val slot: Int, val main: ItemStack, val off: ItemStack)
    private val heldStates = mutableMapOf<UUID, HeldState>()
    private fun menu(player:ServerPlayer,stage:Int){
        if(player !is net.minecraftforge.common.util.FakePlayer) com.example.sbwhelldiver.network.BeaconNetwork.CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with{player},com.example.sbwhelldiver.network.DestroyerMenuPacket(stage))
    }
    fun reset(player: UUID) { val old=sessions.remove(player);if(old?.stage!=null&&old.stage!=0) net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()?.playerList?.getPlayer(player)?.let{menu(it,0)} }
    fun menuStage(player:UUID)=sessions[player]?.stage?:0
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
        if (!Stratagems.dimensionAllowed(player.level().dimension().location().toString())) {
            reset(player.uuid)
            player.playNotifySound(ModSounds.EVENTS.getValue("beacon_input_error").get(), SoundSource.PLAYERS, 1f, 1f)
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
        if(session.stage!=0){
            val heldOrbital=SuperDestroyers.hasOrbital(player)
            if(!heldOrbital){reset(player.uuid);return false}
            val chooseBearing=session.stage==2&&direction==Stratagems.RIGHT
            val accepted=when(session.stage){
                1->SuperDestroyers.move(player,direction)
                2->when(direction){Stratagems.LEFT->SuperDestroyers.depart(player);Stratagems.RIGHT->{session.stage=1;menu(player,1);true};else->false}
                else->false
            }
            if(accepted){
                player.playNotifySound(ModSounds.EVENTS.getValue("beacon_ready").get(),SoundSource.PLAYERS,1f,1f)
                if(!chooseBearing)reset(player.uuid)
            }else player.playNotifySound(ModSounds.EVENTS.getValue("beacon_input_error").get(),SoundSource.PLAYERS,1f,1f)
            return accepted
        }
        session.prefix.add(direction)
        val base=Stratagems.commands(BeaconData.loadout(stack), StratagemCooldowns.equipped(player).isNotEmpty())
        val commands=if(SuperDestroyers.hasOrbital(player)&&Stratagems.destroyerCall !in base)listOf(Stratagems.destroyerCall)+base else base
        val candidates = Stratagems.matching(commands, session.prefix)
            .filter { StratagemCooldowns.available(player, it.id) }
        if (candidates.isEmpty()) {
            session.prefix.clear()
            player.playNotifySound(ModSounds.EVENTS.getValue("beacon_input_error").get(), SoundSource.PLAYERS, 1f, 1f)
            return false
        }
        // The reviewed recordings follow input progress; each event has one fixed sample.
        val step = session.prefix.size.coerceIn(1, 8)
        player.playNotifySound(ModSounds.EVENTS.getValue("beacon_arrow_input_$step").get(), SoundSource.PLAYERS, 1f, 1f)
        val complete = candidates.firstOrNull { it.code.size == session.prefix.size } ?: return false
        if(complete==Stratagems.destroyerCall){
            session.prefix.clear();session.countAtTick=0;session.stage=if(SuperDestroyers.state(player).anchor==null)1 else 2
            menu(player,session.stage)
            player.playNotifySound(ModSounds.EVENTS.getValue("beacon_ready").get(),SoundSource.PLAYERS,1f,1f)
            return true
        }
        if (complete == Stratagems.eagleRearm) {
            if (!StratagemCooldowns.rearm(player)) { reset(player.uuid); return false }
        } else BeaconData.activate(stack, complete)
        player.inventory.setChanged()
        player.containerMenu.broadcastChanges()
        player.playNotifySound(ModSounds.EVENTS.getValue("beacon_ready").get(), SoundSource.PLAYERS, 1f, 1f)
        reset(player.uuid)
        return true
    }
}
