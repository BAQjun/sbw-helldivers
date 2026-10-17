package com.example.sbwhelldiver.entity

/** Contact-mine ThrowerComponentData, not the tank mine. */
object MinefieldClock {
    const val PANELS=6
    const val PER_PANEL=8
    const val COUNT=PANELS*PER_PANEL
    const val THROW_DELAY_TICKS=40
    const val THROW_TICKS=30
    const val SPINDOWN_TICKS=30
    const val ARM_TICKS=40
    const val RPM=400.0
    const val MIN_SPEED=5.0
    const val MAX_SPEED=12.5
    const val SPREAD_DEGREES=45.0
    /** Four staggered landing bands, twelve mines each, in metres from the pod. */
    fun landingRadius(wave: Int)=7.0+3.5*(wave.coerceIn(0,PER_PANEL-1)/2)
    fun releasedWaves(age: Int,perPanel: Int=PER_PANEL): Int = if(age<0) 0 else (age*perPanel/THROW_TICKS+1).coerceAtMost(perPanel)
    fun speedAt(age: Double): Double = when {
        age<0 -> 0.0
        age<THROW_TICKS -> RPM*360.0/1200.0
        else -> RPM*360.0/1200.0*(1-(age-THROW_TICKS)/SPINDOWN_TICKS).coerceIn(0.0,1.0)
    }
    fun angleAt(age: Double): Double {
        val full=age.coerceIn(0.0,THROW_TICKS.toDouble())
        val decel=(age-THROW_TICKS).coerceIn(0.0,SPINDOWN_TICKS.toDouble())
        return RPM*360.0/1200.0*(full+decel-decel*decel/(2*SPINDOWN_TICKS))
    }
}
