package com.example.sbwhelldiver.entity

import net.minecraft.world.phys.Vec2
import kotlin.math.*

/** Frame-rate independent shot response, separate from handling and whole-body animation. */
object EmplacementRecoil {
    const val AIM_END_SECONDS=.50
    const val CAMERA_END_SECONDS=.42
    private val times=doubleArrayOf(0.0,.025,.065,.13,.22,.34,AIM_END_SECONDS)
    private val amounts=doubleArrayOf(0.0,1.0,1.0,.76,.38,.10,0.0)
    fun aimEnvelope(age:Double):Double {
        if(!age.isFinite() || age<=0 || age>=AIM_END_SECONDS)return 0.0
        val i=times.indexOfFirst {it>age}-1
        val t=((age-times[i])/(times[i+1]-times[i])).coerceIn(0.0,1.0)
        val smooth=t*t*(3-2*t)
        return amounts[i]+(amounts[i+1]-amounts[i])*smooth
    }
    fun cameraKick(age:Double,side:Double):Vec2 {
        if(!age.isFinite() || !side.isFinite() || age<=0 || age>=CAMERA_END_SECONDS)return Vec2.ZERO
        val attack=.025
        val impact=if(age<attack) {
            val t=age/attack;t*t*(3-2*t)
        } else ((CAMERA_END_SECONDS-age)/(CAMERA_END_SECONDS-attack)).pow(3.0)
        // The view kicks up, then vibrates in both axes while the impulse settles.
        val gate=(age/attack).coerceAtMost(1.0)
        val vibration=sin((age-attack)*2*PI*10)*exp(-age*9)*gate
        val fade=((CAMERA_END_SECONDS-age)/CAMERA_END_SECONDS).coerceIn(0.0,1.0)
        return Vec2((side*impact+.22*vibration*fade).toFloat(),
            (-1.10*impact+.15*vibration*fade).toFloat())
    }
}
