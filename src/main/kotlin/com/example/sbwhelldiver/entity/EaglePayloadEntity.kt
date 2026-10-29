package com.example.sbwhelldiver.entity

import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import com.atsuishio.superbwarfare.tools.ParticleTool
import com.example.sbwhelldiver.config.EagleConfig
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import java.util.UUID
import kotlin.math.*

/** One real carrier projectile becomes eight individually colliding bomblets. */
class EagleClusterBombEntity(type:EntityType<out SmallCannonShellEntity>,level:Level):SentryCannonShellEntity(type,level) {
 override val innerBlastRadius get()=1.0
 private var splitTicks=EagleConfig.clusterSplitTicks()
 private var elapsed=0
 private var emitted=0
 private var carrier:UUID?=null
 private var aim=Vec3.ZERO
 private var forward=Vec3(0.0,0.0,1.0)
 init{setDamage(240f);setExplosionDamage(60f);setExplosionRadius(2f);setCustomGravity(EagleFlight.BOMB_GRAVITY.toFloat())}
 fun configureSplit(flight:UUID,point:Vec3,direction:Vec3){carrier=flight;aim=point;forward=direction}
 override fun crushProjectile(velocity:Vec3) {} // Sibling payloads must never intercept one another.
 override fun tick(){
  super.tick()
  if(level().isClientSide||isRemoved)return
  elapsed++
  if(elapsed>=splitTicks)split()
 }
 private fun split(){
  val server=level() as? ServerLevel?:return
  val across=Vec3(-forward.z,0.0,forward.x)
  while(emitted<8){
   val child=ModEntities.EAGLE_CLUSTER_BOMBLET.get().create(server)?:return
   // Two staggered rows cover a broad strip, symmetric about the mother's intended impact.
   val side=(emitted%4-1.5)*8.0
   val along=if(emitted<4)-4.0 else 4.0
   val hit=aim.add(across.scale(side)).add(forward.scale(along))
   val t=max(6,EagleFlight.BOMB_FLIGHT-elapsed).toDouble()
   val v=hit.subtract(position()).scale(1/t).add(0.0,EagleFlight.BOMB_GRAVITY*(t-1)/2,0.0)
   child.owner=owner;child.setPos(position());child.shoot(v.x,v.y,v.z,v.length().toFloat(),0f)
   if(!server.addFreshEntity(child)){child.discard();return}
   // Transfer lifetime tracking before removing the mother. The beacon must await all 64 impacts.
   (carrier?.let{server.getEntity(it)} as? EagleEntity)?.trackOrdnance(child)
   emitted++
  }
  server.sendParticles(ParticleTypes.POOF,x,y,z,5,.25,.12,.25,.02)
  discard() // Separation does not also cause a mother-bomb explosion.
 }
 override fun addAdditionalSaveData(n:CompoundTag){
  super.addAdditionalSaveData(n);n.putInt("SplitTicks",splitTicks);n.putInt("SplitElapsed",elapsed);n.putInt("BombletsEmitted",emitted)
  carrier?.let{n.putUUID("Carrier",it)}
  n.putDouble("AimX",aim.x);n.putDouble("AimY",aim.y);n.putDouble("AimZ",aim.z);n.putDouble("RunX",forward.x);n.putDouble("RunZ",forward.z)
 }
 override fun readAdditionalSaveData(n:CompoundTag){
  super.readAdditionalSaveData(n);splitTicks=if(n.contains("SplitTicks"))n.getInt("SplitTicks").coerceAtLeast(1) else EagleConfig.clusterSplitTicks()
  elapsed=n.getInt("SplitElapsed");emitted=n.getInt("BombletsEmitted").coerceIn(0,8);carrier=if(n.hasUUID("Carrier"))n.getUUID("Carrier") else null
  aim=Vec3(n.getDouble("AimX"),n.getDouble("AimY"),n.getDouble("AimZ"));forward=Vec3(n.getDouble("RunX"),0.0,n.getDouble("RunZ"))
 }
}
class EagleClusterBombletEntity(type:EntityType<out SmallCannonShellEntity>,level:Level):SentryCannonShellEntity(type,level){
 override val innerBlastRadius get()=3.0
 override val explosionParticleType get()=ParticleTool.ParticleType.SMALL
 init{setDamage(3f);setExplosionDamage(60f);setExplosionRadius(6f);setCustomGravity(EagleFlight.BOMB_GRAVITY.toFloat())}
 override fun crushProjectile(velocity:Vec3) {}
}
class Eagle110mmRocketEntity(type:EntityType<out SmallCannonShellEntity>,level:Level):SentryCannonShellEntity(type,level){
 override val innerBlastRadius get()=1.6
 init{setDamage(100f);setExplosionDamage(50f);setExplosionRadius(5f);setCustomGravity(.084f)}
 override fun crushProjectile(velocity:Vec3) {}
 override fun projectileMove(level:Level){
  val from=position();super.projectileMove(level)
  if(!isRemoved)RocketTrails.send(this,from,position())
 }
 override fun onHit(result:net.minecraft.world.phys.HitResult){
  RocketTrails.send(this,position(),result.location);super.onHit(result)
 }
 override fun getSound()=com.atsuishio.superbwarfare.init.ModSounds.ROCKET_FLY.get()
 override fun getVolume()=.2f
}
/** Direct impact settles immediately; the embedded bomb detonates once after its saved fuse. */
class Eagle500kgBombEntity(type:EntityType<out SmallCannonShellEntity>,level:Level):SentryCannonShellEntity(type,level){
 override val innerBlastRadius get()=10.0
 override val explosionParticleType get()=ParticleTool.ParticleType.HUGE
 private var detonateAt=-1L
 private var fuseTicks=EagleConfig.heavyBombFuseTicks()
 private var attachedTo:UUID?=null
 private var attachedOffset=Vec3.ZERO
 private var attachedDirection=Vec3(0.0,-1.0,0.0)
 private var clickAt=-1L
 private var clickPlayed=false
 val embedded get()=entityData.get(EMBEDDED)
 val embeddedDirection:Vec3 get(){val v=entityData.get(IMPACT_DIRECTION);return Vec3(v.x.toDouble(),v.y.toDouble(),v.z.toDouble())}
 private fun setEmbeddedDirection(v:Vec3){entityData.set(IMPACT_DIRECTION,org.joml.Vector3f(v.x.toFloat(),v.y.toFloat(),v.z.toFloat()))}
 init{setDamage(320f);setExplosionDamage(240f);setExplosionRadius(25f);setCustomGravity(EagleFlight.BOMB_GRAVITY.toFloat())}
 override fun defineSynchedData(builder: SynchedEntityData.Builder){super.defineSynchedData(builder);builder.define(EMBEDDED,false);builder.define(IMPACT_DIRECTION,org.joml.Vector3f(0f,-1f,0f))}
 private fun embed(at:Vec3,hit:net.minecraft.world.entity.Entity?=null){
  if(embedded||level().isClientSide)return
  setEmbeddedDirection(if(deltaMovement.lengthSqr()>1e-8)deltaMovement.normalize() else Vec3.directionFromRotation(xRot,yRot))
  setPos(at);deltaMovement=Vec3.ZERO;setCustomGravity(0f);noPhysics=true;isNoGravity=true
  entityData.set(EMBEDDED,true);detonateAt=level().gameTime+fuseTicks
  if(hit!=null&&!hit.isRemoved&&(hit !is net.minecraft.world.entity.LivingEntity||hit.isAlive)){
   attachedTo=hit.uuid;val yaw=Math.toRadians(hit.yRot.toDouble()).toFloat();attachedOffset=at.subtract(hit.position()).yRot(yaw);attachedDirection=embeddedDirection.yRot(yaw)
  }
  clickAt=level().gameTime+min(6,fuseTicks)
  playCue("sfx_eagle_500kg_impact")
  (level() as ServerLevel).sendParticles(ParticleTypes.SMOKE,x,y+.2,z,8,.15,.2,.15,.02)
 }
 override fun onHitEntity(r:EntityHitResult){if(!embedded)super.onHitEntity(r)}
 override fun onHitBlock(r:BlockHitResult){if(!embedded)super.onHitBlock(r)}
 override fun afterHitEntity(r:EntityHitResult){
  val hit=r.entity.let{if(it is net.neoforged.neoforge.entity.PartEntity<*>)it.parent else it}
  embed(r.location,hit)
 }
 override fun afterHitBlock(r:BlockHitResult)=embed(r.location)
 override fun projectileMove(level:Level){if(!embedded)super.projectileMove(level)}
 override fun crushProjectile(velocity:Vec3) {}
 override fun tick(){
  if(!embedded){super.tick();return}
  // Do not run SBW's collision loop on a stationary embedded bomb (repeated direct damage).
  tickCount++;xo=x;yo=y;zo=z
  if(level().isClientSide){deltaMovement=Vec3.ZERO;return}
  val host=attachedTo?.let{(level() as ServerLevel).getEntity(it)}
  if(host!=null&&!host.isRemoved&&(host !is net.minecraft.world.entity.LivingEntity||host.isAlive)){
   val yaw=-Math.toRadians(host.yRot.toDouble()).toFloat()
   setPos(host.position().add(attachedOffset.yRot(yaw)));setEmbeddedDirection(attachedDirection.yRot(yaw))
  }
  if(!clickPlayed&&level().gameTime>=clickAt){playCue("sfx_eagle_500kg_fuse");clickPlayed=true}
  if(level().gameTime>=detonateAt){causeExplode(position());discard()}
 }
 private fun playCue(key:String){com.example.sbwhelldiver.init.ModSounds.EVENTS[key]?.get()?.let{level().playSound(null,x,y,z,it,net.minecraft.sounds.SoundSource.PLAYERS,1f,1f)}}
 override fun addAdditionalSaveData(n:CompoundTag){
  super.addAdditionalSaveData(n)
  attachedTo?.let{n.putUUID("AttachedTo",it)};n.putDouble("AttachX",attachedOffset.x);n.putDouble("AttachY",attachedOffset.y);n.putDouble("AttachZ",attachedOffset.z)
  val facing=embeddedDirection;n.putDouble("FacingX",facing.x);n.putDouble("FacingY",facing.y);n.putDouble("FacingZ",facing.z);n.putDouble("AttachDirX",attachedDirection.x);n.putDouble("AttachDirY",attachedDirection.y);n.putDouble("AttachDirZ",attachedDirection.z)
  n.putLong("ClickAt",clickAt);n.putBoolean("ClickPlayed",clickPlayed);n.putLong("DetonateAt",detonateAt);n.putInt("FuseTicks",fuseTicks);n.putBoolean("Embedded",embedded)}
 override fun readAdditionalSaveData(n:CompoundTag){
  super.readAdditionalSaveData(n)
  attachedTo=if(n.hasUUID("AttachedTo"))n.getUUID("AttachedTo") else null;attachedOffset=Vec3(n.getDouble("AttachX"),n.getDouble("AttachY"),n.getDouble("AttachZ"))
  if(n.contains("FacingX"))setEmbeddedDirection(Vec3(n.getDouble("FacingX"),n.getDouble("FacingY"),n.getDouble("FacingZ")))
  attachedDirection=if(n.contains("AttachDirX"))Vec3(n.getDouble("AttachDirX"),n.getDouble("AttachDirY"),n.getDouble("AttachDirZ")) else embeddedDirection
  clickAt=n.getLong("ClickAt");clickPlayed=n.getBoolean("ClickPlayed");detonateAt=n.getLong("DetonateAt");fuseTicks=if(n.contains("FuseTicks"))n.getInt("FuseTicks") else EagleConfig.heavyBombFuseTicks()
  entityData.set(EMBEDDED,n.getBoolean("Embedded"));if(embedded){deltaMovement=Vec3.ZERO;setCustomGravity(0f);noPhysics=true;isNoGravity=true}
 }
 companion object{
  private val EMBEDDED=SynchedEntityData.defineId(Eagle500kgBombEntity::class.java,EntityDataSerializers.BOOLEAN)
  private val IMPACT_DIRECTION=SynchedEntityData.defineId(Eagle500kgBombEntity::class.java,EntityDataSerializers.VECTOR3)
 }
}
