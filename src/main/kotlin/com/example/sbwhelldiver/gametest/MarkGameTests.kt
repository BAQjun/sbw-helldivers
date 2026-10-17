package com.example.sbwhelldiver.gametest
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.marking.TargetMarks
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraft.nbt.CompoundTag
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import net.minecraftforge.common.util.FakePlayerFactory
import com.mojang.authlib.GameProfile
import java.util.UUID

@GameTestHolder("sbw_helldiver_marks")
@PrefixGameTestTemplate(false)
object MarkGameTests {
 // Keep actors inside the server spawn chunks, above all generated structures.
 private fun at(h:GameTestHelper)=Vec3(8.5,300.0,8.5)
 private fun mob(h:GameTestHelper,p:Vec3):LivingEntity=EntityType.HUSK.create(h.level)!!.also {h.level.getChunk(BlockPos.containing(p));it.setPos(p);it.isNoAi=true;it.isNoGravity=true;it.isInvulnerable=true;h.level.addFreshEntity(it)}
 private fun owner(h:GameTestHelper)=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"MarkOwner"))
 private fun turret(h:GameTestHelper,p:Vec3,type:net.minecraft.world.entity.EntityType<SentryHellpodEntity> = ModEntities.MACHINE_GUN_SENTRY.get()):SentryHellpodEntity {
  h.level.getChunk(BlockPos.containing(p));val t=type.create(h.level)!!;t.setPos(p);h.level.addFreshEntity(t)
  val tag=CompoundTag();t.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.DEPLOYING.ordinal);tag.putInt("HellpodPhaseTicks",t.deployTicks-1);tag.putDouble("HellpodRestY",p.y);tag.putBoolean("HellpodCapReleased",true);t.load(tag);t.tick();return t
 }
 private fun clear(h:GameTestHelper){TargetMarks.clear();h.level.getEntities(null,net.minecraft.world.phys.AABB(at(h).subtract(350.0,50.0,350.0),at(h).add(350.0,120.0,350.0))).filter {it is AbstractHellpodEntity||it is net.minecraft.world.entity.projectile.Projectile||it is net.minecraft.world.entity.monster.Husk||it is net.minecraft.world.entity.animal.Cow}.forEach {it.discard()}}
 @JvmStatic @GameTest(template="mark_test",batch="mark_trace",timeoutTicks=15)
 fun rayChoosesVisibleCreatureAndSurface(h:GameTestHelper){
  clear(h);val p=at(h);val user=owner(h);val enemy=mob(h,p.add(0.0,0.0,12.0));val start=p.add(0.0,1.0,0.0)
  val hit=TargetMarks.trace(h.level,user,start,Vec3(0.0,0.0,1.0));h.assertTrue(hit?.first===enemy,"Ray did not choose living target")
  val wall=BlockPos.containing(start.add(0.0,0.0,8.0));h.level.setBlock(wall,Blocks.STONE.defaultBlockState(),2)
  val blocked=TargetMarks.trace(h.level,user,start,Vec3(0.0,0.0,1.0));h.assertTrue(blocked!=null&&blocked.first==null&&blocked.second.z<enemy.z,"Initial ray marked through an intervening surface")
  h.level.setBlock(wall,Blocks.AIR.defaultBlockState(),2);clear(h);h.succeed()
 }
 @JvmStatic @GameTest(template="mark_test",batch="mark_tracking",timeoutTicks=15)
 fun markFollowsMovingLivingEntityAndEndsAtDeath(h:GameTestHelper){
  clear(h);val enemy=mob(h,at(h).add(0.0,0.0,8.0));val id=UUID.randomUUID();val m=TargetMarks.put(h.level,id,enemy,enemy.position());h.level.getChunk(BlockPos.containing(enemy.position().add(8.0,2.0,3.0)));enemy.setPos(enemy.position().add(8.0,2.0,3.0))
  h.assertTrue(TargetMarks.point(h.level,m)==enemy.boundingBox.center,"Moving target lookup=${TargetMarks.resolve(h.level,m)}, point=${TargetMarks.point(h.level,m)}, expected=${enemy.boundingBox.center}, removed=${enemy.isRemoved}")
  enemy.discard();h.assertTrue(TargetMarks.current(h.level,id)==null,"Dead or removed entity kept its mark");clear(h);h.succeed()
 }
 @JvmStatic @GameTest(template="mark_test",batch="mark_expiry",timeoutTicks=175)
 fun normalMarkHidesAt160TicksButItsInstructionRemains(h:GameTestHelper){
  clear(h);val id=UUID.randomUUID();TargetMarks.put(h.level,id,null,at(h));h.runAtTickTime(159){h.assertTrue(TargetMarks.current(h.level,id)!=null,"Mark expired early")};h.runAtTickTime(160){h.assertTrue(TargetMarks.current(h.level,id)!=null&&TargetMarks.views(h.level).none {it.owner==id},"HUD expiry deleted the instruction or left it visible");TargetMarks.cancel(id);h.succeed()}
 }
 @JvmStatic @GameTest(template="mark_test",batch="mark_priority",timeoutTicks=15)
 fun markedFartherEnemyPreemptsAndCancelRestoresNearest(h:GameTestHelper){
  clear(h);val p=at(h);val t=turret(h,p);val near=mob(h,p.add(0.0,0.0,8.0));val far=mob(h,p.add(0.0,0.0,22.0));val id=UUID.randomUUID()
  TargetMarks.put(h.level,id,far,far.position());t.tick();h.assertTrue(t.selectedTarget===far,"Marked enemy didn't preempt nearer enemy")
  TargetMarks.cancel(id);repeat(10){t.tick()};h.assertTrue(t.selectedTarget===near,"Cancellation did not restore normal nearest targeting");clear(h);h.succeed()
 }
 @JvmStatic @GameTest(template="mark_test",batch="mark_neutral",timeoutTicks=15)
 fun explicitNeutralMarkWorksAndRangeStillApplies(h:GameTestHelper){
  clear(h);val p=at(h);val t=turret(h,p);val cow=EntityType.COW.create(h.level)!!;cow.setPos(p.add(0.0,0.0,15.0));cow.isNoAi=true;h.level.addFreshEntity(cow)
  h.assertTrue(!t.validTarget(cow)&&t.validTarget(cow,true),"Explicit mark didn't allow a neutral creature")
  TargetMarks.put(h.level,UUID.randomUUID(),cow,cow.position());t.tick();h.assertTrue(t.selectedTarget===cow,"Neutral mark not selected")
  cow.setPos(p.add(0.0,0.0,600.0));h.assertTrue(TargetMarks.preferred(t)==null,"Mark bypassed turret range");clear(h);h.succeed()
 }
 @JvmStatic @GameTest(template="mark_test",batch="mark_laser",timeoutTicks=15)
 fun markedEnemyPreemptsExistingLaserLock(h:GameTestHelper){
  clear(h);val p=at(h);val t=turret(h,p,ModEntities.LASER_SENTRY.get());val near=mob(h,p.add(0.0,0.0,10.0));val far=mob(h,p.add(0.0,0.0,25.0));t.tick();h.assertTrue(t.selectedTarget===near,"Laser didn't acquire initial lock")
  TargetMarks.put(h.level,UUID.randomUUID(),far,far.position());t.tick();h.assertTrue(t.selectedTarget===far,"Laser retained unmarked lock over a valid mark");clear(h);h.succeed()
 }
 @JvmStatic @GameTest(template="mark_test",batch="mark_block",timeoutTicks=15)
 fun groundMarkIsFixedAndIgnoredByDirectFireSentries(h:GameTestHelper){
  clear(h);val p=at(h);val t=turret(h,p);val id=UUID.randomUUID();val point=p.add(10.0,.1,0.0);val m=TargetMarks.put(h.level,id,null,point)
  h.assertTrue(TargetMarks.point(h.level,m)==point&&TargetMarks.preferred(t)==null,"Direct sentry attacked a ground command or point moved");TargetMarks.cancel(id);clear(h);h.succeed()
 }
 @JvmStatic @GameTest(template="mark_test",batch="mark_limits",timeoutTicks=15)
 fun markRayHonorsMaximumRangeAndRejectsForgedInput(h:GameTestHelper){
  clear(h);val p=at(h);val user=owner(h);user.setPos(p);val e=mob(h,p.add(0.0,0.0,TargetMarks.RANGE+3))
  h.assertTrue(TargetMarks.trace(h.level,user,p.add(0.0,1.0,0.0),Vec3(0.0,0.0,1.0))==null,"Selection exceeded 300m")
  TargetMarks.input(user,p.add(100.0,0.0,0.0),Vec3(0.0,0.0,1.0),false);TargetMarks.input(user,p,Vec3(Double.NaN,0.0,1.0),false)
  h.assertTrue(TargetMarks.current(h.level,user.uuid)==null,"Untrusted camera origin/direction accepted");e.discard();clear(h);h.succeed()
 }
 @JvmStatic @GameTest(template="mark_test",batch="mark_mortar",timeoutTicks=155)
 fun mortarFiresAtEmptyMarkedGroundAndStopsWhenCanceled(h:GameTestHelper){
  clear(h);val p=at(h);val t=turret(h,p,ModEntities.MORTAR_SENTRY.get());val id=UUID.randomUUID();TargetMarks.put(h.level,id,null,p.add(0.0,0.0,35.0))
  var fired=false;var serialAtCancel=0;var canceled=false
  h.onEachTick {
   if(!canceled&&t.shotSerial>0){fired=true;serialAtCancel=t.shotSerial;TargetMarks.cancel(id);canceled=true}
  }
  h.runAtTickTime(145){h.assertTrue(fired,"Mortar never fired at an empty ground mark");h.assertTrue(t.shotSerial==serialAtCancel,"Mortar continued attacking canceled ground");clear(h);h.succeed()}
 }
 @JvmStatic @GameTest(template="mark_test",batch="mark_turret",timeoutTicks=15)
 fun markedTurretHasOwnIconAndNeverBecomesAttackTarget(h:GameTestHelper){
  clear(h);val p=at(h);val a=turret(h,p);val b=turret(h,p.add(0.0,0.0,20.0),ModEntities.AUTOCANNON_SENTRY.get());val id=UUID.randomUUID()
  TargetMarks.put(h.level,id,b,b.position());val v=TargetMarks.views(h.level).single()
  h.assertTrue(v.icon=="textures/item/autocannon_sentry_stratagem.png","Turret mark didn't publish its own icon")
  h.assertTrue(TargetMarks.preferred(a)==null&&TargetMarks.preferred(b)==null,"Marked turret became a sentry attack candidate")
  val hit=TargetMarks.trace(h.level,owner(h),p.add(0.0,.8,5.0),Vec3(0.0,0.0,1.0));h.assertTrue(hit?.first===b,"Turret could not be marked by the camera ray");clear(h);h.succeed()
 }

 @JvmStatic @GameTest(template="mark_test",batch="mark_hidden",timeoutTicks=195)
 fun hiddenGroundMarkStillDirectsMortarAndCannotBeCanceledByStaleHud(h:GameTestHelper){
  clear(h);val p=at(h);val user=owner(h);user.setPos(p)
  val t=turret(h,p,ModEntities.MORTAR_SENTRY.get());val m=TargetMarks.put(h.level,user.uuid,null,p.add(0.0,0.0,35.0))
  h.runAfterDelay(165){
   h.assertTrue(TargetMarks.current(h.level,user.uuid)===m&&TargetMarks.preferred(t)===m,"Hidden ground order stopped directing the mortar")
   h.assertTrue(TargetMarks.views(h.level).none {it.owner==user.uuid},"Hidden order still has a HUD entry")
   // Above the build ceiling this ray cannot select an overhead fixture.
   user.setPos(p.x,h.level.maxBuildHeight+20.0,p.z)
   h.assertTrue(TargetMarks.trace(h.level,user,user.eyePosition,Vec3(0.0,1.0,0.0))==null,"Stale HUD fixture ray must miss")
   TargetMarks.input(user,user.eyePosition,Vec3(0.0,1.0,0.0),true)
   h.assertTrue(TargetMarks.current(h.level,user.uuid)===m,"Hidden instruction was canceled by a stale/forged HUD request")
   val replacement=TargetMarks.put(h.level,user.uuid,null,p.add(8.0,0.0,26.0))
   h.assertTrue(TargetMarks.current(h.level,user.uuid)===replacement&&TargetMarks.preferred(t)===replacement,"A new ping did not replace the old hidden instruction")
   TargetMarks.cancel(user.uuid);h.assertTrue(TargetMarks.preferred(t)==null,"Explicit cancellation left an order active")
   clear(h);h.succeed()
  }
 }
 @JvmStatic @GameTest(template="mark_test",batch="mark_hidden_entity",timeoutTicks=195)
 fun hiddenCreatureRemainsPriorityAndTracksMovement(h:GameTestHelper){
  clear(h);val p=at(h);val t=turret(h,p);val near=mob(h,p.add(0.0,0.0,8.0));val far=mob(h,p.add(0.0,0.0,22.0));val id=UUID.randomUUID()
  val m=TargetMarks.put(h.level,id,far,far.position())
  h.runAfterDelay(165){
   h.level.getChunk(BlockPos.containing(p.add(3.0,0.0,25.0)));far.setPos(p.add(3.0,0.0,25.0));t.tick()
   h.assertTrue(TargetMarks.current(h.level,id)===m&&TargetMarks.point(h.level,m)==far.boundingBox.center,"Hidden moving entity lost its mark")
   h.assertTrue(t.selectedTarget===far&&TargetMarks.views(h.level).none{it.owner==id},"Hidden target selected=${t.selectedTarget?.uuid}, expected=${far.uuid}, preferred=${TargetMarks.preferred(t)?.serial}, phase=${t.phase}, valid=${t.validTarget(far,true)}, clip=${t.clipRemaining}")
   TargetMarks.cancel(id);repeat(10){t.tick()};h.assertTrue(t.selectedTarget===near,"Canceling hidden priority did not restore ordinary targeting")
   clear(h);h.succeed()
  }
 }

}
