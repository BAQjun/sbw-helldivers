package com.example.sbwhelldiver.entity
import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
class AntiTankEmplacementShellEntity(type: EntityType<out SmallCannonShellEntity>,level: Level): SentryCannonShellEntity(type,level) {
    override val innerBlastRadius get() = 3.0
    override val explosionParticleType get() = com.atsuishio.superbwarfare.tools.ParticleTool.ParticleType.SMALL
    init { setDamage(200f);setExplosionDamage(20f);setExplosionRadius(7f) }
}
