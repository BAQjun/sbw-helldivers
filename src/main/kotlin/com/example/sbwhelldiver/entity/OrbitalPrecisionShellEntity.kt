package com.example.sbwhelldiver.entity
import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import com.example.sbwhelldiver.config.OrbitalConfig
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level

open class OrbitalPrecisionShellEntity(t:EntityType<out SmallCannonShellEntity>,l:Level):SentryCannonShellEntity(t,l){
 private var blastInner=4.0
 override val innerBlastRadius get()=blastInner.coerceAtMost(getExplosionRadius().toDouble()).coerceAtLeast(0.0)
 init{configure(OrbitalConfig.direct.get().toFloat(),OrbitalConfig.blast.get().toFloat(),OrbitalConfig.inner.get(),OrbitalConfig.outer.get().toFloat())}
 fun configure(direct:Float,blast:Float,inner:Double,outer:Float){setDamage(direct);setExplosionDamage(blast);setExplosionRadius(outer);blastInner=inner;setCustomGravity(0f)}
 override fun addAdditionalSaveData(t:CompoundTag){super.addAdditionalSaveData(t);t.putDouble("OrbitalInnerRadius",blastInner)}
 override fun readAdditionalSaveData(t:CompoundTag){super.readAdditionalSaveData(t);if(t.contains("OrbitalInnerRadius"))blastInner=t.getDouble("OrbitalInnerRadius");setCustomGravity(0f)}
}
class Orbital120ShellEntity(t:EntityType<out SmallCannonShellEntity>,l:Level):OrbitalPrecisionShellEntity(t,l){
 init{val s=com.example.sbwhelldiver.config.OrbitalBarrageConfig.snapshot("orbital_120mm_he_barrage");configure(s.direct,s.blast,s.inner,s.outer)}
}

class OrbitalRailcannonShellEntity(t:EntityType<out SmallCannonShellEntity>,l:Level):OrbitalPrecisionShellEntity(t,l){
 init{val s=com.example.sbwhelldiver.config.OrbitalRailcannonConfig.snapshot();configure(s.direct,s.blast,s.inner,s.outer)}
}
