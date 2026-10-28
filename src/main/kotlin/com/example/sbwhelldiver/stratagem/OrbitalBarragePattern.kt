package com.example.sbwhelldiver.stratagem

import com.example.sbwhelldiver.config.BarrageSettings
import net.minecraft.world.phys.Vec3
import java.util.Random
import kotlin.math.*

object OrbitalBarragePattern{
 fun due(age:Double,s:BarrageSettings)=(0 until s.total).count{s.shotTime(it)<=age+1e-7}
 fun direction(yaw:Float)=Vec3(-sin(Math.toRadians(yaw.toDouble())),0.0,cos(Math.toRadians(yaw.toDouble())))
 fun center(index:Int,s:BarrageSettings,yaw:Float)=if(s.walking)direction(yaw).scale(s.advanceSpeed*s.shotTime(index)/20.0)else Vec3.ZERO
 /** Plan the complete area before firing; persisted targets stay stable across reloads. */
 fun offsets(seed:Long,s:BarrageSettings,yaw:Float):List<Vec3>{
  val radians=Math.toRadians(yaw.toDouble());val cosYaw=cos(radians);val sinYaw=sin(radians)
  return localOffsets(seed,s).map{p->Vec3(p.x*cosYaw-p.z*sinYaw,0.0,p.x*sinYaw+p.z*cosYaw)}
 }
 private fun localOffsets(seed:Long,s:BarrageSettings):List<Vec3>{
  if(s.total<=0)return emptyList()
  if(s.radius<=0)return List(s.total){center(it,s,0f)}
  val random=Random(seed)
  // Native smoke: six zero-interval salvos, area_size=15, no wandering center.
  // Uniform disk sampling is the MC scatter adaptation; no artillery separation bias.
  if(s.kind in com.example.sbwhelldiver.config.OrbitalFieldSettings.ids)return List(s.total){
   val angle=random.nextDouble()*2*PI;val radius=s.radius*sqrt(random.nextDouble())
   Vec3(cos(angle)*radius,0.0,sin(angle)*radius)
  }
  val result=ArrayList<Vec3>(s.total)
  val phase=random.nextDouble()*2*PI
  // Radius squared is proportional to disk area. Every call visits each small
  // radial stratum once, and each radial band visits every angular sector once.
  val radialSlots=List(s.shots){(0 until s.rounds).toMutableList().also{java.util.Collections.shuffle(it,random)}}
  val unusedSectors=List(s.shots){(0 until s.rounds).toMutableList()}
  val separation=2.0*s.outer+.5
  val separationSquared=separation*separation
  val repeatGapSquared=s.radius*s.radius/s.total
  for(round in 0 until s.rounds){
   val order=(0 until s.shots).toMutableList().also{java.util.Collections.shuffle(it,random)}
   // Fix radii before comparing candidates: spacing can never push them outward.
   val radii=DoubleArray(s.shots){band->
    s.radius*sqrt((band*s.rounds+radialSlots[band][round]+random.nextDouble())/s.total)
   }
   var best=emptyList<Vec3>();var bestSectors=IntArray(s.shots);var bestScore=-1.0
   for(attempt in 0 until 64){
    val sectors=IntArray(s.shots){band->unusedSectors[band][random.nextInt(unusedSectors[band].size)]}
    val points=List(s.shots){shot->
     val band=order[shot]
     val angle=phase+(sectors[band]+random.nextDouble())*2*PI/s.rounds
     center(round*s.shots+shot,s,0f).add(cos(angle)*radii[band],0.0,sin(angle)*radii[band])
    }
    // Prefer separated blasts and avoid earlier impacts, subject to the fixed
    // area quotas. Tight salvos may overlap rather than abandoning the mid-radius.
    var score=1.0
    for(i in points.indices){
     for(j in 0 until i)score=min(score,points[i].distanceToSqr(points[j])/separationSquared)
     for(previous in result)score=min(score,points[i].distanceToSqr(previous)/repeatGapSquared)
    }
    if(score>bestScore){best=points;bestSectors=sectors;bestScore=score}
    if(score>=1.0)break
   }
   for(band in 0 until s.shots)unusedSectors[band].remove(bestSectors[band])
   result.addAll(best)
  }
  return result
 }
}
