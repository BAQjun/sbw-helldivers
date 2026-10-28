package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.config.*
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.stratagem.*
import com.example.sbwhelldiver.effect.SmokeOcclusion
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.*
import java.util.UUID
import kotlin.math.abs

@GameTestHolder("sbw_helldiver_orbital_fields")
@PrefixGameTestTemplate(false)
object OrbitalFieldGameTests{
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_orbital_fields",template="sentry_test",batch="field_metadata",timeoutTicks=10)
 fun nativeDefaultsAndAreaSampling(h:GameTestHelper){
  val expected=listOf(Triple(15.0,300,1),Triple(13.0,300,1),Triple(12.0,600,6))
  for((index,id)in OrbitalFieldSettings.ids.withIndex()){
   val s=OrbitalFieldConfig.snapshot(id);val e=expected[index]
   h.assertTrue(s.radius==e.first&&s.lifetime==e.second&&s.shells==e.third&&s.direct==50f&&s.callTicks==40,"Wrong native/default settings $id: $s")
   h.assertTrue(OrbitalFieldSettings.load(s.save())==s&&BarrageSettings.load(s.delivery().save())==s.delivery(),"Settings lost on reload $id")
   h.assertTrue(Stratagems.get(id)!=null&&StratagemCooldownConfig.ticks(id)==1283,"Missing stratagem/cooldown $id")
   val plan=s.delivery();val points=OrbitalBarragePattern.offsets(52,plan,33f)
   h.assertTrue(points.size==e.third&&OrbitalBarragePattern.due(0.0,plan)==e.third&&plan.lastShotTime==0,"Not a simultaneous salvo $id")
   if(index<2)h.assertTrue(points.single()==Vec3.ZERO,"Single shell missed beacon")
  }
  val s=OrbitalFieldConfig.snapshot("orbital_smoke_strike").delivery()
  val points=(0L..999L).flatMap{OrbitalBarragePattern.offsets(it,s,0f)}
  val mean=points.sumOf{it.lengthSqr()}/points.size
  h.assertTrue(points.all{it.lengthSqr()<=225.0001}&&abs(mean-112.5)<5.0,"Smoke outside native spread or center-biased: $mean")
  h.assertTrue(!Stratagems.dimensionAllowed("minecraft:the_nether"),"Nether restrictions lost")
  h.succeed()
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_orbital_fields",template="sentry_test",batch="field_persistence",timeoutTicks=20)
 fun customLifetimeBoundsEffectsAndExpiry(h:GameTestHelper){
  val world=h.level;val at=Vec3.atCenterOf(h.absolutePos(BlockPos(3,20,3)))
  val zombie=EntityType.ZOMBIE.create(world)!!;zombie.setPos(at);zombie.isNoGravity=true;world.addFreshEntity(zombie)
  val far=EntityType.ZOMBIE.create(world)!!;far.setPos(at.add(13.1,0.0,0.0));far.isNoGravity=true;world.addFreshEntity(far)
  val field=MortarFieldEntity.create(world,at.add(0.0,-.1,0.0),MortarFieldEntity.Kind.EMS,13.0,lifetimeTicks=400,orbitalEms=true)
  field.tick();h.assertTrue(zombie.hasEffect(ModEffects.STUN.get())&&!far.hasEffect(ModEffects.STUN.get()),"EMS bounds/effect wrong")
  val high=EntityType.ZOMBIE.create(world)!!;high.setPos(field.position().add(0.0,4.9,0.0));high.isNoGravity=true;world.addFreshEntity(high)
  val above=EntityType.ZOMBIE.create(world)!!;above.setPos(field.position().add(0.0,5.1,0.0));above.isNoGravity=true;world.addFreshEntity(above)
  field.tick();h.assertTrue(field.fieldHeight==5.0&&high.hasEffect(ModEffects.STUN.get())&&!above.hasEffect(ModEffects.STUN.get()),"EMS five-block vertical bound wrong")
  val mortar=MortarFieldEntity.create(world,field.position(),MortarFieldEntity.Kind.EMS);high.removeEffect(ModEffects.STUN.get());mortar.tick()
  h.assertTrue(mortar.fieldHeight==5.0&&high.hasEffect(ModEffects.STUN.get())&&!above.hasEffect(ModEffects.STUN.get()),"Mortar EMS five-block bound wrong");mortar.discard();high.discard();above.discard()
  val t=CompoundTag();field.saveWithoutId(t);field.discard();val restored=ModEntities.MORTAR_FIELD.get().create(world)!!;restored.load(t)
  h.assertTrue(restored.orbitalEms&&restored.lifetimeTicks==400&&restored.remainingTicks==398,"Custom lifetime truncated to mortar's 140 ticks")
  t.putInt("Remaining",2);restored.load(t);repeat(3){restored.tick()};h.assertTrue(restored.isRemoved,"Field did not expire")
  t.remove("Lifetime");t.remove("OrbitalEms");t.putInt("Remaining",100);val legacy=ModEntities.MORTAR_FIELD.get().create(world)!!;legacy.load(t)
  h.assertTrue(!legacy.orbitalEms&&legacy.lifetimeTicks==140&&legacy.remainingTicks==100,"Legacy mortar save changed");legacy.discard()
  val gas=MortarFieldEntity.create(world,at.add(0.0,-.1,0.0),MortarFieldEntity.Kind.GAS,15.0,effectTicks=120)
  gas.tick();h.assertTrue(zombie.hasEffect(ModEffects.GAS.get())&&zombie.hasEffect(ModEffects.CONFUSION.get()),"Gas did not apply shared effects")
  gas.discard();zombie.discard();far.discard();h.succeed()
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_orbital_fields",template="sentry_test",batch="field_collision",timeoutTicks=30)
 fun realDirectHitNoBlastAndShellPersistence(h:GameTestHelper){
  val at=Vec3.atCenterOf(h.absolutePos(BlockPos(3,15,3)));val world=h.level
  world.getChunk(BlockPos.containing(at));world.setBlock(BlockPos.containing(at).below(),Blocks.BEDROCK.defaultBlockState(),3);h.runAfterDelay(5){
   val cow=EntityType.COW.create(world)!!;cow.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=200.0;cow.health=200f;cow.isNoGravity=true;cow.setPos(at);world.addFreshEntity(cow)
   val neighbor=EntityType.COW.create(world)!!;neighbor.isNoGravity=true;neighbor.setPos(at.add(2.5,0.0,0.0));world.addFreshEntity(neighbor)
   val s=OrbitalFieldConfig.snapshot("orbital_smoke_strike");val shell=ModEntities.ORBITAL_FIELD_SHELL.get().create(world)!!;shell.configureField(s)
   val tag=CompoundTag();shell.saveWithoutId(tag);val restored=ModEntities.ORBITAL_FIELD_SHELL.get().create(world)!!;restored.load(tag);shell.discard()
   h.assertTrue(restored.fieldSettings==s&&restored.getDamage()==50f&&restored.getExplosionDamage()==0f&&restored.getCustomGravity()==0f,"Shell settings lost")
   restored.setPos(at.add(0.0,3.0,0.0));restored.shoot(0.0,-1.0,0.0,2f,0f);world.addFreshEntity(restored)
   h.runAfterDelay(5){
    h.assertTrue(restored.isRemoved&&abs(cow.health-150f)<.01&&neighbor.health==neighbor.maxHealth,"Real direct hit or unwanted explosion: ${cow.health}, ${neighbor.health}")
    val fields=world.getEntitiesOfClass(MortarFieldEntity::class.java,cow.boundingBox.inflate(4.0));h.assertTrue(fields.size==1&&fields.single().kind==MortarFieldEntity.Kind.SMOKE,"Missing/duplicate collision field")
    fields.forEach{it.discard()};cow.discard();neighbor.discard();h.succeed()
   }
  }
 }
 private fun complete(h:GameTestHelper,id:String){
  val world=h.level;val at=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(15,155,15)))
  val player=FakePlayerFactory.get(world,GameProfile(UUID.randomUUID(),"FieldTest"));player.setPos(at)
  player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack(ModItems.BEACON.get()).also{b->BeaconData.ensureIdentity(b);BeaconData.setItems(b,listOf(net.minecraft.world.item.ItemStack(ModItems.ORBITAL_GAS_STRIKE.get())))})
  val now=StratagemCooldowns.now(player.server);h.assertTrue(SuperDestroyers.move(player,Stratagems.UP),"Test player could not call ship");SuperDestroyers.state(player).advance(now+60)
  val chunk=net.minecraft.world.level.ChunkPos(BlockPos.containing(at));val forced=mutableListOf<Pair<Int,Int>>()
  for(x in chunk.x-8..chunk.x+8)for(z in chunk.z-8..chunk.z+8)if(world.setChunkForced(x,z,true))forced.add(x to z)
  for(x in -20..20)for(z in -20..20)world.setBlock(BlockPos.containing(at.add(x.toDouble(),-1.0,z.toDouble())),Blocks.BEDROCK.defaultBlockState(),3)
  h.runAfterDelay(5){
   var marker=ModEntities.STRATAGEM_MARKER.get().create(world)!!;marker.setPos(at);marker.configure(Stratagems.get(id)!!.dropId,10,player.uuid);world.addFreshEntity(marker)
   val s=marker.fieldSettings;val positions=marker.barrageTargets();val start=world.gameTime
   h.assertTrue(positions.size==s.shells&&marker.orbitalOrigin!=null,"Missing ship/targets")
   val token=UUID.randomUUID();marker.managedRequest=token;val ledger=StratagemCooldowns.state(player);ledger.reserve(id,token,now);ledger.beginArrival(id,token,now,now+s.callTicks)
   var shots=0;val launchTimes=mutableSetOf<Long>()
   val listener=object{@net.minecraftforge.eventbus.api.SubscribeEvent fun join(e:net.minecraftforge.event.entity.EntityJoinLevelEvent){
    val shell=e.entity as? OrbitalFieldShellEntity?:return
    if(shell.level()===world&&shell.position().distanceTo(at)<130){shots++;launchTimes.add(world.gameTime);h.assertTrue(shell.fieldSettings==s,"Wrong payload snapshot")}
   }}
   net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(listener)
   h.runAfterDelay(35){h.assertTrue(shots==0&&marker.orbitalFiredAt<0,"Fired before call delay")}
   h.runAfterDelay(50){
    val t=CompoundTag();marker.saveWithoutId(t);marker.discard();marker=ModEntities.STRATAGEM_MARKER.get().create(world)!!;marker.load(t);world.addFreshEntity(marker)
    h.assertTrue(marker.barrageTargets()==positions&&marker.fieldSettings==s&&marker.orbitalFiredAt==start+40,"Flight/target changed on reload")
   }
   h.runAfterDelay(100){
    net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener)
    val fields=world.getEntitiesOfClass(MortarFieldEntity::class.java,marker.boundingBox.inflate(40.0))
    h.assertTrue(shots==s.shells&&launchTimes.size==1&&fields.size==s.shells,"Incomplete/duplicate salvo $id shots=$shots fields=${fields.size} times=$launchTimes")
    h.assertTrue(fields.all{it.orbitalEms==(s.kind==MortarFieldEntity.Kind.EMS)},"Lost orbital EMS audio profile")
    h.assertTrue(fields.all{it.kind==s.kind&&it.radius==s.radius&&it.lifetimeTicks==s.lifetime&&it.remainingTicks in 1 until s.lifetime},"Wrong area radius/duration $id")
    for(p in positions)h.assertTrue(fields.any{f->abs(f.x-p.x)<.1&&abs(f.z-p.z)<.1},"Actual impact missed planned target")
    h.assertTrue(marker.isRemoved&&marker.hasArrived,"Marker did not finish after impact")
    if(s.kind==MortarFieldEntity.Kind.SMOKE){
     val field=fields.first();h.assertTrue(SmokeOcclusion.intersects(field,field.position().add(-20.0,1.0,0.0),field.position().add(20.0,1.0,0.0)),"Orbital smoke does not occlude")
    }
    fields.forEach{it.discard()};forced.forEach{(x,z)->world.setChunkForced(x,z,false)};player.discard();h.succeed()
   }
  }
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_orbital_fields",template="sentry_test",batch="field_gas_full",timeoutTicks=160)
 fun gasCompleteAndReload(h:GameTestHelper)=complete(h,"orbital_gas_strike")
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_orbital_fields",template="sentry_test",batch="field_ems_full",timeoutTicks=160)
 fun emsCompleteAndReload(h:GameTestHelper)=complete(h,"orbital_ems_strike")
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_orbital_fields",template="sentry_test",batch="field_smoke_full",timeoutTicks=160)
 fun smokeSixCompleteAndReload(h:GameTestHelper)=complete(h,"orbital_smoke_strike")
}
