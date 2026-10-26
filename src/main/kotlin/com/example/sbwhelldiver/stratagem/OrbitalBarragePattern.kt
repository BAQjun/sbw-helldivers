package com.example.sbwhelldiver.stratagem

import com.example.sbwhelldiver.config.BarrageSettings
import net.minecraft.world.phys.Vec3
import java.util.Random
import kotlin.math.*

object OrbitalBarragePattern{
 fun due(age:Double,s:BarrageSettings)=(0 until s.total).count{s.shotTime(it)<=age+1e-7}
 fun direction(yaw:Float)=Vec3(-sin(Math.toRadians(yaw.toDouble())),0.0,cos(Math.toRadians(yaw.toDouble())))
 fun center(index:Int,s:BarrageSettings,yaw:Float)=if(s.walking)direction(yaw).scale(s.advanceSpeed*s.shotTime(index)/20.0)else Vec3.ZERO
 /** Independent uniform-area disk samples. Seeded once per call for save/client consistency. */
 fun offsets(seed:Long,s:BarrageSettings,yaw:Float):List<Vec3>{
  val random=Random(seed)
  return List(s.total){i->val a=random.nextDouble()*2*PI;val radius=sqrt(random.nextDouble())*s.radius
   center(i,s,yaw).add(cos(a)*radius,0.0,sin(a)*radius)
  }
 }
}
