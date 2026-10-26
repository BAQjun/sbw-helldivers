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
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",batch="precision_flight",timeoutTicks=125)
 fun countdownThenFlightKeepsBeaconUntilImpact(h:GameTestHelper){
  val p=pilot(h);val world=h.level;val now=StratagemCooldowns.now(p.server)
  SuperDestroyers.move(p,Stratagems.UP);SuperDestroyers.state(p).advance(now+60)
  val target=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(15,3,15)))
  // A fake player does not load the terminal flight corridor like a real player.
  val loaded=mutableListOf<Pair<Int,Int>>()
  val chunk=net.minecraft.world.level.ChunkPos(BlockPos.containing(target))
  for(cx in chunk.x-6..chunk.x+6)for(cz in chunk.z-6..chunk.z+6)if(world.setChunkForced(cx,cz,true))loaded.add(cx to cz)
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
   h.assertTrue(marker.isRemoved&&marker.podUUID!=null,"Terminal shell failed: removed=${marker.isRemoved} pod=${marker.podUUID} origin=${marker.orbitalOrigin} target=$target now=${world.gameTime} fired=${marker.orbitalFiredAt} projectile=${marker.podUUID?.let(world::getEntity)?.let{ it.position().toString()+" tick="+it.tickCount+" velocity="+it.deltaMovement }}")
   h.assertTrue(world.getEntity(marker.podUUID!!)==null,"Terminal shell survived impact")
   loaded.forEach{(x,z)->world.setChunkForced(x,z,false)};p.discard();h.succeed()
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

 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",timeoutTicks=5)
 fun warpFlightStopsAtAnchorAndNeverDuplicatesRelocation(h:GameTestHelper){
  val dim="minecraft:overworld";val center=Vec3(12.0,64.0,30.0)
  for(direction in 0..3){
   val a=DestroyerAnchor.at(dim,center,direction,700.0,900.0);val f=DestroyerWarp.forward(direction)
   h.assertTrue(f.dot(center.subtract(a.position))>0,"Warp flew backward relative to the mesh")
   val state=DestroyerState();state.schedule(a,0)
   h.assertTrue(DestroyerWarp.poses(state,dim,56.99).isEmpty(),"Arrival appeared before the flight window")
   val start=DestroyerWarp.poses(state,dim,57.0).single().position
   val mid=DestroyerWarp.poses(state,dim,58.5).single().position
   val fractional=DestroyerWarp.poses(state,dim,58.575).single().position
   h.assertTrue(start==a.position.subtract(f.scale(8000.0))&&mid==a.position.subtract(f.scale(4000.0)),"Incorrect arrival path")
   h.assertTrue(abs(fractional.distanceTo(mid)-200.0)<1e-6,"Warp ignored partial-frame timing")
   h.assertTrue(DestroyerWarp.poses(DestroyerState.load(state.save()),dim,58.575)==DestroyerWarp.poses(state,dim,58.575),"Pending warp changed on reload")
   h.assertTrue(DestroyerWarp.poses(state,dim,60.0).single().position==a.position&&DestroyerWarp.poses(state,dim,65.0).single().position==a.position,"Delayed snapshot caused drift after arrival")
   state.advance(60);h.assertTrue(DestroyerWarp.poses(state,dim,100.0).single().position==a.position,"Stable ship restarted its warp")
   val next=DestroyerAnchor.at(dim,center.add(200.0,0.0,100.0),(direction+1)%4,700.0,900.0)
   state.schedule(next,100)
   for(t in listOf(154.0,155.0,156.5,156.99,157.0,158.5,159.99,160.0,165.0))
    h.assertTrue(DestroyerWarp.poses(state,dim,t).size==1,"Relocation duplicated the ship or introduced a snapshot gap at $t")
   h.assertTrue(DestroyerWarp.poses(state,dim,156.0).single().position==a.position,"Relocation left current anchor before the direct jump")
   val middle=DestroyerWarp.poses(state,dim,158.5).single()
   h.assertTrue(middle.position.distanceTo(a.position.lerp(next.position,.5))<1e-8,"Relocation took an offscreen departure/arrival detour")
   h.assertTrue(middle.motion.dot(next.position.subtract(a.position))>0,"Relocation trail points away from actual movement")
   state.advance(160);state.schedule(null,200)
   h.assertTrue(DestroyerWarp.poses(state,dim,258.0).single().position==next.position,"Ship left before departure window")
   h.assertTrue(DestroyerWarp.poses(state,dim,260.0).isEmpty()&&DestroyerWarp.poses(state,dim,265.0).isEmpty(),"Departed ship reappeared while waiting for snapshot")
   h.assertTrue(DestroyerWarp.poses(state,"minecraft:the_end",255.0).isEmpty()&&DestroyerWarp.poses(state,"minecraft:the_nether",255.0).isEmpty(),"Warp leaked across dimensions")
  }
  h.succeed()
 }

 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",timeoutTicks=5)
 fun gatlingCadenceWalkAndUpgradedSettings(h:GameTestHelper){
  val s=com.example.sbwhelldiver.config.OrbitalGatlingConfig.snapshot()
  h.assertTrue(s.total==300&&s.interval==.045&&abs(s.lastShotTime-269.1)<1e-8,"Wrong continuous cadence")
  h.assertTrue((0 until s.total).count{OrbitalGatlingPattern.highExplosive(it)}==75,"Wrong HE mix")
  for(boundary in listOf(60,120,180,240)){
   h.assertTrue(abs(s.shotTime(boundary)-s.shotTime(boundary-1)-.9)<1e-8,"Added pause between salvos")
  }
  h.assertTrue(OrbitalGatlingPattern.due(-.001,s)==0&&OrbitalGatlingPattern.due(269.099,s)==299&&OrbitalGatlingPattern.due(269.1,s)==300,"Cadence rounded to one tick or lost last shot")
  for(seed in 0L..32L){
   val a=OrbitalGatlingPattern.offsets(seed,s);val b=OrbitalGatlingPattern.offsets(seed,s)
   h.assertTrue(a==b&&a.size==300&&a.first().length()<=1.0&&a.all{it.horizontalDistance()<=7.000001},"Walk outside native-derived region or nondeterministic")
   h.assertTrue(a.maxOf{it.x}-a.minOf{it.x}>5&&a.maxOf{it.z}-a.minOf{it.z}>5,"Walk stayed at one impact point")
  }
  h.assertTrue(Stratagems.get("orbital_gatling_barrage")!!.code==listOf(1,2,3,0,0)&&StratagemCooldownConfig.ticks("orbital_gatling_barrage")==1197,"Code or full-upgrade cooldown incorrect")
  h.succeed()
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",timeoutTicks=5)
 fun gatlingDirectAndHighExplosiveFalloff(h:GameTestHelper){
  val at=Vec3.atCenterOf(h.absolutePos(BlockPos(15,12,15)))
  fun victim(distance:Double)=EntityType.RAVAGER.create(h.level)!!.also{m->m.isNoAi=true;m.isNoGravity=true;m.getAttribute(Attributes.ARMOR)!!.baseValue=0.0;m.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=1000.0;m.health=1000f;m.setPos(at.add(distance+m.bbWidth/2,0.0,0.0));h.level.addFreshEntity(m)}
  val ordinary=ModEntities.ORBITAL_GATLING_ROUND.get().create(h.level)!!;ordinary.configure(com.example.sbwhelldiver.config.GatlingSettings(),false);ordinary.setPos(at);ordinary.tickCount=1;h.level.addFreshEntity(ordinary)
  val direct=victim(0.0);ordinary.onHitEntity(ExtendedEntityRayTraceResult(EntityResult(direct,at,false,false)))
  h.assertTrue(abs(1000-direct.health-30)<.01&&ordinary.isRemoved,"Regular round did not deal exactly 30 direct damage")
  direct.discard()
  val shell=ModEntities.ORBITAL_GATLING_ROUND.get().create(h.level)!!;shell.configure(com.example.sbwhelldiver.config.GatlingSettings(),true);shell.setPos(at);shell.tickCount=1;h.level.addFreshEntity(shell)
  val targets=listOf(0.0,1.5,3.0,4.5,4.6).map(::victim)
  shell.onHitEntity(ExtendedEntityRayTraceResult(EntityResult(targets[0],at,false,false)))
  targets.zip(listOf(90f,60f,29.5f,0f,0f)).forEachIndexed{i,(m,d)->h.assertTrue(abs(1000-m.health-d)<.03,"HE damage $i expected $d got ${1000-m.health}")}
  val health=targets.map{it.health};shell.causeExplode(at);h.assertTrue(health==targets.map{it.health},"Duplicate HE explosion")
  val saved=CompoundTag();shell.saveWithoutId(saved);val restored=ModEntities.ORBITAL_GATLING_ROUND.get().create(h.level)!!;restored.load(saved)
  h.assertTrue(restored.getDamage()==30f&&restored.getExplosionDamage()==60f&&restored.getExplosionRadius()==4.5f,"Shell settings lost on save")
  targets.forEach{it.discard()};restored.discard();h.succeed()
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",batch="gatling_full",timeoutTicks=365)
 fun gatlingCompletesAllRoundsAndSurvivesMidBarrageReload(h:GameTestHelper){
  val p=pilot(h);val world=h.level;val now=StratagemCooldowns.now(p.server)
  SuperDestroyers.move(p,Stratagems.UP);SuperDestroyers.state(p).advance(now+60)
  val at=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(15,3,15)))
  val loaded=mutableListOf<Pair<Int,Int>>()
  val chunk=net.minecraft.world.level.ChunkPos(BlockPos.containing(at))
  for(cx in chunk.x-6..chunk.x+6)for(cz in chunk.z-6..chunk.z+6)if(world.setChunkForced(cx,cz,true))loaded.add(cx to cz)
  for(x in -9..9)for(z in -9..9)world.setBlock(BlockPos.containing(at.add(x.toDouble(),-1.0,z.toDouble())),net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(),3)
  var marker=ModEntities.STRATAGEM_MARKER.get().create(world)!!;marker.setPos(at);marker.configure(Stratagems.get("orbital_gatling_barrage")!!.dropId,10,p.uuid);world.addFreshEntity(marker)
  h.assertTrue(marker.duration==40,"Native two-second firing delay missing")
  h.runAfterDelay(38){h.assertTrue(marker.orbitalFiredAt<0,"Barrage fired early")}
  val seen=mutableMapOf<UUID,Boolean>()
  val observer=object {
   @net.minecraftforge.eventbus.api.SubscribeEvent fun join(e:net.minecraftforge.event.entity.EntityJoinLevelEvent){
    val shell=e.entity as? OrbitalGatlingRoundEntity?:return
    if(shell.level()===world && shell.position().distanceToSqr(at)<200*200)seen[shell.uuid]=shell.getExplosionDamage()>0
   }
  }
  net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(observer)
  var last=0;var finished=0;var maxActive=0
  h.onEachTick{
   val tag=CompoundTag();marker.saveWithoutId(tag);val n=tag.getInt("GatlingNextShot")
   h.assertTrue(n>=last&&n-last<=2,"Barrage duplicated/dropped cadence on an ordinary server tick")
   last=n;if(n==300)finished++
   maxActive=maxOf(maxActive,world.getEntitiesOfClass(OrbitalGatlingRoundEntity::class.java,marker.boundingBox.inflate(140.0)).size)
  }
  h.runAfterDelay(160){
   h.assertTrue(!marker.isRemoved&&last>60&&last<180,"Barrage ended before all salvos")
   val targets=marker.gatlingTargets();val saved=CompoundTag();marker.saveWithoutId(saved);marker.discard()
   marker=ModEntities.STRATAGEM_MARKER.get().create(world)!!;marker.load(saved);world.addFreshEntity(marker)
   h.assertTrue(targets==marker.gatlingTargets()&&marker.gatlingSettings==com.example.sbwhelldiver.config.GatlingSettings.load(saved.getCompound("GatlingSettings")),"Reload changed target walk or settings")
  }
  h.runAfterDelay(350){
   h.assertTrue(last==300&&finished>0&&marker.isRemoved,"Last shells never fired or beacon ended too early")
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(observer)
   h.assertTrue(seen.size==300&&seen.values.count{it}==75,"Actual shell spawns or HE ratio wrong: ${seen.size}/${seen.values.count{it}}")
   h.assertTrue(maxActive in 1..12,"Barrage accumulated projectile entities: $maxActive")
   loaded.forEach{(x,z)->world.setChunkForced(x,z,false)};p.discard();h.succeed()
  }
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",timeoutTicks=12)
 fun redBeaconBypassesClearanceWhileHellpodsStillCheck(h:GameTestHelper){
  val world=h.level
  val balls=mutableListOf<StratagemBeacon>()
  for((index,id) in listOf("eagle_airstrike","machine_gun_sentry").withIndex()){
   val at=h.absolutePos(BlockPos(7+index*14,128,15))
   for(x in -2..2)for(z in -2..2)world.setBlock(at.offset(x,0,z),net.minecraft.world.level.block.Blocks.BEDROCK.defaultBlockState(),3)
   world.setBlock(at.offset(-1,4,-1),net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState(),3)
   val stack=ItemStack(ModItems.BEACON.get());val def=Stratagems.get(id)!!
   BeaconData.setItems(stack,listOf(ItemStack(net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(def.itemId)!!)));BeaconData.activate(stack,def)
   val ball=ModEntities.STRATAGEM_BEACON.get().create(world)!!;ball.setItem(stack);ball.setPos(at.x+.5,at.y+1.3,at.z+.5);ball.deltaMovement=Vec3(0.0,-.4,0.0);world.addFreshEntity(ball);balls.add(ball)
  }
  h.runAfterDelay(5){
   h.assertTrue(balls[0].isRemoved&&!balls[1].isRemoved&&!balls[1].deploymentFailed,"Red clearance bypass was missing or also affected hellpod stratagems")
   val markers=world.getEntitiesOfClass(StratagemMarker::class.java,balls[0].boundingBox.inflate(3.0))
   h.assertTrue(markers.size==1&&markers.single().stratagem?.id=="eagle_airstrike","Red beacon did not deploy exactly once")
   markers.forEach{m->m.podUUID?.let(world::getEntity)?.discard();m.discard()};balls.forEach{it.discard()};h.succeed()
  }
 }

}
