package com.example.sbwhelldiver.entity

import com.atsuishio.superbwarfare.entity.projectile.SmallRocketEntity
import com.atsuishio.superbwarfare.resource.model.ProjectileModelReloadListener
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

class SentryRocketEntity(type: EntityType<out SmallRocketEntity>, level: Level) : SmallRocketEntity(type, level) {
    init {
        setDamage(80f)
        setExplosionDamage(20f)
        setExplosionRadius(4f)
        setHeadShot(1f)
        setLegShot(1f)
    }

    // SmallRocketEntity calls this after moving; authoritative segments below replace it.
    override fun smallTrail() {}

    override fun projectileMove(level: Level) {
        val from = position()
        super.projectileMove(level)
        if (!isRemoved) RocketTrails.send(this, from, position())
    }

    override fun onHit(result: net.minecraft.world.phys.HitResult) {
        RocketTrails.send(this, position(), result.location)
        super.onHit(result)
    }

    override fun causeExplode(vec3: Vec3) = SentryExplosion.explode(this, vec3,
        com.example.sbwhelldiver.config.TurretConfigs.get("rocket_sentry").innerRadius!!.get(), hasExplosionDestroy())
    override fun getModelInstance() = ProjectileModelReloadListener.getModel(MODEL)?.createInstance()

    companion object {
        val MODEL = ResourceLocation.fromNamespaceAndPath("superbwarfare", "models/bedrock/projectile/small_rocket.geo.json")
    }
}
