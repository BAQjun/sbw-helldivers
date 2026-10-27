package com.example.sbwhelldiver.config

import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.ForgeConfigSpec

/** Settings are frozen when the beacon activates, including across save/load. */
data class RailcannonSettings(val callTicks:Int=20,val lockTicks:Int=34,val beamTicks:Int=40,
 val flightTicks:Int=10,val range:Double=30.0,val direct:Float=160f,val blast:Float=160f,
 val inner:Double=2.5,val outer:Float=2.5f) {
 fun save()=CompoundTag().also {
  it.putInt("Call",callTicks);it.putInt("Lock",lockTicks);it.putInt("Beam",beamTicks);it.putInt("Flight",flightTicks)
  it.putDouble("Range",range);it.putFloat("Direct",direct);it.putFloat("Blast",blast);it.putDouble("Inner",inner);it.putFloat("Outer",outer)
 }
 companion object {
  fun load(t:CompoundTag)=if(t.isEmpty)RailcannonSettings() else RailcannonSettings(
   t.getInt("Call").coerceIn(1,1200),t.getInt("Lock").coerceIn(1,1200),t.getInt("Beam").coerceIn(1,1200),t.getInt("Flight").coerceIn(10,200),
   t.getDouble("Range").coerceIn(1.0,128.0),t.getFloat("Direct").coerceIn(0f,100000f),t.getFloat("Blast").coerceIn(0f,100000f),t.getDouble("Inner").coerceIn(0.0,128.0),t.getFloat("Outer").coerceIn(.1f,256f))
 }
}
object OrbitalRailcannonConfig {
 private lateinit var call:ForgeConfigSpec.DoubleValue
 private lateinit var lock:ForgeConfigSpec.DoubleValue
 private lateinit var beam:ForgeConfigSpec.DoubleValue
 private lateinit var flight:ForgeConfigSpec.DoubleValue
 private lateinit var range:ForgeConfigSpec.DoubleValue
 private lateinit var direct:ForgeConfigSpec.DoubleValue
 private lateinit var blast:ForgeConfigSpec.DoubleValue
 private lateinit var inner:ForgeConfigSpec.DoubleValue
 private lateinit var outer:ForgeConfigSpec.DoubleValue
 fun register(b:ForgeConfigSpec.Builder) {
  b.comment("轨道炮攻击。Orbital Railcannon Strike.").push("railcannonStrike")
  call=b.comment("红光启动前的等待时间。Delay before the targeting beam starts.").defineInRange("callDelaySeconds",1.0,.05,60.0)
  lock=b.comment("红光启动至发射；原版1.7秒。Beam start to firing; native 1.7 seconds.").defineInRange("lockSeconds",1.7,.05,60.0)
  beam=b.comment("原版指示光束持续2秒。Native pointer duration: 2 seconds.").defineInRange("beamSeconds",2.0,.05,60.0)
  flight=b.comment("MC炮弹飞行时间适配值。Minecraft shell travel time adaptation.").defineInRange("flightSeconds",.5,.5,10.0)
  range=b.comment("原版搜索半径，按最大生命值优先。Native search radius; highest maximum health first.").defineInRange("searchRadius",30.0,1.0,128.0)
  direct=b.defineInRange("directDamage",160.0,0.0,100000.0);blast=b.defineInRange("explosionDamage",160.0,0.0,100000.0)
  inner=b.defineInRange("innerRadius",2.5,0.0,128.0);outer=b.defineInRange("outerRadius",2.5,.1,256.0)
  b.pop()
 }
 fun snapshot()=RailcannonSettings((call.get()*20).toInt(),(lock.get()*20).toInt(),(beam.get()*20).toInt(),(flight.get()*20).toInt(),range.get(),direct.get().toFloat(),blast.get().toFloat(),inner.get(),outer.get().toFloat())
}
