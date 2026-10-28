package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.*
import java.util.UUID

@GameTestHolder("sbw_helldiver_impact")
@PrefixGameTestTemplate(false)
object HellpodImpactGameTests {
 private fun point(h:GameTestHelper,dx:Int=0):Vec3 {
  val b=h.absolutePos(BlockPos(15+dx,200,15))
  for(cx in ((b.x-6) shr 4)..((b.x+6) shr 4))for(cz in ((b.z-6) shr 4)..((b.z+6) shr 4)){h.level.setChunkForced(cx,cz,true);h.level.getChunk(cx,cz)}
  for(x in -5..5)for(z in -5..5)h.level.setBlock(b.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2)
  return Vec3(b.x+.5,b.y.toDouble(),b.z+.5)
 }
 private fun mob(h:GameTestHelper,p:Vec3)=EntityType.HUSK.create(h.level)!!.also {
  it.setPos(p);it.setPersistenceRequired();it.isNoAi=true;it.isNoGravity=true;it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=1000.0;it.health=1000f
  it.getAttribute(Attributes.ARMOR)!!.baseValue=30.0;it.addEffect(MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,1000,4));h.level.addFreshEntity(it)
 }
 private fun falling(h:GameTestHelper,p:Vec3,type:EntityType<out AbstractHellpodEntity>):AbstractHellpodEntity {
  val g=type.create(h.level)!!;g.setPos(p.add(0.0,100.0,0.0));g.scheduleArrival(p.y,h.level.gameTime+25);h.level.addFreshEntity(g);return g
 }
 @JvmStatic @GameTest(template="sentry_test",batch="hellpod_impact",timeoutTicks=50)
 fun everyPodCrushesOnceWithNoSplash(h:GameTestHelper){
  val types=listOf(ModEntities.MACHINE_GUN_SENTRY.get(),ModEntities.GATLING_SENTRY.get(),ModEntities.FLAME_SENTRY.get(),ModEntities.LASER_SENTRY.get(),ModEntities.HMG_EMPLACEMENT.get(),ModEntities.GRENADIER_BATTLEMENT.get(),ModEntities.SHIELD_GENERATOR.get(),ModEntities.SUPPLY_HELLPOD.get(),ModEntities.PLAYER_HELLPOD.get())
  val cases=types.mapIndexed { i,t ->val p=point(h,i*16);val victim=mob(h,p);val outside=mob(h,p.add(3.2,0.0,0.0));victim.hurt(h.level.damageSources().generic(),1f);Triple(falling(h,p,t),victim,outside)}
  h.runAfterDelay(30){
   for((g,v,outside)in cases){h.assertTrue(g.phase!=AbstractHellpodEntity.Phase.FALLING,"Pod did not land");h.assertTrue(v.health==600f,"Landing must apply exactly 400 once: ${g.type} ${v.health} removed=${v.isRemoved} lookup=${h.level.getEntity(v.id)===v} pos=${v.position()} nearby=${h.level.getEntities(g,g.boundingBox.inflate(4.0)).map{it.type}}");h.assertTrue(outside.health==1000f,"Crush became radial splash");listOf(g,v,outside).forEach{it.discard()}}
   h.succeed()
  }
 }
 @JvmStatic @GameTest(template="sentry_test",batch="hellpod_overlap",timeoutTicks=50)
 fun crushDestroysLowerFuelPodAndWallColliders(h:GameTestHelper){
  val p=point(h);val lower=ModEntities.FLAME_SENTRY.get().create(h.level)!!;lower.setPos(p)
  val tag=CompoundTag();lower.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal);tag.putDouble("HellpodRestY",p.y);tag.putBoolean("HellpodCapReleased",true);lower.load(tag);h.level.addFreshEntity(lower)
  val incoming=falling(h,p,ModEntities.LASER_SENTRY.get())
  val wp=point(h,24);val wall=ModEntities.GRENADIER_BATTLEMENT.get().create(h.level)!!;wall.setPos(wp);val wt=CompoundTag();wall.saveWithoutId(wt);wt.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal);wt.putDouble("HellpodRestY",wp.y);wt.putBoolean("HellpodCapReleased",true);wall.load(wt);h.level.addFreshEntity(wall)
  val panel=ModEntities.GRENADIER_WALL_PANEL.get().create(h.level)!!;panel.bind(wall,1);h.level.addFreshEntity(panel)
  val replacement=falling(h,wp.add(2.4,0.0,0.0),ModEntities.SUPPLY_HELLPOD.get())
  h.runAfterDelay(30){
   h.assertTrue(lower.isRemoved&&wall.isRemoved&&panel.isRemoved,"Lower pod or wall collider survived overlap: lower=${lower.isRemoved}/${lower.position()} wall=${wall.isRemoved}/${wall.position()} panel=${panel.isRemoved} incoming=${incoming.phase}/${incoming.position()} replacement=${replacement.phase}/${replacement.position()}")
   h.assertTrue(!incoming.isRemoved&&!replacement.isRemoved,"Incoming pod was removed")
   val fields=h.level.getEntitiesOfClass(GroundFireEntity::class.java,incoming.boundingBox.inflate(5.0));h.assertTrue(fields.count{it.radius in 3.0..4.0}==1,"Fuel destruction did not ignite exactly once")
   (fields+listOf(incoming,replacement)).forEach{it.discard()};h.succeed()
  }
 }
 @JvmStatic @GameTest(template="sentry_test",batch="hellpod_passenger",timeoutTicks=500)
 fun transportCrushesEnemyWithoutHurtingItsPassenger(h:GameTestHelper){
  val p=point(h)
  val user=object:net.minecraftforge.common.util.FakePlayer(h.level,GameProfile(UUID.randomUUID(),"ImpactRider")){
   override fun teleportTo(l:ServerLevel,x:Double,y:Double,z:Double,yaw:Float,pitch:Float){moveTo(x,y,z,yaw,pitch)}
  };user.setPos(p);h.level.addFreshEntity(user)
  val transport=ModEntities.PLAYER_HELLPOD.get().create(h.level)!!;h.assertTrue(transport.launch(user,p),"Cannot mount transport");transport.scheduleArrival(p.y,h.level.gameTime+PlayerHellpodEntity.PLAYER_FALL_TICKS)
  val victim=mob(h,p)
  h.runAfterDelay(135){h.assertTrue(user.isAlive&&user.health==20f&&user.vehicle===transport,"Own passenger was crushed");h.assertTrue(victim.health==600f,"Player pod did not crush enemy: hp=${victim.health} phase=${transport.phase} pod=${transport.position()} victim=${victim.position()} landing=${transport.landingY} box=${victim.boundingBox} root=${victim.rootVehicle.type} removed=${victim.isRemoved}");transport.release();listOf(transport,user,victim).forEach{it.discard()};h.succeed()}
 }
}
