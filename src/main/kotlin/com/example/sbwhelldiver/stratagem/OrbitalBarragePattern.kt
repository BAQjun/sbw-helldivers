package com.example.sbwhelldiver.stratagem

import com.example.sbwhelldiver.config.BarrageSettings
import net.minecraft.world.phys.Vec3
import java.util.Random
import kotlin.math.*

object OrbitalBarragePattern{
 fun due(age:Double,s:BarrageSettings)=(0 until s.total).count{s.shotTime(it)<=age+1e-7}
 fun direction(yaw:Float)=Vec3(-sin(Math.toRadians(yaw.toDouble())),0.0,cos(Math.toRadians(yaw.toDouble())))
 fun center(index:Int,s:BarrageSettings,yaw:Float)=if(s.walking)direction(yaw).scale(s.advanceSpeed*s.shotTime(index)/20.0)else Vec3.ZERO
 /** Plan separated salvos once per call. Saved impact points remain stable across reloads. */
 fun offsets(seed:Long,s:BarrageSettings,yaw:Float):List<Vec3>{
  val radians=Math.toRadians(yaw.toDouble());val cosYaw=cos(radians);val sinYaw=sin(radians)
  return localOffsets(seed,s).map{p->Vec3(p.x*cosYaw-p.z*sinYaw,0.0,p.x*sinYaw+p.z*cosYaw)}
 }
 private fun localOffsets(seed:Long,s:BarrageSettings):List<Vec3>{
  val random=Random(seed)
  val result=ArrayList<Vec3>(s.total)
  // Horizontal spacing keeps even terrain-projected blast footprints apart.
  val separation=2.0*s.outer+.5
  val requiredSquared=separation*separation
  for(round in 0 until s.rounds){
   val first=round*s.shots
   var best=emptyList<Vec3>();var bestSpacing=-1.0
   fun spacing(points:List<Vec3>):Double{
    var closest=Double.POSITIVE_INFINITY
    for(i in points.indices)for(j in 0 until i)closest=minOf(closest,points[i].distanceToSqr(points[j]))
    return closest
   }
   // Retry complete salvos rather than accepting an early point that traps later shots.
   for(attempt in 0 until 128){
    val points=List(s.shots){shot->
     val angle=random.nextDouble()*2*PI;val radius=sqrt(random.nextDouble())*s.radius
     center(first+shot,s,0f).add(cos(angle)*radius,0.0,sin(angle)*radius)
    }
    val distance=spacing(points)
    if(distance>bestSpacing){best=points;bestSpacing=distance}
    if(distance>=requiredSquared)break
   }
   if(bestSpacing<requiredSquared){
    // A regular perimeter layout guarantees separation for all three default salvos.
    // Impossible custom radius/shot-count combinations retain their configured footprint
    // and use the most separated bounded candidate instead of looping or widening it.
    for(attempt in 0 until 64){
     val phase=random.nextDouble()*2*PI
     val points=List(s.shots){shot->
      val angle=phase+shot*2*PI/s.shots
      center(first+shot,s,0f).add(cos(angle)*s.radius,0.0,sin(angle)*s.radius)
     }
     val distance=spacing(points)
     if(distance>bestSpacing){best=points;bestSpacing=distance}
     if(distance>=requiredSquared)break
    }
   }
   result.addAll(best)
  }
  return result
 }
}
