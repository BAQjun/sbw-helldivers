package com.example.sbwhelldiver.stratagem

import net.minecraft.world.phys.Vec3

/** Preserve a distant source's bearing while keeping its spatial cue audible. */
object OrbitalAudioPosition {
    fun project(listener: Vec3, source: Vec3, radius: Double = 24.0, horizon: Boolean = false): Vec3 {
        var delta = source.subtract(listener)
        if (horizon && delta.horizontalDistance() > .001) {
            val limit = delta.horizontalDistance() * .5
            delta = Vec3(delta.x, delta.y.coerceIn(-limit, limit), delta.z)
        }
        return listener.add(delta.scale(minOf(1.0, radius / delta.length().coerceAtLeast(.001))))
    }
}
