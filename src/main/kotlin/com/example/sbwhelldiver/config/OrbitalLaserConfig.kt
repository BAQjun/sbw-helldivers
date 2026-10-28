package com.example.sbwhelldiver.config

import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.common.ModConfigSpec
import kotlin.math.roundToInt

/** Native movement/timing, with the user-requested damage cadence. Frozen per call. */
data class OrbitalLaserSettings(val callTicks:Int=40,val durationTicks:Int=500,val range:Double=50.0,
 val speed:Double=10.0,val refreshTicks:Int=20,val radius:Double=1.0,val damage:Float=25f,val interval:Int=5) {
 fun save()=CompoundTag().also {
  it.putInt("Call",callTicks);it.putInt("Duration",durationTicks);it.putDouble("Range",range);it.putDouble("Speed",speed)
  it.putInt("Refresh",refreshTicks);it.putDouble("Radius",radius);it.putFloat("Damage",damage);it.putInt("Interval",interval)
 }
 companion object {
  fun load(t:CompoundTag)=if(t.isEmpty)OrbitalLaserSettings() else OrbitalLaserSettings(
   t.getInt("Call").coerceIn(1,1200),t.getInt("Duration").coerceIn(1,12000),t.getDouble("Range").coerceIn(1.0,128.0),
   t.getDouble("Speed").coerceIn(.1,100.0),t.getInt("Refresh").coerceIn(1,200),t.getDouble("Radius").coerceIn(.1,8.0),
   t.getFloat("Damage").coerceIn(0f,100000f),t.getInt("Interval").coerceIn(1,200))
 }
}
object OrbitalLaserConfig {
 private lateinit var call:ModConfigSpec.DoubleValue;private lateinit var duration:ModConfigSpec.DoubleValue
 private lateinit var range:ModConfigSpec.DoubleValue;private lateinit var speed:ModConfigSpec.DoubleValue
 private lateinit var refresh:ModConfigSpec.IntValue;private lateinit var radius:ModConfigSpec.DoubleValue
 private lateinit var damage:ModConfigSpec.DoubleValue;private lateinit var interval:ModConfigSpec.IntValue
 fun register(b:ModConfigSpec.Builder){
  b.comment("轨道激光。Orbital Laser.").push("laser")
  call=b.comment("原版抵达等待2秒。Native call-in delay: 2 seconds.").defineInRange("callDelaySeconds",2.0,.05,60.0)
  duration=b.comment("解包组件持续25秒。Decoded component duration: 25 seconds.").defineInRange("durationSeconds",25.0,.05,600.0)
  range=b.comment("以信标为中心的搜索半径。Search radius around the beacon.").defineInRange("searchRadius",50.0,1.0,128.0)
  speed=b.comment("光斑移动速度，格/秒。Impact movement speed, blocks per second.").defineInRange("movementSpeed",10.0,.1,100.0)
  refresh=b.comment("目标列表刷新间隔，刻。Target-list refresh interval in ticks.").defineInRange("targetRefreshTicks",20,1,200)
  radius=b.comment("光斑球形伤害半径。Impact damage sphere radius.").defineInRange("damageRadius",1.0,.1,8.0)
  damage=b.defineInRange("damagePerPulse",25.0,0.0,100000.0);interval=b.defineInRange("damageIntervalTicks",5,1,200)
  b.pop()
 }
 fun snapshot()=OrbitalLaserSettings((call.get()*20).roundToInt(),(duration.get()*20).roundToInt(),range.get(),speed.get(),refresh.get(),radius.get(),damage.get().toFloat(),interval.get())
}
