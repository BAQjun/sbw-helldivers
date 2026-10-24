package com.example.sbwhelldiver.gametest
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.stratagem.Stratagems
import com.example.sbwhelldiver.stratagem.StratagemCategory
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs
@GameTestHolder("sbw_helldiver_eagle")
@PrefixGameTestTemplate(false)
object EagleGameTests {
 private class SpawnRecorder {
  val shots=mutableListOf<net.minecraft.world.entity.Entity>()
  val aircraft=mutableListOf<EagleEntity>()
  @net.minecraftforge.eventbus.api.SubscribeEvent fun joined(e:net.minecraftforge.event.entity.EntityJoinLevelEvent){if(e.entity is EagleEntity)aircraft.add(e.entity as EagleEntity);if(e.entity is EagleBombEntity||e.entity is EagleCannonRoundEntity)shots.add(e.entity)}
 }

 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=40)
 fun pathsAreExactlyPerpendicularOrAlongTheOriginalThrow(h:GameTestHelper){
  for(degrees in listOf(0.0,23.0,45.0,90.0,137.0,180.0,270.0)){
   val a=Math.toRadians(degrees);val o=Vec3(317.2,60.0,-142.7);val v=Vec3(kotlin.math.cos(a),0.0,kotlin.math.sin(a));val p=o.add(v.scale(35.4))
   val s=EagleFlight.heading(o,p,0f,false);val b=EagleFlight.heading(o,p,0f,true)
   h.assertTrue(s.dot(v)>1-1e-10&&abs(b.dot(v))<1e-10,"Direction drift at $degrees")
  };h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=40)
 fun zeroLengthThrowUsesLaunchYawAndIsFinite(h:GameTestHelper){
  val d=EagleFlight.heading(Vec3.ZERO,Vec3.ZERO,42f,false);h.assertTrue(abs(d.length()-1)<1e-9&&d.x.isFinite(),"Foot throw invalid");h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=40)
 fun bombBallisticsCenterSixSeparateImpactsOnBeacon(h:GameTestHelper){
  val target=Vec3(43.0,70.0,31.0);val f=Vec3(.6,0.0,.8);var sum=Vec3.ZERO
  for(i in 0 until EagleFlight.BOMB_COUNT){val start=EagleFlight.position(target,f,(EagleFlight.attackStart(true)+i*4).toDouble(),true).add(0.0,-1.2,0.0);val aim=target.add(f.scale(EagleFlight.bombOffset(i)));val velocity=EagleFlight.bombVelocity(start,aim);val t=EagleFlight.BOMB_FLIGHT;val hit=start.add(velocity.scale(t.toDouble())).add(0.0,-EagleFlight.BOMB_GRAVITY*t*(t-1)/2,0.0);h.assertTrue(hit.distanceTo(aim)<1e-8,"Bomb $i misses intended line");sum=sum.add(aim)}
  h.assertTrue(sum.scale(1.0/6).distanceTo(target)<1e-8,"Bomb line is off centre");h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=40)
 fun egressClimbsContinuouslyAndDoesNotTurnBack(h:GameTestHelper){
  for(b in listOf(true,false)){var p=EagleFlight.position(Vec3.ZERO,Vec3(0.0,0.0,1.0),EagleFlight.attackEnd(b).toDouble(),b);for(t in EagleFlight.attackEnd(b)+1..EagleFlight.end(b)){val q=EagleFlight.position(Vec3.ZERO,Vec3(0.0,0.0,1.0),t.toDouble(),b);h.assertTrue(q.y>=14&&q.z>p.z,"Unsafe pullout or turnback");if(t>EagleFlight.attackEnd(b)+15)h.assertTrue(q.y>=p.y,"Failed to climb after recovery");p=q}}

  h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=40)
 fun redCardsCarryTheNativeCodes(h:GameTestHelper){
  h.assertTrue(Stratagems.get("eagle_airstrike")!!.let {it.category==StratagemCategory.RED&&it.code==listOf(0,1,2,1)},"Airstrike code/category")
  h.assertTrue(Stratagems.get("eagle_strafing_run")!!.let {it.category==StratagemCategory.RED&&it.code==listOf(0,1,1)},"Strafe code/category");h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=40)
 fun airstrikeUsesUserSpecifiedDamageAndDualRadii(h:GameTestHelper){
  val b=ModEntities.EAGLE_BOMB.get().create(h.level)!!;h.assertTrue(b.getDamage()==240f&&b.getExplosionDamage()==128f&&b.getExplosionRadius()==10f,"Airstrike bomb damage")
  h.assertTrue(SentryExplosion.damageAt(5.0,128f,5.0,10.0)==128f&&SentryExplosion.damageAt(10.01,128f,5.0,10.0)==0f,"Full-damage inner sphere or outside radius");b.discard();h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",batch="eagle_damage",timeoutTicks=40)
 fun actualBombImpactApplies240Plus128(h:GameTestHelper){
  val at=Vec3.atCenterOf(h.absolutePos(net.minecraft.core.BlockPos(0,12,0)));val cow=net.minecraft.world.entity.EntityType.COW.create(h.level)!!;cow.setPos(at);cow.isNoGravity=true;cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)!!.baseValue=1000.0;cow.health=1000f;cow.xOld=cow.x;cow.yOld=cow.y;cow.zOld=cow.z;h.level.addFreshEntity(cow)
  val shot=ModEntities.EAGLE_BOMB.get().create(h.level)!!;shot.setPos(at.add(-3.0,.5,0.0));shot.shoot(1.0,0.0,0.0,6f,0f);h.level.addFreshEntity(shot);shot.tickCount=1;shot.onHitEntity(com.atsuishio.superbwarfare.world.phys.ExtendedEntityRayTraceResult(com.atsuishio.superbwarfare.world.phys.EntityResult(cow,at.add(0.0,.5,0.0),false,false)));h.assertTrue(abs(cow.health-632f)<.01,"Bomb actual direct + blast was ${1000-cow.health}, expected 368");shot.discard();cow.discard();h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",batch="eagle_strafe_damage",timeoutTicks=40)
 fun solidShot55AndHeShot110UseActualCollision(h:GameTestHelper){
  for(he in listOf(false,true)){
   val at=Vec3.atCenterOf(h.absolutePos(net.minecraft.core.BlockPos(if(he)15 else 0,12,0)));val cow=net.minecraft.world.entity.EntityType.COW.create(h.level)!!;cow.setPos(at);cow.isNoGravity=true;cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)!!.baseValue=1000.0;cow.health=1000f;cow.xOld=cow.x;cow.yOld=cow.y;cow.zOld=cow.z;h.level.addFreshEntity(cow)
   val shot=ModEntities.EAGLE_CANNON_ROUND.get().create(h.level)!!;if(!he)shot.setExplosionDamage(0f);shot.setPos(at.add(-3.0,.5,0.0));shot.shoot(1.0,0.0,0.0,6f,0f);h.level.addFreshEntity(shot);shot.tickCount=1;shot.onHitEntity(com.atsuishio.superbwarfare.world.phys.ExtendedEntityRayTraceResult(com.atsuishio.superbwarfare.world.phys.EntityResult(cow,at.add(0.0,.5,0.0),false,false)));val expected=if(he)890f else 945f;h.assertTrue(abs(cow.health-expected)<.01,"Actual strafe collision damage ${1000-cow.health}, HE=$he");shot.discard();cow.discard()
  };h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=40)
 fun strafeBoresightAndImpactLineAgreeAtEveryShot(h:GameTestHelper){
  for(height in listOf(EagleFlight.STRAFE_HEIGHT,EagleFlight.STRAFE_HEIGHT+85,EagleFlight.STRAFE_HEIGHT+160))for(i in 0..99){val t=EagleFlight.attackStart(false)+i*30.0/100;val at=EagleFlight.position(Vec3.ZERO,Vec3(0.0,0.0,1.0),t,false,height);val pitch=Math.toRadians(EagleFlight.pitch(t,false,height).toDouble());val hit=at.z+at.y/kotlin.math.tan(pitch);h.assertTrue(abs(hit-EagleFlight.strafeOffset(i,height))<1e-6,"Visual nose and gameplay aim disagree")}
  h.assertTrue(abs(EagleFlight.strafeOffset(0)+30)<1e-4&&EagleFlight.strafeOffset(99) in 28.0..30.0,"Sweep does not start on beacon or finish 60 m ahead");h.succeed()
 }

 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=20)
 fun highApproachAttackAndPulloutKeepNativeSpeed(h:GameTestHelper){
  for(b in listOf(true,false))for(height in listOf(if(b)EagleFlight.HEIGHT else EagleFlight.STRAFE_HEIGHT,if(b)EagleFlight.HEIGHT+160 else EagleFlight.STRAFE_HEIGHT+160)){
   var p=EagleFlight.position(Vec3.ZERO,Vec3(0.0,0.0,1.0),0.0,b,height)
   for(i in 1..EagleFlight.end(b)*10){val age=i*.1;val q=EagleFlight.position(Vec3.ZERO,Vec3(0.0,0.0,1.0),age,b,height);val speed=q.distanceTo(p)/.005;h.assertTrue(speed in 298.0..301.0,"Speed discontinuity $speed at $age, airstrike=$b");p=q}
  };h.succeed()
 }

 @JvmStatic @GameTest(template="eagle_test",batch="eagle_remote_flight",timeoutTicks=240)
 fun distantAircraftStaysTickingUntilItsOneCompleteAttack(h:GameTestHelper){
  val record=SpawnRecorder();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(record)
  val e=eagle(h,true)
  // This fixture fires outside the small structure. Supply real impact surfaces rather than
  // relying on terrain/chunk generation completing before the last projectile reaches it.
  for(i in 0 until EagleFlight.BOMB_COUNT){
   val at=net.minecraft.core.BlockPos.containing(e.target.add(e.runDirection.scale(EagleFlight.bombOffset(i)))).below()
   for(dx in -1..1)for(dz in -1..1)h.level.setBlockAndUpdate(at.offset(dx,0,dz),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState())
  }
  h.runAfterDelay((EagleFlight.end(true)+12).toLong()){
   h.assertTrue(e.fired==6&&e.isRemoved&&e.removalReason==net.minecraft.world.entity.Entity.RemovalReason.DISCARDED,"Remote aircraft stopped: fired=${e.fired}, age=${e.age}, reason=${e.removalReason}")
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(record);record.shots.forEach{it.discard()};e.discard();h.succeed()
  }
 }

 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=20)
 fun arrivalCountdownIsTwoPointFourOrFourPointOneSeconds(h:GameTestHelper){
  for(b in listOf(true,false)){
   val record=SpawnRecorder();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(record)
   val m=ModEntities.STRATAGEM_MARKER.get().create(h.level)!!;m.setPos(Vec3.atCenterOf(h.absolutePos(net.minecraft.core.BlockPos(0,12,0))))
   m.configure(ResourceLocation("sbw_helldiver",if(b)"eagle_airstrike" else "eagle_strafing_run"),80,null,beamColor=0xFF3434)
   m.tick();val e=record.aircraft.single();h.assertTrue(m.duration==if(b)82 else 48,"Wrong call-in countdown")
   h.assertTrue(e.startedAt==m.callStartedAt,"First server tick shifted the flight clock")
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(record);e.discard();m.discard()
  };h.succeed()
 }

 @JvmStatic @GameTest(template="eagle_test",batch="eagle_beacon_lifetime",timeoutTicks=40)
 fun arrivedBeaconWaitsForTheLastActualProjectileForBothAttacks(h:GameTestHelper){
  for(b in listOf(true,false)){
   val record=SpawnRecorder();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(record)
   val m=ModEntities.STRATAGEM_MARKER.get().create(h.level)!!;m.setPos(Vec3.atCenterOf(h.absolutePos(net.minecraft.core.BlockPos(0,12,0))))
   m.configure(ResourceLocation("sbw_helldiver",if(b)"eagle_airstrike" else "eagle_strafing_run"),80,null,beamColor=0xFF3434);h.level.addFreshEntity(m);m.tick()
   val e=record.aircraft.single();val now=h.level.gameTime;val start=now-EagleFlight.attackEnd(b)
   val mt=CompoundTag();m.saveWithoutId(mt);mt.putLong("StartedAt",start);m.load(mt)
   val et=CompoundTag();e.saveWithoutId(et);et.putLong("StartedAt",start);e.load(et);e.tick();m.tick()
   h.assertTrue(m.hasArrived&&m.ticksLeft==0&&!m.isRemoved,"Beacon disappeared at countdown zero")
   val total=if(b)6 else 100;h.assertTrue(record.shots.size==total,"Incomplete fixture salvo")
   record.shots.dropLast(1).forEach{it.discard()};e.tick();m.tick()
   h.assertTrue(!e.strikeComplete&&!m.isRemoved,"Beacon disappeared with the final projectile still flying")
   record.shots.last().discard();e.tick();m.tick()
   h.assertTrue(e.strikeComplete&&m.isRemoved,"Beacon did not finish after the final impact")
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(record);e.discard();m.discard()
  };h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",batch="eagle_payload_restore",timeoutTicks=40)
 fun savedArrivedBeaconAndPendingPayloadDoNotFinishEarly(h:GameTestHelper){
  val record=SpawnRecorder();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(record)
  val e=eagle(h,true);val et=CompoundTag();e.saveWithoutId(et);et.putLong("StartedAt",h.level.gameTime-EagleFlight.attackEnd(true));e.load(et);e.tick()
  val last=record.shots.last() as EagleBombEntity;record.shots.dropLast(1).forEach{it.discard()};last.setPos(Vec3.atCenterOf(h.absolutePos(net.minecraft.core.BlockPos(1,12,1))));last.deltaMovement=Vec3.ZERO;last.setCustomGravity(0f);e.tick()
  val saved=CompoundTag();e.saveWithoutId(saved);h.assertTrue(saved.getList("ActiveOrdnance",10).size==1,"Outstanding projectile was not saved")
  val restored=ModEntities.EAGLE.get().create(h.level)!!;restored.load(saved);h.assertTrue(!restored.strikeComplete,"Missing chunk lookup was mistaken for a completed impact")
  e.discard()
  h.runAfterDelay(5){
   restored.tick();h.assertTrue(!restored.strikeComplete,"Restored flight forgot its live projectile")
   last.discard();restored.tick();h.assertTrue(restored.strikeComplete,"Restored flight failed to observe final impact")
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(record);restored.discard();record.shots.forEach{it.discard()};h.succeed()
  }
 }

 private fun eagle(h:GameTestHelper,bombing:Boolean):EagleEntity {
  val at=Vec3.atCenterOf(h.absolutePos(net.minecraft.core.BlockPos(0,100,0)));val e=ModEntities.EAGLE.get().create(h.level)!!;e.configure(at,Vec3(1.0,0.0,0.0),bombing,null);h.level.addFreshEntity(e);return e
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=40)
 fun allBombsAreActualSpawnedProjectilesAndNotRepeatedOnRestore(h:GameTestHelper){
  val record=SpawnRecorder();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(record);val e=eagle(h,true);val n=CompoundTag();e.saveWithoutId(n);n.putLong("StartedAt",h.level.gameTime-EagleFlight.attackStart(true)-21);e.load(n);e.tick()
  h.assertTrue(e.fired==6,"Incomplete bomb salvo");val area=e.boundingBox.inflate(130.0);val bombs=record.shots.filterIsInstance<EagleBombEntity>();h.assertTrue(bombs.size==6,"Bombs were not emitted by aircraft")
  e.saveWithoutId(n);e.load(n);e.tick();h.assertTrue(e.fired==6&&record.shots.size==6,"Restore duplicated bomb slots");net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(record);bombs.forEach{it.discard()};e.discard();h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=40)
 fun strafeFires100RoundsWith25ExplosiveRounds(h:GameTestHelper){
  val record=SpawnRecorder();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(record);val e=eagle(h,false);val n=CompoundTag();e.saveWithoutId(n);n.putLong("StartedAt",h.level.gameTime-EagleFlight.attackEnd(false));e.load(n);e.tick();val shots=record.shots.filterIsInstance<EagleCannonRoundEntity>();h.assertTrue(e.fired==100&&shots.size==100,"Strafe round count ${shots.size}, expected 100, fired=${e.fired}");h.assertTrue(shots.count{it.getExplosionDamage()==55f}==25&&shots.all{it.getDamage()==55f&&it.getExplosionRadius()==5f},"Incorrect HE to solid-shot ratio");net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(record);shots.forEach{it.discard()};e.discard();h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=40)
 fun beaconDispatchesExactlyOneAircraftAndNoHellpod(h:GameTestHelper){
  val m=ModEntities.STRATAGEM_MARKER.get().create(h.level)!!;m.setPos(Vec3.atCenterOf(h.absolutePos(net.minecraft.core.BlockPos(0,100,0))));m.yRot=27f;m.configure(ResourceLocation("sbw_helldiver","eagle_airstrike"),80,null,beamColor=0xFF3434);h.level.addFreshEntity(m);m.tick();val id=m.podUUID;val e=id?.let {h.level.getEntity(it)} as? EagleEntity
  h.assertTrue(id!=null&&m.color==0xFF3434,"Did not dispatch red Eagle call")
  val n=CompoundTag();m.saveWithoutId(n);m.load(n);m.tick();h.assertTrue(m.podUUID==id,"Beacon dispatched a duplicate aircraft");h.runAfterDelay(5){(id?.let {h.level.getEntity(it)})?.discard();m.discard();h.succeed()}
 }
 @JvmStatic @GameTest(template="eagle_test",batch="eagle_late_restore",timeoutTicks=40)
 fun lateRestoredFlightReleasesEveryRoundBeforeRetiring(h:GameTestHelper){
  val record=SpawnRecorder();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(record)
  try {
   for(b in listOf(true,false)){
    record.shots.clear();val e=eagle(h,b);val tag=CompoundTag();e.saveWithoutId(tag)
    tag.putLong("StartedAt",h.level.gameTime-EagleFlight.end(b)-5);e.load(tag);e.tick()
    val total=EagleFlight.payloadCount(b)
    h.assertTrue(e.fired==total&&record.shots.size==total&&!e.isRemoved,"Retirement discarded the remaining salvo after a late restore")
    record.shots.dropLast(1).forEach{it.discard()};e.tick()
    h.assertTrue(!e.isRemoved&&!e.strikeComplete,"Aircraft tracking retired before the last projectile resolved")
    record.shots.last().discard();e.tick();h.assertTrue(e.strikeComplete&&e.isRemoved,"Finished aircraft did not retire")
   }
  }finally{record.shots.forEach{it.discard()};record.aircraft.forEach{it.discard()};net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(record)}
  h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",timeoutTicks=40)
 fun preplannedTerrainClearanceIncludesFullAttackAndRecovery(h:GameTestHelper){
  val target=Vec3(0.0,64.0,0.0);val forward=Vec3(0.0,0.0,1.0)
  for(b in listOf(true,false))for(ground in listOf(64.0,180.0,310.0)){
   val height=EagleFlight.terrainClearance(target,forward,b){ground}
   for(t in EagleFlight.attackStart(b)-12..EagleFlight.end(b)){
    val p=EagleFlight.position(target,forward,t.toDouble(),b,height)
    h.assertTrue(p.y-ground>=17.5,"Terrain plan did not protect the entire pass/pullout at $t, bombing=$b, ground=$ground")
   }
   val last=EagleFlight.releaseAge(EagleFlight.payloadCount(b)-1,b)
   h.assertTrue(last<EagleFlight.attackEnd(b),"Planned pull-up starts before the final release")
  }
  h.succeed()
 }

}
