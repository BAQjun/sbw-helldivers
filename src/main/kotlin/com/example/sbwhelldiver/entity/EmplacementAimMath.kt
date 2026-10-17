package com.example.sbwhelldiver.entity

import net.minecraft.util.Mth
import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/** Gameplay handling, independent of the motor and of the authored recoil animation. */
object EmplacementAimMath {
    const val FOLLOW_RATE = 3.14
    const val RANGE = 200.0
    fun follow(current:Float,target:Float,wrap:Boolean,dt:Double=.05,ergonomics:Double=65.0):Float {
        val error=if(wrap)Mth.wrapDegrees(target-current) else target-current
        if(dt<=0.0)return current
        val ergoScale=(ergonomics/65.0).coerceIn(.25,2.0)
        val response=1-exp(-FOLLOW_RATE*ergoScale*dt)
        // Finish small corrections in finite time instead of crawling asymptotically.
        // Cap at the remaining error so the circle settles without overshoot.
        val closingStep=30.0*sqrt(ergoScale)*dt
        val step=max(abs(error)*response,closingStep).coerceAtMost(abs(error).toDouble())
        return current + (sign(error)*step).toFloat()
    }
    fun direction(yaw:Float,pitch:Float):Vec3 {
        val y=Math.toRadians(yaw.toDouble());val p=Math.toRadians(pitch.toDouble())
        return Vec3(-sin(y)*cos(p),-sin(p),cos(y)*cos(p))
    }
    fun angles(direction:Vec3):Vec2 = Vec2(Math.toDegrees(atan2(-direction.x,direction.z)).toFloat(),
        Math.toDegrees(atan2(-direction.y,hypot(direction.x,direction.z))).toFloat())
    /** A spherical cone, rather than separate yaw/pitch limits (which allow diagonal overshoot). */
    fun constrain(desired:Vec3,bore:Vec3,limitDegrees:Double):Vec3 {
        val axis=if(bore.lengthSqr()<1e-12)Vec3(0.0,0.0,1.0) else bore.normalize()
        if(desired.lengthSqr()<1e-12)return axis
        val wanted=desired.normalize();val dot=axis.dot(wanted).coerceIn(-1.0,1.0)
        val limit=Math.toRadians(limitDegrees.coerceIn(0.0,180.0))
        if(dot>=cos(limit))return wanted
        val lateral=wanted.subtract(axis.scale(dot))
        val tangent=if(lateral.lengthSqr()>1e-12)lateral.normalize()
            else axis.cross(if(abs(axis.y)<.9)Vec3(0.0,1.0,0.0) else Vec3(1.0,0.0,0.0)).normalize()
        return axis.scale(cos(limit)).add(tangent.scale(sin(limit))).normalize()
    }
    private fun random(seed:Long):Double {
        var n=seed xor (seed ushr 33);n*= -49064778989728563L;n=n xor (n ushr 33);n*= -4265267296055464877L;n=n xor (n ushr 33)
        return ((n ushr 11).toDouble()/9007199254740992.0)*2-1
    }
    /** Coherent value noise with a quintic transition; no new random value per tick. */
    fun noise(time:Double,seed:Long):Double {
        val cell=floor(time).toLong();val f=time-cell;val t=f*f*f*(f*(f*6-15)+10)
        return random(seed+cell*6364136223846793005L)*(1-t)+random(seed+(cell+1)*6364136223846793005L)*t
    }
    fun shotHorizontal(seed:Long,serial:Int,scale:Double):Float=(random(seed+serial*1442695040888963407L)*scale).toFloat()
}
