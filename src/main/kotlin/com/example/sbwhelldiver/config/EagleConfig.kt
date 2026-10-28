package com.example.sbwhelldiver.config
import net.neoforged.neoforge.common.ModConfigSpec
import kotlin.math.roundToInt
object EagleConfig {
 lateinit var clusterSplitSeconds:ModConfigSpec.DoubleValue
 lateinit var heavyBombFuseSeconds:ModConfigSpec.DoubleValue
 fun register(b:ModConfigSpec.Builder){
  b.push("eagle")
  clusterSplitSeconds=b.comment("集束母弹分裂延时（秒）；暂定0.35，可在试射后调整。Cluster separation delay, seconds.")
   .defineInRange("clusterSplitSeconds",.35,.05,5.0)
  heavyBombFuseSeconds=b.comment("500kg落地后引信延迟（秒）。500kg impact fuse delay, seconds.")
   .defineInRange("heavyBombFuseSeconds",.8,0.0,10.0)
  b.pop()
 }
 fun clusterSplitTicks()=(clusterSplitSeconds.get()*20).roundToInt().coerceAtLeast(1)
 fun heavyBombFuseTicks()=(heavyBombFuseSeconds.get()*20).roundToInt().coerceAtLeast(0)
}
