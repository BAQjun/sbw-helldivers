package com.example.sbwhelldiver.entity
import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import com.example.sbwhelldiver.config.OrbitalConfig
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
class OrbitalPrecisionShellEntity(t:EntityType<out SmallCannonShellEntity>,l:Level):SentryCannonShellEntity(t,l){
 override val innerBlastRadius get()=OrbitalConfig.inner.get().coerceAtMost(OrbitalConfig.outer.get()-.001)
 init{setDamage(OrbitalConfig.direct.get().toFloat());setExplosionDamage(OrbitalConfig.blast.get().toFloat());setExplosionRadius(OrbitalConfig.outer.get().toFloat());setCustomGravity(0f)}
}
