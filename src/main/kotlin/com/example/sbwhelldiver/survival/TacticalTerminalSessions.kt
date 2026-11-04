package com.example.sbwhelldiver.survival

import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.network.*
import com.example.sbwhelldiver.stratagem.Stratagems
import com.example.sbwhelldiver.stratagem.SuperDestroyers
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.Item
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
            if(id!="destroyer_call" && id !in TacticalTerminalOffers.byId)return
            s.offer=id;s.started=now;s.startedNanos=monotonicNanos;s.heartbeat=now;return
        }
        if(s.offer!=id||id.isEmpty())return
        if(now-s.heartbeat>1200){s.offer="";return}
        if(action==1){s.heartbeat=now;return}
        if(action!=2||!TacticalTerminalHold.serverDurationReached(now-s.started,monotonicNanos-s.startedNanos))return
        s.offer=""
        val cost=if(id=="destroyer_call")TacticalTerminalOffers.destroyerCall else TacticalTerminalOffers.byId[id]?.cost?:return
        if(id=="destroyer_call"&&SuperDestroyers.state(player).callsRemaining>=5){feedback(player,"message.sbw_helldiver.terminal_full");return}
        val offer=if(id=="destroyer_call")null else Stratagems.get(id)?:return
        val item=offer?.let{BuiltInRegistries.ITEM.getOptional(it.itemId).orElse(null)}
        if(offer!=null&&item==null)return
        val costs=listOf(ModItems.COMMON_SAMPLE.get() to cost.common,ModItems.RARE_SAMPLE.get() to cost.rare,ModItems.SUPER_SAMPLE.get() to cost.superSample)
        if(costs.any{(type,count)->countOf(player,type)<count}){feedback(player,"message.sbw_helldiver.terminal_insufficient");return}
        // No resource is removed until all failure conditions have been checked.
        if(id=="destroyer_call"&&!SuperDestroyers.refillCall(player))return
        for((type,count)in costs)consume(player,type,count)
        if(item!=null){val stack=ItemStack(item);if(!player.inventory.add(stack)&&!stack.isEmpty)player.drop(stack,false)}
        player.inventory.setChanged();player.containerMenu.broadcastChanges()
        feedback(player,"message.sbw_helldiver.terminal_success")
    }
    private fun countOf(player:ServerPlayer,item:Item)=(0 until player.inventory.containerSize).sumOf{slot->player.inventory.getItem(slot).takeIf{it.`is`(item)}?.count?:0}
    private fun consume(player:ServerPlayer,item:Item,amount:Int){
        var left=amount
        for(slot in 0 until player.inventory.containerSize){val stack=player.inventory.getItem(slot);if(stack.`is`(item)){val n=minOf(left,stack.count);stack.shrink(n);left-=n;if(left==0)return}}
    }
    private fun feedback(player:ServerPlayer,key:String){if(player is net.neoforged.neoforge.common.util.FakePlayer)return;sessions[player.uuid]?.let{BeaconNetwork.CHANNEL.send(PacketTargets.PLAYER.with{player},TacticalTerminalResultPacket(it.token,key))}}
}
