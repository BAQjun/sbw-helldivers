package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.entity.GroundFireEntity
import com.example.sbwhelldiver.init.ModBlocks
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.*

@GameTestHolder("sbw_helldiver_fire_decay")
@PrefixGameTestTemplate(false)
object GroundFireDecayGameTests {
 private fun origin(h:GameTestHelper):Vec3 {
  val p=h.absolutePos(BlockPos(15,30,15))
  for(cx in ((p.x-12) shr 4)..((p.x+12) shr 4))for(cz in ((p.z-12) shr 4)..((p.z+12) shr 4)){h.level.setChunkForced(cx,cz,true);h.level.getChunk(cx,cz)}
  for(x in -11..11)for(z in -11..11)h.level.setBlock(p.offset(x,-1,z),Blocks.BEDROCK.defaultBlockState(),3)
  return Vec3.atBottomCenterOf(p)
 }
 private fun fire(h:GameTestHelper,p:BlockPos)=h.level.getBlockState(p).`is`(ModBlocks.SENTRY_FIRE.get())
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_fire_decay",template="sentry_test",batch="fire_rings",timeoutTicks=150)
 fun realBlocksExtinguishOutsideInAndStopIgniting(h:GameTestHelper){
  val at=origin(h);val center=BlockPos.containing(at);val middle=center.offset(6,0,0);val edge=center.offset(9,0,0)
  val field=GroundFireEntity.create(h.level,at,radius=10.0,lifetimeTicks=120,innerRadius=2.0)
  h.assertTrue(listOf(center,middle,edge).all{fire(h,it)},"Initial fire footprint incomplete")
  h.runAfterDelay(45){
   h.assertTrue(!fire(h,edge) && fire(h,middle) && fire(h,center),"Outer ring did not expire before middle/center")
   val victim=EntityType.COW.create(h.level)!!;victim.setPos(Vec3.atBottomCenterOf(edge));field.igniteInside(victim)
   h.assertTrue(victim.activeEffects.isEmpty(),"Expired outer ring still ignites living entities");victim.discard()
  }
  h.runAfterDelay(105){h.assertTrue(!fire(h,middle) && fire(h,center),"Middle ring did not expire before center")}
  h.runAfterDelay(125){h.assertTrue(field.isRemoved && !fire(h,center),"Center exceeded the configured maximum");h.succeed()}
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_fire_decay",template="sentry_test",batch="fire_reload",timeoutTicks=150)
 fun savedSlopeFireKeepsAgeAndExtinguishedRings(h:GameTestHelper){
  val at=origin(h);val center=BlockPos.containing(at);val edge=center.offset(9,0,0);val raised=center.offset(1,0,0)
  h.level.setBlock(raised,Blocks.BEDROCK.defaultBlockState(),3)
  val field=GroundFireEntity.create(h.level,at,radius=10.0,heavyBurning=true,lifetimeTicks=120,innerRadius=3.3)
  h.assertTrue(fire(h,raised.above()),"Fire did not follow terrain")
  h.runAfterDelay(65){
   val tag=CompoundTag();field.saveWithoutId(tag);field.discard()
   val restored=ModEntities.GROUND_FIRE.get().create(h.level)!!;restored.load(tag);h.level.addFreshEntity(restored)
   h.assertTrue(restored.maximumLifetime==120 && restored.innerRadius==3.3 && !restored.coversCell(edge) && restored.coversCell(raised.above()),"Save lost radial age/terrain/profile")
   h.runAfterDelay(2){h.assertTrue(!fire(h,edge) && fire(h,raised.above()),"Reload reignited expired outer fire")}
   h.runAfterDelay(65){h.assertTrue(restored.isRemoved && !fire(h,raised.above()),"Reload extended the original deadline");h.succeed()}
  }
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_fire_decay",template="sentry_test",batch="fire_overlap",timeoutTicks=190)
 fun overlapsKeepTheirOwnDeadlinesAndFreshFuelRelights(h:GameTestHelper){
  val at=origin(h);val edge=BlockPos.containing(at).offset(9,0,0)
  val field=GroundFireEntity.create(h.level,at,radius=10.0,lifetimeTicks=120,innerRadius=2.0)
  h.runAfterDelay(30){GroundFireEntity.create(h.level,Vec3.atBottomCenterOf(edge),lifetimeTicks=120)}
  h.runAfterDelay(50){h.assertTrue(!field.coversCell(edge) && fire(h,edge),"Expired ring removed overlapping live fire")}
  h.runAfterDelay(60){
   val refreshed=GroundFireEntity.create(h.level,at,radius=10.0,lifetimeTicks=120,innerRadius=2.0)
   h.assertTrue(refreshed===field && field.coversCell(edge),"Fresh fuel did not reuse/relight the field")
  }
  h.runAfterDelay(155){h.assertTrue(!fire(h,edge),"Overlap or refreshed outer ring exceeded its own deadline")}
  h.runAfterDelay(185){h.assertTrue(field.isRemoved && !fire(h,BlockPos.containing(at)),"Refresh left immortal fire");h.succeed()}
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_fire_decay",template="sentry_test",batch="fire_eagle",timeoutTicks=20)
 fun eagleTerrainAndLifetime(h:GameTestHelper)=EagleFieldGameTests.napalmFollowsTerrainAndSavesHeavyFire(h)
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_fire_decay",template="sentry_test",batch="fire_status",timeoutTicks=20)
 fun unchangedStackedBurnDamage(h:GameTestHelper)=EagleFieldGameTests.napalmRefreshesThreeSecondNormalPlusOneSecondDoubleBurn(h)
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_fire_decay",template="sentry_test",batch="fire_orbital",timeoutTicks=20)
 fun orbitalImpactProfile(h:GameTestHelper)=OrbitalNapalmGameTests.napalmShellDamageAndHeavyTerrainFire(h)
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_fire_decay",template="sentry_test",batch="fire_custom",timeoutTicks=100)
 fun configurableLifetimeReload(h:GameTestHelper)=OrbitalNapalmGameTests.napalmCustomLifetimeSurvivesReloadAndExpires(h)
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_fire_decay",template="sentry_test",batch="fire_mine",timeoutTicks=20)
 fun incendiaryMineProfile(h:GameTestHelper)=ElementalMineGameTests.incendiaryBlastAndCustomBurn(h)
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_fire_decay",template="sentry_test",batch="fire_field_save",timeoutTicks=20)
 fun savedFireAndGasRetainSeparateLifetimes(h:GameTestHelper)=ElementalMineGameTests.savedFieldsKeepRadiusAndExpire(h)
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_fire_decay",template="sentry_test",batch="fire_gas_regression",timeoutTicks=30)
 fun gasLifetimeIsUnchanged(h:GameTestHelper){
  val p=h.absolutePos(BlockPos(15,260,15))
  for(cx in ((p.x-22) shr 4)..((p.x+22) shr 4))for(cz in ((p.z-22) shr 4)..((p.z+22) shr 4)){h.level.setChunkForced(cx,cz,true);h.level.getChunk(cx,cz)}
  h.runAfterDelay(5){ElementalMineGameTests.gasHasNoBlastAndReusesMortarEffects(h)}
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_fire_decay",template="sentry_test",batch="fire_refresh",timeoutTicks=450)
 fun singleCellRefreshAndExpiry(h:GameTestHelper){
  origin(h) // Give newly forced chunks time to become entity-accessible before immediate contact queries.
  h.runAfterDelay(5){ElementalMineGameTests.realFireBlocksRefreshAndExpireWithoutPanic(h)}
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_fire_decay",template="sentry_test",batch="fire_jet",timeoutTicks=40)
 fun jetRangeAndSingleCellLifetime(h:GameTestHelper){
  val p=h.absolutePos(BlockPos(15,30,15))
  for(cx in ((p.x-5) shr 4)..((p.x+6) shr 4))for(cz in ((p.z-2) shr 4)..((p.z+38) shr 4)){h.level.setChunkForced(cx,cz,true);h.level.getChunk(cx,cz)}
  h.runAfterDelay(5){FlameGameTests.streamRangeWallsAndGroundFireLifetime(h)}
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_fire_decay",template="sentry_test",batch="fire_fuel",timeoutTicks=50)
 fun destroyedSentryFuel(h:GameTestHelper)=HellpodImpactGameTests.crushDestroysLowerFuelPodAndWallColliders(h)
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_fire_decay",template="sentry_test",batch="fire_laser",timeoutTicks=350)
 fun laserSupportedTrail(h:GameTestHelper)=OrbitalLaserGameTests.sweepLeavesPersistentSupportedFireWithoutDuplicateTrackers(h)
}
