package com.example.sbwhelldiver.config

import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.ForgeConfigSpec

/** Immutable per-call settings, including ordnance already airborne during a config reload. */
data class AirburstSettings(val rounds:Int=4,val intervalTicks:Int=80,val flightTicks:Int=30,
 val height:Double=20.0,val cone:Double=35.0,val children:Int=12,val childSpeed:Double=15.0,
 val direct:Float=50f,val blast:Float=60f,val inner:Double=.2,val outer:Float=.5f,
 val childDirect:Float=50f,val childBlast:Float=60f,val childInner:Double=3.0,val childOuter:Float=6f) {
 fun shotTime(index:Int)=index*intervalTicks
 fun save()=CompoundTag().also{
  it.putInt("Rounds",rounds);it.putInt("Interval",intervalTicks);it.putInt("Flight",flightTicks)
  it.putDouble("Height",height);it.putDouble("Cone",cone);it.putInt("Children",children);it.putDouble("ChildSpeed",childSpeed)
  it.putFloat("Direct",direct);it.putFloat("Blast",blast);it.putDouble("Inner",inner);it.putFloat("Outer",outer)
  it.putFloat("ChildDirect",childDirect);it.putFloat("ChildBlast",childBlast);it.putDouble("ChildInner",childInner);it.putFloat("ChildOuter",childOuter)
 }
 companion object {
  fun load(t:CompoundTag)=AirburstSettings(t.getInt("Rounds").coerceIn(1,20),t.getInt("Interval").coerceIn(1,1200),t.getInt("Flight").coerceIn(10,200),
   t.getDouble("Height").coerceIn(2.0,128.0),t.getDouble("Cone").coerceIn(1.0,60.0),t.getInt("Children").coerceIn(1,48),t.getDouble("ChildSpeed").coerceIn(.5,32.0),
   t.getFloat("Direct").coerceIn(0f,100000f),t.getFloat("Blast").coerceIn(0f,100000f),t.getDouble("Inner").coerceIn(0.0,128.0),t.getFloat("Outer").coerceIn(.1f,256f),
   t.getFloat("ChildDirect").coerceIn(0f,100000f),t.getFloat("ChildBlast").coerceIn(0f,100000f),t.getDouble("ChildInner").coerceIn(0.0,128.0),t.getFloat("ChildOuter").coerceIn(.1f,256f))
 }
}
object OrbitalAirburstConfig {
 private lateinit var rounds:ForgeConfigSpec.IntValue
 private lateinit var interval:ForgeConfigSpec.DoubleValue
 private lateinit var delay:ForgeConfigSpec.DoubleValue
 private lateinit var flight:ForgeConfigSpec.DoubleValue
 private lateinit var height:ForgeConfigSpec.DoubleValue
 private lateinit var cone:ForgeConfigSpec.DoubleValue
 private lateinit var children:ForgeConfigSpec.IntValue
 private lateinit var speed:ForgeConfigSpec.DoubleValue
 private lateinit var direct:ForgeConfigSpec.DoubleValue
 private lateinit var blast:ForgeConfigSpec.DoubleValue
 private lateinit var inner:ForgeConfigSpec.DoubleValue
 private lateinit var outer:ForgeConfigSpec.DoubleValue
 private lateinit var childDirect:ForgeConfigSpec.DoubleValue
 private lateinit var childBlast:ForgeConfigSpec.DoubleValue
 private lateinit var childInner:ForgeConfigSpec.DoubleValue
 private lateinit var childOuter:ForgeConfigSpec.DoubleValue
 fun register(b:ForgeConfigSpec.Builder){
  b.comment("轨道空爆攻击。Orbital Airburst Strike.").push("airburstStrike")
  rounds=b.defineInRange("rounds",4,1,20);interval=b.defineInRange("salvoIntervalSeconds",4.0,.05,60.0)
  delay=b.defineInRange("firingDelaySeconds",2.0,.5,60.0);flight=b.defineInRange("flightSeconds",1.5,.5,10.0)
  height=b.comment("MC 空爆高度，以信标地面为基准；原版引信时序的适配值。Minecraft burst height above the beacon; an adaptation, not a decoded fixed native height.").defineInRange("burstHeight",20.0,2.0,128.0)
  cone=b.comment("原版空爆母弹分裂锥角，沿入射方向。Native airburst fragmentation cone, along the incoming direction.").defineInRange("spreadAngleDegrees",35.0,1.0,60.0)
  children=b.defineInRange("bombletsPerShell",12,1,48);speed=b.comment("子弹速度，格/秒；原版为300m/s。Bomblet speed in blocks/second; native 300 m/s.").defineInRange("bombletSpeed",300.0,10.0,640.0)
  direct=b.defineInRange("directDamage",50.0,0.0,100000.0);blast=b.defineInRange("explosionDamage",60.0,0.0,100000.0)
  inner=b.defineInRange("innerRadius",.2,0.0,128.0);outer=b.defineInRange("outerRadius",.5,.1,256.0)
  childDirect=b.defineInRange("bombletDirectDamage",50.0,0.0,100000.0);childBlast=b.defineInRange("bombletExplosionDamage",60.0,0.0,100000.0)
  childInner=b.defineInRange("bombletInnerRadius",3.0,0.0,128.0);childOuter=b.defineInRange("bombletOuterRadius",6.0,.1,256.0)
  b.pop()
 }
 fun callTicks()=(delay.get()*20).toInt().coerceAtLeast(10)
 fun snapshot()=AirburstSettings(rounds.get(),(interval.get()*20).toInt().coerceAtLeast(1),(flight.get()*20).toInt().coerceAtLeast(10),height.get(),cone.get(),children.get(),speed.get()/20,
  direct.get().toFloat(),blast.get().toFloat(),inner.get(),outer.get().toFloat(),childDirect.get().toFloat(),childBlast.get().toFloat(),childInner.get(),childOuter.get().toFloat())
}
