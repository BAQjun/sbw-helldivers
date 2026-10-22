package com.example.sbwhelldiver.entity

import net.minecraft.nbt.CompoundTag

/** Twenty ticks of charge, one discharge, then twenty ticks of quiet cooldown. */
class TeslaClock {
    enum class State { IDLE, CHARGING, COOLDOWN }
    var state = State.IDLE
        private set
    var age = 0
        private set
    private var cadenceError = 0.0
    private val phaseDuration get() = 600.0 / settings.rpm.get()
    private fun phaseComplete(): Boolean {
        if (++age + cadenceError + 1e-9 < phaseDuration) return false
        cadenceError = (age + cadenceError - phaseDuration).coerceIn(0.0, .999999999)
        return true
    }
    fun advance(hasTarget: Boolean): Boolean {
        when (state) {
            State.IDLE -> if (hasTarget) { state=State.CHARGING; age=0 }
            State.CHARGING -> {
                if (!hasTarget) { reset(); return false }
                if (phaseComplete()) { state=State.COOLDOWN; age=0; return true }
            }
            State.COOLDOWN -> if (phaseComplete()) {
                state=if(hasTarget) State.CHARGING else State.IDLE; age=0
            }
        }
        return false
    }
    fun reset() { state=State.IDLE; age=0; cadenceError=0.0 }
    fun save(tag: CompoundTag) { tag.putInt("TeslaState",state.ordinal);tag.putInt("TeslaAge",age);tag.putDouble("TeslaCadenceError",cadenceError) }
    fun restore(tag: CompoundTag) {
        state=State.entries[tag.getInt("TeslaState").coerceIn(0,2)]
        age=tag.getInt("TeslaAge").coerceIn(0,maxOf(CHARGE_TICKS,COOLDOWN_TICKS)-1)
        cadenceError=tag.getDouble("TeslaCadenceError").takeIf {it.isFinite()}?.coerceIn(0.0,.999999999) ?: 0.0
    }
    companion object {
        private val settings get() = com.example.sbwhelldiver.config.TurretConfigs.get("tesla_tower")
        val CHARGE_TICKS get() = kotlin.math.ceil(600.0/settings.rpm.get()).toInt().coerceAtLeast(1)
        val COOLDOWN_TICKS get() = CHARGE_TICKS
        val MAIN_RANGE get() = settings.range!!.get()
        val JUMP_RANGE get() = settings.jumpRange!!.get()
        val MAX_VICTIMS get() = settings.maxVictims!!.get()
        val DAMAGE get() = settings.damage.get().toFloat()
        const val STUN_TICKS=2
    }
}
