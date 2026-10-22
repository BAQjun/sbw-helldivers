package com.example.sbwhelldiver.entity
import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import com.atsuishio.superbwarfare.tools.ParticleTool
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
class GrenadierGrenadeEntity(type:EntityType<out SmallCannonShellEntity>,level:Level):SentryCannonShellEntity(type,level) {
    override val configId get() = "grenadier_battlement"
    override val explosionParticleType get() = ParticleTool.ParticleType.SMALL
    init {setCustomGravity(.025f)}
}
