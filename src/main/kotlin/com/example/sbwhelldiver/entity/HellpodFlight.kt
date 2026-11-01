package com.example.sbwhelldiver.entity

/** Shared trajectory for hellpods and equipment deliveries. */
object HellpodFlight {
    fun height(now:Double,launch:Long,arrival:Long,start:Double,landing:Double,brakeHeight:Double,brakingTicks:Int=AbstractHellpodEntity.SCHEDULED_BRAKE_TICKS,
        brake:(Double,Double,Long,Double)->Double={remaining,distance,_,_->distance*(.28*remaining+.72*remaining*remaining)}):Double {
        val duration=(arrival-launch).coerceAtLeast(1)
        val age=(now-launch).coerceIn(0.0,duration.toDouble())
        val ticks=minOf(brakingTicks.toLong(),maxOf(1L,duration-1));val fast=duration-ticks
        val distance=(start-landing).coerceAtLeast(0.0);val braking=minOf(brakeHeight,distance)
        return landing+if(age<fast&&fast>0)distance-(distance-braking)*age/fast else brake(((duration-age)/ticks).coerceIn(0.0,1.0),braking,ticks,if(fast>0)(distance-braking)/fast else 0.0)
    }
    fun brakeStart(launch:Long,arrival:Long)=arrival-minOf(AbstractHellpodEntity.SCHEDULED_BRAKE_TICKS.toLong(),maxOf(1,arrival-launch-1))
}
