package com.example.sbwhelldiver.stratagem

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.config.OrbitalConfig
import com.example.sbwhelldiver.init.ModSounds
import com.example.sbwhelldiver.network.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.TickEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.network.PacketDistributor
import java.util.UUID

/** World anchors, not entities: ships never load remote chunks or follow a moving player. */
data class DestroyerAnchor(val dimension:String,val center:Vec3,val position:Vec3,val direction:Int){
 fun covers(dimension:String,point:Vec3,radius:Double)=this.dimension==dimension &&
  (point.x-center.x)*(point.x-center.x)+(point.z-center.z)*(point.z-center.z)<=radius*radius
 fun save()=CompoundTag().also{t->
  t.putString("Dimension",dimension);t.putDouble("CX",center.x);t.putDouble("CY",center.y);t.putDouble("CZ",center.z)
  t.putDouble("X",position.x);t.putDouble("Y",position.y);t.putDouble("Z",position.z);t.putInt("Direction",direction)
 }
 companion object{
  fun at(dimension:String,center:Vec3,direction:Int,offset:Double,altitude:Double):DestroyerAnchor {
   require(direction in 0..3)
   val horizontal=when(direction){Stratagems.UP->Vec3(0.0,0.0,-offset);Stratagems.LEFT->Vec3(-offset,0.0,0.0);Stratagems.RIGHT->Vec3(offset,0.0,0.0);else->Vec3(0.0,0.0,offset)}
   return DestroyerAnchor(dimension,center,center.add(horizontal).add(0.0,altitude,0.0),direction)
  }
  fun load(t:CompoundTag)=DestroyerAnchor(t.getString("Dimension"),Vec3(t.getDouble("CX"),t.getDouble("CY"),t.getDouble("CZ")),Vec3(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z")),t.getInt("Direction"))
 }
}
class DestroyerState {
 var anchor:DestroyerAnchor?=null;private set
 var destination:DestroyerAnchor?=null;private set
 var transitionEnd=0L;private set
 var appearedAt=0L;private set
 fun transitioning(now:Long)=transitionEnd>now
 fun schedule(destination:DestroyerAnchor?,now:Long):Boolean {
  if(transitioning(now)||destination==null&&anchor==null)return false
  this.destination=destination;transitionEnd=now+60;return true
 }
 fun advance(now:Long):Boolean {
  if(transitionEnd==0L||now<transitionEnd)return false
  anchor=destination;destination=null;appearedAt=transitionEnd;transitionEnd=0;return true
 }
 fun available(dimension:String,point:Vec3,radius:Double,now:Long)=!transitioning(now)&&anchor?.covers(dimension,point,radius)==true
 fun save()=CompoundTag().also{t->anchor?.let{t.put("Anchor",it.save())};destination?.let{t.put("Destination",it.save())};t.putLong("TransitionEnd",transitionEnd);t.putLong("AppearedAt",appearedAt)}
 companion object{fun load(t:CompoundTag)=DestroyerState().apply{
  anchor=if(t.contains("Anchor"))DestroyerAnchor.load(t.getCompound("Anchor")) else null
  destination=if(t.contains("Destination"))DestroyerAnchor.load(t.getCompound("Destination")) else null
  transitionEnd=t.getLong("TransitionEnd");appearedAt=t.getLong("AppearedAt")
 }}
}
@Mod.EventBusSubscriber(modid=MOD_ID)
object SuperDestroyers {
 class Fleet:SavedData(){
  val ships=mutableMapOf<UUID,DestroyerState>()
  override fun save(t:CompoundTag):CompoundTag {val list=ListTag();ships.forEach{(id,s)->list.add(s.save().also{it.putUUID("Owner",id)})};t.put("Ships",list);return t}
  companion object{fun load(t:CompoundTag)=Fleet().apply{t.getList("Ships",Tag.TAG_COMPOUND.toInt()).forEach{raw->val a=raw as CompoundTag;if(a.hasUUID("Owner"))ships[a.getUUID("Owner")]=DestroyerState.load(a)}}}
 }
 private fun fleet(s:MinecraftServer):Fleet=s.overworld().dataStorage.computeIfAbsent(Fleet::load,::Fleet,"sbw_helldiver_destroyers")
 fun state(p:ServerPlayer)=fleet(p.server).ships.getOrPut(p.uuid){DestroyerState()}
 fun hasOrbital(p:ServerPlayer)=(0 until p.inventory.containerSize).any{slot->BeaconData.loadout(p.inventory.getItem(slot)).any{it?.id?.let(Stratagems::isOrbital)==true}}
 fun available(p:ServerPlayer)=state(p).available(p.level().dimension().location().toString(),p.position(),OrbitalConfig.range.get(),StratagemCooldowns.now(p.server))
 fun origin(server:MinecraftServer,owner:UUID?,dimension:String,target:Vec3):Vec3? {
  val s=owner?.let{fleet(server).ships[it]}?:return null
  if(!s.available(dimension,target,OrbitalConfig.range.get(),StratagemCooldowns.now(server)))return null
  return s.anchor!!.position.add(0.0,-15.0,0.0)
 }
 fun move(p:ServerPlayer,direction:Int):Boolean {
  if(!hasOrbital(p)||direction !in 0..3)return false
  val destination=DestroyerAnchor.at(p.level().dimension().location().toString(),p.position(),direction,OrbitalConfig.offset.get(),OrbitalConfig.altitude.get())
  if(!state(p).schedule(destination,StratagemCooldowns.now(p.server)))return false
  fleet(p.server).setDirty();syncAll(p.server);return true
 }
 fun depart(p:ServerPlayer):Boolean {
  if(!hasOrbital(p)||!state(p).schedule(null,StratagemCooldowns.now(p.server)))return false
  fleet(p.server).setDirty();syncAll(p.server);return true
 }
 fun sync(p:ServerPlayer){
  if(p is net.minecraftforge.common.util.FakePlayer)return
  val f=fleet(p.server);val list=ListTag();val dimension=p.level().dimension().location().toString()
  f.ships.forEach{(id,s)->if(s.anchor?.dimension==dimension)list.add(s.save().also{it.putUUID("Owner",id)})}
  val data=CompoundTag();data.put("Ships",list);data.put("Own",state(p).save());data.putDouble("Range",OrbitalConfig.range.get());data.putBoolean("Equipped",hasOrbital(p))
  BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with{p},DestroyerSnapshotPacket(StratagemCooldowns.now(p.server),data))
 }
 private fun syncAll(s:MinecraftServer)=s.playerList.players.forEach(::sync)
 private fun cue(s:MinecraftServer,anchor:DestroyerAnchor?,name:String){
  if(anchor==null)return
  s.playerList.players.filter{it.level().dimension().location().toString()==anchor.dimension&&it.position().distanceToSqr(anchor.center)<4096.0*4096.0}.forEach{
   it.playNotifySound(ModSounds.EVENTS.getValue(name).get(),SoundSource.AMBIENT,.85f,1f)
  }
 }
 @SubscribeEvent fun tick(e:TickEvent.ServerTickEvent){
  if(e.phase!=TickEvent.Phase.END)return
  val server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer()?:return
  val now=StratagemCooldowns.now(server);val f=fleet(server);var changed=false
  for(s in f.ships.values){val old=s.anchor;if(s.advance(now)){cue(server,old,"super_destroyer_warp_out");cue(server,s.anchor,"super_destroyer_warp_in");changed=true}}
  if(changed)f.setDirty()
  if(changed||now%20L==0L)syncAll(server)
 }
 @SubscribeEvent fun login(e:PlayerEvent.PlayerLoggedInEvent){(e.entity as? ServerPlayer)?.let(::sync)}
 @SubscribeEvent fun dimension(e:PlayerEvent.PlayerChangedDimensionEvent){(e.entity as? ServerPlayer)?.let{BeaconInput.reset(it.uuid);sync(it)}}
 @SubscribeEvent fun respawn(e:PlayerEvent.PlayerRespawnEvent){(e.entity as? ServerPlayer)?.let(::sync)}
}
