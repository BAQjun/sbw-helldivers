package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.stratagem.*
import com.example.sbwhelldiver.config.StratagemCooldownConfig
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import com.mojang.authlib.GameProfile
import net.minecraft.gametest.framework.*
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.*
import com.atsuishio.superbwarfare.world.phys.*
import java.util.UUID
import kotlin.math.abs

@GameTestHolder("sbw_helldiver_orbital")
@PrefixGameTestTemplate(false)
object OrbitalGameTests {
 private fun pilot(h:GameTestHelper)=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"OrbitalTest")).also{
  it.inventory.clearContent();it.inventory.selected=0
  it.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(ModItems.BEACON.get()).also{s->BeaconData.ensureIdentity(s);BeaconData.setItems(s,listOf(ItemStack(ModItems.ORBITAL_PRECISION_STRIKE.get())))})
  val at=h.absolutePos(BlockPos(15,8,15));it.setPos(Vec3.atCenterOf(at))
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",timeoutTicks=5)
 fun bearingsRangeDelayAndPersistence(h:GameTestHelper){
  val center=Vec3(12.0,64.0,30.0)
  val expected=listOf(Vec3(12.0,964.0,-670.0),Vec3(712.0,964.0,30.0),Vec3(12.0,964.0,730.0),Vec3(-688.0,964.0,30.0))
  for(i in 0..3){
   val a=DestroyerAnchor.at("minecraft:overworld",center,i,700.0,900.0)
   h.assertTrue(a.position==expected[i],"Wrong cardinal mapping $i")
   h.assertTrue(a.covers(a.dimension,center.add(1024.0,800.0,0.0),1024.0),"Range boundary or horizontal coverage wrong")
   h.assertTrue(!a.covers(a.dimension,center.add(1024.01,0.0,0.0),1024.0)&&!a.covers("minecraft:the_nether",center,1024.0),"Out-of-range/dimension accepted")
   val s=DestroyerState();h.assertTrue(s.schedule(a,100),"Call rejected")
   h.assertTrue(!s.advance(159)&&!s.available(a.dimension,center,1024.0,159),"Warp completed early")
   val restored=DestroyerState.load(s.save());h.assertTrue(restored.advance(160)&&restored.anchor==a,"Pending call lost on reload")
   h.assertTrue(restored.anchor!!.position==expected[i],"Ship followed caller")
   h.assertTrue(restored.schedule(null,200)&&!restored.schedule(a,201),"Duplicate transition accepted")
   val departing=DestroyerState.load(restored.save());h.assertTrue(departing.advance(260)&&departing.anchor==null,"Departure failed after reload")
  };h.succeed()
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",timeoutTicks=5)
 fun callInputDirectionRelocationAndDeparture(h:GameTestHelper){
  val p=pilot(h);val stack=p.mainHandItem;val id=BeaconData.identity(stack)!!
  fun input(code:List<Int>){BeaconInput.reset(p.uuid);code.forEach{BeaconInput.accept(p,InteractionHand.MAIN_HAND,id,it)}}
  input(Stratagems.destroyerCall.code)
  h.assertTrue(BeaconInput.menuStage(p.uuid)==1&&BeaconData.selected(stack)==null,"Call did not open direction choices")
  BeaconInput.accept(p,InteractionHand.MAIN_HAND,id,Stratagems.LEFT)
  val state=SuperDestroyers.state(p);val now=StratagemCooldowns.now(p.server)
  h.assertTrue(state.destination?.direction==Stratagems.LEFT&&state.transitionEnd==now+60,"Direction was not a single choice or delay wrong")
  state.advance(now+60)
  input(Stratagems.destroyerCall.code);h.assertTrue(BeaconInput.menuStage(p.uuid)==2,"Existing ship did not open management")
  BeaconInput.accept(p,InteractionHand.MAIN_HAND,id,Stratagems.RIGHT)
  h.assertTrue(BeaconInput.menuStage(p.uuid)==1,"Bearing option did not open compass")
  p.setPos(p.position().add(2000.0,0.0,0.0));val newCenter=p.position()
  BeaconInput.accept(p,InteractionHand.MAIN_HAND,id,Stratagems.DOWN)
  h.assertTrue(state.destination?.center==newCenter&&state.destination?.direction==Stratagems.DOWN,"Relocation did not use current caller position")
  state.advance(now+60)
  input(Stratagems.destroyerCall.code);BeaconInput.accept(p,InteractionHand.MAIN_HAND,id,Stratagems.LEFT)
  h.assertTrue(state.transitionEnd==now+60&&state.destination==null,"Departure command rejected")
  state.advance(now+60);h.assertTrue(state.anchor==null,"Departure retained ship")
  BeaconInput.logout(p.uuid);p.discard();h.succeed()
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",timeoutTicks=5)
 fun unavailableGateOwnershipAndNativeCodes(h:GameTestHelper){
  val p=pilot(h);val other=pilot(h);val now=StratagemCooldowns.now(p.server)
  StratagemCooldowns.setEnabled(p,false)
  h.assertTrue(!StratagemCooldowns.available(p,"orbital_precision_strike")&&!StratagemCooldowns.reserve(p,"orbital_precision_strike",UUID.randomUUID()),"Cooldown bypass also bypassed missing ship")
  h.assertTrue(SuperDestroyers.move(p,Stratagems.UP),"Call failed");SuperDestroyers.state(p).advance(now+60)
  h.assertTrue(StratagemCooldowns.available(p,"orbital_precision_strike")&&!SuperDestroyers.available(other),"Ship ownership incorrect")
  val fixed=SuperDestroyers.state(p).anchor!!
  p.setPos(p.position().add(1025.0,0.0,0.0));h.assertTrue(!SuperDestroyers.available(p)&&SuperDestroyers.state(p).anchor==fixed,"Ship followed player or ignored range")
  h.assertTrue(Stratagems.get("orbital_precision_strike")!!.code==listOf(1,1,0)&&StratagemCooldownConfig.ticks("orbital_precision_strike")==1368,"Native code/upgraded cooldown incorrect")
  val rows=Stratagems.commands(BeaconData.loadout(p.mainHandItem));h.assertTrue(rows.first()==Stratagems.destroyerCall&&Stratagems.eagleRearm !in rows,"Orbital incorrectly treated as Eagle")
  SuperDestroyers.state(p).schedule(null,now);SuperDestroyers.state(p).advance(now+60)
  StratagemCooldowns.setEnabled(p,true);p.discard();other.discard();h.succeed()
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",timeoutTicks=5)
 fun precisionDirectAndBlastDamage(h:GameTestHelper){
  val origin=Vec3.atCenterOf(h.absolutePos(BlockPos(15,12,15)))
  val shell=ModEntities.ORBITAL_SHELL.get().create(h.level)!!;shell.setPos(origin);shell.tickCount=1;h.level.addFreshEntity(shell)
  h.assertTrue(shell.getDamage()==640f&&shell.getExplosionDamage()==240f&&shell.getExplosionRadius()==12f,"Wrong precision shell values")
  fun victim(distance:Double)=EntityType.RAVAGER.create(h.level)!!.also{m->m.isNoAi=true;m.isNoGravity=true;m.getAttribute(Attributes.ARMOR)!!.baseValue=0.0;m.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=1000.0;m.health=1000f;m.setPos(origin.add(distance+m.bbWidth/2,0.0,0.0));h.level.addFreshEntity(m)}
  val victims=listOf(0.0,4.0,8.0,12.0,12.1).map(::victim)
  shell.onHitEntity(ExtendedEntityRayTraceResult(EntityResult(victims[0],origin,false,false)))
  victims.zip(listOf(880f,240f,119.5f,0f,0f)).forEachIndexed{i,(m,d)->h.assertTrue(abs(1000-m.health-d)<.02,"Precision victim $i expected $d got ${1000-m.health}")}
  val after=victims.map{it.health};shell.causeExplode(origin);h.assertTrue(after==victims.map{it.health},"Duplicate blast")
  victims.forEach{it.discard()};h.succeed()
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",timeoutTicks=5)
 fun spatialWarpBearingAndMinimumSoundReach(h:GameTestHelper){
  val eye=Vec3(20.0,70.0,30.0)
  for(offset in listOf(Vec3(700.0,900.0,0.0),Vec3(-700.0,900.0,0.0),Vec3(0.0,900.0,700.0),Vec3(0.0,900.0,-700.0))){
   val projected=OrbitalAudioPosition.project(eye,eye.add(offset),horizon=true).subtract(eye)
   h.assertTrue(abs(projected.length()-24.0)<1e-6,"Warp cue outside audible projection")
   h.assertTrue(projected.horizontalDistance()>20.0&&projected.x*offset.z-projected.z*offset.x==0.0&&projected.dot(offset)>0,"Warp cue lost horizontal bearing")
  }
  ModSounds.EVENTS.forEach{(key,event)->h.assertTrue(event.get().getRange(1f)>=64f,"Sound below 64 blocks: $key")}
  h.assertTrue(ModSounds.EVENTS.getValue("sfx_orbital_incoming").get().getRange(1f)==128f,"Incoming shell range lost")
  h.succeed()
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",timeoutTicks=125)
 fun countdownThenFlightKeepsBeaconUntilImpact(h:GameTestHelper){
  val p=pilot(h);val world=h.level;val now=StratagemCooldowns.now(p.server)
  SuperDestroyers.move(p,Stratagems.UP);SuperDestroyers.state(p).advance(now+60)
  val target=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(15,3,15)))
  for(x in -2..2)for(z in -2..2)world.setBlock(net.minecraft.core.BlockPos.containing(target.add(x.toDouble(),-1.0,z.toDouble())),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),3)
  val marker=ModEntities.STRATAGEM_MARKER.get().create(world)!!;marker.setPos(target)
  marker.configure(Stratagems.get("orbital_precision_strike")!!.dropId,10,p.uuid,groundY=target.y)
  h.assertTrue(marker.duration==60&&marker.orbitalFlightTicks==30&&marker.orbitalOrigin!=null,"Firing/flight defaults incorrect")
  world.addFreshEntity(marker)
  h.runAfterDelay(58){h.assertTrue(marker.orbitalFiredAt<0&&marker.orbitalProgress()<0&&marker.podUUID==null,"Orbital shell fired before countdown zero")}
  h.runAfterDelay(65){
   h.assertTrue(marker.orbitalFiredAt-marker.callStartedAt==60L&&!marker.isRemoved&&marker.podUUID==null,"Firing did not start at three seconds or skipped shell flight")
   val saved=CompoundTag();marker.saveWithoutId(saved)
   val restored=ModEntities.STRATAGEM_MARKER.get().create(world)!!;restored.load(saved)
   h.assertTrue(restored.orbitalFiredAt==marker.orbitalFiredAt&&restored.orbitalImpactAt==marker.orbitalImpactAt&&restored.orbitalOrigin==marker.orbitalOrigin,"Midflight reload reset shot timing")
   restored.discard()
  }
  h.runAfterDelay(83){h.assertTrue(!marker.isRemoved&&marker.orbitalProgress() in .65.. .9,"Beacon disappeared during flight")}
  h.runAfterDelay(103){
   h.assertTrue(marker.isRemoved&&marker.podUUID!=null,"Terminal shell never hit or beacon remained after impact")
   h.assertTrue(world.getEntity(marker.podUUID!!)==null,"Terminal shell survived impact")
   p.discard();h.succeed()
  }
 }

 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",timeoutTicks=10)
 fun netherBlocksEveryStratagemAndPreactivatedBeacons(h:GameTestHelper){
  val nether=h.level.server.getLevel(net.minecraft.world.level.Level.NETHER)!!
  val p=FakePlayerFactory.get(nether,GameProfile(UUID.randomUUID(),"NetherStratTest"))
  p.inventory.clearContent();p.inventory.selected=0;p.setPos(0.5,80.0,0.5)
  val beacon=ItemStack(ModItems.BEACON.get());BeaconData.ensureIdentity(beacon)
  BeaconData.setItems(beacon,listOf(ItemStack(ModItems.ORBITAL_PRECISION_STRIKE.get())))
  p.setItemInHand(InteractionHand.MAIN_HAND,beacon)
  val id=BeaconData.identity(beacon)!!
  val all=Stratagems.definitions+listOf(Stratagems.destroyerCall,Stratagems.eagleRearm)
  for(enabled in listOf(true,false)){
   StratagemCooldowns.setEnabled(p,enabled)
   for(def in all){
    h.assertTrue(!StratagemCooldowns.available(p,def.id),"Nether accepted availability: ${def.id} cooldown=$enabled")
    h.assertTrue(!StratagemCooldowns.reserve(p,def.id,UUID.randomUUID()),"Nether reserved: ${def.id}")
   }
   h.assertTrue(!SuperDestroyers.move(p,Stratagems.UP)&&SuperDestroyers.state(p).destination==null,"Nether scheduled a ship")
   h.assertTrue(!StratagemCooldowns.rearm(p),"Nether allowed rearming")
   Stratagems.destroyerCall.code.forEach{BeaconInput.accept(p,InteractionHand.MAIN_HAND,id,it)}
   h.assertTrue(BeaconInput.menuStage(p.uuid)==0,"Nether opened ship menu")
   val def=Stratagems.get("machine_gun_sentry")!!
   BeaconData.setItems(beacon,listOf(ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(def.itemId)!!)))
   def.code.forEach{BeaconInput.accept(p,InteractionHand.MAIN_HAND,id,it)}
   h.assertTrue(BeaconData.selected(beacon)==null,"Nether accepted beacon input")
   BeaconData.activate(beacon,def)
   h.assertTrue(!(beacon.item as com.example.sbwhelldiver.item.BeaconItem).throwActivated(p,InteractionHand.MAIN_HAND,id),"Nether threw a preactivated beacon")
   BeaconData.clear(beacon)
  }
  val state=DestroyerState();state.schedule(DestroyerAnchor.at("minecraft:the_nether",p.position(),0,700.0,900.0),0);state.advance(60)
  h.assertTrue(!state.available("minecraft:the_nether",p.position(),1024.0,60),"Legacy Nether ship remained usable")
  h.assertTrue(Stratagems.dimensionAllowed("minecraft:overworld")&&Stratagems.dimensionAllowed("minecraft:the_end"),"Other dimensions were blocked")
  val marker=ModEntities.STRATAGEM_MARKER.get().create(nether)!!;marker.setPos(p.position());marker.configure(Stratagems.get("machine_gun_sentry")!!.dropId,20,p.uuid);marker.tick()
  h.assertTrue(marker.isRemoved&&marker.podUUID==null,"Nether marker spawned a pod")
  BeaconInput.logout(p.uuid);StratagemCooldowns.setEnabled(p,true);p.discard();h.succeed()
 }

}
