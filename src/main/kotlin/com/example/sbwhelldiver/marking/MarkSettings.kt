package com.example.sbwhelldiver.marking

import com.example.sbwhelldiver.network.BeaconNetwork
import com.example.sbwhelldiver.network.MarkEnabledPacket
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.saveddata.SavedData
import com.example.sbwhelldiver.network.PacketTargets

/** Stored with the world, authoritative for both selection and sentry commands. */
class MarkSettings:SavedData(){
 var enabled=true;private set
 override fun save(n:CompoundTag,registries:net.minecraft.core.HolderLookup.Provider):CompoundTag {n.putBoolean("Enabled",enabled);return n}
 companion object {
  fun get(server:MinecraftServer):MarkSettings=server.overworld().dataStorage.computeIfAbsent(SavedData.Factory(::MarkSettings,{n, _ ->MarkSettings().also{it.enabled=!n.contains("Enabled")||n.getBoolean("Enabled")}},null),"sbw_helldiver_marking")
  fun set(server:MinecraftServer,enabled:Boolean){val state=get(server);state.enabled=enabled;state.setDirty()
   if(!enabled)TargetMarks.clear()
   server.allLevels.forEach{TargetMarks.sync(it)}
   BeaconNetwork.CHANNEL.send(PacketTargets.ALL.noArg(),MarkEnabledPacket(enabled))
  }
  fun sync(player:ServerPlayer){BeaconNetwork.CHANNEL.send(PacketTargets.PLAYER.with{player},MarkEnabledPacket(get(player.server).enabled))}
 }
}
