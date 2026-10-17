package com.example.sbwhelldiver.entity

import net.minecraft.nbt.CompoundTag

/** One tick clock owns beam readiness, heat, alarm and the irreversible fuse. */
class LaserClock {
    enum class State { OFF, WARMING, FIRING, OVERLOADED }
    var state = State.OFF
        private set
    var heat = 0.0
        private set
    var warmupLeft = 0
        private set
    var fuseLeft = 0
        private set
    val alarm get() = heat >= WARNING_HEAT - 1e-6
    val destroyNow get() = state == State.OVERLOADED && fuseLeft <= 0

    fun advance(hasTarget: Boolean, aligned: Boolean, coolingPerSecond: Double): Boolean {
        if (state == State.OVERLOADED) { fuseLeft--; return false }
        if (!hasTarget) {
            state = State.OFF; warmupLeft = 0
            heat = (heat - coolingPerSecond / 20.0).coerceAtLeast(0.0)
            return false
        }
        if (state == State.OFF) { state = State.WARMING; warmupLeft = WARMUP_TICKS }
        if (warmupLeft > 0) {
            warmupLeft--
            heat = (heat - coolingPerSecond / 20.0).coerceAtLeast(0.0)
            return false
        }
        if (state != State.FIRING && !aligned) {
            heat = (heat - coolingPerSecond / 20.0).coerceAtLeast(0.0)
            return false
        }
        state = State.FIRING
        heat = (heat + HEAT_PER_SECOND / 20.0).coerceAtMost(MAX_HEAT)
        if (heat >= MAX_HEAT - 1e-6) {
            heat = MAX_HEAT; state = State.OVERLOADED; fuseLeft = FUSE_TICKS
            return false
        }
        return true
    }
    fun stop() { if (state != State.OVERLOADED) { state = State.OFF; warmupLeft = 0 } }
    fun save(tag: CompoundTag) {
        tag.putInt("LaserState",state.ordinal);tag.putDouble("LaserHeat",heat)
        tag.putInt("LaserWarmup",warmupLeft);tag.putInt("LaserFuse",fuseLeft)
    }
    fun restore(tag: CompoundTag) {
        state = State.entries[tag.getInt("LaserState").coerceIn(0,State.entries.lastIndex)]
        heat = tag.getDouble("LaserHeat").takeIf { it.isFinite() }?.coerceIn(0.0,MAX_HEAT) ?: 0.0
        warmupLeft = tag.getInt("LaserWarmup").coerceIn(0,WARMUP_TICKS)
        fuseLeft = tag.getInt("LaserFuse").coerceIn(0,FUSE_TICKS)
        if (heat >= MAX_HEAT - 1e-6 && state != State.OVERLOADED) { state=State.OVERLOADED;fuseLeft=FUSE_TICKS }
    }
    companion object {
        const val WARMUP_TICKS = 10
        const val FUSE_TICKS = 20
        const val MAX_HEAT = 250.0
        const val WARNING_HEAT = 200.0
        const val HEAT_PER_SECOND = 8.0
        const val SEEK_RANGE = 50.0
        const val BEAM_RANGE = 200.0
        fun coolingRate(nether: Boolean, temperature: Float) = when {
            nether || temperature >= 1.0f -> 3.5
            temperature <= .15f -> 7.5
            else -> 5.0
        }
    }
}
