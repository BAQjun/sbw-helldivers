package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.config.*
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.stratagem.Stratagems
import com.atsuishio.superbwarfare.world.phys.*
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.*
import net.minecraftforge.gametest.*
import kotlin.math.abs

@GameTestHolder("sbw_helldiver_orbital")
@PrefixGameTestTemplate(false)
object OrbitalNapalmGameTests {
 private fun origin(h:GameTestHelper)=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(15,20,15)))
 private fun floor(h:GameTestHelper,at:Vec3){
  for(x in -12..12)for(z in -12..12)h.level.setBlock(BlockPos.containing(at).offset(x,-1,z),Blocks.BEDROCK.defaultBlockState(),3)
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",timeoutTicks=20)
 fun napalmShellDamageAndHeavyTerrainFire(h:GameTestHelper){
  val at=origin(h);floor(h,at)
  val s=OrbitalBarrageConfig.snapshot("orbital_napalm_barrage")
  h.assertTrue(s.total==30 && s.radius==25.0 && s.shotGap==10 && s.roundPause==40 && s.fireRadius==10.0 && s.fireTicks==GroundFireEntity.NAPALM_LIFETIME,"Native footprint/lifetime or requested cadence incorrect")
  h.assertTrue(Stratagems.get(s.kind)!!.itemId==net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(ModItems.ORBITAL_NAPALM_BARRAGE.get()),"Card not connected to deployment")
  val shell=ModEntities.ORBITAL_NAPALM_SHELL.get().create(h.level)!!;shell.setPos(at);shell.tickCount=1;h.level.addFreshEntity(shell)
  fun victim(distance:Double)=EntityType.RAVAGER.create(h.level)!!.also{m->
   m.isNoAi=true;m.isNoGravity=true;m.getAttribute(Attributes.ARMOR)!!.baseValue=0.0
   m.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=1000.0;m.health=1000f
   m.setPos(at.add(distance+m.bbWidth/2,0.0,0.0));h.level.addFreshEntity(m)
  }
  val victims=listOf(0.0,3.3,6.65,10.0,10.1).map(::victim)
  shell.onHitEntity(ExtendedEntityRayTraceResult(EntityResult(victims[0],at,false,false)))
  victims.zip(listOf(140f,60f,29.5f,0f,0f)).forEachIndexed{i,(m,damage)->h.assertTrue(abs(1000-m.health-damage)<.03,"Napalm damage $i expected $damage got ${1000-m.health}")}
  val fields=h.level.getEntitiesOfClass(GroundFireEntity::class.java,AABB(at,at).inflate(1.0))
  h.assertTrue(fields.size==1 && fields[0].heavyBurning && fields[0].remainingTicks==GroundFireEntity.NAPALM_LIFETIME && fields[0].radius==10.0,"Missing/duplicate Eagle-style heavy napalm field")
  val field=fields.single();val cell=BlockPos.containing(at)
  h.assertTrue(field.coversCell(cell) && h.level.getBlockState(cell).`is`(ModBlocks.SENTRY_FIRE.get()) && !field.coversCell(cell.offset(11,0,0)),"Incorrect real ground-fire footprint")
  val health=victims.map{it.health};shell.causeExplode(at)
  h.assertTrue(health==victims.map{it.health} && h.level.getEntitiesOfClass(GroundFireEntity::class.java,AABB(at,at).inflate(1.0)).size==1,"Repeated impact callback duplicated damage or fire")
  victims[0].setPos(at);field.igniteInside(victims[0])
  h.assertTrue(victims[0].getEffect(ModEffects.BURNING.get())?.duration==60 && victims[0].getEffect(ModEffects.HEAVY_BURNING.get())?.duration==20,"Heavy napalm did not refresh both burn effects")
  victims.forEach{it.discard()};field.discard();shell.discard();h.succeed()
 }
 @JvmStatic @GameTest(templateNamespace="sbw_helldiver_cooldowns",template="sentry_test",timeoutTicks=100)
 fun napalmCustomLifetimeSurvivesReloadAndExpires(h:GameTestHelper){
  val at=origin(h);floor(h,at)
  val raised=BlockPos.containing(at.add(2.0,0.0,0.0));h.level.setBlock(raised,Blocks.BEDROCK.defaultBlockState(),3)
  val a=ModEntities.ORBITAL_NAPALM_SHELL.get().create(h.level)!!;a.configure(80f,60f,3.3,10f);a.configureFire(7.5,840)
  val tag=CompoundTag();a.saveWithoutId(tag);a.discard()
  val b=ModEntities.ORBITAL_NAPALM_SHELL.get().create(h.level)!!;b.load(tag);b.setPos(at);h.level.addFreshEntity(b)
  b.afterHitBlock(BlockHitResult(at,net.minecraft.core.Direction.UP,BlockPos.containing(at).below(),false))
  val field=h.level.getEntitiesOfClass(GroundFireEntity::class.java,AABB(at,at).inflate(1.0)).single()
  h.assertTrue(field.remainingTicks==840 && field.radius==7.5 && field.coversCell(raised.above()),"Shell reload lost configured lifetime/radius or terrain following")
  val saved=CompoundTag();field.saveWithoutId(saved);field.discard()
  val restored=ModEntities.GROUND_FIRE.get().create(h.level)!!;restored.load(saved);h.level.addFreshEntity(restored)
  h.assertTrue(restored.remainingTicks==840 && restored.heavyBurning && restored.coversCell(raised.above()),"Saved fire lifetime was clamped to the old Eagle default")
  restored.discard()
  val short=GroundFireEntity.create(h.level,at,null,7.5,true,40)
  h.runAfterDelay(70){
   h.assertTrue(short.isRemoved && !h.level.getBlockState(raised.above()).`is`(ModBlocks.SENTRY_FIRE.get()),"Expired napalm left permanent fire")
   h.succeed()
  }
 }
}
