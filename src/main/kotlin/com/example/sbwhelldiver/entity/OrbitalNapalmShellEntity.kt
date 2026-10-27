package com.example.sbwhelldiver.entity

import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import com.example.sbwhelldiver.config.OrbitalBarrageConfig
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** Real terminal collision, one dual-radius blast, then the Eagle's terrain-aware heavy napalm. */
class OrbitalNapalmShellEntity(t:EntityType<out SmallCannonShellEntity>,l:Level):OrbitalPrecisionShellEntity(t,l){
 private var fireRadius=GroundFireEntity.NAPALM_RADIUS
 private var fireTicks=GroundFireEntity.NAPALM_LIFETIME
 init{val s=OrbitalBarrageConfig.snapshot("orbital_napalm_barrage");configure(s.direct,s.blast,s.inner,s.outer);configureFire(s.fireRadius,s.fireTicks)}
 fun configureFire(radius:Double,ticks:Int){fireRadius=radius.coerceIn(.1,16.0);fireTicks=ticks.coerceIn(1,72000)}
 override fun causeExplode(vec3:Vec3){
  val server=level() as? ServerLevel?:return
  if(exploded)return
  super.causeExplode(vec3)
  val ground=server.clip(ClipContext(vec3.add(0.0,.15,0.0),vec3.add(0.0,-64.0,0.0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this))
  val at=if(ground.type==HitResult.Type.BLOCK)ground.location else vec3
  GroundFireEntity.create(server,at,owner,fireRadius,true,fireTicks)
 }
 override fun afterHitEntity(result:EntityHitResult){causeExplode(result.location);discard()}
 override fun afterHitBlock(result:BlockHitResult){causeExplode(result.location);discard()}
 override fun addAdditionalSaveData(t:CompoundTag){super.addAdditionalSaveData(t);t.putDouble("NapalmFireRadius",fireRadius);t.putInt("NapalmFireTicks",fireTicks)}
 override fun readAdditionalSaveData(t:CompoundTag){super.readAdditionalSaveData(t);if(t.contains("NapalmFireRadius"))configureFire(t.getDouble("NapalmFireRadius"),t.getInt("NapalmFireTicks"))}
}
