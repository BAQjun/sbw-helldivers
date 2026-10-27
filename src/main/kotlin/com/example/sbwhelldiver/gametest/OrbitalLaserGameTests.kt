package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.config.*
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.stratagem.*
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.*
import java.util.UUID
import kotlin.math.abs

@GameTestHolder("sbw_helldiver_orbital")
@PrefixGameTestTemplate(false)
object OrbitalLaserGameTests {
 private val id="orbital_laser"
 private fun at(h:GameTestHelper)=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(15,8,15))).add(0.0,200.0,0.0).also{at->
  h.level.getEntities(null,net.minecraft.world.phys.AABB(at,at).inflate(100.0,5.0,100.0)).filter{it is OrbitalLaserEntity||it is net.minecraft.world.entity.monster.Ravager}.forEach{it.discard()}
 }
 private fun mob(h:GameTestHelper,at:Vec3,hp:Double=1000.0)=EntityType.RAVAGER.create(h.level)!!.also{
  it.isNoAi=true;it.isNoGravity=true;it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=hp;it.health=hp.toFloat()
  it.getAttribute(Attributes.ARMOR)!!.baseValue=0.0;it.setPos(at);h.level.addFreshEntity(it)
 }
 private fun laser(h:GameTestHelper,at:Vec3,s:OrbitalLaserSettings=OrbitalLaserSettings())=ModEntities.ORBITAL_LASER.get().create(h.level)!!.also{
  it.configure(at,at.add(0.0,300.0,0.0),null,s);h.level.addFreshEntity(it)
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",batch="laser_damage",timeoutTicks=25)
 fun fiveTickPulseAndThreeSecondIgnition(h:GameTestHelper){
  val at=at(h);val victim=mob(h,at);victim.addEffect(MobEffectInstance(MobEffects.FIRE_RESISTANCE,200))
  val l=laser(h,at);val start=h.level.gameTime
  h.runAfterDelay(4){h.assertTrue(victim.health==1000f,"Laser damaged before its first five-tick pulse")}
  h.runAfterDelay(5){h.assertTrue(abs(victim.health-975f)<.01,"Expected 25 damage on tick 5, got ${victim.health}")}
  h.runAfterDelay(9){h.assertTrue(abs(victim.health-975f)<.01,"Laser pulse interval shorter than five ticks")}
  h.runAfterDelay(10){h.assertTrue(abs(victim.health-950f)<.01,"Hurt cooldown blocked the second pulse")
   victim.removeEffect(MobEffects.FIRE_RESISTANCE);l.affect(false)
   h.assertTrue(victim.getEffect(ModEffects.BURNING.get())?.duration==60,"Laser did not apply three-second burning")
   h.assertTrue(l.startedAt==start&&l.endsAt==start+500,"Native duration changed")
   l.discard();victim.discard();h.succeed()
  }
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",batch="laser_sweep",timeoutTicks=60)
 fun sweepsWithoutTeleportingThenRetargets(h:GameTestHelper){
  val at=at(h);val high=mob(h,at.add(8.0,0.0,0.0),1000.0);val low=mob(h,at.add(-3.0,0.0,0.0),500.0)
  val l=laser(h,at);var previous=at
  for(t in 1L..8L)h.runAfterDelay(t){
   h.assertTrue(l.position().distanceTo(previous)<=.50001,"Footprint jumped on target acquisition or tracking")
   h.assertTrue(l.targetId==high.id,"MC maximum-health priority was not respected")
   if(t==2L)h.assertTrue(high.health==1000f,"Selected enemy was hurt before the footprint reached it")
   previous=l.position()
  }
  h.runAfterDelay(12){high.setPos(at.add(8.0,0.0,2.0))}
  h.runAfterDelay(18){h.assertTrue(high.boundingBox.inflate(.02).contains(l.position()),"Beam contact did not transfer to the moving creature surface")}
  h.runAfterDelay(19){high.discard()}
  h.runAfterDelay(21){h.assertTrue(l.targetId==low.id,"Dead target was not replaced");h.assertTrue(l.x>at.x,"Retarget teleported instead of sweeping");l.discard();low.discard();h.succeed()}
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",batch="laser_empty",timeoutTicks=60)
 fun emptyAreaReacquiresAndSavePreservesDeadline(h:GameTestHelper){
  val at=at(h);val l=laser(h,at,OrbitalLaserSettings(durationTicks=45));var target:net.minecraft.world.entity.LivingEntity?=null
  h.runAfterDelay(5){h.assertTrue(l.targetId==-1&&l.position().distanceTo(at)<.0001,"Empty beam wandered");target=mob(h,at.add(3.0,0.0,0.0))}
  h.runAfterDelay(24){h.assertTrue(l.targetId==target!!.id,"A newly arrived enemy was not acquired")
   val n=CompoundTag();l.saveWithoutId(n);val restored=ModEntities.ORBITAL_LASER.get().create(h.level)!!;restored.load(n)
   h.assertTrue(restored.startedAt==l.startedAt&&restored.endsAt==l.endsAt&&restored.settings==l.settings,"Reload reset laser lifetime or settings")
   restored.discard()
  }
  h.runAfterDelay(47){h.assertTrue(l.isRemoved,"Beam outlived configured duration");target?.discard();h.succeed()}
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",batch="laser_charges",timeoutTicks=10)
 fun limitedChargesCoolOnArrivalAndPersist(h:GameTestHelper){
  val s=StratagemCooldownState();var now=100L
  h.assertTrue(StratagemCooldownConfig.maxUses(id)==3&&StratagemCooldownConfig.ticks(id)==5130,"Native uses/upgraded cooldown mismatch")
  val cancelled=UUID.randomUUID();s.reserve(id,cancelled,now);s.beginArrival(id,cancelled,now,now+40);s.cancel(id,cancelled)
  h.assertTrue(s.entry(id).remaining==3,"Cancelled arrival consumed a charge")
  repeat(3){i->val token=UUID.randomUUID();h.assertTrue(s.reserve(id,token,now),"Charge ${i+1} unavailable")
   s.beginArrival(id,token,now,now+40);s.beginArrival(id,token,now+1,now+40)
   h.assertTrue(s.entry(id).remaining==2-i&&s.entry(id).phase==CallPhase.ARRIVING,"Arrival charge decremented twice")
   s.advance(now+40);val e=s.entry(id)
   h.assertTrue(e.phase==CallPhase.COOLDOWN&&e.start==now+40&&e.activity(now+40.0)==null,"Laser did not cool directly at arrival")
   now=e.end;s.advance(now)
  }
  val restored=StratagemCooldownState.load(s.save());h.assertTrue(!restored.available(id,now+10000)&&restored.entry(id).remaining==0,"Exhausted laser rearmed itself")
  restored.setEnabled(false,now);h.assertTrue(restored.available(id,now)&&restored.entry(id).remaining==3,"Cooldown bypass did not restore uses")
  h.assertTrue(Stratagems.get(id)!!.code==listOf(1,2,0,1,2),"Wrong stratagem code");h.succeed()
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",batch="laser_resupply",timeoutTicks=10)
 fun completedDepartureAndReturnRestoreUsesWithoutResettingCooldown(h:GameTestHelper){
  val ledger=StratagemCooldownState()
  val laser=ledger.entry("orbital_laser");laser.remaining=0;laser.phase=CallPhase.COOLDOWN;laser.start=100;laser.end=5230
  val eagle=ledger.entry("eagle_airstrike");eagle.remaining=0
  val unlimited=ledger.entry("orbital_precision_strike")
  var ship=DestroyerState();var resupplies=0
  fun advance(time:Long){ship.advance(time){ledger.restoreAllUses();resupplies++}}
  val north=DestroyerAnchor.at("minecraft:overworld",Vec3.ZERO,Stratagems.UP,100.0,300.0)
  val south=DestroyerAnchor.at("minecraft:overworld",Vec3.ZERO,Stratagems.DOWN,100.0,300.0)
  ship.schedule(north,0);advance(60)
  h.assertTrue(resupplies==0&&laser.remaining==0,"Initial call refilled charges without a departure")
  ship.schedule(south,60);advance(120)
  h.assertTrue(resupplies==0&&laser.remaining==0,"Relocation refilled charges")
  ship.schedule(null,120);advance(179)
  h.assertTrue(ship.anchor!=null&&resupplies==0,"Departure completed early")
  advance(180);h.assertTrue(ship.anchor==null&&resupplies==0,"Departure itself refilled charges")
  ship=DestroyerState.load(ship.save());ship.schedule(north,180)
  ship=DestroyerState.load(ship.save());advance(239)
  h.assertTrue(laser.remaining==0&&resupplies==0,"Return refilled charges before arrival")
  advance(240)
  h.assertTrue(resupplies==1&&laser.remaining==3&&eagle.remaining==StratagemCooldownConfig.maxUses(eagle.id),"Return did not refill every limited stratagem")
  h.assertTrue(laser.phase==CallPhase.COOLDOWN&&laser.start==100L&&laser.end==5230L&&unlimited.remaining==-1,"Resupply changed cooldown or unlimited status")
  laser.remaining=1;ship=DestroyerState.load(ship.save());advance(10000)
  ship.schedule(south,10000);advance(10060)
  h.assertTrue(resupplies==1&&laser.remaining==1,"Return resupply replayed after a save or relocation")
  val restored=StratagemCooldownState.load(ledger.save())
  h.assertTrue(restored.entry(eagle.id).remaining==eagle.remaining,"Refilled Eagle charges did not persist")
  h.succeed()
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",batch="laser_marker",timeoutTicks=60)
 fun beaconEndsAtArrivalAndSpawnsExactlyOneLaser(h:GameTestHelper){
  val at=at(h);val p=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"LaserTest"));p.setPos(at)
  p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack(ModItems.BEACON.get()).also{s->BeaconData.ensureIdentity(s);BeaconData.setItems(s,listOf(net.minecraft.world.item.ItemStack(ModItems.ORBITAL_LASER.get())))})
  val now=StratagemCooldowns.now(p.server);check(SuperDestroyers.move(p,Stratagems.UP));SuperDestroyers.state(p).advance(now+60)
  val m=ModEntities.STRATAGEM_MARKER.get().create(h.level)!!;m.setPos(at);m.configure(Stratagems.get(id)!!.dropId,20,p.uuid,groundY=at.y);h.level.addFreshEntity(m)
  h.assertTrue(m.hasBeaconBeam&&m.duration==40,"Laser must retain its normal inbound beacon")
  h.runAfterDelay(39){h.assertTrue(!m.isRemoved&&m.hasBeaconBeam,"Beacon ended before arrival")}
  h.runAfterDelay(42){
   val beams=h.level.getEntitiesOfClass(OrbitalLaserEntity::class.java,m.boundingBox.inflate(3.0))
   h.assertTrue(m.isRemoved&&!m.hasBeaconBeam&&beams.size==1,"Arrival did not remove beacon and create exactly one laser")
   h.assertTrue(beams.single().startedAt-m.callStartedAt==40L,"Laser started at the wrong arrival time")
   beams.forEach{it.discard()};p.discard();h.succeed()
  }
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",batch="laser_fire_trail",timeoutTicks=350)
 fun sweepLeavesPersistentSupportedFireWithoutDuplicateTrackers(h:GameTestHelper){
  val at=at(h);val base=BlockPos.containing(at)
  for(x in 0..12)for(z in -1..1)h.level.setBlock(base.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3)
  val victim=mob(h,at.add(18.0,0.0,0.0));val l=laser(h,at)
  h.runAfterDelay(22){
   l.discard();victim.discard()
   for(x in 0..10)h.assertTrue(h.level.getBlockState(base.offset(x,0,0)).`is`(ModBlocks.SENTRY_FIRE.get()),"Missing trail cell $x")
   val trackers=h.level.getEntitiesOfClass(GroundFireEntity::class.java,net.minecraft.world.phys.AABB(base).inflate(12.0))
   h.assertTrue(trackers.size<=12,"Repeated contact spawned duplicate ground-fire trackers: ${trackers.size}")
   val walker=mob(h,at.add(2.0,0.0,0.0));trackers.forEach{it.igniteInside(walker)}
   h.assertTrue(walker.getEffect(ModEffects.BURNING.get())?.duration==60,"Residual fire did not ignite a creature")
   walker.discard()
  }
  h.runAfterDelay(60){h.assertTrue(h.level.getBlockState(base.offset(2,0,0)).`is`(ModBlocks.SENTRY_FIRE.get()),"Trail vanished when the beam ended")}
  h.runAfterDelay(345){
   for(x in 0..10)h.assertTrue(h.level.getBlockState(base.offset(x,0,0)).isAir,"Trail did not expire at cell $x")
   for(x in 0..12)for(z in -1..1)h.level.removeBlock(base.offset(x,-1,z),false)
   h.succeed()
  }
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",batch="laser_fire_limits",timeoutTicks=35)
 fun elevatedAndUnderwaterContactsDoNotCreateFloatingFire(h:GameTestHelper){
  val at=at(h);val base=BlockPos.containing(at)
  h.level.setBlock(base.below(),Blocks.STONE.defaultBlockState(),3)
  val elevated=laser(h,at.add(0.0,3.0,0.0))
  h.runAfterDelay(6){
   h.assertTrue(h.level.getEntitiesOfClass(GroundFireEntity::class.java,elevated.boundingBox.inflate(5.0)).isEmpty(),"Air contact created floating or projected fire")
   elevated.discard();h.level.setBlock(base,Blocks.WATER.defaultBlockState(),3)
   laser(h,at,OrbitalLaserSettings(durationTicks=15))
  }
  h.runAfterDelay(25){
   h.assertTrue(h.level.getBlockState(base).`is`(Blocks.WATER),"Laser replaced water with ground fire")
   h.assertTrue(h.level.getEntitiesOfClass(GroundFireEntity::class.java,net.minecraft.world.phys.AABB(base).inflate(5.0)).isEmpty(),"Water contact spawned ground fire")
   h.level.removeBlock(base,false);h.level.removeBlock(base.below(),false);h.succeed()
  }
 }

}
