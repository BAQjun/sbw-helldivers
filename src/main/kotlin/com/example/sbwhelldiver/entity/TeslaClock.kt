package com.example.sbwhelldiver.entity

import net.minecraft.nbt.CompoundTag

/** Twenty ticks of charge, one discharge, then twenty ticks of quiet cooldown. */
class TeslaClock {
    enum class State { IDLE, CHARGING, COOLDOWN }
    var state = State.IDLE
        private set
    var age = 0
        private set
    fun advance(hasTarget: Boolean): Boolean {
        when (state) {
            State.IDLE -> if (hasTarget) { state=State.CHARGING; age=0 }
            State.CHARGING -> {
                if (!hasTarget) { reset(); return false }
                if (++age >= CHARGE_TICKS) { state=State.COOLDOWN; age=0; return true }
            }
            State.COOLDOWN -> if (++age >= COOLDOWN_TICKS) {
                state=if(hasTarget) State.CHARGING else State.IDLE; age=0
            }
        }
        return false
    }
    fun reset() { state=State.IDLE; age=0 }
    fun save(tag: CompoundTag) { tag.putInt("TeslaState",state.ordinal);tag.putInt("TeslaAge",age) }
    fun restore(tag: CompoundTag) {
        state=State.entries[tag.getInt("TeslaState").coerceIn(0,2)]
        age=tag.getInt("TeslaAge").coerceIn(0,19)
    }
    companion object {
        const val CHARGE_TICKS=20
        const val COOLDOWN_TICKS=20
        const val MAIN_RANGE=20.0
        const val JUMP_RANGE=30.0
        const val MAX_VICTIMS=3
        const val DAMAGE=80f
        const val STUN_TICKS=2
    }
}
