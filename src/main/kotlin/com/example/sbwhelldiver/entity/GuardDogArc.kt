package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.effect.FlameDamage
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.acos

/** Current native ArcWeapon type 8. Damage remains relative to the configured Tesla tower. */
object GuardDogArc {
    const val INTERVAL_TICKS = 100
    const val DEPTH = 2
    const val BRANCHES = 5
    const val DISTANCE = 55.0
    const val SPREAD_DISTANCE = 40.0
    const val CONE_DEGREES = 60.0
    const val DAMAGE_MULTIPLIER = .4f
    data class Link(val parent: Int, val point: Vec3, val target: LivingEntity)

    fun discharge(drone: GuardDogEntity, owner: Player, first: LivingEntity): List<Link> {
        if (drone.level().isClientSide || !TeslaArc.eligible(first)) return emptyList()
        val links = mutableListOf(Link(-1, first.boundingBox.center, first))
        val visited = hashSetOf(first.uuid, owner.uuid)
        var frontier = listOf(0)
        repeat(DEPTH) {
            val next = mutableListOf<Int>()
            for (parent in frontier) {
                val previous = links[parent]
                val start = previous.point
                val incoming = start.subtract(if (previous.parent < 0) drone.muzzleOrigin() else links[previous.parent].point).normalize()
                val candidates = drone.level().getEntitiesOfClass(LivingEntity::class.java, AABB(start, start).inflate(DISTANCE)) {
                    TeslaArc.eligible(it) && it.uuid !in visited && !owner.isAlliedTo(it)
                }.sortedWith(compareBy<LivingEntity> { start.distanceToSqr(it.boundingBox.center) }.thenBy { it.id })
                var branches = 0
                for (candidate in candidates) {
                    val end = candidate.boundingBox.center
                    val delta = end.subtract(start)
                    val angle = Math.toDegrees(acos(incoming.dot(delta.normalize()).coerceIn(-1.0, 1.0)))
                    val range = DISTANCE + (SPREAD_DISTANCE - DISTANCE) * (angle / CONE_DEGREES)
                    if (angle > CONE_DEGREES || delta.lengthSqr() > range * range) continue
                    if (drone.level().clip(ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, drone)).type != HitResult.Type.MISS) continue
                    visited += candidate.uuid
                    next += links.size
                    links += Link(parent, end, candidate)
                    if (++branches >= BRANCHES) break
                }
            }
            frontier = next
        }
        // Freeze the complete tree before damage can kill endpoints.
        links.forEach { it.target.hurt(FlameDamage.source(it.target, "tesla_arc", owner), TeslaClock.DAMAGE * DAMAGE_MULTIPLIER) }
        return links
    }
}
