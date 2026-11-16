package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import java.util.UUID

/** Payloads supply their own model/interaction; every pod uses this deployment lifecycle. */
object HellpodDeployment {
    const val IMPACT_TICKS = 10

    inline fun tick(phase: AbstractHellpodEntity.Phase, age: Int, deployTicks: Int, retractTicks: Int,
             fall: () -> Unit, enter: (AbstractHellpodEntity.Phase) -> Unit,
             releaseCap: () -> Unit, discard: () -> Unit,
             closedTicks: Int = AbstractHellpodEntity.CLOSED_LINGER_TICKS): Boolean {
        when (phase) {
            AbstractHellpodEntity.Phase.FALLING -> fall()
            AbstractHellpodEntity.Phase.IMPACT -> if (age >= IMPACT_TICKS) {
                enter(AbstractHellpodEntity.Phase.DEPLOYING)
                releaseCap()
            }
            AbstractHellpodEntity.Phase.DEPLOYING -> {
                releaseCap()
                if (age >= deployTicks) enter(AbstractHellpodEntity.Phase.ACTIVE)
            }
            AbstractHellpodEntity.Phase.RETRACTING -> if (age >= retractTicks) enter(AbstractHellpodEntity.Phase.DONE)
            AbstractHellpodEntity.Phase.DONE -> if (age >= closedTicks) discard()
            else -> return false
        }
        return true
    }

    /** Deadline, braking, contact damage and landing effects are identical for every payload. */
    inline fun fall(entity: Entity, launch: Long, arrival: Long, landingY: Double, surfaceY: Double,
             brakingTicks: Int = AbstractHellpodEntity.SCHEDULED_BRAKE_TICKS,
             height: (Double) -> Double, brake: () -> Unit, landed: () -> Unit) {
        val now = entity.level().gameTime
        val duration = (arrival - launch).coerceAtLeast(1)
        val age = (now - launch).coerceIn(0, duration)
        if (age >= duration - minOf(brakingTicks.toLong(), maxOf(1L, duration - 1))) brake()
        entity.deltaMovement = Vec3.ZERO
        entity.setPos(entity.x, if (age >= duration) landingY else height(now.toDouble()), entity.z)
        if (age >= duration) {
            entity.noPhysics = false
            HellpodImpact.apply(entity, surfaceY)
            (entity.level() as? ServerLevel)?.sendParticles(ParticleTypes.CLOUD,
                entity.x, entity.y + .05, entity.z, 32, .8, .12, .8, .04)
            landed()
        }
    }

    inline fun releaseCap(entity: Entity, released: Boolean, markReleased: () -> Unit) {
        if (released) return
        val cap = ModEntities.HELLPOD_CAP.get().create(entity.level()) ?: return
        cap.ejectFrom(entity)
        if (entity.level().addFreshEntity(cap)) markReleased()
    }

    fun landingHeight(surfaceY: Double, podHeight: Double) = surfaceY - com.example.sbwhelldiver.config.HelldiversConfig.effectiveBurialDepth() + podHeight

    fun spawnHeight(surfaceY: Double) = surfaceY + AbstractHellpodEntity.descentSpawnHeight() + AbstractHellpodEntity.ORIGIN_ABOVE_BOTTOM
}

/** The marker schedules every pod through the same launch path. */
interface HellpodDelivery {
    fun scheduleArrival(surfaceY: Double, arrivalGameTime: Long)
    fun setDeliveryOwner(owner: UUID?)
}
