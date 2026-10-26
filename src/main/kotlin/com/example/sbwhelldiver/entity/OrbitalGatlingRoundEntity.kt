package com.example.sbwhelldiver.entity

import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import com.atsuishio.superbwarfare.tools.ParticleTool
import com.example.sbwhelldiver.config.GatlingSettings
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.*

/** Only the short, collidable final segment is an entity. */
class OrbitalGatlingRoundEntity(t:EntityType<out SmallCannonShellEntity>,l:Level):SentryCannonShellEntity(t,l){
 private var inner=1.5
 private var he=false
 override val innerBlastRadius get()=inner.coerceAtMost(getExplosionRadius().toDouble())
 override val explosionParticleType get()=ParticleTool.ParticleType.SMALL
 init{configure(GatlingSettings(),false);setCustomGravity(0f)}
 fun configure(s:GatlingSettings,explosive:Boolean){
  he=explosive;inner=s.inner
  setDamage(s.damage);setExplosionDamage(if(he)s.blast else 0f);setExplosionRadius(s.outer);setCustomGravity(0f)
 }
 override fun causeExplode(at:Vec3){if(he)super.causeExplode(at)}
 override fun afterHitEntity(result:EntityHitResult){if(he)super.afterHitEntity(result) else discard()}
 override fun afterHitBlock(result:BlockHitResult){
  if(he)super.afterHitBlock(result) else {
   (level() as? net.minecraft.server.level.ServerLevel)?.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,result.location.x,result.location.y+.06,result.location.z,2,.03,.04,.03,.01)
   discard()
  }
 }
 override fun addAdditionalSaveData(t:CompoundTag){super.addAdditionalSaveData(t);t.putBoolean("GatlingHE",he);t.putDouble("GatlingInner",inner);t.putFloat("GatlingDamage",getDamage());t.putFloat("GatlingBlast",getExplosionDamage());t.putFloat("GatlingOuter",getExplosionRadius())}
 override fun readAdditionalSaveData(t:CompoundTag){super.readAdditionalSaveData(t);he=t.getBoolean("GatlingHE");inner=t.getDouble("GatlingInner");setDamage(t.getFloat("GatlingDamage"));setExplosionDamage(t.getFloat("GatlingBlast"));setExplosionRadius(t.getFloat("GatlingOuter"));setCustomGravity(0f)}
}
