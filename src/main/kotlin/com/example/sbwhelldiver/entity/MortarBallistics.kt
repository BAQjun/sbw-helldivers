package com.example.sbwhelldiver.entity

import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.HitResult
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.entity.Entity
import kotlin.math.*

/** Solve the exact native step: position += velocity, then velocity.y -= gravity. */
object MortarBallistics {
    const val SPEED = 4.0
    const val GRAVITY = .12
    const val SCATTER_RADIUS = 10.0

    /** Uniform horizontal area around the center locked for the current four-shot volley. */
    fun scatterPoint(center: Vec3, radiusSample: Double, angleSample: Double, scatterRadius: Double = SCATTER_RADIUS): Vec3 {
        val radius = scatterRadius * sqrt(radiusSample.coerceIn(0.0, 1.0))
        val angle = angleSample * 2.0 * PI
        return center.add(cos(angle)*radius, 0.0, sin(angle)*radius)
    }
    data class Solution(val velocity: Vec3,val flightTicks: Double)
    fun velocity(start: Vec3,end: Vec3,t: Double): Vec3 {
        val d = start.vectorTo(end)
        return Vec3(d.x/t,d.y/t+GRAVITY*(t-1)/2,d.z/t)
    }
    fun solve(start: Vec3,end: Vec3): Solution? {
        if (!start.x.isFinite() || !end.x.isFinite()) return null
        fun error(t: Double) = velocity(start,end,t).lengthSqr()-SPEED*SPEED
        var previousT = 1.0
        var previous = error(previousT)
        // Select the later/high-arc root; the lower root would fire through the ground.
        for (i in 3..360) {
            val t = i*.5
            val current = error(t)
            if (previous <= 0 && current >= 0) {
                var lo = previousT;var hi = t
                repeat(45) { val mid=(lo+hi)/2;if(error(mid)>0)hi=mid else lo=mid }
                val flight = (lo+hi)/2
                return Solution(velocity(start,end,flight),flight)
            }
            previousT=t;previous=current
        }
        return null
    }
    fun position(start: Vec3,velocity: Vec3,t: Int): Vec3 = start.add(velocity.scale(t.toDouble())).add(0.0,-GRAVITY*t*(t-1)/2,0.0)
    fun clearPath(level: Level,owner: Entity,start: Vec3,velocity: Vec3,flight: Double): Boolean {
        var previous = start
        // The last segment may meet the ground/target and is the intended impact.
        for (t in 1..max(1,floor(flight).toInt()-1)) {
            val p = position(start,velocity,t)
            if (level.clip(ClipContext(previous,p,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,owner)).type != HitResult.Type.MISS) return false
            previous=p
        }
        return true
    }
}
