package com.example.sbwhelldiver.gametest
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.projectile.Arrow
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import net.minecraftforge.common.util.FakePlayerFactory
import com.mojang.authlib.GameProfile
import java.util.UUID
import kotlin.math.abs
@GameTestHolder("sbw_helldiver_shield")
@PrefixGameTestTemplate(false)
object ShieldGameTests {
 @JvmStatic @GameTest(template="shield_test",batch="shield_burst_registry",timeoutTicks=20)
 fun simultaneousNativeRoundsAreAllIntercepted(h:GameTestHelper){
  val g=setup(h)
  val rounds=(0 until 20).map{i->ModEntities.SENTRY_BULLET.get().create(h.level)!!.also {
   it.setPos(g.shieldCenter().add(-20.0,0.0,(i-10)*.025));it.setDamage(1f);it.setCustomGravity(0f)
   it.shoot(1.0,0.0,0.0,40f,0f);h.level.addFreshEntity(it)
  }}
  h.runAtTickTime(2){
   h.assertTrue(rounds.all {it.isRemoved},"Removing a shielded round skipped another round in the same tick")
   h.assertTrue(g.shieldHealth in 615f..630f,"Burst interception did not consume the expected shield pool")
   g.discard();h.succeed()
  }
 }
 @JvmStatic @GameTest(template="shield_test",batch="shield_registry",timeoutTicks=35)
 fun interceptionTracksJoinedRemovedAndReloadedGenerators(h:GameTestHelper){
  val first=setup(h)
  fun arrow(g:ShieldGeneratorEntity)=Arrow(EntityType.ARROW,h.level).also{it.setPos(g.shieldCenter().add(ShieldGeneratorEntity.RADIUS+2,0.0,0.0));it.deltaMovement=Vec3(-4.0,0.0,0.0);h.level.addFreshEntity(it)}
  val incoming=arrow(first)
  h.runAtTickTime(2){
   h.assertTrue(incoming.isRemoved&&first.shieldHealth<640f,"New generator was absent from live shield interception")
   first.discard();val next=setup(h);val afterReload=arrow(next)
   h.runAtTickTime(4){
    h.assertTrue(afterReload.isRemoved&&next.shieldHealth<640f,"Replacement generator was lost after removal of the previous shield")
    next.discard();val free=arrow(next)
    h.runAtTickTime(6){h.assertTrue(!free.isRemoved,"Removed shield still intercepted a projectile");free.discard();h.succeed()}
   }
  }
 }
 private fun setup(h:GameTestHelper,age:Int=0):ShieldGeneratorEntity {
  val pos=h.absolutePos(BlockPos(16,240,16));h.level.getChunk(pos)
  val at=Vec3(pos.x+.5,pos.y.toDouble(),pos.z+.5)
  h.level.getEntitiesOfClass(ShieldGeneratorEntity::class.java,net.minecraft.world.phys.AABB(at.subtract(1.0,1.0,1.0),at.add(1.0,1.0,1.0))).forEach {it.discard()}
  val g=ModEntities.SHIELD_GENERATOR.get().create(h.level)!!;g.setPos(at)
  val tag=CompoundTag();g.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal);tag.putInt("HellpodActiveTicks",age);tag.putInt("HellpodPhaseTicks",age);tag.putDouble("HellpodRestY",g.y);tag.putBoolean("HellpodCapReleased",true);g.load(tag);h.level.addFreshEntity(g)
  return g
 }
 @JvmStatic @GameTest(template="shield_test",timeoutTicks=30)
 fun separateHealthRegenerates64PerSecondAndCaps(h:GameTestHelper){
  val g=setup(h);h.assertTrue(g.health==100f&&g.shieldHealth==640f,"Generator/shield health incorrect")
  g.absorb(128f,g.shieldCenter().add(ShieldGeneratorEntity.RADIUS,0.0,0.0));repeat(20){g.tick()}
  h.assertTrue(abs(g.shieldHealth-576f)<.01&&g.health==100f,"Regeneration is not 64/s or damaged the body")
  repeat(40){g.tick()};h.assertTrue(g.shieldHealth==640f,"Regeneration exceeded capacity");g.discard();h.succeed()
 }
 @JvmStatic @GameTest(template="shield_test",timeoutTicks=30)
 fun zeroHealthStaysBrokenAfterTicksAndSaveLoad(h:GameTestHelper){
  val g=setup(h);g.absorb(640f,g.shieldCenter().add(0.0,0.0,ShieldGeneratorEntity.RADIUS));repeat(20){g.tick()}
  val tag=CompoundTag();g.saveWithoutId(tag);g.load(tag);repeat(20){g.tick()}
  h.assertTrue(g.shieldBroken&&g.shieldHealth==0f&&!g.shieldOnline&&g.health==100f,"Broken shield regenerated or destroyed the intact generator")
  g.discard();h.succeed()
 }
 @JvmStatic @GameTest(template="shield_test",timeoutTicks=30)
 fun nativeHighSpeedBulletIsStoppedAtEntrySurface(h:GameTestHelper){
  val g=setup(h);val p=ModEntities.SENTRY_BULLET.get().create(h.level)!!;p.setPos(g.shieldCenter().add(-20.0,0.0,0.0));p.setDamage(15f);p.setCustomGravity(0f);p.shoot(1.0,0.0,0.0,40f,0f);h.level.addFreshEntity(p)
  h.assertTrue(ShieldInterception.intercept(p,listOf(g))&&p.isRemoved,"Bullet crossed the whole sphere in a tick")
  h.assertTrue(abs(p.x-(g.x-ShieldGeneratorEntity.RADIUS))<.0001&&g.shieldHealth==625f,"Interception point or SBW damage incorrect")
  h.assertTrue(g.shieldHits.getList("hits",10).size==1,"Impact wasn't published to clients");g.discard();h.succeed()
 }
 @JvmStatic @GameTest(template="shield_test",timeoutTicks=30)
 fun arrowsEnterBlockedAndInternalFireLeaves(h:GameTestHelper){
  val g=setup(h);val inArrow=Arrow(EntityType.ARROW,h.level);inArrow.setPos(g.shieldCenter().add(ShieldGeneratorEntity.RADIUS+2,0.0,0.0));inArrow.deltaMovement=Vec3(-4.0,0.0,0.0);h.level.addFreshEntity(inArrow)
  h.assertTrue(ShieldInterception.intercept(inArrow,listOf(g))&&inArrow.isRemoved,"Vanilla arrow wasn't absorbed")
  val hp=g.shieldHealth;val out=ModEntities.SENTRY_BULLET.get().create(h.level)!!;out.setPos(g.shieldCenter().add(2.0,0.0,0.0));out.setDamage(15f);out.shoot(1.0,0.0,0.0,25f,0f);h.level.addFreshEntity(out)
  h.assertTrue(!ShieldInterception.intercept(out,listOf(g))&&!out.isRemoved&&g.shieldHealth==hp,"Outgoing fire was blocked")
  out.discard();g.discard();h.succeed()
 }
 @JvmStatic @GameTest(template="shield_test",timeoutTicks=30)
 fun interceptedExplosiveDoesNotDetonateBehindShield(h:GameTestHelper){
  val g=setup(h);val shell=ModEntities.ANTI_TANK_EMPLACEMENT_SHELL.get().create(h.level)!!;shell.setPos(g.shieldCenter().add(ShieldGeneratorEntity.RADIUS+2,0.0,0.0));shell.shoot(-1.0,0.0,0.0,14f,0f);h.level.addFreshEntity(shell)
  val cow=EntityType.COW.create(h.level)!!;cow.isNoGravity=true;cow.isNoAi=true;cow.setPos(g.shieldCenter().add(1.0,0.0,0.0));h.level.addFreshEntity(cow);val hp=cow.health
  h.assertTrue(ShieldInterception.intercept(shell,listOf(g))&&shell.isRemoved,"Explosive crossed the shell")
  h.assertTrue(cow.health==hp&&g.shieldHealth==420f,"Absorption detonated inside or discarded explosive damage")
  cow.discard();g.discard();h.succeed()
 }
 @JvmStatic @GameTest(template="shield_test",timeoutTicks=30)
 fun sphereDoesNotBlockCreatureMovement(h:GameTestHelper){
  val g=setup(h);val cow=EntityType.COW.create(h.level)!!;cow.isNoGravity=true;cow.isNoAi=true;cow.setPos(g.shieldCenter().add(ShieldGeneratorEntity.RADIUS-1,0.0,0.0));h.level.addFreshEntity(cow)
  cow.move(MoverType.SELF,Vec3(3.0,0.0,0.0));h.assertTrue(abs(cow.x-(g.x+ShieldGeneratorEntity.RADIUS+2))<.0001,"Creature collided with the sphere")
  cow.move(MoverType.SELF,Vec3(-4.0,0.0,0.0));h.assertTrue(abs(cow.x-(g.x+ShieldGeneratorEntity.RADIUS-2))<.0001,"Creature couldn't enter sphere")
  cow.discard();g.discard();h.succeed()
 }
 @JvmStatic @GameTest(template="shield_test",timeoutTicks=30)
 fun lifeEndsAt800ActiveTicksAndThenRetracts(h:GameTestHelper){
  val g=setup(h,798);g.tick();h.assertTrue(g.phase==AbstractHellpodEntity.Phase.ACTIVE&&g.shieldOnline,"Retracted before forty seconds")
  g.tick();h.assertTrue(g.phase==AbstractHellpodEntity.Phase.RETRACTING&&!g.shieldOnline,"Still online after forty seconds")
  repeat(g.retractTicks){g.tick()};h.assertTrue(g.phase==AbstractHellpodEntity.Phase.DONE,"Retraction failed to finish");g.discard();h.succeed()
 }
 @JvmStatic @GameTest(template="shield_test",timeoutTicks=30)
 fun bodyCanBeDamagedByOwnerAndHasHalfBlastDamage(h:GameTestHelper){
  val g=setup(h);val p=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"ShieldOwner"));g.setOwnerUUID(p.uuid)
  g.hurt(h.level.damageSources().playerAttack(p),20f);h.assertTrue(abs(g.health-80)<.01,"Owner couldn't damage the body")
  g.hurt(h.level.damageSources().explosion(g,p),40f);h.assertTrue(abs(g.health-60)<.01&&g.shieldHealth==640f,"Body explosion resistance or separate pool incorrect")
  g.hurt(h.level.damageSources().playerAttack(p),100f);h.assertTrue(g.shieldBroken&&!g.shieldOnline&&g.phase==AbstractHellpodEntity.Phase.DESTROYED,"Destroyed generator kept its shield")
  g.discard();h.succeed()
 }
 @JvmStatic @GameTest(template="shield_test",timeoutTicks=30)
 fun nearerWallKeepsProjectileFromPrematureShieldAbsorption(h:GameTestHelper){
  val g=setup(h);val wall=BlockPos.containing(g.shieldCenter().add(ShieldGeneratorEntity.RADIUS+1,0.0,0.0));h.level.setBlock(wall,Blocks.STONE.defaultBlockState(),2)
  val p=Arrow(EntityType.ARROW,h.level);p.setPos(g.shieldCenter().add(ShieldGeneratorEntity.RADIUS+3,0.0,0.0));p.deltaMovement=Vec3(-8.0,0.0,0.0);h.level.addFreshEntity(p)
  h.assertTrue(!ShieldInterception.intercept(p,listOf(g))&&!p.isRemoved&&g.shieldHealth==640f,"Shield ignored the nearer world obstacle")
  p.discard();g.discard();h.level.setBlock(wall,Blocks.AIR.defaultBlockState(),2);h.succeed()
 }
 @JvmStatic @GameTest(template="shield_test",timeoutTicks=30)
 fun tangentAndOutwardMotionDoNotDrainShield(h:GameTestHelper){
  val g=setup(h);val c=g.shieldCenter()
  h.assertTrue(ShieldInterception.entry(c.add(-20.0,ShieldGeneratorEntity.RADIUS,0.0),c.add(20.0,ShieldGeneratorEntity.RADIUS,0.0),c)==null,"Tangent was treated as penetration")
  h.assertTrue(ShieldInterception.entry(c.add(ShieldGeneratorEntity.RADIUS+1,0.0,0.0),c.add(ShieldGeneratorEntity.RADIUS+4,0.0,0.0),c)==null,"Outward projectile drained shield")
  g.discard();h.succeed()
 }
}
