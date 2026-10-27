package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.config.OrbitalLaserSettings
import com.example.sbwhelldiver.effect.FlameDamage
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.NetworkHooks
import java.util.UUID

/** One moving impact controller, not one entity per beam segment. Never loads ship chunks. */
class OrbitalLaserEntity(type:EntityType<*>,level:Level):Entity(type,level) {
 private var settingsCache:OrbitalLaserSettings?=null
 val settings get()=settingsCache?:OrbitalLaserSettings.load(entityData.get(SETTINGS)).also{settingsCache=it}
 override fun onSyncedDataUpdated(key:net.minecraft.network.syncher.EntityDataAccessor<*>){
  super.onSyncedDataUpdated(key);if(key==SETTINGS)settingsCache=null
 }
 val startedAt get()=entityData.get(START)
 val endsAt get()=startedAt+settings.durationTicks
 val active get()=startedAt>=0 && level().gameTime<endsAt && !isRemoved
 val origin get()=vec(entityData.get(ORIGIN))
 private var center=Vec3.ZERO
 private var cursor=Vec3.ZERO
 private var sourceUuid:UUID?=null
 private var targetUuid:UUID?=null
 private val candidates=mutableListOf<UUID>()
 private var nextRefresh=0L
 private var nextPulse=0L
 private var lerpSteps=0
 private var lerpGoal=Vec3.ZERO
 val targetId get()=entityData.get(TARGET)
 init {noPhysics=true;isNoGravity=true}
 fun configure(at:Vec3,ship:Vec3,owner:UUID?,s:OrbitalLaserSettings){
  center=at;cursor=at;setPos(at);sourceUuid=owner;entityData.set(ORIGIN,tag(ship));entityData.set(SETTINGS,s.save())
  entityData.set(START,level().gameTime);nextPulse=level().gameTime+s.interval;nextRefresh=0
 }
 override fun defineSynchedData(){entityData.define(SETTINGS,OrbitalLaserSettings().save());entityData.define(ORIGIN,CompoundTag());entityData.define(START,-1L);entityData.define(TARGET,-1)}
 override fun getAddEntityPacket()=NetworkHooks.getEntitySpawningPacket(this)
 private fun valid(m:LivingEntity):Boolean {
  val owner=sourceUuid?.let{(level() as? ServerLevel)?.getEntity(it)}
  return m.isAlive && !m.isRemoved && !m.isInvulnerable && !m.isSpectator && m !is Player &&
   !(m is TamableAnimal && m.isTame) && !m.isAlliedTo(this) && (owner==null || m!=owner && !m.isAlliedTo(owner)) &&
   (m is Enemy || m is Mob && com.example.sbwhelldiver.config.HelldiversConfig.attackNeutralMobs.get()) && m.distanceToSqr(center)<=settings.range*settings.range
 }
 /** Native data exposes refresh rate, not enemy-class scoring. Max-health priority is the MC adaptation. */
 fun selectTarget():LivingEntity? {
  val server=level() as? ServerLevel?:return null
  return candidates.mapNotNull{server.getEntity(it) as? LivingEntity}.filter(::valid)
   .minWithOrNull(compareByDescending<LivingEntity>{it.maxHealth}.thenBy{it.distanceToSqr(position())}.thenBy{it.id})
 }
 /** Refuse unloaded terrain rather than forcing chunks while tracing toward the ship. */
 private fun traceStart(at:Vec3):Vec3? {
  val top=at.add(origin.subtract(at).normalize().scale(96.0))
  for(i in 0..12)if(!level().hasChunkAt(net.minecraft.core.BlockPos.containing(at.lerp(top,i/12.0))))return null
  return top
 }
 private fun exposed(m:LivingEntity):Boolean {
  val aim=m.position().add(0.0,minOf(.5,m.bbHeight*.25),0.0);val top=traceStart(aim)?:return false
  return level().clip(ClipContext(top,aim,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).type!=net.minecraft.world.phys.HitResult.Type.BLOCK
 }
 private fun refresh(server:ServerLevel){
  candidates.clear();candidates.addAll(server.getEntitiesOfClass(LivingEntity::class.java,AABB(center,center).inflate(settings.range)){valid(it)&&exposed(it)}.map{it.uuid})
  nextRefresh=server.gameTime+settings.refreshTicks
 }
 override fun lerpTo(x:Double,y:Double,z:Double,yaw:Float,pitch:Float,steps:Int,teleport:Boolean){
  lerpGoal=Vec3(x,y,z)
  if(teleport || position().distanceToSqr(lerpGoal)>4096.0){setPos(lerpGoal);lerpSteps=0}else lerpSteps=2
 }
 override fun tick(){
  super.tick();if(level().isClientSide){
   if(lerpSteps>0){setPos(position().lerp(lerpGoal,1.0/lerpSteps));lerpSteps--};return
  }
  val server=level() as ServerLevel
  if(!com.example.sbwhelldiver.stratagem.Stratagems.dimensionAllowed(server.dimension().location().toString()) || !active){discard();return}
  if(server.gameTime>=nextRefresh)refresh(server)
  var target=targetUuid?.let{server.getEntity(it) as? LivingEntity}?.takeIf{valid(it)&&it.uuid in candidates}
  if(target==null){target=selectTarget();targetUuid=target?.uuid}
  entityData.set(TARGET,target?.id?:-1)
  if(target!=null){
   // Steering is independent of surface contact: the tip may hit a creature above the cursor.
   val aim=target.position().add(0.0,minOf(.5,target.bbHeight*.25),0.0)
   cursor=stepToward(cursor,aim,settings.speed/20.0)
  }
  updateContact(server)

  val pulse=server.gameTime>=nextPulse
  if(pulse)nextPulse=server.gameTime+settings.interval
  affect(pulse)
 }
 /** Nearest terrain/creature contact along the visible incoming beam, including collateral targets. */
 private fun updateContact(server:ServerLevel){
  val top=traceStart(cursor)?:return
  val block=server.clip(ClipContext(top,cursor,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this))
  var contact=if(block.type==net.minecraft.world.phys.HitResult.Type.BLOCK)block.location else cursor
  var distance=top.distanceToSqr(contact)
  for(m in server.getEntitiesOfClass(LivingEntity::class.java,AABB(top,contact).inflate(.1)){it.isAlive&&!it.isSpectator}){
   val hit=m.boundingBox.clip(top,contact).orElse(null)?:continue
   val d=top.distanceToSqr(hit)
   if(d<distance){distance=d;contact=hit}
  }
  setPos(contact)
 }
 /** The sphere is centered on the rendered impact, so creatures crossed during a sweep can burn too. */
 fun affect(pulse:Boolean){
  val server=level() as? ServerLevel?:return;val s=settings;val at=position()
  val attacker=sourceUuid?.let(server::getEntity)?:this
  server.getEntitiesOfClass(LivingEntity::class.java,AABB(at,at).inflate(s.radius)){it.isAlive && !it.isSpectator}.forEach { m ->
   val b=m.boundingBox;val nearest=Vec3(at.x.coerceIn(b.minX,b.maxX),at.y.coerceIn(b.minY,b.maxY),at.z.coerceIn(b.minZ,b.maxZ))
   if(nearest.distanceToSqr(at)>s.radius*s.radius)return@forEach
   // Local terrain can stop the beam; do not burn entities through the roof at the impact.
   val end=nearest.lerp(m.boundingBox.center,.01)
   if(server.clip(ClipContext(at.add(0.0,.02,0.0),end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this)).type==net.minecraft.world.phys.HitResult.Type.BLOCK)return@forEach
   if(pulse)m.hurt(FlameDamage.source(m,"laser_beam",attacker),s.damage)
   FlameDamage.ignite(m,attacker)
  }
 }
 override fun shouldRenderAtSqrDistance(distance:Double)=distance<256.0*256.0
 override fun addAdditionalSaveData(n:CompoundTag){
  n.put("Settings",entityData.get(SETTINGS));n.put("Origin",entityData.get(ORIGIN));n.put("Center",tag(center));n.put("Cursor",tag(cursor));n.putLong("Start",startedAt)
  n.putLong("NextPulse",nextPulse);sourceUuid?.let{n.putUUID("Source",it)};targetUuid?.let{n.putUUID("Target",it)}
 }
 override fun readAdditionalSaveData(n:CompoundTag){
  entityData.set(SETTINGS,OrbitalLaserSettings.load(n.getCompound("Settings")).save());entityData.set(ORIGIN,n.getCompound("Origin"))
  center=vec(n.getCompound("Center"));cursor=if(n.contains("Cursor"))vec(n.getCompound("Cursor")) else position();entityData.set(START,n.getLong("Start"));nextPulse=n.getLong("NextPulse")
  sourceUuid=if(n.hasUUID("Source"))n.getUUID("Source") else null;targetUuid=if(n.hasUUID("Target"))n.getUUID("Target") else null
  candidates.clear();nextRefresh=0;entityData.set(TARGET,-1)
 }
 companion object {
  fun stepToward(from:Vec3,to:Vec3,maxStep:Double):Vec3 {val delta=to.subtract(from);val length=delta.length();return if(length<=maxStep)to else from.add(delta.scale(maxStep/length))}
  private fun tag(v:Vec3)=CompoundTag().also{it.putDouble("X",v.x);it.putDouble("Y",v.y);it.putDouble("Z",v.z)}
  private fun vec(t:CompoundTag)=Vec3(t.getDouble("X"),t.getDouble("Y"),t.getDouble("Z"))
  private val SETTINGS=SynchedEntityData.defineId(OrbitalLaserEntity::class.java,EntityDataSerializers.COMPOUND_TAG)
  private val ORIGIN=SynchedEntityData.defineId(OrbitalLaserEntity::class.java,EntityDataSerializers.COMPOUND_TAG)
  private val START=SynchedEntityData.defineId(OrbitalLaserEntity::class.java,EntityDataSerializers.LONG)
  private val TARGET=SynchedEntityData.defineId(OrbitalLaserEntity::class.java,EntityDataSerializers.INT)
 }
}
