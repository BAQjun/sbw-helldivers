package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.config.StratagemCooldownConfig
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.stratagem.*
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.*
import com.atsuishio.superbwarfare.world.phys.*
import java.util.UUID
import kotlin.math.abs

@GameTestHolder("sbw_helldiver_orbital")
@PrefixGameTestTemplate(false)
object RailcannonGameTests {
 private val id="orbital_railcannon_strike"
 private fun at(h:GameTestHelper)=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(15,8,15)))
 private fun mob(h:GameTestHelper,at:Vec3,hp:Double)=EntityType.RAVAGER.create(h.level)!!.also{
  it.isNoAi=true;it.isNoGravity=true;it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=hp;it.health=hp.toFloat()
  it.getAttribute(Attributes.ARMOR)!!.baseValue=0.0;it.setPos(at);h.level.addFreshEntity(it)
 }
 private fun marker(h:GameTestHelper,at:Vec3):StratagemMarker {
  val p=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"RailTest"));p.setPos(at)
  p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(ModItems.BEACON.get()).also{s->BeaconData.ensureIdentity(s);BeaconData.setItems(s,listOf(ItemStack(ModItems.ORBITAL_RAILCANNON_STRIKE.get())))})
  val now=StratagemCooldowns.now(p.server);check(SuperDestroyers.move(p,Stratagems.UP));SuperDestroyers.state(p).advance(now+60)
  return ModEntities.STRATAGEM_MARKER.get().create(h.level)!!.also{it.setPos(at);it.configure(Stratagems.get(id)!!.dropId,20,p.uuid,groundY=at.y);p.discard()}
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",batch="rail_selection",timeoutTicks=10)
 fun choosesMaximumHealthNotRemainingHealthAndFiltersTargets(h:GameTestHelper){
  val at=at(h);val m=marker(h,at);val high=mob(h,at.add(8.0,0.0,0.0),1000.0);high.health=1f
  val low=mob(h,at.add(2.0,0.0,0.0),500.0);val outside=mob(h,at.add(30.1,0.0,0.0),2000.0)
  val wolf=EntityType.WOLF.create(h.level)!!;wolf.isTame=true;wolf.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=3000.0;wolf.health=3000f;wolf.setPos(at);h.level.addFreshEntity(wolf)
  h.assertTrue(m.selectRailTarget()===high,"Targeting ignored max health, radius or tame exclusion")
  high.discard();h.assertTrue(m.selectRailTarget()===low,"Fallback enemy selection failed")
  low.discard();h.assertTrue(m.selectRailTarget()==null,"Tamed or out-of-range entity accepted")
  h.assertTrue(Stratagems.get(id)!!.code==listOf(1,0,2,2,1)&&StratagemCooldownConfig.ticks(id)==3591,"Native code/upgraded cooldown mismatch")
  h.assertTrue(m.duration==54&&m.railcannonSettings.range==30.0&&m.railcannonSettings.lockTicks==34,"Decoded timing/radius mismatch")
  outside.discard();wolf.discard();m.discard();h.succeed()
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",batch="rail_damage",timeoutTicks=10)
 fun directPlusHardEdgeBlast(h:GameTestHelper){
  val at=at(h);val shell=ModEntities.ORBITAL_RAILCANNON_SHELL.get().create(h.level)!!;shell.setPos(at);shell.tickCount=1;h.level.addFreshEntity(shell)
  h.assertTrue(shell.getDamage()==160f&&shell.getExplosionDamage()==160f&&shell.getExplosionRadius()==2.5f,"Wrong railcannon shell damage")
  val hit=mob(h,at,1000.0);val inner=mob(h,at.add(2.49+hit.bbWidth/2,0.0,0.0),1000.0);val outer=mob(h,at.add(2.51+hit.bbWidth/2,0.0,0.0),1000.0)
  shell.onHitEntity(ExtendedEntityRayTraceResult(EntityResult(hit,at,false,false)))
  h.assertTrue(abs(hit.health-680f)<.02,"Direct + explosion must stack to 320, got ${1000-hit.health}")
  h.assertTrue(abs(inner.health-840f)<.02&&outer.health==1000f,"Equal radii did not produce a full-damage 2.5m sphere")
  for(d in listOf(0.0,2.49999,2.5))h.assertTrue(SentryExplosion.damageAt(d,160f,2.5,2.5)==160f,"Hard-edge blast lost damage at $d")
  h.assertTrue(SentryExplosion.damageAt(2.50001,160f,2.5,2.5)==0f,"Hard-edge blast escaped radius")
  val health=hit.health;shell.causeExplode(at);h.assertTrue(hit.health==health,"Duplicate impact damage")
  listOf(hit,inner,outer,shell).forEach{it.discard()};h.succeed()
 }
 private fun loadCorridor(h:GameTestHelper,at:Vec3):List<Pair<Int,Int>> {
  val c=net.minecraft.world.level.ChunkPos(BlockPos.containing(at));val loaded=mutableListOf<Pair<Int,Int>>()
  for(x in c.x-6..c.x+6)for(z in c.z-6..c.z+6)if(h.level.setChunkForced(x,z,true))loaded.add(x to z)
  for(x in -3..3)for(z in -3..3)h.level.setBlock(BlockPos.containing(at.add(x.toDouble(),-1.0,z.toDouble())),Blocks.BEDROCK.defaultBlockState(),3)
  return loaded
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",batch="rail_flight",timeoutTicks=110)
 fun completeLockTracksMovingEnemyAndRestoresWithoutSecondShot(h:GameTestHelper){
  val at=at(h).add(0.0,200.0,0.0);val loaded=loadCorridor(h,at);val target=mob(h,at,1000.0);val m=marker(h,at);h.level.addFreshEntity(m)
  h.runAfterDelay(18){h.assertTrue(m.railLockStartedAt<0&&m.orbitalFiredAt<0,"Lock began before call delay")}
  h.runAfterDelay(23){h.assertTrue(m.railTargetId==target.id&&m.railBeamActive()&&m.orbitalFiredAt<0,"Target/beam not acquired")}
  h.runAfterDelay(35){target.setPos(at.add(2.0,0.0,0.0))}
  h.runAfterDelay(50){
   h.assertTrue(m.orbitalTarget().distanceTo(target.boundingBox.center)<.001&&m.orbitalFiredAt<0,"Moving target not tracked during lock")
   val tag=CompoundTag();m.saveWithoutId(tag);val restored=ModEntities.STRATAGEM_MARKER.get().create(h.level)!!;restored.load(tag)
   h.assertTrue(restored.railLockStartedAt==m.railLockStartedAt&&restored.duration==m.duration,"Reload reset lock deadline")
   restored.tick();h.assertTrue(restored.railTargetId==target.id&&restored.orbitalFiredAt<0,"Reload lost target UUID")
   restored.discard()
  }
  h.runAfterDelay(56){h.assertTrue(m.orbitalFiredAt-m.callStartedAt==54L,"Shot was not 1.7s after beam start")}
  var trace=""
  for(tick in listOf(56,62,63,64,65,70))h.runAfterDelay(tick.toLong()){trace+=" t=$tick markerRemoved=${m.isRemoved} aim=${m.orbitalTarget()} victim=${target.position()} pod=${m.podUUID} shell=${m.podUUID?.let(h.level::getEntity)?.let{it.position().toString()+" motion="+it.deltaMovement+" ticks="+it.tickCount}}"}
  h.runAfterDelay(63){val shell=m.podUUID?.let(h.level::getEntity);if(shell!=null){val hit=h.level.clip(net.minecraft.world.level.ClipContext(shell.position(),shell.position().add(shell.deltaMovement),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.ANY,shell));trace+=" blockRay=${hit.type} ${hit.location} ${h.level.getBlockState(hit.blockPos)}";trace+=" entities="+h.level.getEntities(shell,shell.boundingBox.expandTowards(shell.deltaMovement).inflate(1.0)).map{it.type.toString()+":"+it.id+":"+it.position()}}}
  h.runAfterDelay(85){
   h.assertTrue(abs(target.health-680f)<.05,"Real tracked shell failed to hit for 320: ${target.health} $trace")
   h.assertTrue(m.isRemoved&&m.podUUID!=null,"Completed marker or shell not cleaned")
   val t=CompoundTag();m.saveWithoutId(t);val restored=ModEntities.STRATAGEM_MARKER.get().create(h.level)!!;restored.load(t);restored.tick()
   h.assertTrue(restored.podUUID==m.podUUID&&restored.orbitalFiredAt==m.orbitalFiredAt,"Reload redispatched the shell")
   restored.discard();target.discard();loaded.forEach{(x,z)->h.level.setChunkForced(x,z,false)};h.succeed()
  }
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",batch="rail_empty",timeoutTicks=110)
 fun noEnemyStillFiresAtBeacon(h:GameTestHelper){
  val at=at(h).add(0.0,200.0,0.0);val loaded=loadCorridor(h,at);val m=marker(h,at);h.level.addFreshEntity(m)
  h.runAfterDelay(25){h.assertTrue(m.railTargetId==-1&&m.railBeamActive()&&m.orbitalTarget().distanceTo(at.add(0.0,.02,0.0))<.001,"Empty lock did not point at beacon")}
  h.runAfterDelay(56){h.assertTrue(m.orbitalFiredAt-m.callStartedAt==54L,"Empty call cancelled instead of firing")}
  h.runAfterDelay(85){h.assertTrue(m.isRemoved&&m.podUUID!=null&&h.level.getEntity(m.podUUID!!)==null,"No-target strike failed to impact beacon ground");loaded.forEach{(x,z)->h.level.setChunkForced(x,z,false)};h.succeed()}
 }
}
