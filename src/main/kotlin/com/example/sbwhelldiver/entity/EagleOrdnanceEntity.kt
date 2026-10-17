package com.example.sbwhelldiver.entity
import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import com.atsuishio.superbwarfare.tools.ParticleTool
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level

class EagleBombEntity(type:EntityType<out SmallCannonShellEntity>,level:Level):SentryCannonShellEntity(type,level) {
 override val innerBlastRadius get()=5.0
 init {setDamage(240f);setExplosionDamage(128f);setExplosionRadius(10f);setCustomGravity(EagleFlight.BOMB_GRAVITY.toFloat())}
}
class EagleCannonRoundEntity(type:EntityType<out SmallCannonShellEntity>,level:Level):SentryCannonShellEntity(type,level) {
 override val innerBlastRadius get()=2.5
 override val explosionParticleType get()=ParticleTool.ParticleType.SMALL
 init {setDamage(55f);setExplosionDamage(55f);setExplosionRadius(5f);setCustomGravity(0f)}
 override fun afterHitEntity(r:net.minecraft.world.phys.EntityHitResult){if(getExplosionDamage()>0)super.afterHitEntity(r) else discard()}
 override fun afterHitBlock(r:net.minecraft.world.phys.BlockHitResult){if(getExplosionDamage()>0)super.afterHitBlock(r) else {val s=level() as? net.minecraft.server.level.ServerLevel;s?.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,r.location.x,r.location.y,r.location.z,2,.1,.1,.1,.01);discard()}}

}
