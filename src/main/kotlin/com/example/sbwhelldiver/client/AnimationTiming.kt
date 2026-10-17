package com.example.sbwhelldiver.client

import com.atsuishio.superbwarfare.client.animation.AnimationPlayType
import com.atsuishio.superbwarfare.client.animation.entity.VehicleAnimationContext
import com.maydaymemory.mae.control.runner.AnimationRunner

/** SBW 0.8.9.1 exposes play/stop but no seek. Resume newly tracked pods at their server stage age. */
object AnimationTiming {
    private val runners = VehicleAnimationContext::class.java.getDeclaredField("weaponRunners").apply { isAccessible = true }
    fun play(ctx: VehicleAnimationContext<*>, name: String, type: AnimationPlayType, age: Int = 0) {
        ctx.playAnimation(name, type, 0)
        if (age <= 0) return
        val map = runners.get(ctx) as Map<*, *>
        val clock = (map[name.removePrefix("animation.")] as? AnimationRunner)?.animationContext ?: return
        clock.progress = (age * 50_000_000L).coerceAtMost(clock.maxProgress)
        clock.lastUpdateTime = System.nanoTime()
    }
}
