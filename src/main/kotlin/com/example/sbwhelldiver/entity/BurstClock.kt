package com.example.sbwhelldiver.entity

/** One sentry-wide deadline and burst count, independent of its current target or aim. */
class BurstClock {
    var remaining = 0
        private set
    private var readyAt = 0.0
    fun reset() { remaining = 0; readyAt = 0.0 }
    fun cooldownTicks(gameTime: Long) = (readyAt - gameTime).coerceAtLeast(0.0)
    fun restore(roundsRemaining: Int, cooldownTicks: Double, gameTime: Long) {
        remaining = roundsRemaining.coerceAtLeast(0)
        readyAt = gameTime + if (cooldownTicks.isFinite()) cooldownTicks.coerceAtLeast(0.0) else 0.0
    }
    fun advance(size: Int, interval: Int, pauseTicks: Double, gameTime: Long): Int =
        advance(size, interval.toDouble(), pauseTicks, gameTime)

    fun advance(size: Int, interval: Double, pauseTicks: Double, gameTime: Long): Int {
        if (size <= 0 || !interval.isFinite() || interval <= 0 || !pauseTicks.isFinite() || pauseTicks <= 0) { reset(); return 0 }
        val now = gameTime.toDouble()
        if (now + 1e-9 < readyAt) return 0
        // Keep sub-tick rounding error, but do not bank shots during idle or turning time.
        val overdue = (now-readyAt).takeIf { it in 0.0..0.999999999 } ?: 0.0
        if (remaining == 0) remaining = size
        remaining--
        readyAt = now + (if (remaining > 0) interval else pauseTicks.coerceAtLeast(1.0)) - overdue
        return 1
    }
}
