package com.example.sbwhelldiver.survival

import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.network.*
import com.example.sbwhelldiver.stratagem.Stratagems
import com.example.sbwhelldiver.stratagem.SuperDestroyers
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import java.util.UUID

/** Server decides both the hold duration and the atomic sample transaction. */
object TacticalTerminalSessions {
    const val SCREEN_OFF = 3
    private data class Session(val pos:BlockPos,val dimension:String,val token:UUID,var offer:String="",var started:Long=0,var startedNanos:Long=0,var heartbeat:Long=0)
    private val sessions=mutableMapOf<UUID,Session>()
    fun open(player:ServerPlayer,be:TacticalTerminalBlockEntity) {
        val s=Session(be.blockPos,player.level().dimension().location().toString(),UUID.randomUUID())
        sessions[player.uuid]=s
        if(player !is net.neoforged.neoforge.common.util.FakePlayer) BeaconNetwork.CHANNEL.send(PacketTargets.PLAYER.with{player},TacticalTerminalOpenPacket(s.pos,s.token))
    }
    fun token(player:ServerPlayer)=sessions[player.uuid]?.token
    fun accept(player:ServerPlayer,pos:BlockPos,token:UUID,action:Int,id:String,monotonicNanos:Long=System.nanoTime()) {
        val s=sessions[player.uuid]?:return
        if(s.pos!=pos||s.token!=token)return
        if(action == -2){sessions.remove(player.uuid);return}
        val l=player.serverLevel();val be=l.getBlockEntity(pos) as? TacticalTerminalBlockEntity
        if(l.dimension().location().toString()!=s.dimension || player.isSpectator || !player.isAlive || player.distanceToSqr(pos.x+.5,pos.y+.5,pos.z+.5)>36 || be?.screenOn!=true){sessions.remove(player.uuid);return}
        if (action == SCREEN_OFF) {
            be.setScreenEnabled(false)
            sessions.values.removeIf { it.pos == pos && it.dimension == s.dimension }
            return
        }
        val now=l.gameTime
        if(action == -1){s.offer="";return}
        if(action==0){
            if(id !in TacticalTerminalOffers.refillsById && id !in TacticalTerminalOffers.byId)return
            s.offer=id;s.started=now;s.startedNanos=monotonicNanos;s.heartbeat=now;return
        }
        if(s.offer!=id||id.isEmpty())return
        if(now-s.heartbeat>1200){s.offer="";return}
        if(action==1){s.heartbeat=now;return}
        if(action!=2||!TacticalTerminalHold.serverDurationReached(now-s.started,monotonicNanos-s.startedNanos))return
        s.offer=""
        val refill=TacticalTerminalOffers.refillsById[id]
        val cost=refill?.cost?:TacticalTerminalOffers.byId[id]?.cost?:return
        if(refill!=null&&SuperDestroyers.state(player).calls(refill.minutes)>=refill.limit){feedback(player,"message.sbw_helldiver.terminal_full");return}
        val offer=if(refill!=null)null else Stratagems.get(id)?:return
        val item=offer?.let{BuiltInRegistries.ITEM.getOptional(it.itemId).orElse(null)}
        if(offer!=null&&item==null)return
        val costs=listOf(ModItems.COMMON_SAMPLE.get() to cost.common,ModItems.RARE_SAMPLE.get() to cost.rare,ModItems.SUPER_SAMPLE.get() to cost.superSample)
        if(costs.withIndex().any{(index,pair)->SampleStorage.total(player,index)<java.math.BigInteger.valueOf(pair.second.toLong())}){feedback(player,"message.sbw_helldiver.terminal_insufficient");return}
        // No resource is removed until all failure conditions have been checked.
        if(refill!=null&&!SuperDestroyers.refillCall(player,refill.minutes))return
        for((index,pair)in costs.withIndex())SampleStorage.consume(player,index,pair.second)
        if(item!=null){val stack=ItemStack(item);if(!player.inventory.add(stack)&&!stack.isEmpty)player.drop(stack,false)}
        player.inventory.setChanged();player.containerMenu.broadcastChanges()
        feedback(player,"message.sbw_helldiver.terminal_success")
    }
    private fun feedback(player:ServerPlayer,key:String){if(player is net.neoforged.neoforge.common.util.FakePlayer)return;sessions[player.uuid]?.let{BeaconNetwork.CHANNEL.send(PacketTargets.PLAYER.with{player},TacticalTerminalResultPacket(it.token,key))}}
}
