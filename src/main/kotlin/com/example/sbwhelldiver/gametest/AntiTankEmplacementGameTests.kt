package com.example.sbwhelldiver.gametest
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import com.atsuishio.superbwarfare.world.phys.EntityResult
import com.atsuishio.superbwarfare.world.phys.ExtendedEntityRayTraceResult
import java.util.UUID
import kotlin.math.abs
@GameTestHolder("sbw_helldiver_hmg")
@PrefixGameTestTemplate(false)
object AntiTankEmplacementGameTests {
    private fun setup(h:GameTestHelper,ammo:Int=30):AntiTankEmplacementEntity {
        val p=h.absolutePos(BlockPos(8,240,8));h.level.getChunk(p)
        for(x in -4..4)for(z in -4..4)h.level.setBlock(p.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2)
        return ModEntities.ANTI_TANK_EMPLACEMENT.get().create(h.level)!!.also { d ->
            d.setPos(p.x+.5,p.y.toDouble(),p.z+.5)
            val t=CompoundTag();d.saveWithoutId(t);t.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal)
            t.putDouble("HellpodRestY",d.y);t.putInt("HmgAmmo",ammo);d.load(t);h.level.addFreshEntity(d)
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_emplacement_optic",timeoutTicks=45)
    fun opticLowersForOperatorAndStaysFlatDuringFeed(h: GameTestHelper) {
        val d=setup(h)
        val p=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"AtOptic"))
        p.setPos(d.position());h.level.addFreshEntity(p)
        var ticks=0;var prior=0;var feeding=false
        h.onEachTick {
            ticks++
            if(ticks<5)h.assertTrue(d.opticLowerTicks==0&&abs(d.opticPitchDegrees()-AntiTankEmplacementEntity.OPTIC_STOWED_PITCH)<.001,"Unused optic lowered without an operator")
            if(ticks==5)p.startRiding(d,true)
            if(ticks in 6..14) {
                h.assertTrue(d.opticLowerTicks>=prior,"Mounted optic reversed its lowering")
                prior=d.opticLowerTicks
            }
            if(ticks==14) {
                h.assertTrue(d.opticLowerTicks==AntiTankEmplacementEntity.OPTIC_LOWER_TICKS&&abs(d.opticPitchDegrees())<.001,"Occupied optic did not finish horizontal")
                d.acceptInput(p,0f,0f,true)
            }
            if(ticks in 15..19) {
                feeding=feeding||d.isMechanicallyFeeding
                h.assertTrue(abs(d.opticPitchDegrees())<.001,"Feed cycle raised the operator optic")
                d.acceptInput(p,0f,0f,false)
            }
            if(ticks==20)p.stopRiding()
            if(ticks==30) {
                h.assertTrue(feeding&&d.opticLowerTicks==0,"Optic or firing cycle failed to follow operator occupancy")
                p.discard();d.discard();h.succeed()
            }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_emplacement_stats",timeoutTicks=10)
    fun fiftyHealthAndErgoAim(h:GameTestHelper) {
        val d=setup(h);h.assertTrue(d.health==50f&&d.ammoRemaining==30&&d.shouldRiderSit(),"Wrong health, capacity or seated posture: ${d.health}")
        val step=HmgErgonomics.step(0f,179f,true,d.mechanicalErgonomics)
        h.assertTrue(abs(step-12.3125f)<.001&&d.aimZoom==4.0,"Wrong Ergo 32.5 or 4x zoom")
        val reduced=HmgErgonomics.step(0f,179f,true,d.mechanicalErgonomics,d.aimSpeedMultiplier)
        h.assertTrue(abs(reduced-8.003125f)<.001,"AT turning cap was not reduced")
        for(target in listOf(d.position().add(4.0,2.0,8.0),d.position().add(-10.0,1.0,30.0))) {
            val aim=d.aimAnglesAt(target);val t=CompoundTag();d.saveWithoutId(t);t.putFloat("HmgYaw",aim.x);t.putFloat("HmgPitch",aim.y);d.load(t)
            val offset=target.subtract(d.muzzlePosition(0));val bore=d.boreDirection()
            h.assertTrue(offset.subtract(bore.scale(offset.dot(bore))).length()<.002,"Actual single bore misses aim target")
        }
        val player=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"AtOwner"));player.setPos(d.position());h.level.addFreshEntity(player)
        val before=d.health;d.hurt(h.level.damageSources().explosion(d,player),20f)
        h.assertTrue(abs(d.health-(before-10f))<.01,"Explosion resistance differs from other emplacements")
        d.hurt(h.level.damageSources().playerAttack(player),1000f)
        h.assertTrue(d.phase==AbstractHellpodEntity.Phase.DESTROYED,"Operator could not destroy emplacement")
        player.discard();d.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_emplacement_close_aim",timeoutTicks=30)
    fun smallCorrectionSettlesPromptlyWithoutOvershoot(h:GameTestHelper) {
        val d=setup(h);val p=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"AtCloseAim"));p.setPos(d.position());h.level.addFreshEntity(p);p.startRiding(d,true)
        val started=h.level.gameTime
        h.onEachTick {
            d.acceptInput(p,5f,-5f,false)
            h.assertTrue(d.weaponYaw in 0f..5.001f&&d.weaponPitch in -5.001f..0f,"Finishing correction overshot the aim point")
            if(h.level.gameTime-started>=10) {
                h.assertTrue(abs(d.weaponYaw-5f)<.001&&abs(d.weaponPitch+5f)<.001,"Five-degree correction still has a slow proportional tail: ${d.weaponYaw}/${d.weaponPitch}")
                for(target in listOf(.01f,-.01f,.1f,-.1f,.5f,-.5f)) {
                    val step=HmgErgonomics.step(0f,target,false,d.mechanicalErgonomics,d.aimSpeedMultiplier,d.minimumClosingSpeed)
                    h.assertTrue(abs(step-target)<.0001,"Sub-degree final correction overshot or failed to settle")
                }
                val wrapped=HmgErgonomics.step(179.8f,-179.8f,true,d.mechanicalErgonomics,d.aimSpeedMultiplier,d.minimumClosingSpeed)
                h.assertTrue(abs(net.minecraft.util.Mth.wrapDegrees(wrapped+179.8f))<.001,"Finishing yaw chose the wrong path across 180 degrees")
                p.discard();d.discard();h.succeed()
            }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_emplacement_feed",timeoutTicks=80)
    fun feedCannotBeSkippedByRemountOrTrigger(h:GameTestHelper) {
        val d=setup(h,3);val p=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"AtRate"));p.setPos(d.position());h.level.addFreshEntity(p);p.startRiding(d,true)
        var serial=0;var last=-100L;var emptyAt=-1L;var dismounted=false
        h.onEachTick {
            d.acceptInput(p,0f,0f,true)
            if(d.shotSerial>serial) {
                if(serial>0)h.assertTrue(h.level.gameTime-last>=20,"Reload was skipped on remount")
                last=h.level.gameTime;serial=d.shotSerial
                if(serial<3){p.stopRiding();p.startRiding(d,true);d.acceptInput(p,0f,0f,false);d.acceptInput(p,0f,0f,true)}
            }
            if(d.ammoRemaining==0) {
                if(emptyAt<0)emptyAt=h.level.gameTime
                if(!dismounted) {
                    h.assertTrue(serial==3&&p.vehicle===d&&d.phase==AbstractHellpodEntity.Phase.ACTIVE,"Empty cannon ejected its operator or began retracting")
                    if(h.level.gameTime-emptyAt>=10){p.stopRiding();dismounted=true}
                } else if(h.level.gameTime>emptyAt+10) {
                    h.assertTrue(p.vehicle==null&&d.phase==AbstractHellpodEntity.Phase.RETRACTING,"Empty cannon did not retract after voluntary dismount")
                    p.discard();d.discard();h.succeed()
                }
            }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_emplacement_free_feed",timeoutTicks=45)
    fun feedKeepsAimAndAllowsTrackingForTwentyTicks(h:GameTestHelper) {
        val d=setup(h);val tag=CompoundTag();d.saveWithoutId(tag);tag.putFloat("HmgYaw",15f);tag.putFloat("HmgPitch",-30f);d.load(tag)
        val p=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"AtFreeFeed"));p.setPos(d.position());h.level.addFreshEntity(p);p.startRiding(d,true)
        var shotAt=-1L;var feedTicks=0
        h.onEachTick {
            val age=if(shotAt<0)0 else h.level.gameTime-shotAt
            d.acceptInput(p,if(age>=6)25f else 15f,if(age>=6)-20f else -30f,shotAt<0)
            if(shotAt<0&&d.shotSerial>0)shotAt=h.level.gameTime
            if(shotAt>=0) {
                val elapsed=h.level.gameTime-shotAt
                if(elapsed<20) {
                    h.assertTrue(d.isMechanicallyFeeding&&d.shotInterval==20,"Supply clock must remain one second")
                    h.assertTrue(d.weaponPitch<-19.9f,"Reload unexpectedly levelled the gun")
                    feedTicks++
                } else {
                    h.assertTrue(feedTicks==20&&!d.isMechanicallyFeeding,"Supply did not finish in exactly twenty ticks")
                    h.assertTrue(abs(d.weaponYaw-25f)<.001&&abs(d.weaponPitch+20f)<.001,"Aim was locked during supply")
                    h.assertTrue(d.shotSerial==1,"Unexpected second shot")
                    p.discard();d.discard();h.succeed()
                }
            }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_emplacement_damage",timeoutTicks=10)
    fun realShellDirectHitAndThreeToSevenMetreBlast(h:GameTestHelper) {
        val pos=h.absolutePos(BlockPos(15,240,15));val origin=Vec3(pos.x+.5,pos.y.toDouble(),pos.z+.5)
        val shell=ModEntities.ANTI_TANK_EMPLACEMENT_SHELL.get().create(h.level)!!;shell.setPos(origin);shell.tickCount=1;shell.setExplosionDestroy(false);h.level.addFreshEntity(shell)
        fun target(distance:Double)=EntityType.RAVAGER.create(h.level)!!.also {
            it.isNoAi=true;it.isNoGravity=true;it.getAttribute(Attributes.ARMOR)!!.baseValue=0.0;it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=1000.0;it.health=1000f
            it.setPos(origin.x+distance+it.bbWidth/2,origin.y-.5,origin.z);h.level.addFreshEntity(it)
        }
        val mobs=listOf(target(0.0),target(3.0),target(5.0),target(7.1));val before=mobs.map{it.health}
        // Entity additions outside the template chunk become queryable after the chunk-manager tick.
        mobs.forEach {h.level.getChunk(it.blockPosition())}
        h.runAfterDelay(2) {
            shell.onHitEntity(ExtendedEntityRayTraceResult(EntityResult(mobs[0],origin,false,false)))
            val damage=listOf(220f,20f,9.5f,0f)
            mobs.forEachIndexed { i,m -> h.assertTrue(abs(before[i]-m.health-damage[i])<.03,"Real shell damage at index $i: ${before[i]-m.health}");m.discard() }
            h.succeed()
        }
    }
}
