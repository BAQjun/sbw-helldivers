package com.example.sbwhelldiver.entity

import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

class GasMortarShellEntity(type: EntityType<out SmallCannonShellEntity>,level: Level) : MortarShellEntity(type,level) {
    init { setExplosionDamage(0f); setExplosionRadius(0f) }
    override fun impact(at: Vec3,destroyBlocks: Boolean) {
        val server=level() as? ServerLevel ?: return
        if (exploded) return
        exploded=true
        MortarFieldEntity.create(server,at,MortarFieldEntity.Kind.GAS)
    }
}
class EmsMortarShellEntity(type: EntityType<out SmallCannonShellEntity>,level: Level) : MortarShellEntity(type,level) {
    init { setExplosionDamage(15f); setExplosionRadius(2f) }
    override fun impact(at: Vec3,destroyBlocks: Boolean) {
        val server=level() as? ServerLevel ?: return
        if (exploded) return
        SentryExplosion.explode(this,at,com.example.sbwhelldiver.config.TurretConfigs.get("ems_mortar_sentry").innerRadius!!.get(),false,genericEffects=false)
        MortarFieldEntity.create(server,at,MortarFieldEntity.Kind.EMS)
    }
}
