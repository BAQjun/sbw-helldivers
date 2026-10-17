package com.example.sbwhelldiver.entity
import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import com.atsuishio.superbwarfare.tools.ParticleTool
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
class GrenadierGrenadeEntity(type:EntityType<out SmallCannonShellEntity>,level:Level):SentryCannonShellEntity(type,level) {
    override val innerBlastRadius get() = 3.5
    override val explosionParticleType get() = ParticleTool.ParticleType.SMALL
    init {setDamage(5f);setExplosionDamage(60f);setExplosionRadius(7.5f);setCustomGravity(.025f)}
}
