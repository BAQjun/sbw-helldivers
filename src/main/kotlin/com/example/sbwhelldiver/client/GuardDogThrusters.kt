package com.example.sbwhelldiver.client

import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.math.abs
import kotlin.math.sin

/** Capture animated sockets, then compose exhaust after clouds using scene depth. */
internal object GuardDogThrusters {
    private data class Plume(val corners: List<Vec3>, val power: Float)
    private val plumes = ArrayList<Plume>()

    fun beginFrame() = plumes.clear()

    fun capture(pose: PoseStack, origin: Vector3f, axis: Vector3f,
        power: Float, age: Float) {
        if (ShaderPackCompat.shadowPass()) return
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position
        val pulse = 1f + .045f * sin(age * 2.7f + origin.x * 17f)
        val length = .22f * power * pulse
        val width = .034f * (.65f + .35f * power)
        val right = Vector3f(axis).cross(if (abs(axis.z) > .9f) Vector3f(1f, 0f, 0f) else Vector3f(0f, 0f, 1f)).normalize().mul(width)
        val other = Vector3f(axis).cross(right).normalize().mul(width)
        val end = Vector3f(origin).fma(length, axis)
        for (side in arrayOf(right, other)) {
            val points = listOf(Vector3f(origin).sub(side), Vector3f(end).sub(side),
                Vector3f(end).add(side), Vector3f(origin).add(side)).map {
                it.mulPosition(pose.last().pose())
                Vec3(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()).add(camera)
            }
            plumes.add(Plume(points, power.coerceIn(0f, 1f)))
        }
    }

    fun collect(camera: Vec3): FloatArray {
        val data = ArrayList<Float>(plumes.size * 60)
        for (plume in plumes) for (i in intArrayOf(0, 1, 2, 0, 2, 3)) {
            val p = plume.corners[i].subtract(camera)
            data.addAll(listOf(p.x.toFloat(), p.y.toFloat(), p.z.toFloat(),
                if (i < 2) 0f else 1f, if (i == 0 || i == 3) 0f else 1f,
                4f, 1f, 1f, 1f, plume.power))
        }
        return data.toFloatArray()
    }
}
