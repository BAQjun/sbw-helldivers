package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.config.BarrageSettings
import com.example.sbwhelldiver.stratagem.OrbitalBarragePattern
import net.minecraft.gametest.framework.*
import net.minecraftforge.gametest.*

@GameTestHolder("sbw_helldiver_uniform_barrage")
@PrefixGameTestTemplate(false)
object OrbitalUniformGameTests {
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_uniform_barrage",template="sentry_test",batch="uniform_cadence",timeoutTicks=20)
 fun cadenceAndSavedSettings(h:GameTestHelper)=OrbitalGameTests.barrageNativeCadenceDirectionScatterAndPersistence(h)
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_uniform_barrage",template="sentry_test",batch="uniform_custom",timeoutTicks=20)
 fun customRadiusRotationAndDegenerateCases(h:GameTestHelper)=OrbitalGameTests.barrageScatterRespectsCustomFootprintsAndRotation(h)
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_uniform_barrage",template="sentry_test",batch="uniform_coverage",timeoutTicks=20)
 fun centerMiddleAndRimCoverage(h:GameTestHelper)=OrbitalGameTests.barrageCoversMiddleAndOuterAreaWithoutRadialGaps(h)
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_uniform_barrage",template="sentry_test",batch="uniform_120",timeoutTicks=780)
 fun complete120AndReload(h:GameTestHelper)=OrbitalGameTests.full120Barrage(h)
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_uniform_barrage",template="sentry_test",batch="uniform_380",timeoutTicks=780)
 fun complete380AndReload(h:GameTestHelper)=OrbitalGameTests.full380Barrage(h)
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_uniform_barrage",template="sentry_test",batch="uniform_walking",timeoutTicks=780)
 fun completeWalkingAndReload(h:GameTestHelper)=OrbitalGameTests.fullWalkingBarrage(h)
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_uniform_barrage",template="sentry_test",batch="uniform_napalm",timeoutTicks=780)
 fun completeNapalmAndReload(h:GameTestHelper)=OrbitalGameTests.fullNapalmBarrage(h)
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_uniform_barrage",template="sentry_test",batch="uniform_samples",timeoutTicks=20)
 fun sampleActualPlannedTargets(h:GameTestHelper){
  val output=System.getProperty("sbw_helldiver.scatterSamples")
  if(output!=null){
   val rows=mutableListOf("kind,seed,index,round,x,z,cx,cz,radius")
   for(id in BarrageSettings.ids){
    val s=BarrageSettings.defaults(id)
    for(seed in 0L..31L)OrbitalBarragePattern.offsets(seed,s,0f).forEachIndexed{i,p->
     val center=OrbitalBarragePattern.center(i,s,0f)
     rows.add("$id,$seed,$i,${i/s.shots},${p.x},${p.z},${center.x},${center.z},${s.radius}")
    }
   }
   java.nio.file.Files.write(java.nio.file.Path.of(output),rows)
  }
  h.succeed()
 }
}
