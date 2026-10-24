package com.example.sbwhelldiver.entity
import net.minecraft.world.phys.Vec3
import kotlin.math.*
/** Native 300 m/s attack/egress; distant high approach, fixed nose gun and finite pull-up rate. */
object EagleFlight {
 const val AIRSTRIKE_START=82 // 4.1 seconds from beacon confirmation
 const val STRAFE_START=48 // 2.4 seconds from beacon confirmation
 fun attackStart(attack:EagleAttack)=attack.callTicks
 fun attackStart(airstrike:Boolean)=attackStart(EagleAttack.legacy(airstrike))
 fun attackEnd(attack:EagleAttack)=attack.callTicks+attack.duration
 fun attackEnd(airstrike:Boolean)=attackEnd(EagleAttack.legacy(airstrike))
 fun end(attack:EagleAttack)=attackEnd(attack)+58
 fun end(airstrike:Boolean)=end(EagleAttack.legacy(airstrike))
 const val SPEED=15.0 // native 300 m/s at 20 TPS
 const val HEIGHT=100.0
 const val STRAFE_HEIGHT=253.57095704441966 // 600 m slant range, 25 degree dive
 const val ENTRY_PITCH=25.0
 const val PULL_UP_DEGREES_PER_TICK=1.8
 const val MAX_CLIMB_DEGREES=35.0
 const val BOMB_COUNT=6
 const val BOMB_INTERVAL=4
 const val BOMB_FLIGHT=20
 const val BOMB_GRAVITY=.16
 const val STRAFE_ROUNDS=100
 private fun rad(d:Double)=Math.toRadians(d)
 fun heading(throwPoint:Vec3,target:Vec3,yaw:Float,airstrike:Boolean):Vec3 {
  var f=target.subtract(throwPoint).multiply(1.0,0.0,1.0)
  f=if(f.lengthSqr()<1e-8)Vec3(-sin(rad(yaw.toDouble())),0.0,cos(rad(yaw.toDouble()))) else f.normalize()
  return if(airstrike)Vec3(-f.z,0.0,f.x) else f
 }
 private fun arc(pitch:Double,rate:Double,t:Double):Pair<Double,Double> {
  if(abs(rate)<1e-12)return SPEED*cos(pitch)*t to -SPEED*sin(pitch)*t
  val b=pitch+rate*t
  return SPEED/rate*(sin(b)-sin(pitch)) to SPEED/rate*(cos(b)-cos(pitch))
 }
 private val terminalAngles=java.util.concurrent.ConcurrentHashMap<Pair<Double,EagleAttack>,Double>()
 /** Solve the terminal firing angle so the fixed gun sweeps exactly 60 m at any terrain clearance. */
 fun exitPitch(height:Double,attack:EagleAttack=EagleAttack.STRAFE):Double = terminalAngles.computeIfAbsent(height to attack){
  val a=rad(ENTRY_PITCH);var lo=rad(2.0);var hi=a
  repeat(40){val b=(lo+hi)/2;val d=arc(a,(b-a)/attack.duration,attack.duration.toDouble());val hit=-height/tan(a)+d.first+(height+d.second)/tan(b);if(hit>attack.spread)lo=b else hi=b}
  (lo+hi)/2
 }
 private data class Approach(val points:List<Pair<Double,Double>>,val distances:DoubleArray)
 private val approaches=java.util.concurrent.ConcurrentHashMap<Pair<EagleAttack,Double>,Approach>()
 private fun approach(attack:EagleAttack,height:Double,attackS:Double,before:Double):Pair<Double,Double> {
  val bombing=attack.levelBombing
  val curve=approaches.computeIfAbsent(attack to height){
   val startS=min(if(attack==EagleAttack.AIRSTRIKE)-800.0 else -1000.0,attackS-400)
   val startH=(if(attack==EagleAttack.AIRSTRIKE)1200.0 else 800.0)+max(0.0,height-attack.height)
   val tangent=if(bombing)0.0 else rad(ENTRY_PITCH)
   val pts=(0..400).map {i->val u=i/400.0;val v=1-u
    val x=v*v*v*startS+3*v*v*u*(startS+35)+3*v*u*u*(attackS-120*cos(tangent))+u*u*u*attackS
    val y=v*v*v*startH+3*v*v*u*(startH-250)+3*v*u*u*(height+120*sin(tangent))+u*u*u*height
    x to y
   }
   val dist=DoubleArray(pts.size);for(i in 1 until pts.size)dist[i]=dist[i-1]+hypot(pts[i].first-pts[i-1].first,pts[i].second-pts[i-1].second)
   Approach(pts,dist)
  }
  val d=curve.distances.last()+before*SPEED
  if(d<=0){val p=curve.points[0];val q=curve.points[1];val len=hypot(q.first-p.first,q.second-p.second);return p.first+(q.first-p.first)*d/len to p.second+(q.second-p.second)*d/len}
  var hi=curve.distances.binarySearch(d);if(hi<0)hi=-hi-1;hi=hi.coerceIn(1,curve.points.lastIndex)
  val lo=hi-1;val u=(d-curve.distances[lo])/(curve.distances[hi]-curve.distances[lo]);val p=curve.points[lo];val q=curve.points[hi]
  return p.first+(q.first-p.first)*u to p.second+(q.second-p.second)*u
 }
 private fun local(age:Double,attack:EagleAttack,height:Double):Pair<Double,Double> {
  val bombing=attack.levelBombing;val startAngle=rad(ENTRY_PITCH)
  val attackS=if(bombing)-attack.spread/2-SPEED*BOMB_FLIGHT else -attack.spread/2-height/tan(startAngle)
  if(age<attackStart(attack))return approach(attack,height,attackS,age-attackStart(attack))
  val q=(age-attackStart(attack)).coerceAtMost(attack.duration.toDouble())
  val end=if(bombing)0.0 else exitPitch(height,attack)
  val d=if(bombing)SPEED*q to 0.0 else arc(startAngle,(end-startAngle)/attack.duration,q)
  val e=(age-attackEnd(attack)).coerceAtLeast(0.0);val rate=-rad(PULL_UP_DEGREES_PER_TICK);val bendTime=((end+rad(MAX_CLIMB_DEGREES))/-rate).coerceAtLeast(0.0);val bend=arc(end,rate,min(e,bendTime));val straight=arc(-rad(MAX_CLIMB_DEGREES),0.0,(e-bendTime).coerceAtLeast(0.0))
  return attackS+d.first+bend.first+straight.first to height+d.second+bend.second+straight.second
 }
 fun position(target:Vec3,forward:Vec3,age:Double,attack:EagleAttack,clearance:Double=attack.height):Vec3 {
  val p=local(age,attack,clearance);return target.add(forward.scale(p.first)).add(0.0,p.second,0.0)
 }
 fun position(target:Vec3,forward:Vec3,age:Double,airstrike:Boolean,clearance:Double=if(airstrike)HEIGHT else STRAFE_HEIGHT)=position(target,forward,age,EagleAttack.legacy(airstrike),clearance)
 fun pitch(age:Double,attack:EagleAttack,height:Double=attack.height):Float {
  if(age<attackStart(attack)){
   val a=position(Vec3.ZERO,Vec3(0.0,0.0,1.0),age,attack,height)
   val b=position(Vec3.ZERO,Vec3(0.0,0.0,1.0),age+.01,attack,height)
   return Math.toDegrees(atan2(a.y-b.y,b.z-a.z)).toFloat()
  }
  val end=if(attack.levelBombing)0.0 else Math.toDegrees(exitPitch(height,attack))
  if(age<=attackEnd(attack))return if(attack.levelBombing)0f else (ENTRY_PITCH+(end-ENTRY_PITCH)*(age-attackStart(attack))/attack.duration).toFloat()
  return (end-PULL_UP_DEGREES_PER_TICK*(age-attackEnd(attack))).coerceAtLeast(-MAX_CLIMB_DEGREES).toFloat()
 }
 fun pitch(age:Double,airstrike:Boolean=true,height:Double=if(airstrike)HEIGHT else STRAFE_HEIGHT)=pitch(age,EagleAttack.legacy(airstrike),height)
 /** Fit the complete firing and pull-out corridor before committing to the pass. */
 fun terrainClearance(target:Vec3,forward:Vec3,attack:EagleAttack,groundAt:(Vec3)->Double):Double {
  var height=attack.height
  val across=Vec3(-forward.z,0.0,forward.x)
  repeat(4) {
   var deficit=0.0
   for(t in attackStart(attack)-12..end(attack) step 2) {
    val p=position(target,forward,t.toDouble(),attack,height)
    for(side in listOf(-9.0,0.0,9.0))deficit=max(deficit,groundAt(p.add(across.scale(side)))+18.0-p.y)
   }
   if(deficit<=.01)return height
   height+=deficit+2.0
  }
  return height
 }
 fun terrainClearance(target:Vec3,forward:Vec3,bombing:Boolean,groundAt:(Vec3)->Double)=terrainClearance(target,forward,EagleAttack.legacy(bombing),groundAt)
 fun payloadCount(airstrike:Boolean)=if(airstrike)BOMB_COUNT else STRAFE_ROUNDS
 fun releaseAge(index:Int,airstrike:Boolean)=attackStart(airstrike)+if(airstrike)index*BOMB_INTERVAL.toDouble() else index*30.0/STRAFE_ROUNDS
 fun bombOffset(index:Int)= -30.0+index*12.0
 fun strafeOffset(index:Int,height:Double=STRAFE_HEIGHT):Double {
  val t=attackStart(false)+index*30.0/STRAFE_ROUNDS;val p=local(t,EagleAttack.STRAFE,height);return p.first+p.second/tan(rad(pitch(t,false,height).toDouble()))
 }
 fun bombVelocity(start:Vec3,end:Vec3):Vec3 {val t=BOMB_FLIGHT.toDouble();return end.subtract(start).scale(1/t).add(0.0,BOMB_GRAVITY*(t-1)/2,0.0)}
}
