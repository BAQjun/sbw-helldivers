package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.effect.FlameDamage
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** Damage follows the same ray rendered by the client, including blockers and close overlaps. */
object LaserBeam {
    data class Trace(val end: Vec3, val target: LivingEntity?, val mine: AntiPersonnelMineEntity?=null)
    fun trace(sentry: SentryHellpodEntity, origin: Vec3, direction: Vec3): Trace {
        val end = origin.add(direction.normalize().scale(LaserClock.BEAM_RANGE))
        val wall = sentry.level().clip(ClipContext(origin,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,sentry)).location
        var nearest = wall
        var victim: LivingEntity? = null
        sentry.level().getEntitiesOfClass(LivingEntity::class.java,AABB(origin,wall).inflate(.08)) {
            it.isAlive && !it.isSpectator
        }.forEach { candidate ->
            val box = candidate.boundingBox.inflate(.05)
            val hit = if (box.contains(origin)) origin else box.clip(origin,wall).orElse(null)
            if (hit != null && origin.distanceToSqr(hit) <= origin.distanceToSqr(nearest)) { nearest=hit;victim=candidate }
        }
        var mine: AntiPersonnelMineEntity?=null
        sentry.level().getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(origin,wall).inflate(.08)) { !it.isRemoved }.forEach { candidate ->
            val box=candidate.boundingBox.inflate(.05)
            val hit=if(box.contains(origin))origin else box.clip(origin,wall).orElse(null)
            if(hit!=null && origin.distanceToSqr(hit)<=origin.distanceToSqr(nearest)) { nearest=hit;victim=null;mine=candidate }
        }
        return Trace(nearest,victim,mine)
    }
    fun fire(sentry: SentryHellpodEntity, hit: Trace) {
        hit.mine?.detonate()
        hit.target?.let {
            // The dedicated damage type bypasses the vanilla hit cooldown: 2/tick = 40/s.
            it.hurt(FlameDamage.source(it,"laser_beam",sentry),2f)
            FlameDamage.ignite(it,sentry)
        }
    }
}
