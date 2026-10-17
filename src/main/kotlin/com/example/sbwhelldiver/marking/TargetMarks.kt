package com.example.sbwhelldiver.marking

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.example.sbwhelldiver.network.BeaconNetwork
import com.example.sbwhelldiver.network.MarkSnapshotPacket
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.level.Level
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.HitResult
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.network.PacketDistributor
import java.util.UUID

/** Server owns selection, visual lifetime and moving target positions; clients send a view ray only. */
@Mod.EventBusSubscriber(modid=MOD_ID)
object TargetMarks {
    const val RANGE=300.0
    const val LIFETIME=160 // Eight seconds of HUD visibility; the targeting instruction remains active.
    data class Mark(val owner:UUID,val dimension:ResourceKey<Level>,val target:UUID?,val fixed:Vec3,
                    val created:Long,val expires:Long,val serial:Long)
    data class View(val owner:UUID,val serial:Long,val entityId:Int,val target:UUID?,val point:Vec3,
                    val hostile:Boolean,val name:Component,val remaining:Int,val icon:String="")
    private val marks=linkedMapOf<UUID,Mark>()
    private val lastInput=mutableMapOf<UUID,Long>()
    private var serial=0L
    fun resolve(level:ServerLevel,m:Mark):Entity?=m.target?.let {level.getEntity(it)}
    fun point(level:ServerLevel,m:Mark):Vec3?=if(m.target==null)m.fixed else resolve(level,m)?.takeIf {it.isAlive&&!it.isRemoved}?.boundingBox?.center
    fun current(level:ServerLevel,owner:UUID)=marks[owner]?.takeIf {it.dimension==level.dimension()&&point(level,it)!=null}
    fun all(level:ServerLevel)=marks.values.filter {it.dimension==level.dimension()&&point(level,it)!=null}
    fun put(level:ServerLevel,owner:UUID,target:Entity?,position:Vec3):Mark {
        val m=Mark(owner,level.dimension(),target?.uuid,position,level.gameTime,level.gameTime+LIFETIME,++serial)
        marks[owner]=m;return m
    }
    fun visible(level:ServerLevel,m:Mark)=m.expires>level.gameTime
    fun cancel(owner:UUID)=marks.remove(owner)
    fun clear(){marks.clear();lastInput.clear()}
    fun trace(level:ServerLevel,player:LivingEntity,start:Vec3,direction:Vec3):Pair<Entity?,Vec3>? {
        val end=start.add(direction.scale(RANGE))
        val block=level.clip(ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player))
        val limit=if(block.type==HitResult.Type.MISS)RANGE*RANGE else start.distanceToSqr(block.location)
        var best=limit;var target:Entity?=null;var at=block.location
        for(e in level.getEntities(player,AABB(start,end).inflate(1.0)){
            (it is LivingEntity||it is AbstractHellpodEntity)&&it!==player&&it.isAlive&&!it.isSpectator&&!it.isRemoved&&!it.isPassengerOfSameVehicle(player)
        }){
            val box=e.boundingBox.inflate(.12);val hit=if(box.contains(start))start else box.clip(start,end).orElse(null) ?: continue
            val distance=start.distanceToSqr(hit)
            if(distance<best){best=distance;target=e;at=e.boundingBox.center}
        }
        return if(target!=null || block.type!=HitResult.Type.MISS)target to at else null
    }
    fun input(player:ServerPlayer,start:Vec3,direction:Vec3,cancelCurrent:Boolean){
        val level=player.serverLevel();val now=level.gameTime
        if(!MarkSettings.get(level.server).enabled)return
        if(java.lang.Boolean.getBoolean("sbw_helldiver.markSmokeTest"))com.example.sbwhelldiver.LOGGER.info("MARK_SERVER playerEye={} start={} dir={} cancel={}",player.eyePosition,start,direction,cancelCurrent)
        if(!player.isAlive||player.isSpectator||!finite(start)||!finite(direction)||direction.lengthSqr() !in .99..1.01)return
        // The actual third-person camera can be behind an occupied emplacement.
        if(start.distanceToSqr(player.eyePosition)>144.0)return
        if(lastInput[player.uuid]?.let {now-it<3}==true)return
        lastInput[player.uuid]=now
        var sound=3
        val existing=current(level,player.uuid)
        if(cancelCurrent && existing!=null && visible(level,existing)){
            val m=existing;val toward=point(level,m)!!.subtract(start)
            if(toward.lengthSqr()<1 || toward.normalize().dot(direction)>.985)cancel(player.uuid) else return
        }else {
            val hit=trace(level,player,start,direction)
            if(java.lang.Boolean.getBoolean("sbw_helldiver.markSmokeTest"))com.example.sbwhelldiver.LOGGER.info("MARK_HIT {}",hit)
            if(hit==null)return
            put(level,player.uuid,hit.first,hit.second)
            sound=if(hit.first is Enemy)0 else if(hit.first==null)2 else 1
        }
        sync(level)
        BeaconNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with {level.dimension()},com.example.sbwhelldiver.network.MarkFeedbackPacket(sound))
    }
    private fun finite(v:Vec3)=v.x.isFinite()&&v.y.isFinite()&&v.z.isFinite()
    private fun compatible(sentry:SentryHellpodEntity,m:Mark):Boolean {
        val owner=sentry.owner as? net.minecraft.world.entity.player.Player ?: return true
        val marker=(sentry.level() as ServerLevel).server.playerList.getPlayer(m.owner) ?: return false
        return marker.uuid==owner.uuid || owner.team==null || owner.isAlliedTo(marker)
    }
    fun preferred(sentry:SentryHellpodEntity):Mark? {
        if(sentry.level() is ServerLevel && !MarkSettings.get((sentry.level() as ServerLevel).server).enabled)return null
        val level=sentry.level() as? ServerLevel ?: return null
        return all(level).asSequence().filter {compatible(sentry,it)}.sortedWith(
            compareByDescending<Mark>{it.owner==sentry.owner?.uuid}.thenByDescending{it.serial}
        ).firstOrNull {m->
            if(m.target==null)sentry.profile.isMortar&&sentry.markPointInRange(m.fixed)
            else (resolve(level,m) as? LivingEntity)?.let {sentry.validTarget(it,true)}==true
        }
    }
    fun views(level:ServerLevel):List<View> = all(level).filter {visible(level,it)}.map {m->
        val e=resolve(level,m)
        View(m.owner,m.serial,e?.id ?: -1,m.target,point(level,m)!!,e is Enemy,
            e?.displayName ?: Component.translatable("hud.sbw_helldiver.special_location"),(m.expires-level.gameTime).toInt(),if(e is AbstractHellpodEntity && !e.profile.isSupply) "textures/item/${e.profile.id}_stratagem.png" else "")
    }
    fun sync(level:ServerLevel){BeaconNetwork.CHANNEL.send(PacketDistributor.DIMENSION.with {level.dimension()},MarkSnapshotPacket(level.dimension().location(),views(level)))}
    @SubscribeEvent fun tick(e:TickEvent.ServerTickEvent){
        if(e.phase!=TickEvent.Phase.END)return
        val changed=mutableSetOf<ServerLevel>()
        val iter=marks.iterator()
        while(iter.hasNext()){
            val (_,m)=iter.next();val level=e.server.getLevel(m.dimension)
            if(level==null||point(level,m)==null){iter.remove();if(level!=null)changed.add(level)}
            else if(level.gameTime==m.expires)changed.add(level)
        }
        e.server.allLevels.forEach {if(it in changed || (it.gameTime%2==0L&&all(it).any {m->visible(it,m)}))sync(it)}
    }
    private fun leaving(player:net.minecraft.world.entity.player.Player){
        val server=player.server ?: return;val old=cancel(player.uuid);lastInput.remove(player.uuid)
        old?.let {server.getLevel(it.dimension)?.let(::sync)}
    }
    @SubscribeEvent fun logout(e:PlayerEvent.PlayerLoggedOutEvent)=leaving(e.entity)
    @SubscribeEvent fun dimension(e:PlayerEvent.PlayerChangedDimensionEvent){leaving(e.entity);(e.entity as? ServerPlayer)?.serverLevel()?.let(::sync)}
    @SubscribeEvent fun login(e:PlayerEvent.PlayerLoggedInEvent){(e.entity as? ServerPlayer)?.let {MarkSettings.sync(it);sync(it.serverLevel())}}
    @SubscribeEvent fun stop(e:net.minecraftforge.event.server.ServerStoppedEvent)=clear()
}
