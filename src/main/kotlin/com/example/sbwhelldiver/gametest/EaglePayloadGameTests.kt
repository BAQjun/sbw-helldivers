package com.example.sbwhelldiver.gametest
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.config.StratagemCooldownConfig
import com.example.sbwhelldiver.config.EagleConfig
import com.example.sbwhelldiver.stratagem.*
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import net.minecraft.core.BlockPos
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs
@GameTestHolder("sbw_helldiver_eagle")
@PrefixGameTestTemplate(false)
object EaglePayloadGameTests {
 private class Recorder{
  val shots=mutableListOf<Entity>()
  @net.minecraftforge.eventbus.api.SubscribeEvent fun joined(e:net.minecraftforge.event.entity.EntityJoinLevelEvent){
   if(e.entity is EagleClusterBombEntity||e.entity is EagleClusterBombletEntity||e.entity is Eagle110mmRocketEntity||e.entity is Eagle500kgBombEntity)shots.add(e.entity)
  }
 }
 private fun anchor(h:GameTestHelper)=Vec3.atCenterOf(h.absolutePos(BlockPos(0,30,0)))
 private fun flight(h:GameTestHelper,mode:EagleAttack):EagleEntity{
  val e=ModEntities.EAGLE.get().create(h.level)!!;e.configure(anchor(h),Vec3(0.0,0.0,1.0),mode,null);h.level.addFreshEntity(e);return e
 }
 private fun jump(h:GameTestHelper,e:EagleEntity,t:Int){val n=CompoundTag();e.saveWithoutId(n);n.putLong("StartedAt",h.level.gameTime-t);e.load(n);e.tick()}
 private fun collision(e:Entity,at:Vec3)=com.atsuishio.superbwarfare.world.phys.ExtendedEntityRayTraceResult(com.atsuishio.superbwarfare.world.phys.EntityResult(e,at,false,false))
 private fun cow(h:GameTestHelper,at:Vec3)=EntityType.COW.create(h.level)!!.also{
  it.setPos(at);it.yRot=0f;it.isNoGravity=true;it.noPhysics=true;it.isNoAi=true;it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=1000.0;it.health=1000f;h.level.addFreshEntity(it)
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=10,batch="eagle_new_registry")
 fun newCardsCodesUsesAndSharedRearm(h:GameTestHelper){
  val codes=mapOf(EagleAttack.CLUSTER to listOf(0,1,2,2,1),EagleAttack.ROCKETS to listOf(0,1,0,3),EagleAttack.HEAVY_BOMB to listOf(0,1,2,2,2))
  for((mode,code) in codes){
   h.assertTrue(Stratagems.get(mode.id)!!.let{it.code==code&&it.category==StratagemCategory.RED},"New card code/category: $mode")
   h.assertTrue(StratagemCooldownConfig.maxUses(mode.id)==mode.defaultUses&&StratagemCooldownConfig.ticks(mode.id)==143,"New Eagle defaults: $mode")
  }
  val state=StratagemCooldownState();state.equip(codes.keys.map{it.id})
  h.assertTrue(state.rearm(100)&&codes.keys.all{state.entry(it.id).remaining==it.defaultUses}&&state.rearming(101),"Shared rearm did not include all three new payloads")
  h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=10,batch="eagle_new_ballistics")
 fun newPassesKeepSpeedDirectionAndFullReleaseWindow(h:GameTestHelper){
  for(mode in listOf(EagleAttack.CLUSTER,EagleAttack.ROCKETS,EagleAttack.HEAVY_BOMB)){
   var p=EagleFlight.position(Vec3.ZERO,Vec3(0.0,0.0,1.0),0.0,mode)
   for(i in 1..EagleFlight.end(mode)*10){val age=i*.1;val q=EagleFlight.position(Vec3.ZERO,Vec3(0.0,0.0,1.0),age,mode);h.assertTrue(q.distanceTo(p)/.005 in 298.0..301.0,"Speed discontinuity: $mode at $age");p=q}
   h.assertTrue(mode.releaseAge(mode.count-1)<EagleFlight.attackEnd(mode),"Early pullup truncates payload: $mode")
  }
  h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=20,batch="eagle_new_cluster")
 fun eightMothersSplitInto64OnceAndTransferBeaconLifetime(h:GameTestHelper){
  val record=Recorder();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(record)
  val e=flight(h,EagleAttack.CLUSTER)
  try{
   jump(h,e,EagleAttack.CLUSTER.callTicks+15)
   val mothers=record.shots.filterIsInstance<EagleClusterBombEntity>()
   h.assertTrue(mothers.size==8&&e.fired==8,"Did not release eight mothers")
   mothers.forEach{m->repeat(6){m.tick()}}
   h.assertTrue(record.shots.none{it is EagleClusterBombletEntity},"Cluster separated before 0.35s")
   // Saving six ticks into the fuse must not restart the delay.
   mothers.forEach{m->val n=CompoundTag();m.saveWithoutId(n);m.load(n);m.tick()}
   val children=record.shots.filterIsInstance<EagleClusterBombletEntity>()
   h.assertTrue(children.size==64&&mothers.all{it.isRemoved},"Expected 8 x 8 bomblets, got ${children.size}")
   e.tick();h.assertTrue(!e.strikeComplete,"Mother removal ended strike while bomblets were airborne")
   children.dropLast(1).forEach{it.discard()};e.tick();h.assertTrue(!e.strikeComplete,"Lost final child")
   val saved=CompoundTag();e.saveWithoutId(saved);e.load(saved);e.tick();h.assertTrue(!e.strikeComplete,"Reload forgot final child")
   children.last().discard();e.tick();h.assertTrue(e.strikeComplete,"Strike did not complete after final child")
   h.assertTrue(record.shots.size==72,"Reload duplicated children")
  }finally{record.shots.forEach{it.discard()};e.discard();net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(record)}
  h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=10,batch="eagle_new_cluster_config")
 fun clusterSplitDelayIsConfigurableAndSnapshotted(h:GameTestHelper){
  val old=EagleConfig.clusterSplitSeconds.get();val record=Recorder();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(record)
  try{
   EagleConfig.clusterSplitSeconds.set(.5)
   val m=ModEntities.EAGLE_CLUSTER_BOMB.get().create(h.level)!!;m.setPos(anchor(h).add(0.0,80.0,0.0));m.configureSplit(java.util.UUID.randomUUID(),anchor(h),Vec3(0.0,0.0,1.0));h.level.addFreshEntity(m)
   EagleConfig.clusterSplitSeconds.set(.35)
   repeat(9){m.tick()};h.assertTrue(record.shots.none{it is EagleClusterBombletEntity},"Live config changed already-flying fuse")
   m.tick();h.assertTrue(record.shots.count{it is EagleClusterBombletEntity}==8,"Configured 0.5s fuse ignored")
  }finally{EagleConfig.clusterSplitSeconds.set(old);record.shots.forEach{it.discard()};net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(record)}
  h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=10,batch="eagle_new_rockets")
 fun rocketsChooseMaximumHealthAndFireThreePairs(h:GameTestHelper){
  val e=flight(h,EagleAttack.ROCKETS);val at=e.target
  val a=EntityType.ZOMBIE.create(h.level)!!;a.setPos(at.add(2.0,0.0,0.0));a.isNoAi=true;h.level.addFreshEntity(a)
  val b=EntityType.ZOMBIE.create(h.level)!!;b.setPos(at.add(12.0,0.0,0.0));b.isNoAi=true;b.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=500.0;b.health=1f;h.level.addFreshEntity(b)
  val out=EntityType.ZOMBIE.create(h.level)!!;out.setPos(at.add(21.0,0.0,0.0));out.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=1000.0;out.isNoAi=true;h.level.addFreshEntity(out)
  val record=Recorder();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(record)
  try{
   h.assertTrue(e.rocketTarget()===b,"Rocket chose current health/closest target rather than highest maximum health in20m")
   jump(h,e,82);h.assertTrue(record.shots.size==2,"First pair")
   jump(h,e,91);h.assertTrue(record.shots.size==2,"Fired second pair before0.5s")
   b.discard();h.assertTrue(e.rocketTarget()===a,"Did not reacquire after target died")
   jump(h,e,92);h.assertTrue(record.shots.size==4,"Second pair")
   jump(h,e,102);h.assertTrue(record.shots.size==6&&e.fired==6,"Third pair")
   jump(h,e,122);h.assertTrue(record.shots.size==6,"Duplicated rockets after save/load")
  }finally{record.shots.forEach{it.discard()};e.discard();a.discard();b.discard();out.discard();net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(record)}
  h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=10,batch="eagle_new_damage")
 fun newActualImpactsUseSeparateDirectAndBlastDamage(h:GameTestHelper){
  val kinds=listOf(ModEntities.EAGLE_CLUSTER_BOMB.get(),ModEntities.EAGLE_CLUSTER_BOMBLET.get(),ModEntities.EAGLE_110MM_ROCKET.get())
  val damage=listOf(300f,63f,150f)
  for((i,type) in kinds.withIndex()){
   val at=anchor(h).add(i*20.0,0.0,0.0);val target=cow(h,at)
   val shot=type.create(h.level)!!;shot.setPos(at.add(-3.0,.5,0.0));shot.shoot(1.0,0.0,0.0,6f,0f);h.level.addFreshEntity(shot);shot.tickCount=1
   shot.onHitEntity(collision(target,at.add(0.0,.5,0.0)))
   h.assertTrue(abs((1000-target.health)-damage[i])<.01,"Actual direct+blast mismatch: index=$i, damage=${1000-target.health}")
   shot.discard();target.discard()
  };h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=30,batch="eagle_new_heavy")
 fun heavyBombEmbedsInSurvivorFollowsAndDetonatesAfterSavedFuse(h:GameTestHelper){
  val at=anchor(h);val target=cow(h,at);val bomb=ModEntities.EAGLE_500KG_BOMB.get().create(h.level)!!
  bomb.setPos(at.add(-3.0,.5,0.0));bomb.shoot(1.0,0.0,0.0,6f,0f);h.level.addFreshEntity(bomb);bomb.tickCount=1
  bomb.onHitEntity(collision(target,at.add(0.0,.5,0.0)))
  h.assertTrue(target.health==680f&&bomb.embedded&&!bomb.isRemoved,"500kg must apply only320 direct then embed")
  target.setPos(at.add(4.0,0.0,2.0));bomb.tick();h.assertTrue(bomb.position().distanceTo(target.position().add(0.0,.5,0.0))<1e-6,"Embedded bomb did not follow survivor")
  target.yRot=90f;bomb.tick()
  h.assertTrue(bomb.embeddedDirection.distanceTo(Vec3(0.0,0.0,1.0))<1e-5,"Embedded facing failed to rotate with host")
  val tag=CompoundTag();bomb.saveWithoutId(tag);bomb.load(tag)
  h.assertTrue(bomb.embeddedDirection.distanceTo(Vec3(0.0,0.0,1.0))<1e-5,"Saved embedded facing was lost")
  // Repeated collision notifications cannot deal another320 while embedded.
  bomb.onHitEntity(collision(target,target.position().add(0.0,.5,0.0)));h.assertTrue(target.health==680f,"Embedded bomb repeated direct damage")
  h.runAfterDelay(10){h.assertTrue(!bomb.isRemoved&&target.health==680f,"500kg fuse exploded early after restore: removed=${bomb.isRemoved}, health=${target.health}")}
  h.runAfterDelay(17){bomb.tick();h.assertTrue(bomb.isRemoved&&abs(target.health-440f)<.01,"Expected exactly240 delayed blast: health=${target.health}");target.discard();h.succeed()}
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=30,batch="eagle_new_ground_heavy")
 fun heavyBombSticksInGroundAndBeaconWaitsForDetonation(h:GameTestHelper){
  val e=flight(h,EagleAttack.HEAVY_BOMB);val record=Recorder();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(record)
  try{jump(h,e,82)}finally{net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(record)}
  val b=record.shots.single() as Eagle500kgBombEntity;val at=anchor(h)
  b.afterHitBlock(net.minecraft.world.phys.BlockHitResult(at,net.minecraft.core.Direction.UP,BlockPos.containing(at),false))
  val tag=CompoundTag();b.saveWithoutId(tag);b.load(tag);e.tick()
  h.assertTrue(b.embedded&&b.position().distanceTo(at)<1e-6&&!e.strikeComplete,"Ground embed/fuse tracking failed")
  h.runAfterDelay(17){b.tick();e.tick();h.assertTrue(e.strikeComplete,"Heavy strike ended before or failed to observe explosion");e.discard();b.discard();h.succeed()}
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=10,batch="eagle_heavy_lethal")
 fun lethalDirectHitLeavesBombAtImpactInsteadOfFollowingCorpse(h:GameTestHelper){
  val at=anchor(h);val target=cow(h,at);target.health=100f
  val b=ModEntities.EAGLE_500KG_BOMB.get().create(h.level)!!;b.setPos(at.add(-3.0,.5,0.0));b.shoot(1.0,0.0,0.0,6f,0f);b.tickCount=1
  b.onHitEntity(collision(target,at.add(0.0,.5,0.0)));val impact=b.position()
  h.assertTrue(!target.isAlive&&b.embedded&&!b.isRemoved,"Lethal direct hit did not leave an armed bomb")
  target.setPos(at.add(10.0,0.0,0.0));b.tick();val n=CompoundTag();b.saveWithoutId(n)
  h.assertTrue(b.position()==impact&&!n.hasUUID("AttachedTo"),"Bomb attached to a dead creature")
  b.discard();target.discard();h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=10,batch="eagle_heavy_native_impact")
 fun heavyBombStopsAtFirstRealBlockHitWithoutAnExtraMovementStep(h:GameTestHelper){
  val at=BlockPos.containing(anchor(h));h.level.setBlockAndUpdate(at,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState())
  for(dy in 1..5)h.level.setBlockAndUpdate(at.above(dy),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState())
  val b=ModEntities.EAGLE_500KG_BOMB.get().create(h.level)!!;b.setPos(Vec3.atCenterOf(at).add(0.0,2.0,0.0));b.shoot(0.0,-1.0,0.0,4f,0f);b.tickCount=1;h.level.addFreshEntity(b);b.tick()
  h.assertTrue(b.embedded&&!b.isRemoved&&abs(b.y-(at.y+1.0))<.01&&b.deltaMovement==Vec3.ZERO,"Real block collision did not stop on the surface: ${b.position()}")
  h.assertTrue(b.embeddedDirection.distanceTo(Vec3(0.0,-1.0,0.0))<1e-5,"Embedded direction changed on impact")
  b.discard();h.succeed()
 }
}
