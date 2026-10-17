package com.example.sbwhelldiver.entity

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/** Aim along the actual bore, including the offset between the yaw and pitch pivots. */
object SentryAim {
    data class Angles(val yaw: Double, val pitch: Double)

    fun angles(delta: Vec3, barrel: Vec3, muzzle: Vec3): Angles {
        val horizontal = hypot(delta.x, delta.z).coerceAtLeast(.001)
        val heading = atan2(delta.x, delta.z)
        val yaw = heading - asin(((barrel.x + muzzle.x) / horizontal).coerceIn(-.95, .95))
        val along = horizontal * cos(heading - yaw) - barrel.z
        val pitch = atan2(delta.y, along) -
            asin((muzzle.y / hypot(delta.y, along).coerceAtLeast(.001)).coerceIn(-.95, .95))
        return Angles(yaw, pitch)
    }

    /** A touching enemy may overlap the barrel: start just before its body, not beyond it. */
    fun firingOrigin(muzzle: Vec3, direction: Vec3, barrelLength: Double, bounds: AABB): Vec3 {
        val axis = direction.normalize()
        val root = muzzle.subtract(axis.scale(barrelLength.coerceAtLeast(0.0)))
        val entry = if (bounds.contains(root)) {
            val behind = root.subtract(axis.scale(bounds.size * 3.0 + .1))
            bounds.clip(behind, root).orElse(root)
        } else bounds.clip(root, muzzle).orElse(null) ?: return muzzle
        return entry.subtract(axis.scale(.05))
    }

    fun aligned(origin: Vec3, direction: Vec3, bounds: AABB): Boolean {
        val axis = direction.normalize()
        val delta = origin.vectorTo(bounds.center)
        if (delta.lengthSqr() > .001 && axis.dot(delta.normalize()) >= cos(Math.toRadians(4.0))) return true
        // Nearby bodies occupy a large angle, and their center need not lie on the bore.
        return bounds.contains(origin) || bounds.clip(origin,
            origin.add(axis.scale(delta.length() + bounds.size * 3.0 + 1.0))).isPresent
    }
}
