package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.effect.FlameDamage
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.acos

/** Select the complete arc tree before applying damage to any endpoint. */
object GuardDogArc {
    const val DEPTH = 2
    const val CONE_DEGREES = 60.0
    data class Link(val parent: Int, val point: Vec3, val target: LivingEntity)

    fun discharge(drone: GuardDogEntity, owner: Player, first: LivingEntity): List<Link> {
        if (drone.level().isClientSide || !TeslaArc.eligible(first)) return emptyList()
        val links = mutableListOf(Link(-1, first.boundingBox.center, first))
        val settings = drone.settings
        val maxBranches = settings.arcBranches!!.get()
        val jumpRange = settings.arcJumpRange!!.get()
        val damage = TeslaClock.DAMAGE * settings.damageMultiplier!!.get().toFloat()
        val visited = hashSetOf(first.uuid, owner.uuid)
        var frontier = listOf(0)
        repeat(if (maxBranches > 0 && jumpRange > 0.0) DEPTH else 0) {
            val next = mutableListOf<Int>()
            for (parent in frontier) {
                val previous = links[parent]
                val start = previous.point
                val incoming = start.subtract(if (previous.parent < 0) drone.muzzleOrigin() else links[previous.parent].point).normalize()
                val candidates = drone.level().getEntitiesOfClass(LivingEntity::class.java, AABB(start, start).inflate(jumpRange)) {
                    TeslaArc.eligible(it) && it.uuid !in visited && !owner.isAlliedTo(it) && drone.targetListDecision(it) != false
                }.sortedWith(compareBy<LivingEntity> { start.distanceToSqr(it.boundingBox.center) }.thenBy { it.id })
                var branches = 0
                for (candidate in candidates) {
                    val end = candidate.boundingBox.center
                    val delta = end.subtract(start)
                    val angle = Math.toDegrees(acos(incoming.dot(delta.normalize()).coerceIn(-1.0, 1.0)))
                    val range = jumpRange * (1.0 - (15.0 / 55.0) * (angle / CONE_DEGREES))
                    if (angle > CONE_DEGREES || delta.lengthSqr() > range * range) continue
                    if (drone.level().clip(ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, drone)).type != HitResult.Type.MISS) continue
                    visited += candidate.uuid
                    next += links.size
                    links += Link(parent, end, candidate)
                    if (++branches >= maxBranches) break
                }
            }
            frontier = next
        }
        // Freeze the complete tree before damage can kill endpoints.
        links.forEach { it.target.hurt(FlameDamage.source(it.target, "tesla_arc", owner), damage) }
        return links
    }
}
