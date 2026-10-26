package com.example.sbwhelldiver.stratagem

import com.example.sbwhelldiver.config.AirburstSettings
import net.minecraft.world.phys.Vec3
import kotlin.math.*

object OrbitalAirburstPattern {
 /** Burst on the ship-to-beacon line, so the spread continues toward the beacon rather than beyond it. */
 fun burstPoint(source:Vec3,target:Vec3,height:Double):Vec3 {
  val fraction=(height/(source.y-target.y).coerceAtLeast(height)).coerceIn(0.0,1.0)
  return target.lerp(source,fraction)
 }
 /** Equal solid-angle strata prevent twelve fragments from randomly bunching into a single streak. */
 fun directions(axis:Vec3,settings:AirburstSettings,seed:Long):List<Vec3> {
  val forward=axis.normalize().takeIf{it.lengthSqr()>.5}?:Vec3(0.0,-1.0,0.0)
  val reference=if(abs(forward.y)>.9)Vec3(1.0,0.0,0.0) else Vec3(0.0,1.0,0.0)
  val right=forward.cross(reference).normalize();val up=right.cross(forward).normalize()
  val phase=java.util.Random(seed).nextDouble()*2*PI
  val edge=cos(Math.toRadians(settings.cone))
  return List(settings.children){i ->
   val c=1-(1-edge)*(i+.5)/settings.children;val s=sqrt(1-c*c);val a=phase+i*PI*(3-sqrt(5.0))
   forward.scale(c).add(right.scale(cos(a)*s)).add(up.scale(sin(a)*s)).normalize()
  }
 }
}
