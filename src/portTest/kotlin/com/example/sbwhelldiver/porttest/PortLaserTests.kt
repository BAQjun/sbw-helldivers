package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.entity.OrbitalLaserEntity
import com.example.sbwhelldiver.config.OrbitalLaserSettings
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.monster.Zombie
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.*

@GameTestHolder("sbw_helldiver")
@PrefixGameTestTemplate(false)
object PortLaserTests {
 private fun arena(h:GameTestHelper):Vec3 {
  val at=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(8,180,8)))
  for(x in -16..112 step 8)for(z in -8..8 step 8){
   val p=BlockPos.containing(at.add(x.toDouble(),0.0,z.toDouble()))
   h.level.setChunkForced(p.x shr 4,p.z shr 4,true);h.level.getChunk(p)
  }
  for(x in -15..12)for(z in -2..2)h.level.setBlock(BlockPos.containing(at).offset(x,-1,z),Blocks.STONE.defaultBlockState(),3)
  return at
 }
 private fun zombie(h:GameTestHelper,at:Vec3)=EntityType.HUSK.create(h.level)!!.also{
  it.isNoAi=true;it.setNoGravity(true);it.setPersistenceRequired();it.setPos(at);h.level.addFreshEntity(it)
 }
 private fun laser(h:GameTestHelper,at:Vec3)=ModEntities.ORBITAL_LASER.get().create(h.level)!!.also{
  h.assertTrue(it.noCulling,"Long beam must remain updated when its small contact box is occluded")
  it.configure(at,at.add(100.0,100.0,0.0),null,OrbitalLaserSettings());h.level.addFreshEntity(it)
 }
 private fun blocked(h:GameTestHelper,ship:Vec3,at:Vec3):Boolean {
  val top=at.add(ship.subtract(at).normalize().scale(96.0))
  return h.level.clip(ClipContext(top,at,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty())).type==HitResult.Type.BLOCK
 }
 @JvmStatic @GameTest(template="port_test",batch="laser_partial_cover",timeoutTicks=100)
 fun exposedHeadIsAcquiredHitAndThenRetargeted(h:GameTestHelper){
  val at=arena(h);val wall=BlockPos.containing(at).offset(9,0,0)
  for(y in 0..1)h.level.setBlock(wall.above(y),Blocks.STONE.defaultBlockState(),3)
  val first=zombie(h,at.add(8.0,0.0,0.0));val next=zombie(h,at.add(-12.0,0.0,0.0))
  val l=laser(h,at);var firstSelected=false;var nextSelected=false
  h.assertTrue(blocked(h,l.origin,first.position().add(0.0,.49,0.0)),"Fixture must obstruct the old feet-only aim")
  h.assertTrue(!blocked(h,l.origin,Vec3(first.x,first.eyeY,first.z)),"Fixture must expose the head")
  for(t in 1L..80L)h.runAfterDelay(t){
   if(l.targetId==first.id)firstSelected=true
   if(l.targetId==next.id)nextSelected=true
  }
  h.runAfterDelay(82){try{
   h.assertTrue(firstSelected,"Partially exposed target was never acquired")
   h.assertTrue(!first.isAlive,"Beam selected the exposed target but failed to steer past the cover")
   h.assertTrue(nextSelected&&!next.isAlive,"Beam failed to switch to and kill the remaining target")
   h.succeed()
  }finally{l.discard();first.discard();next.discard()}}
 }
 @JvmStatic @GameTest(template="port_test",batch="laser_full_cover",timeoutTicks=40)
 fun fullyCoveredEnemyIsNotTargetedOrDamaged(h:GameTestHelper){
  val at=arena(h);val wall=BlockPos.containing(at).offset(9,0,0)
  for(y in 0..4)for(z in -2..2)h.level.setBlock(wall.offset(0,y,z),Blocks.STONE.defaultBlockState(),3)
  val hidden=zombie(h,at.add(8.0,0.0,0.0));val l=laser(h,at)
  h.assertTrue(blocked(h,l.origin,Vec3(hidden.x,hidden.eyeY,hidden.z)),"Fixture must hide the head")
  h.runAfterDelay(30){try{
   h.assertTrue(l.targetId==-1&&l.selectTarget()==null,"Laser acquired an enemy through solid cover")
   h.assertTrue(hidden.health==hidden.maxHealth,"Hidden enemy was damaged through solid cover")
   h.succeed()
  }finally{l.discard();hidden.discard()}}
 }
}
