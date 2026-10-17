package com.example.sbwhelldiver.entity

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.sqrt

/** Continuous sphere/voxel-box contact, including rounded edge and corner contacts. */
internal object BeaconCollision {
    data class Contact(val fraction: Double, val center: Vec3, val point: Vec3, val normal: Vec3)

    fun sweep(start: Vec3, movement: Vec3, radius: Double, box: AABB): Contact? {
        val origin = doubleArrayOf(start.x, start.y, start.z)
        val delta = doubleArrayOf(movement.x, movement.y, movement.z)
        val low = doubleArrayOf(box.minX, box.minY, box.minZ)
        val high = doubleArrayOf(box.maxX, box.maxY, box.maxZ)
        fun contact(t: Double): Contact? {
            val center = start.add(movement.scale(t))
            val point = Vec3(center.x.coerceIn(box.minX, box.maxX), center.y.coerceIn(box.minY, box.maxY), center.z.coerceIn(box.minZ, box.maxZ))
            val separation = center.subtract(point)
            if (separation.lengthSqr() < 1e-18) return null
            val normal = separation.normalize()
            if (movement.dot(normal) >= -1e-9) return null // Ignore a tangent or a separating contact.
            return Contact(t, center, point, normal)
        }
        val cuts = mutableListOf(0.0, 1.0)
        for (axis in 0..2) if (abs(delta[axis]) > 1e-12) {
            for (edge in doubleArrayOf(low[axis], high[axis])) {
                val t = (edge - origin[axis]) / delta[axis]
                if (t > 0.0 && t < 1.0) cuts.add(t)
            }
        }
        val ordered = cuts.distinct().sorted()
        // Squared distance to a box is quadratic between crossings of its six planes.
        for (i in 0 until ordered.lastIndex) {
            val a = ordered[i]; val b = ordered[i + 1]; val mid = (a + b) / 2
            var aa = 0.0; var bb = 0.0; var cc = -radius * radius
            for (axis in 0..2) {
                val position = origin[axis] + delta[axis] * mid
                val edge = if (position < low[axis]) low[axis] else if (position > high[axis]) high[axis] else continue
                val offset = origin[axis] - edge
                aa += delta[axis] * delta[axis]; bb += 2 * offset * delta[axis]; cc += offset * offset
            }
            if (aa * a * a + bb * a + cc <= 1e-12) contact(a)?.let { return it }
            if (aa < 1e-20) continue
            val discriminant = bb * bb - 4 * aa * cc
            if (discriminant < 0) continue
            val t = (-bb - sqrt(discriminant)) / (2 * aa)
            if (t >= a - 1e-10 && t <= b + 1e-10) contact(t.coerceIn(a, b))?.let { return it }
        }
        return null
    }
}
