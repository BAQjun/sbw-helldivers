package com.example.sbwhelldiver.entity

import kotlin.math.floor

/** Three barrels pass one firing station: each actual firing slot is exactly 120 degrees apart. */
class RotorClock {
    var angle = 0.0
    var speed = 0.0
    var lastSlotAngle = 0.0
        private set

    fun advance(spin: Boolean, rpm: Int): Int {
        val target = rpm.coerceIn(0, 4800) / 10.0 // RPM / 3 barrels * 360 / 60 / 20.
        speed = if (spin) (speed + target / 16).coerceAtMost(target)
            else (speed - target / 12).coerceAtLeast(0.0)
        val before = floor((angle + 1e-8) / 120)
        angle += speed
        val after = floor((angle + 1e-8) / 120)
        lastSlotAngle = after * 120
        return if (spin && target > 0 && speed >= target - 1e-8) (after - before).toInt() else 0
    }
}
