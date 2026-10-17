package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.marking.*
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.util.UUID

@GameTestHolder("sbw_helldiver_drop")
@PrefixGameTestTemplate(false)
object PlayerDropGameTests {
 private val at=Vec3(8.5,300.0,8.5)
 private fun floor(h:GameTestHelper){for(x in -15..15)for(z in -15..15)h.level.setBlock(BlockPos.containing(at).offset(x,-1,z),Blocks.STONE.defaultBlockState(),2)}
 private fun player(h:GameTestHelper)=object:net.minecraftforge.common.util.FakePlayer(h.level,GameProfile(UUID.randomUUID(),"DropTest"+h.level.random.nextInt(1000))){
  // FakePlayer's dummy network deliberately ignores teleports. Model their server-side position.
  override fun teleportTo(l:net.minecraft.server.level.ServerLevel,x:Double,y:Double,z:Double,yaw:Float,pitch:Float){check(l===level());moveTo(x,y,z,yaw,pitch)}
 }.also{it.setPos(at)}
 private fun drop(h:GameTestHelper):Pair<PlayerHellpodEntity,net.minecraft.server.level.ServerPlayer>{floor(h);val p=player(h);val d=ModEntities.PLAYER_HELLPOD.get().create(h.level)!!;h.assertTrue(d.launch(p,at),"Cannot mount a launched transport");return d to p}
 private fun clean(h:GameTestHelper,d:PlayerHellpodEntity,p:net.minecraft.server.level.ServerPlayer){d.release();d.discard();p.discard();h.level.getEntitiesOfClass(HellpodCapEntity::class.java,d.boundingBox.inflate(30.0)).forEach{it.discard()}}
 @JvmStatic @GameTest(template="eagle_test",batch="player_drop",timeoutTicks=230)
 fun completesDescentRaisesPlayerAndRestoresDamage(h:GameTestHelper){
  val(d,p)=drop(h);val hp=p.health
  h.assertTrue(d.y>at.y+700&&p.vehicle===d,"Player did not start in the high pod")
  val end=d.impactDeadline.toDouble();val boundary=end-68
  var previous=d.scheduledHeight(end-131)
  for(t in 1..131){val y=d.scheduledHeight(end-131+t);h.assertTrue(y<previous,"Descent stalled or rose at tick $t");previous=y}
  val before=(d.scheduledHeight(boundary-.001)-d.scheduledHeight(boundary))/.001
  val after=(d.scheduledHeight(boundary)-d.scheduledHeight(boundary+.001))/.001
  h.assertTrue(kotlin.math.abs(before-after)<.01,"Brake boundary has a velocity discontinuity")
  h.assertTrue(d.scheduledHeight(end-1)-d.landingY in 4.9..5.1,"Touchdown no longer retains the reference terminal speed")
  p.hurt(h.level.damageSources().inWall(),5f);h.assertTrue(p.health==hp,"Transport failed to protect occupant")
  p.stopRiding();h.assertTrue(p.vehicle===d,"Ordinary sneak dismount escaped a falling pod")
  h.runAfterDelay(64){h.assertTrue(d.braking&&d.y-d.landingY in 470.0..501.0,"Player brake did not start at reference height/time: y=${d.y-d.landingY}")}
  h.runAfterDelay(146){h.assertTrue(d.phase==AbstractHellpodEntity.Phase.DEPLOYING&&p.vehicle===d,"Early release phase=${d.phase} age=${d.phaseAge} removed=${d.isRemoved} passenger=${p.vehicle} noPhysics=${p.noPhysics} at=${p.position()}")}
  h.runAfterDelay(201){
   h.assertTrue(d.phase==AbstractHellpodEntity.Phase.ACTIVE&&!p.isPassenger,"Player did not exit after opening")
   h.assertTrue(!p.noPhysics&&p.fallDistance==0f&&p.position().distanceTo(at.add(0.0,.35,0.0))<.01,"Exit left noclip, fall damage, or a wrong position")
   h.assertTrue(!net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(net.minecraftforge.event.entity.living.LivingAttackEvent(p,h.level.damageSources().generic(),1f)),"Protection remained after exit")
   clean(h,d,p);h.succeed()
  }
 }
 @JvmStatic @GameTest(template="eagle_test",batch="drop_cleanup",timeoutTicks=20)
 fun removingTransportReleasesSafely(h:GameTestHelper){val(d,p)=drop(h);d.tick();d.discard();h.assertTrue(!p.isPassenger&&!p.noPhysics&&p.position().distanceTo(at.add(0.0,.35,0.0))<.01,"Removed pod passenger=${p.vehicle}, noclip=${p.noPhysics}, pos=${p.position()}, expected=$at");clean(h,d,p);h.succeed()}
 @JvmStatic @GameTest(template="eagle_test",batch="drop_saved",timeoutTicks=20)
 fun transportSaveKeepsOwnerDestinationAndPhase(h:GameTestHelper){val(d,p)=drop(h);val tag=CompoundTag();d.saveWithoutId(tag);val r=ModEntities.PLAYER_HELLPOD.get().create(h.level)!!;r.load(tag);h.assertTrue(r.occupant==p.uuid&&r.impactDeadline==d.impactDeadline&&r.scheduledHeight(h.level.gameTime+30.0)==d.scheduledHeight(h.level.gameTime+30.0),"Save lost occupant or descent schedule");r.discard();clean(h,d,p);h.succeed()}
 @JvmStatic @GameTest(template="eagle_test",batch="drop_safe",timeoutTicks=20)
 fun landingRejectsAirWaterAndBlockedHeadroom(h:GameTestHelper){floor(h);val p=player(h);h.assertTrue(PlayerHellpodEntity.safeLanding(h.level,p,at),"Flat solid ground rejected");h.assertTrue(!PlayerHellpodEntity.safeLanding(h.level,p,at.add(0.0,4.0,0.0)),"Floating landing accepted");val b=BlockPos.containing(at);h.level.setBlock(b.above(),Blocks.STONE.defaultBlockState(),2);h.assertTrue(!PlayerHellpodEntity.safeLanding(h.level,p,at),"Blocked headroom accepted");h.level.setBlock(b.above(),Blocks.AIR.defaultBlockState(),2);h.level.setBlock(b,Blocks.WATER.defaultBlockState(),2);h.assertTrue(!PlayerHellpodEntity.safeLanding(h.level,p,at),"Water landing accepted");h.level.setBlock(b,Blocks.AIR.defaultBlockState(),2);p.discard();h.succeed()}
 @JvmStatic @GameTest(template="eagle_test",batch="mark_command",timeoutTicks=20)
 fun markingCommandClearsHiddenOrdersAndRejectsInput(h:GameTestHelper){
  val p=player(h);val server=h.level.server;val source=server.createCommandSourceStack().withLevel(h.level).withPermission(4)
  val m=TargetMarks.put(h.level,p.uuid,null,at)
  h.assertTrue(server.commands.performPrefixedCommand(source,"helldivers marking false")==1,"Marking command missing")
  h.assertTrue(!MarkSettings.get(server).enabled&&TargetMarks.current(h.level,p.uuid)==null,"Disabling left a hidden instruction")
  TargetMarks.input(p,p.eyePosition,Vec3(0.0,-1.0,0.0),false);h.assertTrue(TargetMarks.current(h.level,p.uuid)==null,"Disabled marking accepted a packet")
  val n=CompoundTag();MarkSettings.get(server).save(n);h.assertTrue(!n.getBoolean("Enabled"),"World flag was not saved")
  server.commands.performPrefixedCommand(source,"helldivers marking true");h.assertTrue(MarkSettings.get(server).enabled,"Marking failed to re-enable");TargetMarks.clear();p.discard();h.succeed()
 }
 @JvmStatic @GameTest(template="eagle_test",batch="drop_command",timeoutTicks=60)
 fun commandSelectsPlayerAndRejectsPermissionZero(h:GameTestHelper){
  floor(h);val p=player(h);val server=h.level.server
  val source=p.createCommandSourceStack().withLevel(h.level).withPermission(0)
  h.assertTrue(server.commands.performPrefixedCommand(source,"helldivers drop @s")==0,"Unprivileged source triggered a drop")
  val admin=source.withPermission(4)
  h.assertTrue(server.commands.performPrefixedCommand(admin,"helldivers drop @s 8.5 300 8.5")==1,"Selector drop command failed")
  val order=PlayerDrops.pending(p.uuid)!!
  h.assertTrue(!p.isPassenger,"Drop started before loading confirmation")
  PlayerDrops.ready(p,UUID.randomUUID());h.assertTrue(!p.isPassenger,"Forged readiness token launched a drop")
  h.assertTrue(server.commands.performPrefixedCommand(admin,"helldivers drop @s")==0,"Repeated call duplicated a pending transport")
  h.runAfterDelay(25){
   PlayerDrops.ready(p,order.token);val d=p.vehicle as? PlayerHellpodEntity
   h.assertTrue(d!=null&&PlayerDrops.pending(p.uuid)==null,"Loading-confirmed player is not transported")
   h.assertTrue(!d!!.shouldRiderSit(),"Transport forces sitting pose")
   clean(h,d,p);h.succeed()
  }
 }
 @JvmStatic @GameTest(template="eagle_test",batch="drop_steer",timeoutTicks=30)
 fun steeringIsBoundedOwnerOnlyAndMovesLanding(h:GameTestHelper){
  val(d,p)=drop(h);val stranger=player(h);val z=d.z
  d.acceptSteering(stranger,1f,0f);d.tick();h.assertTrue(d.z==z,"Another player steered the pod")
  d.acceptSteering(p,Float.NaN,0f);d.tick();h.assertTrue(d.z==z,"Non-finite input moved the pod")
  d.acceptSteering(p,1f,0f);repeat(5){d.tick()}
  h.assertTrue(d.z>z+1.0&&d.z<=z+2.251,"Steering didn't use the bounded speed")
  val n=CompoundTag();d.saveWithoutId(n);h.assertTrue(kotlin.math.abs(n.getDouble("ExitZ")-d.z)<.001,"Steering did not move the landing point")
  val tag=CompoundTag();d.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.DEPLOYING.ordinal);d.load(tag);val stopped=d.z;d.acceptSteering(p,1f,0f);d.tick();h.assertTrue(d.z==stopped,"Steering continued after touchdown")
  stranger.discard();clean(h,d,p);h.succeed()
 }

}
