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
   // Keep one independently sampled inner hit instead of rejecting whole salvos
   // until every shell has migrated toward the rim. Randomize its firing order.
   val innerShot=if(s.shots>1)random.nextInt(s.shots)else -1
   val innerAngle=random.nextDouble()*2*PI
   val innerRadius=sqrt(random.nextDouble())*s.radius*.5
   val innerPoint=if(innerShot>=0)center(first+innerShot,s,0f).add(cos(innerAngle)*innerRadius,0.0,sin(innerAngle)*innerRadius)else Vec3.ZERO
   var best=emptyList<Vec3>();var bestSpacing=-1.0
   fun spacing(points:List<Vec3>):Double{
    var closest=Double.POSITIVE_INFINITY
    for(i in points.indices)for(j in 0 until i)closest=minOf(closest,points[i].distanceToSqr(points[j]))
    return closest
   }
   // Keep the inner sample fixed while finding separated companion impacts.
   for(attempt in 0 until 128){
    val points=List(s.shots){shot->
     if(shot==innerShot)innerPoint else {
      val angle=random.nextDouble()*2*PI;val radius=sqrt(random.nextDouble())*s.radius
      center(first+shot,s,0f).add(cos(angle)*radius,0.0,sin(angle)*radius)
     }
    }
    val distance=spacing(points)
    if(distance>bestSpacing){best=points;bestSpacing=distance}
    if(distance>=requiredSquared)break
   }
   if(bestSpacing<requiredSquared && innerShot>=0){
    // Sweep companion-ring orientations without discarding the inner hit.
    // This also handles the tighter Walking Barrage footprint.
    val phase=random.nextDouble()*2*PI
    for(attempt in 0 until 64){
     val points=List(s.shots){shot->
      if(shot==innerShot)innerPoint else {
       val companion=if(shot<innerShot)shot else shot-1
       val angle=phase+attempt*2*PI/64+companion*2*PI/(s.shots-1)
       center(first+shot,s,0f).add(cos(angle)*s.radius,0.0,sin(angle)*s.radius)
      }
     }
     val distance=spacing(points)
     if(distance>bestSpacing){best=points;bestSpacing=distance}
     if(distance>=requiredSquared)break
    }
   }
   if(bestSpacing<requiredSquared && !s.walking && innerShot>=0 && s.shots>3 && s.radius>0.0){
    // Five-shell salvos need an irregular companion arc: a regular square may
    // clip the retained inner hit. Fit the arc outside its exclusion circle.
    val r=innerPoint.length();val companions=s.shots-1
    val minAngle=2*asin((separation/(2*s.radius)).coerceIn(0.0,1.0))+1e-6
    val exclusion=if(r<1e-9)0.0 else (acos(((s.radius*s.radius+r*r-requiredSquared)/(2*s.radius*r)).coerceIn(-1.0,1.0))+1e-6).coerceAtMost(PI)
    val available=2*PI-2*exclusion
    val span=minOf(available,2*PI-minAngle-1e-6)
    if(span>=(companions-1)*minAngle && (r>1e-9 || s.radius>=separation)){
     val phase=atan2(innerPoint.z,innerPoint.x)+exclusion+(available-span)*random.nextDouble()
     val points=List(s.shots){shot->
      if(shot==innerShot)innerPoint else {
       val companion=if(shot<innerShot)shot else shot-1
       val angle=phase+companion*span/(companions-1)
       Vec3(cos(angle)*s.radius,0.0,sin(angle)*s.radius)
      }
     }
     val distance=spacing(points)
     if(distance>bestSpacing){best=points;bestSpacing=distance}
    }
   }
   if(bestSpacing<requiredSquared){
    // Extremely tight custom settings may not permit an inner hit and separated blasts.
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
