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
 var attack=EagleAttack.AIRSTRIKE;private set
 val airstrike get()=attack.levelBombing
 var startedAt=0L;private set
 var flightHeight=EagleFlight.HEIGHT;private set
 var ownerUuid:UUID?=null;private set
 var fired=0;private set
 private val activeOrdnance=mutableMapOf<UUID,Entity>()
 private val awaitingOrdnance=mutableSetOf<UUID>()
 private val awaitingChunks=mutableMapOf<UUID,ChunkPos>()
 val payloadReleased get()=fired>=attack.count
 private var legacyPayloadTracking=false
 val strikeComplete get()=payloadReleased && activeOrdnance.isEmpty() && awaitingOrdnance.isEmpty() && (!legacyPayloadTracking || age>=EagleFlight.attackEnd(attack)+20)
 private fun refreshOrdnance() {
  val server=level() as? ServerLevel?:return
  val it=activeOrdnance.iterator()
  while(it.hasNext()) {val(id,shot)=it.next();if(shot.isRemoved){
   if(shot.removalReason==RemovalReason.UNLOADED_TO_CHUNK || shot.removalReason==RemovalReason.UNLOADED_WITH_PLAYER){awaitingOrdnance.add(id);awaitingChunks[id]=ChunkPos(shot.blockPosition())}
   it.remove()
  }}
  val pending=awaitingOrdnance.iterator()
  while(pending.hasNext()){val id=pending.next();val shot=server.getEntity(id);if(shot!=null&&!shot.isRemoved){activeOrdnance[id]=shot;pending.remove();awaitingChunks.remove(id)}}
 }
 private var flybyPlayed=false
 private var gunPlayed=false
 private val heldChunks=mutableSetOf<ChunkPos>()
 val age get()=(level().gameTime-startedAt).coerceAtLeast(0).toDouble()
 init {isNoGravity=true;noPhysics=true}
 fun configure(point:Vec3,direction:Vec3,bombing:Boolean,owner:UUID?,launchTime:Long=level().gameTime)=configure(point,direction,EagleAttack.legacy(bombing),owner,launchTime)
 fun configure(point:Vec3,direction:Vec3,mode:EagleAttack,owner:UUID?,launchTime:Long=level().gameTime) {
  target=point;runDirection=direction.normalize();attack=mode;flightHeight=mode.height;ownerUuid=owner;startedAt=launchTime
  // Terrain changes the planned approach height, never the amount of ordnance released.
  val server=level() as? ServerLevel
  if(server!=null){
   val heights=mutableMapOf<Long,Double>()
   flightHeight=EagleFlight.terrainClearance(point,runDirection,mode) {p->
    val x=kotlin.math.floor(p.x).toInt();val z=kotlin.math.floor(p.z).toInt()
    val key=(x.toLong() shl 32) xor (z.toLong() and 0xffffffffL)
    // Inspect resident terrain only: planning a visual flight must not synchronously
    // generate hundreds of unseen chunks along the approach and pull-out.
    heights.getOrPut(key){server.chunkSource.getChunkNow(x shr 4,z shr 4)
     ?.getHeight(Heightmap.Types.MOTION_BLOCKING,x and 15,z and 15)?.plus(1)?.toDouble()
     ?: maxOf(point.y,server.seaLevel.toDouble())}
   }
  }
  updateFlight(0.0);xo=x;yo=y;zo=z;xOld=x;yOld=y;zOld=z;yRotO=yRot;xRotO=xRot
  holdFlightChunks()
 }
 fun positionAt(t:Double)=EagleFlight.position(target,runDirection,t,attack,flightHeight)
 fun muzzleAt(t:Double):Vec3 {val a=Math.toRadians(EagleFlight.pitch(t,attack,flightHeight).toDouble());return positionAt(t).add(runDirection.scale(7.96*kotlin.math.cos(a))).add(0.0,-7.96*kotlin.math.sin(a)-.064,0.0)}
 private fun updateFlight(t:Double) {
  setPos(target);yRot=Math.toDegrees(kotlin.math.atan2(-runDirection.x,runDirection.z)).toFloat();xRot=EagleFlight.pitch(t,attack,flightHeight)
 }
 override fun defineSynchedData() {}
 override fun tick() {
  super.tick();updateFlight(age)
  if(level().isClientSide)return
  refreshOrdnance()
  holdFlightChunks()
  if(!flybyPlayed && age>=26)flybyPlayed=true
  if(age<EagleFlight.attackStart(attack))return
  // A release belongs to a slot, so delayed ticks/save restore never lose or duplicate ammunition.
  val due=if(attack==EagleAttack.STRAFE)
   minOf(attack.count,((age-attack.callTicks+1)*attack.count/attack.duration).toInt())
  else (0 until attack.count).count{attack.releaseAge(it)<=age}
  while(fired<due){
   val released=when(attack){
    EagleAttack.STRAFE->fireRound(fired)
    EagleAttack.ROCKETS->fireRocket(fired)
    else->dropBomb(fired)
   }
   if(!released)break
   fired++
  }
  // Catch up every due release before retiring, including a delayed/restored carrier.
  // Keep the carrier and projectile tickets until the last real impact is resolved.
  if(age>=EagleFlight.end(attack)&&strikeComplete)discard()
 }
 internal fun trackOrdnance(shot:Entity) {activeOrdnance[shot.uuid]=shot}
 private fun dropBomb(index:Int):Boolean {
  val server=level() as ServerLevel
  val shot=when(attack){
   EagleAttack.CLUSTER->ModEntities.EAGLE_CLUSTER_BOMB.get().create(server)
   EagleAttack.HEAVY_BOMB->ModEntities.EAGLE_500KG_BOMB.get().create(server)
   EagleAttack.NAPALM->ModEntities.EAGLE_NAPALM_BOMB.get().create(server)
   EagleAttack.GAS->ModEntities.EAGLE_GAS_BOMB.get().create(server)
   EagleAttack.SMOKE->ModEntities.EAGLE_SMOKE_BOMB.get().create(server)
   else->ModEntities.EAGLE_BOMB.get().create(server)
  }?:return false
  val release=positionAt(attack.releaseAge(index)).add(0.0,-1.2,0.0)
  val aim=target.add(attack.landingOffset(index,runDirection))
  if(shot is EagleClusterBombEntity)shot.configureSplit(uuid,aim,runDirection)
  shot.setPos(release);ownerUuid?.let {shot.owner=server.getEntity(it)}
  val v=EagleFlight.bombVelocity(release,aim);shot.shoot(v.x,v.y,v.z,v.length().toFloat(),0f)
  if(!server.addFreshEntity(shot)){shot.discard();return false}
  trackOrdnance(shot);return true
 }
 internal fun rocketTarget():net.minecraft.world.entity.LivingEntity? {
  val owner=ownerUuid?.let{(level() as? ServerLevel)?.getEntity(it)}
  return level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity::class.java,net.minecraft.world.phys.AABB(target,target).inflate(20.0)) {e->
   e.isAlive&&!e.isRemoved&&e !is net.minecraft.world.entity.player.Player&&e!==owner&&
   (e !is net.minecraft.world.entity.TamableAnimal||!e.isTame)&&!e.isAlliedTo(this)&&
   (owner==null||!e.isAlliedTo(owner))&&
   (e is net.minecraft.world.entity.monster.Enemy||e is net.minecraft.world.entity.Mob&&com.example.sbwhelldiver.config.HelldiversConfig.attackNeutralMobs.get())&&e.distanceToSqr(target)<=400
  }.sortedWith(compareByDescending<net.minecraft.world.entity.LivingEntity>{it.maxHealth}.thenBy{it.distanceToSqr(target)}.thenBy{it.id}).firstOrNull()
 }
 private var rocketAim:Vec3?=null
 private fun fireRocket(index:Int):Boolean {
  val server=level() as ServerLevel;val shot=ModEntities.EAGLE_110MM_ROCKET.get().create(server)?:return false
  // One targeting solution per pair, selected anew for each group in case the previous target died.
  if(index%2==0||rocketAim==null)rocketAim=rocketTarget()?.boundingBox?.center?:target
  val across=Vec3(-runDirection.z,0.0,runDirection.x)
  val release=muzzleAt(attack.releaseAge(index)).add(across.scale(if(index%2==0)-2.6 else 2.6))
  shot.setPos(release);ownerUuid?.let{shot.owner=server.getEntity(it)}
  val aim=rocketAim!!;val time=release.distanceTo(aim)/35.0
  val v=aim.subtract(release).add(0.0,.084*time*(time-1)/2,0.0).normalize()
  shot.shoot(v.x,v.y,v.z,35f,0f)
  if(!server.addFreshEntity(shot)){shot.discard();return false}
  trackOrdnance(shot);return true
 }
 private fun fireRound(index:Int):Boolean {
  val server=level() as ServerLevel;val shot=ModEntities.EAGLE_CANNON_ROUND.get().create(server)?:return false
  val t=EagleFlight.releaseAge(index,false);val release=muzzleAt(t)
  val across=Vec3(-runDirection.z,0.0,runDirection.x).scale(if(index%2==0)-1.1 else 1.1)
  val aim=target.add(runDirection.scale(EagleFlight.strafeOffset(index,flightHeight))).add(across)
  if(index%4!=0)shot.setExplosionDamage(0f)
  shot.setPos(release);ownerUuid?.let {shot.owner=server.getEntity(it)}
  val v=aim.subtract(release).normalize();shot.shoot(v.x,v.y,v.z,20f,0f);if(!server.addFreshEntity(shot)){shot.discard();return false}
  activeOrdnance[shot.uuid]=shot;return true
 }
 private fun sound(name:String,volume:Float) {ModSounds.EVENTS[name]?.get()?.let {level().playSound(null,x,y,z,it,SoundSource.PLAYERS,volume,1f)}}
 private fun holdFlightChunks() {
  val server=level() as? ServerLevel?:return
  // Only prewarm upcoming real releases, not the empty visual flight corridor.
  // Twelve ticks give chunk loading time without holding the entire future salvo.
  val desired=mutableSetOf<ChunkPos>()
  for(index in fired until attack.count)if(attack.releaseAge(index)<=age+12)
   desired.add(ChunkPos(net.minecraft.core.BlockPos.containing(positionAt(attack.releaseAge(index)))))
  // Follow each projectile rather than releasing a fixed flight corridor too soon on high terrain.
  for(shot in activeOrdnance.values)if(!shot.isRemoved){
   desired.add(ChunkPos(shot.blockPosition()))
   desired.add(ChunkPos(net.minecraft.core.BlockPos.containing(shot.position().add(shot.deltaMovement))))
   desired.add(ChunkPos(net.minecraft.core.BlockPos.containing(shot.position().add(shot.deltaMovement.scale(2.0)))))
  }
  desired.addAll(awaitingChunks.values)
  desired.add(ChunkPos(net.minecraft.core.BlockPos.containing(target)))
  for(c in heldChunks-desired)server.chunkSource.removeRegionTicket(TICKET,c,2,id,true)
  for(c in desired-heldChunks)server.chunkSource.addRegionTicket(TICKET,c,2,id,true)
  heldChunks.clear();heldChunks.addAll(desired)
 }
 override fun remove(reason:RemovalReason) {if(java.lang.Boolean.getBoolean("sbw_helldiver.eagleSmokeTest"))com.example.sbwhelldiver.LOGGER.info("EAGLE_REMOVE reason={} age={} fired={} at={}",reason,age,fired,position());val s=level() as? ServerLevel;if(s!=null)for(c in heldChunks)s.chunkSource.removeRegionTicket(TICKET,c,2,id,true);heldChunks.clear();super.remove(reason)}
 override fun shouldRenderAtSqrDistance(distance:Double)=distance<1024.0*1024.0
 override fun getAddEntityPacket():Packet<ClientGamePacketListener> =NetworkHooks.getEntitySpawningPacket(this)
 override fun writeSpawnData(b:FriendlyByteBuf){b.writeDouble(target.x);b.writeDouble(target.y);b.writeDouble(target.z);b.writeDouble(runDirection.x);b.writeDouble(runDirection.z);b.writeEnum(attack);b.writeLong(startedAt);b.writeDouble(flightHeight)}
 override fun readSpawnData(b:FriendlyByteBuf){target=Vec3(b.readDouble(),b.readDouble(),b.readDouble());runDirection=Vec3(b.readDouble(),0.0,b.readDouble());attack=b.readEnum(EagleAttack::class.java);startedAt=b.readLong();flightHeight=b.readDouble();updateFlight(age);xo=x;yo=y;zo=z;xOld=x;yOld=y;zOld=z}
 override fun lerpTo(x:Double,y:Double,z:Double,yaw:Float,pitch:Float,steps:Int,teleport:Boolean) { /* Same clock and path on both sides; vanilla lerp would lag behind the firing position. */ }
 override fun readAdditionalSaveData(n:CompoundTag){target=Vec3(n.getDouble("TargetX"),n.getDouble("TargetY"),n.getDouble("TargetZ"));runDirection=Vec3(n.getDouble("ForwardX"),0.0,n.getDouble("ForwardZ"));attack=if(n.contains("Attack"))EagleAttack.fromId(n.getString("Attack")) else EagleAttack.legacy(n.getBoolean("Airstrike"));startedAt=n.getLong("StartedAt");flightHeight=n.getDouble("Height");fired=n.getInt("Fired");flybyPlayed=n.getBoolean("FlybyPlayed");gunPlayed=n.getBoolean("GunPlayed");ownerUuid=if(n.hasUUID("Owner"))n.getUUID("Owner") else null
  rocketAim=if(n.contains("RocketAimX"))Vec3(n.getDouble("RocketAimX"),n.getDouble("RocketAimY"),n.getDouble("RocketAimZ")) else null
  activeOrdnance.clear();awaitingOrdnance.clear();awaitingChunks.clear();legacyPayloadTracking=n.getBoolean("LegacyPayloadTracking")||!n.contains("ActiveOrdnance")
  for(t in n.getList("ActiveOrdnance",net.minecraft.nbt.Tag.TAG_COMPOUND.toInt())){val c=t as CompoundTag;if(c.hasUUID("Id")){val id=c.getUUID("Id");awaitingOrdnance.add(id);if(c.contains("ChunkX"))awaitingChunks[id]=ChunkPos(c.getInt("ChunkX"),c.getInt("ChunkZ"))}}
  updateFlight(age)}
 override fun addAdditionalSaveData(n:CompoundTag){n.putDouble("TargetX",target.x);n.putDouble("TargetY",target.y);n.putDouble("TargetZ",target.z);n.putDouble("ForwardX",runDirection.x);n.putDouble("ForwardZ",runDirection.z);n.putBoolean("Airstrike",airstrike);n.putString("Attack",attack.id);rocketAim?.let{n.putDouble("RocketAimX",it.x);n.putDouble("RocketAimY",it.y);n.putDouble("RocketAimZ",it.z)};n.putLong("StartedAt",startedAt);n.putDouble("Height",flightHeight);n.putInt("Fired",fired);n.putBoolean("FlybyPlayed",flybyPlayed);n.putBoolean("GunPlayed",gunPlayed);ownerUuid?.let {n.putUUID("Owner",it)}
  val list=net.minecraft.nbt.ListTag()
  val ids=awaitingOrdnance+activeOrdnance.filterValues{!it.isRemoved||it.removalReason==RemovalReason.UNLOADED_TO_CHUNK||it.removalReason==RemovalReason.UNLOADED_WITH_PLAYER}.keys
  for(id in ids)list.add(CompoundTag().also{tag->
   tag.putUUID("Id",id)
   val c=activeOrdnance[id]?.let{ChunkPos(it.blockPosition())} ?: awaitingChunks[id]
   if(c!=null){tag.putInt("ChunkX",c.x);tag.putInt("ChunkZ",c.z)}
  })
  n.put("ActiveOrdnance",list);n.putBoolean("LegacyPayloadTracking",legacyPayloadTracking)
 }
 companion object {private val TICKET:TicketType<Int> =TicketType.create("helldivers_eagle",Comparator.naturalOrder<Int>())}
}
