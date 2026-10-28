package com.example.sbwhelldiver.stratagem

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.item.BeaconItem
import com.example.sbwhelldiver.network.BeaconNetwork
import com.example.sbwhelldiver.network.CooldownSnapshotPacket
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.saveddata.SavedData
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import com.example.sbwhelldiver.network.PacketTargets
import java.util.UUID

class CooldownSavedData : SavedData() {
    val players = mutableMapOf<UUID, StratagemCooldownState>()
    override fun save(n:CompoundTag,registries:net.minecraft.core.HolderLookup.Provider): CompoundTag = n.also {
        it.put("Players", ListTag().also { list -> players.forEach { (id, state) ->
            list.add(state.save().also { entry -> entry.putUUID("Player", id) })
        } })
    }
    companion object {
        fun get(server: MinecraftServer): CooldownSavedData = server.overworld().dataStorage.computeIfAbsent(SavedData.Factory(::CooldownSavedData,{ n, _ ->
            CooldownSavedData().also { data -> for (raw in n.getList("Players", Tag.TAG_COMPOUND.toInt())) {
                val entry = raw as CompoundTag
                if (entry.hasUUID("Player")) data.players[entry.getUUID("Player")] = StratagemCooldownState.load(entry)
            } }
        },null), "sbw_helldiver_cooldowns")
    }
}

/** One ledger per player, independent of the number of beacon items and dimensions. */
@EventBusSubscriber(modid = MOD_ID)
object StratagemCooldowns {
    fun now(server: MinecraftServer) = server.overworld().gameTime
    fun state(server: MinecraftServer, player: UUID): StratagemCooldownState {
        val data = CooldownSavedData.get(server)
        return data.players.getOrPut(player) { data.setDirty(); StratagemCooldownState() }
    }
    fun state(player: ServerPlayer) = state(player.server, player.uuid)
    fun equipped(player: ServerPlayer): Set<String> = (0 until player.inventory.containerSize).flatMap { slot ->
        val stack = player.inventory.getItem(slot)
        if (stack.item is BeaconItem) BeaconData.loadout(stack).filterNotNull().map { it.id } else emptyList()
    }.filter { it.startsWith("eagle_") }.toSet()

    fun <T> change(server: MinecraftServer, player: UUID, action: (StratagemCooldownState, Long) -> T): T {
        val s = state(server, player)
        val before = s.revision
        val result = action(s, now(server))
        if (before != s.revision) {
            CooldownSavedData.get(server).setDirty()
            server.playerList.getPlayer(player)?.let(::sync)
        }
        return result
    }
    fun available(player: ServerPlayer, id: String) = change(player.server, player.uuid) { s, time ->
        s.equip(equipped(player))
        if(!Stratagems.dimensionAllowed(player.level().dimension().location().toString())) false
        else if(id=="call_super_destroyer") SuperDestroyers.hasOrbital(player) && !SuperDestroyers.state(player).transitioning(time)
        else if(Stratagems.isOrbital(id) && !SuperDestroyers.available(player)) false
        else if (id == "eagle_rearm") s.enabled && s.equippedEagles.isNotEmpty() && !s.rearming(time)
        else s.available(id, time)
    }
    fun reserve(player: ServerPlayer, id: String, token: UUID) = change(player.server, player.uuid) { s, time -> Stratagems.dimensionAllowed(player.level().dimension().location().toString()) && (!Stratagems.isOrbital(id) || SuperDestroyers.available(player)) && s.reserve(id, token, time) }
    fun arrival(server: MinecraftServer, player: UUID, id: String, token: UUID, deadline: Long) =
        change(server, player) { s, time -> s.beginArrival(id, token, time, deadline) }
    fun cancel(server: MinecraftServer, player: UUID, id: String, token: UUID) = change(server, player) { s, _ -> s.cancel(id, token) }
    fun rearm(player: ServerPlayer) = change(player.server, player.uuid) { s, time -> s.equip(equipped(player)); Stratagems.dimensionAllowed(player.level().dimension().location().toString()) && s.rearm(time) }
    fun setEnabled(player: ServerPlayer, enabled: Boolean) = change(player.server, player.uuid) { s, time -> s.setEnabled(enabled, time) }
    fun sync(player: ServerPlayer) {
        if (player is FakePlayer) return
        BeaconNetwork.CHANNEL.send(PacketTargets.PLAYER.with { player }, CooldownSnapshotPacket(now(player.server), state(player).save()))
    }
    @SubscribeEvent fun tick(event: net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre) {
        val p = event.entity as? ServerPlayer ?: return
        // Clock transitions happen every tick; inventory scans and clock correction are infrequent.
        change(p.server, p.uuid) { s, time ->
            if (p.tickCount % 20 == 0) s.equip(equipped(p))
            s.advance(time)
        }
        if (p.tickCount % 100 == 0) sync(p)
    }
    @SubscribeEvent fun login(event: PlayerEvent.PlayerLoggedInEvent) { (event.entity as? ServerPlayer)?.let(::sync) }
    @SubscribeEvent fun dimension(event: PlayerEvent.PlayerChangedDimensionEvent) { (event.entity as? ServerPlayer)?.let(::sync) }
    @SubscribeEvent fun respawn(event: PlayerEvent.PlayerRespawnEvent) { (event.entity as? ServerPlayer)?.let(::sync) }
}
