package com.example.sbwhelldiver.config

import net.neoforged.neoforge.common.ModConfigSpec
import net.minecraft.nbt.CompoundTag

/** Snapshot per call; editing config cannot alter an airborne barrage. */
data class GatlingSettings(val rounds:Int=5,val shots:Int=60,val interval:Double=.045,
 val damage:Float=30f,val blast:Float=60f,val inner:Double=1.5,val outer:Float=4.5f,
 val radius:Double=7.0,val startRadius:Double=1.0,val stepMin:Double=1.0,val stepMax:Double=2.0,
 val flightTicks:Int=30) {
 val total get()=rounds*shots
 val intervalTicks get()=interval*20.0
 fun shotTime(index:Int)=index*intervalTicks
 val lastShotTime get()=shotTime(total-1)
 fun save()=CompoundTag().also{
  it.putInt("Rounds",rounds);it.putInt("Shots",shots);it.putDouble("Interval",interval)
  it.putFloat("Damage",damage);it.putFloat("Blast",blast);it.putDouble("Inner",inner);it.putFloat("Outer",outer)
  it.putDouble("Radius",radius);it.putDouble("StartRadius",startRadius);it.putDouble("StepMin",stepMin);it.putDouble("StepMax",stepMax);it.putInt("Flight",flightTicks)
 }
 companion object {
  fun load(t:CompoundTag)=GatlingSettings(t.getInt("Rounds").coerceIn(1,20),t.getInt("Shots").coerceIn(1,200),
   t.getDouble("Interval").coerceIn(.01,1.0),t.getFloat("Damage").coerceIn(0f,100000f),t.getFloat("Blast").coerceIn(0f,100000f),
   t.getDouble("Inner").coerceIn(0.0,128.0),t.getFloat("Outer").coerceIn(.1f,256f),t.getDouble("Radius").coerceIn(1.0,64.0),
   t.getDouble("StartRadius").coerceIn(0.0,64.0),t.getDouble("StepMin").coerceIn(.1,16.0),t.getDouble("StepMax").coerceIn(.1,16.0),t.getInt("Flight").coerceIn(10,200))
 }
}
object OrbitalGatlingConfig {
 private lateinit var rounds:ModConfigSpec.IntValue
 private lateinit var shots:ModConfigSpec.IntValue
 private lateinit var interval:ModConfigSpec.DoubleValue
 private lateinit var delay:ModConfigSpec.DoubleValue
 private lateinit var flight:ModConfigSpec.DoubleValue
 private lateinit var damage:ModConfigSpec.DoubleValue
 private lateinit var blast:ModConfigSpec.DoubleValue
 private lateinit var inner:ModConfigSpec.DoubleValue
 private lateinit var outer:ModConfigSpec.DoubleValue
 private lateinit var radius:ModConfigSpec.DoubleValue
 private lateinit var start:ModConfigSpec.DoubleValue
 private lateinit var stepMin:ModConfigSpec.DoubleValue
 private lateinit var stepMax:ModConfigSpec.DoubleValue
 fun register(b:ModConfigSpec.Builder){
  b.comment("轨道加特林火力网；连续发射，轮次间无停顿。Continuous Orbital Gatling Barrage.").push("gatlingBarrage")
  rounds=b.defineInRange("rounds",5,1,20);shots=b.defineInRange("shotsPerRound",60,1,200)
  interval=b.defineInRange("shotIntervalSeconds",.045,.01,1.0)
  delay=b.defineInRange("firingDelaySeconds",2.0,.5,60.0);flight=b.defineInRange("flightSeconds",1.5,.5,10.0)
  damage=b.defineInRange("directDamage",30.0,0.0,100000.0);blast=b.defineInRange("highExplosiveDamage",60.0,0.0,100000.0)
  inner=b.defineInRange("innerRadius",1.5,0.0,128.0);outer=b.defineInRange("outerRadius",4.5,.1,256.0)
  radius=b.comment("原生 area_size；MC 中作为游走半径。Native area_size, adapted as walk radius in Minecraft.").defineInRange("wanderRadius",7.0,1.0,64.0)
  start=b.defineInRange("wanderStartRadius",1.0,0.0,64.0);stepMin=b.defineInRange("wanderStepMin",1.0,.1,16.0);stepMax=b.defineInRange("wanderStepMax",2.0,.1,16.0)
  b.pop()
 }
 fun callTicks()=(delay.get()*20).toInt().coerceAtLeast(10)
 fun snapshot()=GatlingSettings(rounds.get(),shots.get(),interval.get(),damage.get().toFloat(),blast.get().toFloat(),inner.get(),outer.get().toFloat(),radius.get(),start.get(),stepMin.get(),stepMax.get(),(flight.get()*20).toInt().coerceAtLeast(10))
}
