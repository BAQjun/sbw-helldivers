package com.example.sbwhelldiver.gametest
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.util.UUID
import kotlin.math.abs
@GameTestHolder("sbw_helldiver_hmg")
@PrefixGameTestTemplate(false)
object HmgGameTests {
    private fun setup(h:GameTestHelper,ammo:Int=300):HmgEmplacementEntity {
        val p=h.absolutePos(BlockPos(8,240,8));h.level.getChunk(p)
        for(x in -4..4)for(z in -4..4)h.level.setBlock(p.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2)
        return ModEntities.HMG_EMPLACEMENT.get().create(h.level)!!.also { d ->
            d.setPos(p.x+.5,p.y.toDouble(),p.z+.5)
            val tag=CompoundTag();d.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal);tag.putBoolean("HmgHasBeenMounted",true)
            tag.putDouble("HellpodRestY",d.y);tag.putInt("HmgAmmo",ammo);d.load(tag);h.level.addFreshEntity(d)
        }
    }
    private fun player(h:GameTestHelper,p:Vec3,name:String)=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),name)).also {it.setPos(p);h.level.addFreshEntity(it)}
    @JvmStatic @GameTest(template="sentry_test",batch="hmg_mount",timeoutTicks=20)
    fun manualOnlyAndSingleStandingOperator(h:GameTestHelper) {
        val d=setup(h);val p=player(h,d.position(),"HmgMount");val other=player(h,d.position(),"HmgOther")
        d.tick();h.assertTrue(d.shotSerial==0&&d.ammoRemaining==300,"Unoccupied stand fired autonomously")
        h.assertTrue(d.interact(p,InteractionHand.MAIN_HAND).consumesAction()&&p.vehicle===d,"Player failed to mount")
        h.assertTrue(!d.shouldRiderSit(),"Operator used a sitting pose")
        val feet=d.operatorPosition()
        h.assertTrue(feet.distanceTo(d.position().add(0.0,.90,-1.0))<.001,"Standing anchor moved off rear footrests")
        val eye=d.getCameraPosition(1f,p,false,true)!!
        h.assertTrue(eye.y>d.y+2.3&&abs(eye.z-(d.z-1.0))<.001,"First-person eye remained inside rear gun housing")
        d.interact(other,InteractionHand.MAIN_HAND);h.assertTrue(other.vehicle==null,"Second operator mounted occupied stand")
        d.acceptInput(other,90f,0f,true);d.tick();h.assertTrue(d.shotSerial==0,"Unauthorized player fired stand")
        d.acceptInput(p,Float.NaN,0f,true);d.tick();h.assertTrue(d.weaponYaw.isFinite()&&d.shotSerial==0,"Invalid input contaminated aim")
        d.ejectPassengers();p.discard();other.discard();d.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="hmg_first_mount",timeoutTicks=50)
    fun firstRiderLevelsOnceBeforeFiringAndSaveKeepsProgress(h:GameTestHelper) {
        val d=setup(h);val unused=CompoundTag();d.saveWithoutId(unused);unused.putBoolean("HmgHasBeenMounted",false);d.load(unused)
        val p=player(h,d.position(),"HmgFirstUse");var ticks=0;var mountedAt=-1L;var saved=false
        h.onEachTick {
            ticks++
            if(ticks<6)h.assertTrue(d.awaitingFirstMount&&d.shotSerial==0,"Unoccupied HMG levelled without its first rider")
            if(ticks==6)p.startRiding(d,true)
            if(p.vehicle===d)d.acceptInput(p,0f,0f,true)
            if(!d.awaitingFirstMount&&mountedAt<0)mountedAt=h.level.gameTime
            if(mountedAt>=0) {
                if(d.isFirstMountLevelling)h.assertTrue(d.shotSerial==0,"HMG fired during first-mount levelling")
                if(!saved&&d.firstMountAge==3) {
                    val tag=CompoundTag();d.saveWithoutId(tag);val copy=ModEntities.HMG_EMPLACEMENT.get().create(h.level)!!;copy.load(tag)
                    h.assertTrue(copy.isFirstMountLevelling&&copy.firstMountAge==3,"Saving reset or skipped the levelling stage");copy.discard();saved=true
                }
                if(d.shotSerial>0) {
                    h.assertTrue(saved&&!d.isFirstMountLevelling&&d.firstMountAge==HmgEmplacementEntity.FIRST_MOUNT_TICKS,"First shot did not wait for the complete levelling stage")
                    val tag=CompoundTag();d.saveWithoutId(tag);d.load(tag)
                    h.assertTrue(!d.awaitingFirstMount&&!d.isFirstMountLevelling,"Used HMG forgot its first mounting after load")
                    p.stopRiding();p.startRiding(d,true);h.assertTrue(!d.isFirstMountLevelling,"A second mounting repeated first-use levelling")
                    p.discard();d.discard();h.succeed()
                }
            }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="pod_motion_assets",timeoutTicks=10)
    fun turretStageClocksAndRetractionAudioMatchAssets(h:GameTestHelper) {
        fun resource(name:String)=javaClass.classLoader.getResourceAsStream("assets/sbw_helldiver/$name")!!.bufferedReader(Charsets.UTF_8).use {com.google.gson.JsonParser.parseReader(it).asJsonObject}
        val audio=resource("helldivers/audio.json");val sound=resource("sounds.json")
        for(p in com.example.sbwhelldiver.asset.AssetCatalog.profiles.values.filter {it.id!="supply_hellpod"&&!it.id.contains("mine")}) {
            val animations=resource("animations/bedrock/vehicle/${p.model}.animation.json")["animations"].asJsonObject
            for((name,ticks) in listOf("animation.hellpod.deploy" to p.deployTicks,"animation.hellpod.retract" to p.retractTicks)) {
                val duration=animations[name].asJsonObject["animation_length"].asDouble
                h.assertTrue(ticks==kotlin.math.ceil(duration*20-1e-6).toInt(),"Server stage does not match animation length: ${p.id}/$name")
                val recipe=audio["models"].asJsonObject[p.model].asJsonObject[name].asJsonObject
                h.assertTrue(abs(recipe["length"].asDouble-duration)<.00001,"Sound clock and model clock disagree: ${p.id}/$name")
                if(name.endsWith("retract")&&p.id in listOf("hmg_emplacement","anti_tank_emplacement")) {
                    val effects=recipe["events"].asJsonArray.map {it.asJsonObject["effect"].asString}
                    h.assertTrue(effects.any {sound.has(it)&&audio["effects"].asJsonObject[it].asJsonObject["audible"].asBoolean},"Emplacement retraction remains silent: ${p.id}")
                }
            }
        }
        val first=resource("animations/bedrock/vehicle/hmg_hellpod_v1.animation.json")["animations"].asJsonObject["animation.hmg.first_mount"].asJsonObject
        h.assertTrue(first["animation_length"].asDouble*20==HmgEmplacementEntity.FIRST_MOUNT_TICKS.toDouble(),"First-mount animation and server firing gate differ")
        h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="hmg_rate",timeoutTicks=40)
    fun sixHundredRpmAlternatesAndReleaseCannotResetClock(h:GameTestHelper) {
        val d=setup(h);val p=player(h,d.position(),"HmgRate");p.startRiding(d,true)
        var age=0;var serial=0
        h.onEachTick {
            age++;d.acceptInput(p,0f,0f,true)
            if(d.shotSerial>serial) {
                h.assertTrue(d.lastBarrel==((d.shotSerial-1) and 1),"Barrels did not alternate")
                serial=d.shotSerial
                d.acceptInput(p,0f,0f,false);d.acceptInput(p,0f,0f,true)
            }
            if(age==23) {
                h.assertTrue(d.shotSerial in 10..12&&d.ammoRemaining==300-d.shotSerial,"600 RPM clock changed: ${d.shotSerial}")
                d.ejectPassengers();p.discard();d.discard();h.succeed()
            }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="hmg_empty",timeoutTicks=30)
    fun lastRoundWaitsForVoluntaryDismountThenRetracts(h:GameTestHelper) {
        val d=setup(h,2);val p=player(h,d.position(),"HmgEmpty");p.startRiding(d,true)
        var emptyAt=-1L;var dismounted=false
        h.onEachTick {
            d.acceptInput(p,0f,0f,true)
            if(d.ammoRemaining==0) {
                if(emptyAt<0)emptyAt=h.level.gameTime
                if(!dismounted) {
                    h.assertTrue(d.shotSerial==2&&d.phase==AbstractHellpodEntity.Phase.ACTIVE&&p.vehicle===d,
                        "Empty HMG ejected its rider, fired extra rounds, or started retracting")
                    if(h.level.gameTime-emptyAt>=10){p.stopRiding();dismounted=true}
                } else if(h.level.gameTime>emptyAt+10) {
                    h.assertTrue(d.phase==AbstractHellpodEntity.Phase.RETRACTING&&p.vehicle==null,"Empty HMG did not retract after dismount")
                    h.assertTrue(p.position().distanceTo(d.position())>1.5,"Voluntary dismount left player inside the mechanism")
                    p.discard();d.discard();h.succeed()
                }
            }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="hmg_ergo",timeoutTicks=10)
    fun ergonomicFollowWrapAndSpeedLimit(h:GameTestHelper) {
        val follow=HmgErgonomics.follow(65.0)
        h.assertTrue(follow in .22.. .24,"Incorrect Ergo 65 follow fraction: $follow")
        h.assertTrue(abs(HmgErgonomics.step(0f,90f,true)-19.625f)<.001,"Maximum yaw step changed")
        h.assertTrue(abs(HmgErgonomics.step(179f,-179f,true)-(179f+2*follow.toFloat()))<.001,"Aim crossed the long side of 180 degrees")
        h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="hmg_aim",timeoutTicks=10)
    fun centredTargetConvergesFromBothCameraHeights(h:GameTestHelper) {
        val d=setup(h);val p=player(h,d.position(),"HmgAim")
        p.yRot=37f;p.yHeadRot=37f;p.xRot=-12f
        for(first in listOf(true,false)) {
            val camera=d.getCameraPosition(1f,p,false,first)
            val rotation=d.getCameraRotation(1f,p,false,first)
            h.assertTrue(abs(rotation.x-p.getViewYRot(1f))<.001&&abs(rotation.y-p.getViewXRot(1f))<.001,"Camera added an independent aim tilt")
            for(distance in listOf(8.0,30.0,200.0)) {
                val target=camera.add(p.getViewVector(1f).scale(distance))
                val angles=d.aimAnglesAt(target)
                val tag=CompoundTag();d.saveWithoutId(tag);tag.putFloat("HmgYaw",angles.x);tag.putFloat("HmgPitch",angles.y);d.load(tag)
                val start=d.muzzlePosition(0).add(d.muzzlePosition(1)).scale(.5)
                val offset=target.subtract(start);val bore=d.boreDirection()
                val miss=offset.subtract(bore.scale(offset.dot(bore))).length()
                h.assertTrue(miss<.001,"Bore misses $distance m screen-centred target by $miss (first=$first)")
            }
        }
        p.discard();d.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="hmg_ground",timeoutTicks=10)
    fun stationaryStandUsesFirstPressDismount(h:GameTestHelper) {
        val d=setup(h);val p=player(h,d.position(),"HmgGround")
        p.startRiding(d,true);d.setOnGround(false);d.deltaMovement=Vec3(0.0,-.2,0.0);d.tick()
        // This is the exact condition used by SBW's handleDismountPress before its confirmation branch.
        h.assertTrue(d.onGround()&&d.deltaMovement.length()<.1,"Fixed stand entered airborne/moving double-press confirmation")
        p.stopRiding();h.assertTrue(p.vehicle==null&&p.position().distanceTo(d.position())>1.5,"Normal dismount failed to leave the mechanism")
        p.discard();d.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="hmg_owner_damage",timeoutTicks=10)
    fun formerOperatorCanDamageAndDestroyOwnStand(h:GameTestHelper) {
        val d=setup(h);val p=player(h,d.position(),"HmgOwner")
        p.startRiding(d,true);d.lastDriverUUID=p.stringUUID;p.stopRiding()
        val attack=h.level.damageSources().playerAttack(p);val before=d.health
        h.assertTrue(d.hurt(attack,20f)&&d.health<before,"Former operator was protected from damaging own stand")
        d.isInvulnerable=true;val protected=d.health
        h.assertTrue(!d.hurt(attack,20f)&&d.health==protected,"Explicit entity invulnerability was ignored")
        d.isInvulnerable=false;d.hurt(attack,10000f)
        h.assertTrue(d.phase==AbstractHellpodEntity.Phase.DESTROYED&&d.operator==null,"Player could not destroy own stand")
        p.discard();d.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="hmg_casing_ports",timeoutTicks=10)
    fun bothCasingPortsFollowTraverseAndPitchAndThrowOutward(h:GameTestHelper) {
        val d=setup(h)
        for(yaw in listOf(0f,90f,179f,-179f))for(pitch in listOf(-65f,0f,75f)) {
            val tag=CompoundTag();d.saveWithoutId(tag);tag.putFloat("HmgYaw",yaw);tag.putFloat("HmgPitch",pitch);d.load(tag)
            val right=Vec3(kotlin.math.cos(Math.toRadians(yaw.toDouble())),0.0,kotlin.math.sin(Math.toRadians(yaw.toDouble())))
            val left=d.casingExitPosition(0);val other=d.casingExitPosition(1)
            h.assertTrue(kotlin.math.abs(left.subtract(other).dot(right)-11.9/16.0)<.00001,"Casing ports lost mirrored receiver spacing")
            for(barrel in 0..1) {
                val side=if(barrel==0)1 else -1;val port=d.casingExitPosition(barrel);val speed=d.casingEjectionVelocity(barrel)
                h.assertTrue(speed.dot(right)*side>.23,"Casing was thrown into the receiver instead of outwards")
                h.assertTrue(port.distanceTo(d.muzzlePosition(barrel))>2.0,"Casing appeared at the muzzle rather than its side port")
                h.assertTrue(port.x.isFinite()&&port.y.isFinite()&&port.z.isFinite()&&speed.length() in .23.. .32,"Casing launch is invalid")
            }
        }
        d.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="hmg_save",timeoutTicks=10)
    fun savedStandKeepsAmmoWithoutResumingFire(h:GameTestHelper) {
        val d=setup(h,117);val t=CompoundTag();d.saveWithoutId(t);d.discard()
        val loaded=ModEntities.HMG_EMPLACEMENT.get().create(h.level)!!;loaded.load(t);h.level.addFreshEntity(loaded);loaded.tick()
        h.assertTrue(loaded.ammoRemaining==117&&loaded.shotSerial==0&&loaded.weaponYaw.isFinite(),"Save/load lost ammo or resumed trigger")
        h.assertTrue(HmgEmplacementEntity.DAMAGE==15f,"Per-shot damage changed")
        loaded.discard();h.succeed()
    }
}
