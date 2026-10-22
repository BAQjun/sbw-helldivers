package com.example.sbwhelldiver.entity

/** One server clock controls ignition, idle readiness and fuel expenditure. */
class FlameClock {
    enum class State { OFF, IGNITING, READY, SPRAYING }
    var state = State.OFF
        private set
    private var readyAt = 0L
    private var lastCombat = 0L
    private var nextJet = 0.0

    fun advance(now: Long, hasTarget: Boolean, aligned: Boolean, ammo: Int): Boolean {
        if (ammo < AMMO_PER_JET) { reset(); return false }
        if (state == State.OFF) {
            if (!hasTarget) return false
            state = State.IGNITING
            readyAt = now + IGNITION_TICKS
            nextJet = readyAt.toDouble()
            lastCombat = now
        }
        if (hasTarget) lastCombat = now
        if (!hasTarget && now - lastCombat >= IDLE_TICKS) { reset(); return false }
        if (now < readyAt) { state = State.IGNITING; return false }
        // Once spraying, sweep toward the next target without interrupting the jet
        // cadence or restarting the firing audio. Losing all targets still stops it.
        state = if (hasTarget && (aligned || state == State.SPRAYING)) State.SPRAYING else State.READY
        if (state != State.SPRAYING || now < nextJet) return false
        val remainder = (now-nextJet).takeIf { it >= 0.0 && it < 1.0 } ?: 0.0
        nextJet = now + JET_INTERVAL - remainder
        return true
    }
    fun reset() { state = State.OFF; readyAt = 0; lastCombat = 0; nextJet = 0.0 }
    fun save(tag: net.minecraft.nbt.CompoundTag, now: Long) {
        tag.putInt("FlameState",state.ordinal)
        tag.putLong("FlameWarmupLeft",(readyAt-now).coerceIn(0,IGNITION_TICKS.toLong()))
        tag.putLong("FlameIdleAge",(now-lastCombat).coerceIn(0,IDLE_TICKS.toLong()))
        tag.putDouble("FlameJetLeft",(nextJet-now).coerceIn(0.0,1200.0))
    }
    fun restore(tag: net.minecraft.nbt.CompoundTag, now: Long) {
        state = State.entries[tag.getInt("FlameState").coerceIn(0,State.entries.lastIndex)]
        readyAt = now+tag.getLong("FlameWarmupLeft").coerceIn(0,IGNITION_TICKS.toLong())
        lastCombat = now-tag.getLong("FlameIdleAge").coerceIn(0,IDLE_TICKS.toLong())
        nextJet = now+(tag.getDouble("FlameJetLeft").takeIf {it.isFinite()} ?: 0.0).coerceIn(0.0,1200.0)
    }
    companion object {
        const val IGNITION_TICKS = 20
        const val IDLE_TICKS = 240
        val DIRECT_DAMAGE_PER_JET get() = com.example.sbwhelldiver.config.TurretConfigs.get("flame_sentry").damage.get().toFloat()
        val JET_INTERVAL get() = 1200.0 / com.example.sbwhelldiver.config.TurretConfigs.get("flame_sentry").rpm.get()
        const val AMMO_PER_JET = 2
        val RANGE get() = com.example.sbwhelldiver.config.TurretConfigs.get("flame_sentry").range!!.get()
    }
}
