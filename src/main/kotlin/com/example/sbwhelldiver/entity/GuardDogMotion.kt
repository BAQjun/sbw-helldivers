package com.example.sbwhelldiver.entity

/** Rover unfolds with the carrier down; folding starts only after the carrier returns. */
object GuardDogMotion {
    private const val FOLD_TICKS = 7f
    private const val CARRIER_TICKS = GuardDogEntity.TRANSITION_TICKS - FOLD_TICKS

    fun carrierAge(laser: Boolean, launch: Boolean, age: Float): Float =
        if (!laser) age else ((if (launch) age - FOLD_TICKS else age) *
            GuardDogEntity.TRANSITION_TICKS / CARRIER_TICKS).coerceIn(0f, GuardDogEntity.TRANSITION_TICKS.toFloat())

    fun foldTime(launch: Boolean, age: Float): Float {
        val progress = (if (launch) 1f - age / FOLD_TICKS else (age - CARRIER_TICKS) / FOLD_TICKS).coerceIn(0f, 1f)
        return progress * .3166667f
    }

    fun liftTick(laser: Boolean) = if (laser) 35 else GuardDogEntity.LIFT_TICK
    fun contactTick(laser: Boolean) = if (laser) 18 else GuardDogEntity.CONTACT_TICK
}
