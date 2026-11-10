package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.entity.GuardDogEntity
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.WeakHashMap

/** One cached cosmetic bolt per synchronized discharge. Uses the shared depth-aware effect pass. */
object StunGuardDogVisual {
    private data class Bolt(val serial: Int, val muzzle: Vec3, val reach: Double, val strands: List<Pair<Int, TeslaLightning.Strand>>)
    private val bolts = WeakHashMap<GuardDogEntity, Bolt>()
    internal var visibleFrames = 0L; private set

    fun collect(drone: GuardDogEntity, partial: Float, camera: Vec3, frustum: Frustum,
                emit: (Vec3, Vec3, Vec3, Vec3, Float, Float, Float, Float, Float) -> Unit) {
        val data = drone.arcData
        val count = data.getInt("Count").coerceIn(0, 31)
        val age = drone.tickCount - drone.arcReceivedTick + partial.toDouble()
        if (count == 0 || age !in 0.0..5.0) return
        val serial = data.getInt("Serial")
        val muzzle = GuardDogVisual.firingMuzzle(drone, partial)
        fun endpoint(i: Int) = Vec3(data.getDouble("X$i"), data.getDouble("Y$i"), data.getDouble("Z$i"))
        val bolt = bolts[drone]?.takeIf { it.serial == serial } ?: run {
            val strands = (0 until count).flatMap { i ->
                val parent = data.getInt("From$i")
                val a = if (parent < 0) muzzle else endpoint(parent.coerceIn(0, i))
                val b = endpoint(i)
                TeslaLightning.bolt(TeslaLightning.Point(a.x, a.y, a.z), TeslaLightning.Point(b.x, b.y, b.z),
                    drone.uuid.leastSignificantBits xor (serial.toLong() shl 16) xor i.toLong(), branches = i == 0)
                    .map { parent to it }
            }
            Bolt(serial, muzzle, muzzle.distanceTo(endpoint(0)).coerceAtLeast(.01), strands).also { bolts[drone] = it }
        }
        val alpha = ((5.0 - age) / 2.0).coerceIn(0.0, 1.0).toFloat()
        var visible = false
        val correction = muzzle.subtract(bolt.muzzle)
        for ((parent, strand) in bolt.strands) for ((a, b) in strand.points.zipWithNext()) {
            fun point(p: TeslaLightning.Point): Vec3 {
                val v = Vec3(p.x, p.y, p.z)
                return if (parent >= 0) v else v.add(correction.scale((1.0-v.distanceTo(bolt.muzzle)/bolt.reach).coerceIn(0.0,1.0)))
            }
            val start = point(a); val end = point(b)
            if (!frustum.isVisible(AABB(start, end).inflate(.15))) continue
            val axis = end.subtract(start)
            val side = axis.cross(camera.subtract(start)).normalize().scale(.07 * strand.width)
            if (side.lengthSqr() < 1e-12) continue
            emit(start.subtract(side), start.add(side), end.add(side), end.subtract(side),
                6f, .65f, 1.6f, 3f, alpha * strand.strength.toFloat())
            visible = true
        }
        if (visible) visibleFrames++
    }
}
