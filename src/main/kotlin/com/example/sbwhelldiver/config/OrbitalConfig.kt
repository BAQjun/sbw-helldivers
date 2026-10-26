package com.example.sbwhelldiver.config
import net.minecraftforge.common.ForgeConfigSpec
object OrbitalConfig {
 lateinit var range:ForgeConfigSpec.DoubleValue
 lateinit var altitude:ForgeConfigSpec.DoubleValue
 lateinit var offset:ForgeConfigSpec.DoubleValue
 lateinit var callSeconds:ForgeConfigSpec.DoubleValue
 lateinit var direct:ForgeConfigSpec.DoubleValue
 lateinit var blast:ForgeConfigSpec.DoubleValue
 lateinit var inner:ForgeConfigSpec.DoubleValue
 lateinit var outer:ForgeConfigSpec.DoubleValue
 fun register(b:ForgeConfigSpec.Builder){
  b.comment("超级驱逐舰与轨道火力。Super Destroyer and orbital support.").push("orbital")
  range=b.comment("以呼叫时地面位置为圆心的水平支援半径。Horizontal coverage around the call position.").defineInRange("supportRadius",1024.0,32.0,16384.0)
  altitude=b.comment("舰船相对呼叫者的高度。Height above the caller.").defineInRange("shipAltitude",900.0,256.0,8192.0)
  offset=b.comment("舰船向所选方位偏移的距离。Ship offset toward the chosen cardinal direction.").defineInRange("shipOffset",700.0,128.0,8192.0)
  b.push("precisionStrike")
  callSeconds=b.defineInRange("arrivalSeconds",2.0,.5,60.0)
  direct=b.defineInRange("directDamage",640.0,0.0,100000.0)
  blast=b.defineInRange("explosionDamage",240.0,0.0,100000.0)
  inner=b.defineInRange("innerRadius",4.0,0.0,128.0)
  outer=b.defineInRange("outerRadius",12.0,.1,256.0)
  b.pop(2)
 }
 fun callTicks()=(callSeconds.get()*20).toInt().coerceAtLeast(10)
}
