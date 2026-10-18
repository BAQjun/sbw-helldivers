package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.asset.AssetCatalog
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.*
import kotlin.math.abs

@GameTestHolder("sbw_helldiver_laser")
@PrefixGameTestTemplate(false)
object LaserGameTests {
    @JvmStatic @GameTest(template="sentry_test",batch="laser",timeoutTicks=10)
    fun heatWarmupTransferCoolingAndIrreversibleFuse(h: GameTestHelper) {
        val c=LaserClock()
        repeat(10) { h.assertTrue(!c.advance(true,true,5.0),"Warmup ended before half a second") }
        h.assertTrue(c.advance(true,true,5.0),"Beam did not start after ten ticks")
        h.assertTrue(c.advance(true,false,5.0),"Transfer interrupted an already firing beam")
        repeat(18) { c.advance(true,true,5.0) }
        h.assertTrue(abs(c.heat-8.0)<1e-6,"Heat is not eight units per firing second")
        val tag=CompoundTag();c.save(tag);val restored=LaserClock();restored.restore(tag)
        h.assertTrue(restored.advance(true,false,5.0),"Save/load lost continuous firing")
        for(rate in listOf(5.0,7.5,3.5)) {
            val cool=LaserClock();cool.restore(tag);repeat(20) { cool.advance(false,false,rate) }
            h.assertTrue(abs(cool.heat-(8-rate))<1e-6,"Incorrect cooling rate: $rate")
        }
        val rewarming=LaserClock();rewarming.restore(tag);rewarming.advance(false,false,5.0)
        repeat(10) { h.assertTrue(!rewarming.advance(true,true,5.0),"Reacquisition bypassed warmup") }
        h.assertTrue(abs(rewarming.heat-5.25)<1e-6,"A stopped beam must keep cooling during warmup")
        h.assertTrue(LaserClock.coolingRate(false,.0f)==7.5 && LaserClock.coolingRate(false,.8f)==5.0 &&
            LaserClock.coolingRate(false,2f)==3.5 && LaserClock.coolingRate(true,.0f)==3.5,"Biome cooling precedence is wrong")
        repeat(479) { c.advance(true,true,5.0) }
        h.assertTrue(!c.alarm,"Alarm sounded below eighty percent")
        c.advance(true,true,5.0);h.assertTrue(c.alarm && abs(c.heat-200)<1e-6,"Alarm threshold must be two hundred")
        repeat(124) { h.assertTrue(c.advance(true,true,5.0),"Beam shut down below maximum heat") }
        h.assertTrue(!c.advance(true,true,5.0) && c.state==LaserClock.State.OVERLOADED && c.heat==250.0,"Overheat did not stop immediately")
        c.stop();repeat(19) { c.advance(false,false,7.5);h.assertTrue(!c.destroyNow,"Self-destruct was early") }
        val fuse=CompoundTag();c.save(fuse);val loaded=LaserClock();loaded.restore(fuse)
        loaded.advance(false,false,7.5);h.assertTrue(loaded.destroyNow && loaded.heat==250.0,"Fuse was canceled by cooling/save")
        val p=AssetCatalog.profiles.getValue("laser_sentry")
        h.assertTrue(p.isLaser && p.totalAmmo==0 && p.shellOffset==null,"Laser acquired an ammunition mechanism")
        h.succeed()
    }

    @JvmStatic @GameTest(template="sentry_test",batch="laser",timeoutTicks=10)
    fun damageCadenceSurvivesSaveAndRestartsAfterWarmup(h: GameTestHelper) {
        val clock=LaserClock()
        repeat(10) { h.assertTrue(!clock.advance(true,true,5.0) && !clock.damagePulse,"Damage during warmup") }
        repeat(3) { t ->
            h.assertTrue(clock.advance(true,true,5.0),"Beam failed to start")
            h.assertTrue(clock.damagePulse==(t==0),"First pulse or spacing is wrong")
        }
        val tag=CompoundTag();clock.save(tag);val loaded=LaserClock();loaded.restore(tag)
        repeat(12) {
            clock.advance(true,false,5.0);loaded.advance(true,false,5.0)
            h.assertTrue(clock.damagePulse==loaded.damagePulse,"Save/load changed the direct damage cadence")
        }
        loaded.stop();h.assertTrue(!loaded.damagePulse,"Stopped beam retained a damage pulse")
        repeat(10) { h.assertTrue(!loaded.advance(true,true,5.0) && !loaded.damagePulse,"Restart bypassed warmup") }
        h.assertTrue(loaded.advance(true,true,5.0) && loaded.damagePulse,"Fresh beam did not start with a damage pulse")
        loaded.advance(false,false,5.0);h.assertTrue(!loaded.damagePulse,"Target loss retained a pulse")
        h.succeed()
    }

    @JvmStatic @GameTest(template="sentry_test",batch="laser",timeoutTicks=200)
    fun exactBeamDamageBurnOnlyAndBlockOcclusion(h: GameTestHelper) {
        val bp=h.absolutePos(BlockPos(15,150,0));val start=Vec3(bp.x+.5,bp.y+1.0,bp.z+.5)
        for(cx in ((bp.x-2) shr 4)..((bp.x+2) shr 4))for(cz in (bp.z shr 4)..((bp.z+202) shr 4)) {
            h.level.setChunkForced(cx,cz,true);h.level.getChunk(cx,cz)
        }
        for(z in 0..202)for(y in 0..2)h.level.setBlock(bp.offset(0,y,z),Blocks.AIR.defaultBlockState(),2)
        val sentry=ModEntities.LASER_SENTRY.get().create(h.level)!!
        val mob=EntityType.HUSK.create(h.level)!!.also {
            it.isNoAi=true;it.setPersistenceRequired();it.isNoGravity=true;it.setPos(start.add(0.0,-.5,150.0))
            it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=500.0;it.health=500f
            it.getAttribute(Attributes.ARMOR)!!.baseValue=30.0
            it.addEffect(MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,1000,4))
        }
        // Wait before inserting the victim: a newly forced chunk may still be
        // reading its entity section. Retire actors from an interrupted run.
        h.startSequence().thenWaitUntil {
            h.assertTrue(h.level.areEntitiesLoaded(net.minecraft.world.level.ChunkPos.asLong(bp.x shr 4,(bp.z+150) shr 4)),"Beam victim section is still loading")
        }.thenExecute {
            h.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Husk::class.java,
                net.minecraft.world.phys.AABB(start,start.add(0.0,0.0,200.0)).inflate(2.0)).forEach { it.discard() }
            h.assertTrue(h.level.addFreshEntity(mob) && h.level.getEntity(mob.id)===mob,"Beam victim failed to register")
            sentry.setPos(start)
            val motion=Vec3(.13,.07,-.09);mob.deltaMovement=motion;mob.hasImpulse=false
            val direction=Vec3(0.0,0.0,1.0)
            val clock=LaserClock()
            repeat(10) { clock.advance(true,true,5.0) }
            repeat(20) { tick ->
                h.assertTrue(clock.advance(true,true,5.0),"Continuous beam stopped unexpectedly")
                LaserBeam.fire(sentry,LaserBeam.trace(sentry,start,direction),clock.damagePulse)
                h.assertTrue(mob.health==500f-7.5f*(tick/5+1),"Direct damage must be 7.5 every five ticks, including between pulses: $tick")
            }
            h.assertTrue(mob.health==470f,"Beam must deal thirty direct damage per second through armor/cooldown")
            h.assertTrue(mob.getEffect(ModEffects.BURNING.get())?.duration==60 && !mob.hasEffect(ModEffects.PANIC.get()),"Laser burn duration or panic exclusion is wrong")
            h.assertTrue(mob.deltaMovement==motion && !mob.hasImpulse,"Beam added a physical hit impulse")
            mob.tickCount=20;ModEffects.BURNING.get().applyEffectTick(mob,0)
            h.assertTrue(mob.health==454f,"Custom burn is not sixteen damage per second")
            h.assertTrue(mob.deltaMovement==motion && !mob.hasImpulse,"Burning added a physical hit impulse")
            mob.invulnerableTime=0
            mob.removeEffect(MobEffects.DAMAGE_RESISTANCE)
            mob.hurt(h.level.damageSources().mobAttack(EntityType.HUSK.create(h.level)!!.also { it.setPos(start) }),1f)
            h.assertTrue(mob.deltaMovement!=motion,"An ordinary physical hit lost its knockback")
            h.level.setBlock(bp.offset(0,1,100),Blocks.STONE.defaultBlockState(),2)
            val blocked=LaserBeam.trace(sentry,start,direction)
            h.assertTrue(blocked.target==null && start.distanceTo(blocked.end)<101,"Laser passed through a solid wall")
            h.level.setBlock(bp.offset(0,1,100),Blocks.AIR.defaultBlockState(),2);mob.discard()
            val empty=LaserBeam.trace(sentry,start,direction)
            h.assertTrue(abs(start.distanceTo(empty.end)-200.0)<1e-6,"Beam range is not two hundred meters")
            h.assertTrue(h.level.getEntitiesOfClass(GroundFireEntity::class.java,sentry.boundingBox.inflate(200.0)).isEmpty(),"Laser created ground fire")
            sentry.discard()
        }.thenSucceed()

    }
}
