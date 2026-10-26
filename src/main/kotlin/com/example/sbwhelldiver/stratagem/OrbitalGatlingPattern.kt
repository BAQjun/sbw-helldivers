package com.example.sbwhelldiver.stratagem

import com.example.sbwhelldiver.config.GatlingSettings
import net.minecraft.world.phys.Vec3
import java.util.Random
import kotlin.math.*

/** Native start/step/area fields, adapted to a bounded continuous walk in Minecraft metres.
 * Seed and immutable settings reproduce the same path on server, client and save reload. */
object OrbitalGatlingPattern {
 fun highExplosive(index:Int)=index%4==3
 fun due(age:Double,s:GatlingSettings)=if(age<0)0 else (floor((age+1e-7)/s.intervalTicks).toInt()+1).coerceAtMost(s.total)
 fun offsets(seed:Long,s:GatlingSettings):List<Vec3> {
  val random=Random(seed)
  var angle=random.nextDouble()*PI*2
  var p=Vec3(cos(angle),0.0,sin(angle)).scale(sqrt(random.nextDouble())*min(s.startRadius,s.radius))
  return List(s.total){index->
   if(index>0){
    angle=random.nextDouble()*PI*2
    val step=min(s.stepMin,s.stepMax)+random.nextDouble()*abs(s.stepMax-s.stepMin)
    var next=p.add(cos(angle)*step,0.0,sin(angle)*step)
    if(next.horizontalDistance()>s.radius){
     // Reflect the radial overshoot inward instead of accumulating shots on the boundary.
     val length=next.horizontalDistance();val reflected=abs(2*s.radius-length).coerceAtMost(s.radius)
     next=next.scale(reflected/length)
    }
    p=next
   };p
  }
 }
}
