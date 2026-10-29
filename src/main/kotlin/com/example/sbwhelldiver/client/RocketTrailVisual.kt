package com.example.sbwhelldiver.client

import com.atsuishio.superbwarfare.client.particle.CustomFlareOption
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import kotlin.math.ceil

/** Preserve SBW's smoke appearance and one-metre spacing without waiting for entity tracking. */
object RocketTrailVisual {
    fun emit(from: Vec3, to: Vec3) {
        val level = Minecraft.getInstance().level ?: return
        val travel = to.subtract(from)
        val length = travel.length()
        if (!from.x.isFinite() || !from.y.isFinite() || !from.z.isFinite() ||
            !length.isFinite() || length < 1e-4 || length > 128.0) return
        val count = ceil(length).toInt().coerceAtMost(64)
        for (index in 0 until count) {
            val pos = from.add(travel.scale((index + .5) / count))
            if (pos.y < level.minBuildHeight || pos.y > level.maxBuildHeight * 2) continue
            val jitter = 2 * (level.random.nextFloat() - .5f)
            level.addParticle(CustomFlareOption(.5f, .43f, .36f, 80, .9f,
                (10 + 8 * jitter).toInt(), .01f, size = .25f),
                pos.x + jitter * .1, pos.y + jitter * .1, pos.z + jitter * .1, 0.0, 0.0, 0.0)
        }
    }
}
