package com.example.sbwhelldiver.entity

import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import com.atsuishio.superbwarfare.tools.ParticleTool
import com.example.sbwhelldiver.config.AirburstSettings
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.stratagem.OrbitalAirburstPattern
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.*
import java.util.UUID

/** A real, collidable terminal shell, fused at the exact burst plane even at high speed. */
class OrbitalAirburstShellEntity(t:EntityType<out SmallCannonShellEntity>,l:Level):SentryCannonShellEntity(t,l){
 private var settings=AirburstSettings()
 private var markerId:UUID?=null
 private var burstY=Double.NEGATIVE_INFINITY
 private var split=false
 private var emitted=0
 private var axis=Vec3(0.0,-1.0,0.0)
 override val innerBlastRadius get()=settings.inner
 override val explosionParticleType get()=ParticleTool.ParticleType.SMALL
 init{configure(AirburstSettings(),null,Double.NEGATIVE_INFINITY,axis)}
 fun configure(s:AirburstSettings,marker:UUID?,height:Double,direction:Vec3){
  settings=s;markerId=marker;burstY=height;axis=direction.normalize()
  setDamage(s.direct);setExplosionDamage(s.blast);setExplosionRadius(s.outer);setCustomGravity(0f)
 }
 override fun crushProjectile(velocity:Vec3) {} // Siblings and nearby shells must not detonate one another in flight.
 override fun tick(){
  // SBW sweeps collisions before projectileMove; trim the sweep itself at the fuse plane.
  val motion=deltaMovement
  if(!level().isClientSide&&!split&&motion.y<0&&y+motion.y<=burstY){
   val fraction=((y-burstY)/-motion.y).coerceIn(0.0,1.0)
   deltaMovement=motion.scale(fraction)
  }
  super.tick()
 }
 override fun projectileMove(level:Level){
  super.projectileMove(level)
  if(!level.isClientSide&&!split&&!isRemoved&&y<=burstY+1e-6){causeExplode(position());discard()}
 }
 override fun causeExplode(at:Vec3){
  val server=level() as? ServerLevel?:return
  if(split)return
  split=true
  super.causeExplode(at)
  val directions=OrbitalAirburstPattern.directions(axis,settings,uuid.mostSignificantBits xor uuid.leastSignificantBits)
  while(emitted<settings.children){
   val child=ModEntities.ORBITAL_AIRBURST_BOMBLET.get().create(server)?:break
   val direction=directions[emitted]
   child.configure(settings);child.owner=owner;child.setPos(at)
   child.shoot(direction.x,direction.y,direction.z,settings.childSpeed.toFloat(),0f)
   if(server.addFreshEntity(child)) (markerId?.let(server::getEntity) as? StratagemMarker)?.trackAirburst(child)
   emitted++
  }
 }
 override fun afterHitEntity(r:EntityHitResult){causeExplode(r.location);discard()}
 override fun afterHitBlock(r:BlockHitResult){causeExplode(r.location);discard()}
 override fun addAdditionalSaveData(t:CompoundTag){super.addAdditionalSaveData(t);t.put("AirburstSettings",settings.save());markerId?.let{t.putUUID("AirburstMarker",it)};t.putDouble("BurstY",burstY);t.putBoolean("Split",split);t.putInt("Emitted",emitted);t.putDouble("AxisX",axis.x);t.putDouble("AxisY",axis.y);t.putDouble("AxisZ",axis.z)}
 override fun readAdditionalSaveData(t:CompoundTag){super.readAdditionalSaveData(t);configure(AirburstSettings.load(t.getCompound("AirburstSettings")),if(t.hasUUID("AirburstMarker"))t.getUUID("AirburstMarker") else null,t.getDouble("BurstY"),Vec3(t.getDouble("AxisX"),t.getDouble("AxisY"),t.getDouble("AxisZ")));split=t.getBoolean("Split");emitted=t.getInt("Emitted").coerceIn(0,settings.children)}
}
class OrbitalAirburstBombletEntity(t:EntityType<out SmallCannonShellEntity>,l:Level):SentryCannonShellEntity(t,l){
 private var settings=AirburstSettings()
 override val innerBlastRadius get()=settings.childInner
 override val explosionParticleType get()=ParticleTool.ParticleType.SMALL
 init{configure(settings)}
 fun configure(s:AirburstSettings){settings=s;setDamage(s.childDirect);setExplosionDamage(s.childBlast);setExplosionRadius(s.childOuter);setCustomGravity(.05f)}
 override fun crushProjectile(velocity:Vec3) {}
 override fun addAdditionalSaveData(t:CompoundTag){super.addAdditionalSaveData(t);t.put("AirburstSettings",settings.save())}
 override fun readAdditionalSaveData(t:CompoundTag){super.readAdditionalSaveData(t);configure(AirburstSettings.load(t.getCompound("AirburstSettings")))}
}
