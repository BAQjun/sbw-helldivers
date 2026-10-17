package com.example.sbwhelldiver.entity

/** One server clock controls ignition, idle readiness and fuel expenditure. */
class FlameClock {
    enum class State { OFF, IGNITING, READY, SPRAYING }
    var state = State.OFF
        private set
    private var readyAt = 0L
    private var lastCombat = 0L
    private var nextJet = 0L

    fun advance(now: Long, hasTarget: Boolean, aligned: Boolean, ammo: Int): Boolean {
        if (ammo < AMMO_PER_JET) { reset(); return false }
        if (state == State.OFF) {
            if (!hasTarget) return false
            state = State.IGNITING
            readyAt = now + IGNITION_TICKS
            nextJet = readyAt
            lastCombat = now
        }
        if (hasTarget) lastCombat = now
        if (!hasTarget && now - lastCombat >= IDLE_TICKS) { reset(); return false }
        if (now < readyAt) { state = State.IGNITING; return false }
        // Once spraying, sweep toward the next target without interrupting the jet
        // cadence or restarting the firing audio. Losing all targets still stops it.
        state = if (hasTarget && (aligned || state == State.SPRAYING)) State.SPRAYING else State.READY
        if (state != State.SPRAYING || now < nextJet) return false
        nextJet = now + JET_INTERVAL
        return true
    }
    fun reset() { state = State.OFF; readyAt = 0; lastCombat = 0; nextJet = 0 }
    fun save(tag: net.minecraft.nbt.CompoundTag, now: Long) {
        tag.putInt("FlameState",state.ordinal)
        tag.putLong("FlameWarmupLeft",(readyAt-now).coerceIn(0,IGNITION_TICKS.toLong()))
        tag.putLong("FlameIdleAge",(now-lastCombat).coerceIn(0,IDLE_TICKS.toLong()))
        tag.putLong("FlameJetLeft",(nextJet-now).coerceIn(0,IGNITION_TICKS.toLong()))
    }
    fun restore(tag: net.minecraft.nbt.CompoundTag, now: Long) {
        state = State.entries[tag.getInt("FlameState").coerceIn(0,State.entries.lastIndex)]
        readyAt = now+tag.getLong("FlameWarmupLeft").coerceIn(0,IGNITION_TICKS.toLong())
        lastCombat = now-tag.getLong("FlameIdleAge").coerceIn(0,IDLE_TICKS.toLong())
        nextJet = now+tag.getLong("FlameJetLeft").coerceIn(0,IGNITION_TICKS.toLong())
    }
    companion object {
        const val IGNITION_TICKS = 20
        const val IDLE_TICKS = 240
        const val JET_INTERVAL = 5
        const val AMMO_PER_JET = 2
        const val RANGE = 35.0
    }
}
