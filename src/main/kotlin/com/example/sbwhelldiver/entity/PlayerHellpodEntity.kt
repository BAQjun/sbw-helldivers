package com.example.sbwhelldiver.entity

import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Command-only transport; it never enters the stratagem loadout or drops supplies. */
class PlayerHellpodEntity(type:EntityType<*>,level:Level):AbstractHellpodEntity(type,level){
 // Reference clip: ignition ~3.45 s, touchdown ~6.55 s (20 TPS quantization).
 override val scheduledBrakingTicks get()=68
 override val scheduledBrakingHeight get()=maxOf(500.0,descentBrakeHeight())
 override fun remainingBrakeDistance(remaining:Double,distance:Double,ticks:Long,approachSpeed:Double):Double {
  // Match approach velocity at the brake boundary, then decay toward 100 m/s.
  // Integrating the positive velocity keeps altitude monotonic; no hover or bounce.
  val terminal=minOf(5.0,distance/ticks*.7)
  val tail=terminal*ticks
  val exponent=((maxOf(approachSpeed,distance/ticks)-terminal)*ticks/(distance-tail).coerceAtLeast(.001)).coerceAtLeast(1.0)
  return tail*remaining+(distance-tail)*Math.pow(remaining,exponent)
 }
 var occupant:UUID?=null;private set
 private var exit=Vec3.ZERO
 private var released=false
 private var missingTicks=0
 private var launchPoint=Vec3.ZERO
 private var steerForward=0f
 private var steerLeft=0f
 private var inputAt=-100L
 override fun defineSynchedData(builder: SynchedEntityData.Builder){super.defineSynchedData(builder);builder.define(TARGET_X,0L);builder.define(TARGET_Z,0L)}
 override fun tickClient(){
  val tx=Double.fromBits(entityData.get(TARGET_X));val tz=Double.fromBits(entityData.get(TARGET_Z))
  setPos(tx,y,tz)
 }
 fun acceptSteering(player:ServerPlayer,forward:Float,left:Float){
  if(player.vehicle!==this||phase!=Phase.FALLING||!forward.isFinite()||!left.isFinite()||kotlin.math.abs(forward)>1.01f||kotlin.math.abs(left)>1.01f)return
  steerForward=forward;steerLeft=left;inputAt=level().gameTime
 }
 private fun steer(){
  val l=level() as? ServerLevel?:return
  if(phase!=Phase.FALLING||l.gameTime-inputAt>5||y-landingY<6.0)return
  val forward=Vec3.directionFromRotation(0f,yRot)
  var move=forward.scale(steerForward.toDouble()).add(Vec3(forward.z,0.0,-forward.x).scale(steerLeft.toDouble()))
  if(move.lengthSqr()<.001)return
  move=move.scale(.45/kotlin.math.max(1.0,move.length()))
  val next=Vec3(x+move.x,0.0,z+move.z)
  if(next.subtract(launchPoint).multiply(1.0,0.0,1.0).lengthSqr()>48.0*48.0)return
  l.getChunk(net.minecraft.core.BlockPos.containing(next.x,exit.y,next.z))
  val ground=l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,kotlin.math.floor(next.x).toInt(),kotlin.math.floor(next.z).toInt()).toDouble()
  val player=passengers.firstOrNull() as? Player?:return
  val target=Vec3(next.x,ground,next.z)
  if(ground>=y-4.0||!safeLanding(l,player,target)||PlayerDrops.reserved(l,target))return
  if(l.getEntitiesOfClass(PlayerHellpodEntity::class.java,net.minecraft.world.phys.AABB(target,target).inflate(3.0,1500.0,3.0)){it!==this&&!it.isRemoved}.isNotEmpty())return
  exit=target;retargetSurface(ground);setPos(next.x,y,next.z);entityData.set(TARGET_X,x.toRawBits());entityData.set(TARGET_Z,z.toRawBits())
 }
 var releasing=false;private set
 fun launch(player:ServerPlayer,landing:Vec3):Boolean {
  val l=level() as? ServerLevel?:return false
  occupant=player.uuid;exit=landing;launchPoint=landing;entityData.set(TARGET_X,landing.x.toRawBits());entityData.set(TARGET_Z,landing.z.toRawBits());yRot=player.yRot;yRotO=yRot
  val high=landing.y+maxOf(1500,descentSpawnHeight())+ORIGIN_ABOVE_BOTTOM
  setPos(landing.x,high,landing.z);scheduleArrival(landing.y,l.gameTime+PLAYER_FALL_TICKS)
  if(!l.addFreshEntity(this))return false
  player.stopRiding();player.teleportTo(l,x,y-4.25,z,player.yRot,0f)
  if(player.level()!==l)return false
  player.fallDistance=0f;player.deltaMovement=Vec3.ZERO
  if(player.startRiding(this,true))return true
  player.teleportTo(l,landing.x,landing.y+.35,landing.z,player.yRot,0f);return false
 }
 override fun canAddPassenger(passenger:Entity)=passengers.isEmpty()&&passenger is Player&&passenger.uuid==occupant&&!released
 override fun shouldRiderSit()=false
 fun visualOrigin(partial:Float):Vec3 {
  if(!level().isClientSide||phase!=Phase.FALLING)return getPosition(partial)
  val clock=if(flightSampleTime.isNaN())level().gameTime.toDouble() else flightSampleTime
  return Vec3(net.minecraft.util.Mth.lerp(partial.toDouble(),xOld,x),scheduledHeight(clock-1+partial),net.minecraft.util.Mth.lerp(partial.toDouble(),zOld,z))
 }
 override fun ejectPassengers(){if(level().isClientSide||releasing)super.ejectPassengers()}
 override fun interact(p:Player,hand:net.minecraft.world.InteractionHand)=net.minecraft.world.InteractionResult.PASS
 override fun hurt(source:net.minecraft.world.damagesource.DamageSource,amount:Float)=false
 override fun automaticRetractionEnabled()=true // Passenger delivery cleanup is independent of stratagem settings.
 override fun shouldRetract()=released&&activeTicks>=60
 private var visualPhase:Phase?=null
 private var visualPhaseTick=0
 private var visualPhaseOffset=0.0
 /** The server's age packets are samples, not the render clock. Anchor once per
  * phase, then advance with the same local tick and partial tick as rendering.
  * Re-reading packet age every frame can rewind between asynchronous updates.
  */
 fun visualPhaseAge(partial:Float):Double {
  if(!level().isClientSide)return phaseAge+partial.toDouble()
  if(visualPhase!=phase){
   visualPhase=phase;visualPhaseTick=tickCount
   visualPhaseOffset=(level().gameTime-phaseStartTime).coerceAtLeast(0).toDouble()
  }
  return (visualPhaseOffset+tickCount-visualPhaseTick-1+partial).coerceAtLeast(0.0)
 }
 fun riderPosition(partial:Float=1f):Vec3 {
  val lift=if(phase==Phase.DEPLOYING)((visualPhaseAge(partial)-9)/12.0).coerceIn(0.0,1.0) else if(phase==Phase.ACTIVE)1.0 else 0.0
  return getPosition(partial).add(0.0,-4.25+4.49*lift,0.0)
 }
 override fun positionRider(passenger:Entity,move:Entity.MoveFunction){
  if(!hasPassenger(passenger))return
  val p=riderPosition();move.accept(passenger,p.x,p.y,p.z);passenger.fallDistance=0f
  if(passenger is LivingEntity)passenger.yBodyRot=yRot
 }
 override fun getDismountLocationForPassenger(passenger:LivingEntity)=exit
 override fun onActivated(){release()}
 fun release(){
  if(level().isClientSide)return
  releasing=true
  for(p in passengers.toList()){
   p.noPhysics=false;p.stopRiding();p.fallDistance=0f;p.deltaMovement=Vec3.ZERO
   if(p is ServerPlayer){p.teleportTo(level() as ServerLevel,exit.x,exit.y+.35,exit.z,yRot,0f);p.connection.resetPosition()}
  }
  released=true;releasing=false
 }
 override fun tick(){
  if(!level().isClientSide)steer()
  super.tick()
  if(level().isClientSide)return
  val p=passengers.firstOrNull() as? ServerPlayer
  if(!released&&occupant!=null){
   if(p==null){if(++missingTicks>40){release();discard()}}else{missingTicks=0;p.fallDistance=0f;p.deltaMovement=Vec3.ZERO;p.noPhysics=true}
  }
 }
 override fun remove(reason:Entity.RemovalReason){if(!level().isClientSide){passengers.forEach{it.noPhysics=false};if(reason!=Entity.RemovalReason.UNLOADED_TO_CHUNK&&reason!=Entity.RemovalReason.UNLOADED_WITH_PLAYER)release()};super.remove(reason)}
 override fun addAdditionalSaveData(n:CompoundTag){super.addAdditionalSaveData(n);occupant?.let{n.putUUID("DropPlayer",it)};n.putDouble("ExitX",exit.x);n.putDouble("ExitY",exit.y);n.putDouble("ExitZ",exit.z);n.putBoolean("DropReleased",released);n.putDouble("DropLaunchX",launchPoint.x);n.putDouble("DropLaunchZ",launchPoint.z)}
 override fun readAdditionalSaveData(n:CompoundTag){super.readAdditionalSaveData(n);occupant=if(n.hasUUID("DropPlayer"))n.getUUID("DropPlayer") else null;exit=Vec3(n.getDouble("ExitX"),n.getDouble("ExitY"),n.getDouble("ExitZ"));released=n.getBoolean("DropReleased");launchPoint=Vec3(n.getDouble("DropLaunchX"),exit.y,n.getDouble("DropLaunchZ"));entityData.set(TARGET_X,x.toRawBits());entityData.set(TARGET_Z,z.toRawBits())}
 companion object {
  const val PLAYER_FALL_TICKS=131
  const val JET_START_TICKS=6
  private val TARGET_X=net.minecraft.network.syncher.SynchedEntityData.defineId(PlayerHellpodEntity::class.java,net.minecraft.network.syncher.EntityDataSerializers.LONG)
  private val TARGET_Z=net.minecraft.network.syncher.SynchedEntityData.defineId(PlayerHellpodEntity::class.java,net.minecraft.network.syncher.EntityDataSerializers.LONG)
  fun safeLanding(l:ServerLevel,p:Player,at:Vec3):Boolean {
   val feet=net.minecraft.core.BlockPos.containing(at)
   return at.y>=l.minBuildHeight+1&&at.y<l.maxBuildHeight-2&&l.worldBorder.isWithinBounds(feet)&&
    l.getBlockState(feet.below()).isFaceSturdy(l,feet.below(),net.minecraft.core.Direction.UP)&&
    l.getFluidState(feet).isEmpty&&l.noCollision(p,p.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(at))
  }
 }
}
