package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.network.*
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.event.server.ServerStoppedEvent
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import com.example.sbwhelldiver.network.PacketTargets
import java.util.UUID

/** Wait for the target client to receive surrounding chunks before starting the descent clock. */
@EventBusSubscriber(modid=MOD_ID)
object PlayerDrops {
 data class Pending(val token:UUID,val player:UUID,val level:ServerLevel,val point:Vec3,val started:Long)
 private val pending=mutableMapOf<UUID,Pending>()
 private val ticket=TicketType.create<UUID>("helldivers_player_drop",Comparator.naturalOrder(),240)
 fun pending(player:UUID)=pending[player]
 fun reserved(l:ServerLevel,point:Vec3)=pending.values.any{it.level===l&&it.point.subtract(point).multiply(1.0,0.0,1.0).lengthSqr()<16.0}
 fun prepare(p:ServerPlayer,l:ServerLevel,at:Vec3):Boolean {
  if(pending.containsKey(p.uuid))return false
  p.stopRiding();p.teleportTo(l,at.x,at.y+.35,at.z,p.yRot,0f)
  if(p.level()!==l)return false
  val order=Pending(UUID.randomUUID(),p.uuid,l,at,l.gameTime);pending[p.uuid]=order
  val center=ChunkPos(net.minecraft.core.BlockPos.containing(at))
  l.chunkSource.addRegionTicket(ticket,center,2,order.token,true)
  for(x in -2..2)for(z in -2..2)l.getChunk(center.x+x,center.z+z)
  BeaconNetwork.CHANNEL.send(PacketTargets.PLAYER.with{p},DropPreparePacket(order.token,l.dimension().location(),at))
  return true
 }
 fun ready(p:ServerPlayer,token:UUID){
  val o=pending[p.uuid]?:return
  if(o.token!=token||o.level!==p.level()||o.level.gameTime-o.started<20)return
  if(!p.isAlive||p.isSpectator||!PlayerHellpodEntity.safeLanding(o.level,p,o.point)){cancel(p);return}
  pending.remove(p.uuid)
  val pod=ModEntities.PLAYER_HELLPOD.get().create(o.level)!!
  if(!pod.launch(p,o.point)){pod.discard();BeaconNetwork.CHANNEL.send(PacketTargets.PLAYER.with{p},DropCancelPacket(token))}
  o.level.chunkSource.removeRegionTicket(ticket,ChunkPos(net.minecraft.core.BlockPos.containing(o.point)),2,o.token,true)
 }
 fun cancel(p:ServerPlayer){
  val o=pending.remove(p.uuid)?:return
  o.level.chunkSource.removeRegionTicket(ticket,ChunkPos(net.minecraft.core.BlockPos.containing(o.point)),2,o.token,true)
  BeaconNetwork.CHANNEL.send(PacketTargets.PLAYER.with{p},DropCancelPacket(o.token))
 }
 @SubscribeEvent fun tick(e:net.neoforged.neoforge.event.tick.ServerTickEvent.Post){for(o in pending.values.toList()){
   val p=e.server.playerList.getPlayer(o.player)
   if(p==null){if(o.level.gameTime-o.started>220){pending.remove(o.player);o.level.chunkSource.removeRegionTicket(ticket,ChunkPos(net.minecraft.core.BlockPos.containing(o.point)),2,o.token,true)};continue}
   if(!p.isAlive||p.isSpectator||p.level()!==o.level||o.level.gameTime-o.started>220){cancel(p);continue}
   p.fallDistance=0f;p.deltaMovement=Vec3.ZERO
  }
 }
 @SubscribeEvent fun stop(e:ServerStoppedEvent){pending.clear()}
}
