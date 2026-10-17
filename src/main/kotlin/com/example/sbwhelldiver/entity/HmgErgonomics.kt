package com.example.sbwhelldiver.entity

import net.minecraft.util.Mth
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.sign

/** User-specified HD2 approximation, evaluated once per server tick. */
object HmgErgonomics {
    const val BASE = 65.0
    fun follow(ergo: Double = BASE): Double = .08 + .32 * ((ergo.coerceIn(20.0,100.0)-20.0)/80.0).pow(1.3)
    fun limit(ergo: Double = BASE): Double = (100.0 + 4.5 * ergo.coerceIn(20.0,100.0)) / 20.0
    fun step(current: Float, target: Float, wrap: Boolean, ergo: Double = BASE, speedMultiplier: Double = 1.0, minimumClosingSpeed: Double = 0.0): Float {
        val error = if(wrap) Mth.wrapDegrees(target-current) else target-current
        val speed = speedMultiplier.coerceIn(.05,1.0)
        val maximumStep = limit(ergo)*speed
        val minimumStep = (minimumClosingSpeed/20.0).coerceIn(0.0,maximumStep)
        // A finite closing speed prevents the proportional tail from lingering.
        // Clamp to the remaining angle as well, so a tiny correction cannot overshoot.
        val step = maxOf(abs(error)*follow(ergo)*speed,minimumStep)
            .coerceAtMost(maximumStep).coerceAtMost(abs(error).toDouble())
        return current + (step*error.sign).toFloat()
    }
}
