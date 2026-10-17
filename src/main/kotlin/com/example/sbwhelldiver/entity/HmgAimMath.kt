package com.example.sbwhelldiver.entity

import net.minecraft.world.phys.Vec2
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/** Inverse of the pitch/yaw muzzle transform; aim the bore, rather than its pivot, at a target. */
object HmgAimMath {
    fun angles(target:Vec3,localMuzzle:Vec3,pivotForward:Double):Vec2 {
        val radius=target.horizontalDistance().coerceAtLeast(.0001)
        val lateral=localMuzzle.x.coerceIn(-radius*.999,radius*.999)
        val forward=sqrt((radius*radius-lateral*lateral).coerceAtLeast(.000001))
        val pitchedForward=forward-pivotForward
        val distance=hypot(pitchedForward,target.y)
        val yaw=atan2(-target.x,target.z)+asin(lateral/radius)
        val pitch=asin((localMuzzle.y/distance).coerceIn(-1.0,1.0))-atan2(target.y,pitchedForward)
        return Vec2(Math.toDegrees(yaw).toFloat(),Math.toDegrees(pitch).toFloat())
    }
}
