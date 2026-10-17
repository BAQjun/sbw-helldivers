package com.example.sbwhelldiver.entity
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModSounds
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3
import net.minecraftforge.entity.IEntityAdditionalSpawnData
import net.minecraftforge.network.NetworkHooks
import java.util.UUID

/** The replicated carrier stays at the strike anchor so Minecraft never suspends its attack clock.
 * Model, moving audio and real ordnance use the same world-space flight curve, independently of carrier position. */
class EagleEntity(type:EntityType<*>,level:Level):Entity(type,level),IEntityAdditionalSpawnData {
 var target=Vec3.ZERO;private set
 var runDirection=Vec3(0.0,0.0,1.0);private set
 var airstrike=true;private set
 var startedAt=0L;private set
 var flightHeight=EagleFlight.HEIGHT;private set
 var ownerUuid:UUID?=null;private set
 var fired=0;private set
 private val activeOrdnance=mutableMapOf<UUID,Entity>()
 private val awaitingOrdnance=mutableSetOf<UUID>()
 private var legacyPayloadTracking=false
 val strikeComplete get()=fired>=(if(airstrike)EagleFlight.BOMB_COUNT else EagleFlight.STRAFE_ROUNDS) && activeOrdnance.isEmpty() && awaitingOrdnance.isEmpty() && (!legacyPayloadTracking || age>=EagleFlight.attackEnd(airstrike)+20)
 private fun refreshOrdnance() {
  val server=level() as? ServerLevel?:return
  val it=activeOrdnance.iterator()
  while(it.hasNext()) {val(id,shot)=it.next();if(shot.isRemoved){
   if(shot.removalReason==RemovalReason.UNLOADED_TO_CHUNK || shot.removalReason==RemovalReason.UNLOADED_WITH_PLAYER)awaitingOrdnance.add(id)
   it.remove()
  }}
  val pending=awaitingOrdnance.iterator()
  while(pending.hasNext()){val id=pending.next();val shot=server.getEntity(id);if(shot!=null&&!shot.isRemoved){activeOrdnance[id]=shot;pending.remove()}}
 }
 private var flybyPlayed=false
 private var gunPlayed=false
 private val heldChunks=mutableSetOf<ChunkPos>()
 val age get()=(level().gameTime-startedAt).coerceAtLeast(0).toDouble()
 init {isNoGravity=true;noPhysics=true}
 fun configure(point:Vec3,direction:Vec3,bombing:Boolean,owner:UUID?,launchTime:Long=level().gameTime) {
  target=point;runDirection=direction.normalize();airstrike=bombing;flightHeight=if(bombing)EagleFlight.HEIGHT else EagleFlight.STRAFE_HEIGHT;ownerUuid=owner;startedAt=launchTime
  // Sample the complete attack corridor, keeping the belly fin above nearby terrain.
  val server=level() as? ServerLevel
  if(server!=null){
   val base=flightHeight;val across=Vec3(-runDirection.z,0.0,runDirection.x)
   for(i in -16..12)for(side in listOf(-8.0,0.0,8.0)){
    val q=point.add(runDirection.scale(i*40.0)).add(across.scale(side));val ground=server.getHeight(Heightmap.Types.MOTION_BLOCKING,kotlin.math.floor(q.x).toInt(),kotlin.math.floor(q.z).toInt())
    flightHeight=maxOf(flightHeight,base+(ground-point.y).coerceAtLeast(0.0))
   }
  }
  updateFlight(0.0);xo=x;yo=y;zo=z;xOld=x;yOld=y;zOld=z;yRotO=yRot;xRotO=xRot
  holdFlightChunks()
 }
 fun positionAt(t:Double)=EagleFlight.position(target,runDirection,t,airstrike,flightHeight)
 fun muzzleAt(t:Double):Vec3 {val a=Math.toRadians(EagleFlight.pitch(t,airstrike,flightHeight).toDouble());return positionAt(t).add(runDirection.scale(7.96*kotlin.math.cos(a))).add(0.0,-7.96*kotlin.math.sin(a)-.064,0.0)}
 private fun updateFlight(t:Double) {
  setPos(target);yRot=Math.toDegrees(kotlin.math.atan2(-runDirection.x,runDirection.z)).toFloat();xRot=EagleFlight.pitch(t,airstrike,flightHeight)
 }
 override fun defineSynchedData() {}
 override fun tick() {
  super.tick();updateFlight(age)
  if(level().isClientSide)return
  refreshOrdnance()
  if(age>=EagleFlight.end(airstrike)){discard();return}
  holdFlightChunks()
  if(!flybyPlayed && age>=26)flybyPlayed=true
  if(age<EagleFlight.attackStart(airstrike))return
  if(airstrike){
   val due=minOf(EagleFlight.BOMB_COUNT,((age-EagleFlight.attackStart(airstrike))/EagleFlight.BOMB_INTERVAL).toInt()+1)
   while(fired<due){dropBomb(fired);fired++}
  } else {
   if(!gunPlayed)gunPlayed=true
   val due=minOf(EagleFlight.STRAFE_ROUNDS,((age-EagleFlight.attackStart(airstrike)+1)*EagleFlight.STRAFE_ROUNDS/30).toInt())
   while(fired<due){fireRound(fired);fired++}
  }
 }
 private fun dropBomb(index:Int) {
  val server=level() as ServerLevel;val shot=ModEntities.EAGLE_BOMB.get().create(server)?:return
  val release=positionAt((EagleFlight.attackStart(airstrike)+index*EagleFlight.BOMB_INTERVAL).toDouble()).add(0.0,-1.2,0.0)
  val aim=target.add(runDirection.scale(EagleFlight.bombOffset(index)))
  shot.setPos(release);ownerUuid?.let {shot.owner=server.getEntity(it)}
  val v=EagleFlight.bombVelocity(release,aim);shot.shoot(v.x,v.y,v.z,v.length().toFloat(),0f);if(server.addFreshEntity(shot))activeOrdnance[shot.uuid]=shot else shot.discard()
 }
 private fun fireRound(index:Int) {
  val server=level() as ServerLevel;val shot=ModEntities.EAGLE_CANNON_ROUND.get().create(server)?:return
  val t=EagleFlight.attackStart(airstrike)+index*30.0/EagleFlight.STRAFE_ROUNDS;val release=muzzleAt(t)
  val across=Vec3(-runDirection.z,0.0,runDirection.x).scale(if(index%2==0)-1.1 else 1.1)
  val aim=target.add(runDirection.scale(EagleFlight.strafeOffset(index,flightHeight))).add(across)
  if(index%4!=0)shot.setExplosionDamage(0f)
  shot.setPos(release);ownerUuid?.let {shot.owner=server.getEntity(it)}
  val v=aim.subtract(release).normalize();shot.shoot(v.x,v.y,v.z,20f,0f);if(server.addFreshEntity(shot))activeOrdnance[shot.uuid]=shot else shot.discard()
 }
 private fun sound(name:String,volume:Float) {ModSounds.EVENTS[name]?.get()?.let {level().playSound(null,x,y,z,it,SoundSource.PLAYERS,volume,1f)}}
 private fun holdFlightChunks() {
  val server=level() as? ServerLevel?:return
  // The carrier needs only its strike chunk. During the attack keep the actual launch corridor active
  // for projectiles crossing the player's simulation boundary; do not force-load the distant high approach.
  val desired=mutableSetOf<ChunkPos>()
  if(age>=EagleFlight.attackStart(airstrike)-20 && age<EagleFlight.attackEnd(airstrike)+21){
   for(ahead in -20..24)desired.add(ChunkPos(net.minecraft.core.BlockPos.containing(positionAt((age+ahead).coerceAtLeast(EagleFlight.attackStart(airstrike).toDouble())))))
  }
  desired.add(ChunkPos(net.minecraft.core.BlockPos.containing(target)))
  for(c in heldChunks-desired)server.chunkSource.removeRegionTicket(TICKET,c,2,id,true)
  for(c in desired)server.chunkSource.addRegionTicket(TICKET,c,2,id,true)
  heldChunks.clear();heldChunks.addAll(desired)
 }
 override fun remove(reason:RemovalReason) {if(java.lang.Boolean.getBoolean("sbw_helldiver.eagleSmokeTest"))com.example.sbwhelldiver.LOGGER.info("EAGLE_REMOVE reason={} age={} fired={} at={}",reason,age,fired,position());val s=level() as? ServerLevel;if(s!=null)for(c in heldChunks)s.chunkSource.removeRegionTicket(TICKET,c,2,id,true);heldChunks.clear();super.remove(reason)}
 override fun shouldRenderAtSqrDistance(distance:Double)=distance<1024.0*1024.0
 override fun getAddEntityPacket():Packet<ClientGamePacketListener> =NetworkHooks.getEntitySpawningPacket(this)
 override fun writeSpawnData(b:FriendlyByteBuf){b.writeDouble(target.x);b.writeDouble(target.y);b.writeDouble(target.z);b.writeDouble(runDirection.x);b.writeDouble(runDirection.z);b.writeBoolean(airstrike);b.writeLong(startedAt);b.writeDouble(flightHeight)}
 override fun readSpawnData(b:FriendlyByteBuf){target=Vec3(b.readDouble(),b.readDouble(),b.readDouble());runDirection=Vec3(b.readDouble(),0.0,b.readDouble());airstrike=b.readBoolean();startedAt=b.readLong();flightHeight=b.readDouble();updateFlight(age);xo=x;yo=y;zo=z;xOld=x;yOld=y;zOld=z}
 override fun lerpTo(x:Double,y:Double,z:Double,yaw:Float,pitch:Float,steps:Int,teleport:Boolean) { /* Same clock and path on both sides; vanilla lerp would lag behind the firing position. */ }
 override fun readAdditionalSaveData(n:CompoundTag){target=Vec3(n.getDouble("TargetX"),n.getDouble("TargetY"),n.getDouble("TargetZ"));runDirection=Vec3(n.getDouble("ForwardX"),0.0,n.getDouble("ForwardZ"));airstrike=n.getBoolean("Airstrike");startedAt=n.getLong("StartedAt");flightHeight=n.getDouble("Height");fired=n.getInt("Fired");flybyPlayed=n.getBoolean("FlybyPlayed");gunPlayed=n.getBoolean("GunPlayed");ownerUuid=if(n.hasUUID("Owner"))n.getUUID("Owner") else null
  activeOrdnance.clear();awaitingOrdnance.clear();legacyPayloadTracking=n.getBoolean("LegacyPayloadTracking")||!n.contains("ActiveOrdnance")
  for(t in n.getList("ActiveOrdnance",net.minecraft.nbt.Tag.TAG_COMPOUND.toInt())){val c=t as CompoundTag;if(c.hasUUID("Id"))awaitingOrdnance.add(c.getUUID("Id"))}
  updateFlight(age)}
 override fun addAdditionalSaveData(n:CompoundTag){n.putDouble("TargetX",target.x);n.putDouble("TargetY",target.y);n.putDouble("TargetZ",target.z);n.putDouble("ForwardX",runDirection.x);n.putDouble("ForwardZ",runDirection.z);n.putBoolean("Airstrike",airstrike);n.putLong("StartedAt",startedAt);n.putDouble("Height",flightHeight);n.putInt("Fired",fired);n.putBoolean("FlybyPlayed",flybyPlayed);n.putBoolean("GunPlayed",gunPlayed);ownerUuid?.let {n.putUUID("Owner",it)}
  val list=net.minecraft.nbt.ListTag()
  val ids=awaitingOrdnance+activeOrdnance.filterValues{!it.isRemoved||it.removalReason==RemovalReason.UNLOADED_TO_CHUNK||it.removalReason==RemovalReason.UNLOADED_WITH_PLAYER}.keys
  for(id in ids)list.add(CompoundTag().also{it.putUUID("Id",id)})
  n.put("ActiveOrdnance",list);n.putBoolean("LegacyPayloadTracking",legacyPayloadTracking)
 }
 companion object {private val TICKET:TicketType<Int> =TicketType.create("helldivers_eagle",Comparator.naturalOrder<Int>())}
}
