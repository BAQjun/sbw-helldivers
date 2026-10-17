package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.network.HmgInputPacket
import com.mojang.authlib.GameProfile
import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.util.UUID
import kotlin.math.abs

@GameTestHolder("sbw_helldiver_hmg")
@PrefixGameTestTemplate(false)
object EmplacementAimGameTests {
    private fun setup(h:GameTestHelper,at:Boolean=false,offset:Int=0):HmgEmplacementEntity {
        val pos=h.absolutePos(BlockPos(8+offset,240,8));h.level.getChunk(pos)
        return (if(at)ModEntities.ANTI_TANK_EMPLACEMENT.get().create(h.level)!! else ModEntities.HMG_EMPLACEMENT.get().create(h.level)!!).also { d ->
            d.setPos(pos.x+.5,pos.y.toDouble(),pos.z+.5)
            val tag=CompoundTag();d.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal);tag.putBoolean("HmgHasBeenMounted",true);tag.putDouble("HellpodRestY",d.y);d.load(tag);h.level.addFreshEntity(d)
        }
    }
    private fun mount(h:GameTestHelper,d:HmgEmplacementEntity,name:String)=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),name)).also {p ->p.setPos(d.position());h.level.addFreshEntity(p);p.startRiding(d,true)}
    @JvmStatic @GameTest(template="sentry_test",batch="aim_independent",timeoutTicks=35)
    fun halfErgoSlowsAtHandlingIndependentlyOfMotor(h:GameTestHelper) {
        val hmg=setup(h);val at=setup(h,true,50);val p=mount(h,hmg,"AimHmg");val q=mount(h,at,"AimAt");var ticks=0
        h.onEachTick {
            hmg.acceptInput(p,90f,0f,false);at.acceptInput(q,90f,0f,false);ticks++
            if(ticks==22) {
                h.assertTrue(hmg.handlingYaw-at.handlingYaw>10f,"AT half-Ergo handling is not slower than HMG")
                h.assertTrue(at.handlingYaw in 70f..76f&&abs(at.handlingYaw-at.weaponYaw)>1f,"AT handling failed its slower independent response: ${at.handlingYaw}/${at.weaponYaw}")
                h.assertTrue(hmg.weaponYaw-at.weaponYaw>5f,"Independent mechanical speed difference was lost")
                h.assertTrue(hmg.ergonomics==65.0&&at.ergonomics==32.5,"AT gameplay Ergo must be half of HMG")
                p.discard();q.discard();hmg.discard();at.discard();h.succeed()
            }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="aim_final_convergence",timeoutTicks=35)
    fun smallCorrectionSettlesPromptlyAndStaysStill(h:GameTestHelper) {
        val hmg=setup(h);val at=setup(h,true,50);val p=mount(h,hmg,"CloseHmg");val q=mount(h,at,"CloseAt");var ticks=0
        val expected=EmplacementAimMath.direction(5f,-5f)
        h.onEachTick {
            hmg.acceptInput(p,5f,-5f,false);at.acceptInput(q,5f,-5f,false);ticks++
            for(d in listOf(hmg,at)) {
                h.assertTrue(d.handlingYaw in 0f..5.00001f&&d.handlingPitch in -5.00001f..0f,"Final correction overshot its target")
                if(ticks>=8) {
                    h.assertTrue(abs(d.handlingYaw-5f)<.0001&&abs(d.handlingPitch+5f)<.0001,"Five-degree correction still crawls after 0.4 seconds: ${d.handlingYaw}/${d.handlingPitch}")
                    h.assertTrue(d.gameplayAimDirection().distanceTo(expected)<1e-6,"Stationary occupied emplacement keeps drifting after alignment")
                }
            }
            if(ticks==25) {
                val wrapped=EmplacementAimMath.follow(179f,-179f,true,ergonomics=32.5)
                h.assertTrue(wrapped>179f&&wrapped<=181f,"Small correction crossed the yaw seam in the wrong direction")
                h.assertTrue(EmplacementAimMath.follow(5f,10f,false,dt=0.0)==5f,"Zero elapsed time moved the aim")
                p.discard();q.discard();hmg.discard();at.discard();h.succeed()
            }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="aim_real_shot",timeoutTicks=15)
    fun realShellAndCircleRespectNativeFiveDegreeCone(h:GameTestHelper) {
        val d=setup(h,true) as AntiTankEmplacementEntity;val p=mount(h,d,"AimCone")
        val point=d.position().add(100.0,d.weaponPivot.y,0.0)
        val tag=CompoundTag();d.saveWithoutId(tag);tag.putFloat("HandlingYaw",-90f);tag.putFloat("HandlingPitch",0f);tag.putFloat("AimDistance",100f);d.load(tag)
        h.onEachTick {
            d.acceptInput(p,-90f,0f,d.shotSerial==0,point)
            if(d.shotSerial>0) {
                h.assertTrue(abs(d.weaponYaw)<15f,"Mechanical turret had already caught up")
                val shell=h.level.getEntitiesOfClass(AntiTankEmplacementShellEntity::class.java,d.boundingBox.inflate(200.0)){it.owner===p}.firstOrNull()
                h.assertTrue(shell!=null,"Real shell was not spawned")
                val shotAngle=Math.toDegrees(kotlin.math.acos(shell!!.deltaMovement.normalize().dot(d.boreDirection()).coerceIn(-1.0,1.0)))
                val solutionAngle=Math.toDegrees(kotlin.math.acos(d.firingDirection().dot(d.boreDirection()).coerceIn(-1.0,1.0)))
                h.assertTrue(shotAngle in 4.8..5.05,"Real round violated or ignored the 5 degree cone: $shotAngle")
                h.assertTrue(solutionAngle in 4.99..5.01,"Circle does not obey the same 5 degree cone: $solutionAngle")
                shell.discard();p.discard();d.discard();h.succeed()
            }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="aim_cone_geometry",timeoutTicks=10)
    fun firingConeHandlesDiagonalAndOppositeDirections(h:GameTestHelper) {
        val bore=EmplacementAimMath.direction(179f,70f)
        for(wanted in listOf(EmplacementAimMath.direction(-160f,50f),bore.scale(-1.0),Vec3.ZERO)) {
            val limited=EmplacementAimMath.constrain(wanted,bore,5.0)
            val angle=Math.toDegrees(kotlin.math.acos(limited.dot(bore).coerceIn(-1.0,1.0)))
            h.assertTrue(limited.x.isFinite()&&abs(limited.length()-1)<1e-9&&angle<=5.00001,"Diagonal/opposite firing direction escaped the spherical cone: $angle")
        }
        val inside=EmplacementAimMath.direction(179f,68f)
        h.assertTrue(EmplacementAimMath.constrain(inside,bore,5.0).distanceTo(inside)<1e-9,"Allowed small aim correction was removed")
        val hmg=setup(h);h.assertTrue(hmg.fireNodePoseLimitDegrees==5.0,"HMG native pose limit is missing");hmg.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="aim_obstruction",timeoutTicks=10)
    fun muzzleCoverStopsSolutionDespiteClearCameraLine(h:GameTestHelper) {
        val d=setup(h,true);val muzzle=d.muzzlePosition(0);val wall=BlockPos.containing(muzzle.add(0.0,0.0,2.0))
        for(x in -2..2)for(y in -1..1)h.level.setBlock(wall.offset(x,y,0),Blocks.STONE.defaultBlockState(),2)
        val distant=muzzle.add(0.0,0.0,20.0);val camera=muzzle.add(0.0,4.5,-2.0)
        val cameraPoint=EmplacementAimTrace.trace(d,camera,distant.subtract(camera),camera.distanceTo(distant))
        h.assertTrue(cameraPoint.distanceTo(distant)<.001,"Reference camera was not above the obstruction")
        val solution=d.effectiveAimTarget(0)
        h.assertTrue(solution.distanceTo(muzzle)<3.5&&abs(solution.z-wall.z)<.002,"Ring did not land on the muzzle obstruction: $solution")
        h.assertTrue(d.firingDirection(0).lengthSqr()>.999,"Obstructed solution created an invalid launch direction")
        d.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="aim_recoil",timeoutTicks=50)
    fun hmgRecoilAccumulatesAtKickIsLargerAndBothRecover(h:GameTestHelper) {
        val hmg=setup(h);val at=setup(h,true,50);val p=mount(h,hmg,"KickHmg");val q=mount(h,at,"KickAt");var ticks=0;var sawHmg=false;var sawAt=false
        h.onEachTick {
            ticks++;hmg.acceptInput(p,0f,0f,ticks<16);at.acceptInput(q,0f,0f,at.shotSerial==0)
            if(!sawHmg&&hmg.shotSerial>0){h.assertTrue(abs(hmg.recoilPitch) in .23f.. .4f,"HMG first kick is not small");sawHmg=true}
            if(!sawAt&&at.shotSerial>0){h.assertTrue(abs(at.recoilPitch) in 4.3f..4.6f,"AT single-shot kick is not larger");sawAt=true}
            if(ticks==15) {
                h.assertTrue(hmg.recoilPitch<-.5f,"HMG rapid impulses did not accumulate")
                h.assertTrue(at.shotSerial==1&&abs(at.recoilPitch)<.12,"AT single kick did not recover promptly")
                h.assertTrue(abs(hmg.weaponPitch)<.001&&abs(at.weaponPitch)<.001,"Gameplay recoil changed the mechanical pitch")
            }
            if(ticks==38) {
                h.assertTrue(sawHmg&&sawAt&&abs(hmg.recoilPitch)<.005&&abs(at.recoilPitch)<.005,"Aim did not recover after release")
                p.discard();q.discard();hmg.discard();at.discard();h.succeed()
            }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_camera_impact",timeoutTicks=25)
    fun shotViewImpactIsBriefAndLeavesPlayerInputUnchanged(h:GameTestHelper) {
        val d=setup(h,true);val p=mount(h,d,"AtCameraKick");var shotAt=-1L;var sawKick=false
        p.yRot=0f;p.yRotO=0f;p.xRot=0f;p.xRotO=0f
        h.onEachTick {
            d.acceptInput(p,0f,0f,shotAt<0)
            if(shotAt<0&&d.shotSerial>0)shotAt=h.level.gameTime
            if(shotAt>=0) {
                val elapsed=h.level.gameTime-shotAt
                val camera=d.getCameraRotation(1f,p,false,false)
                val yawOffset=camera.x-p.getViewYRot(1f);val pitchOffset=camera.y-p.getViewXRot(1f)
                h.assertTrue(p.xRot==0f&&p.yRot==0f,"Camera impact permanently modified player mouse input")
                if(!sawKick&&d.reloadAge in 1..2) {
                    h.assertTrue(pitchOffset in -1.25f..-.30f&&abs(yawOffset)<.35f,"Camera lacks a short restrained upward impact: yaw=${camera.x}, pitch=${camera.y}, shotAge=${d.reloadAge}, elapsed=$elapsed")
                    h.assertTrue(d.cameraAimDirection(1f,p,false).y>0,"Central aiming ray did not follow the actual camera impact")
                    sawKick=true
                }
                if(elapsed>=10) {
                    h.assertTrue(sawKick&&abs(yawOffset)<.00001&&abs(pitchOffset)<.00001,"Camera failed to settle after its brief impact")
                    p.discard();d.discard();h.succeed()
                }
            }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_recoil_profile",timeoutTicks=10)
    fun cannonImpulseHasFastPeakAndFiniteRecovery(h:GameTestHelper) {
        h.assertTrue(EmplacementRecoil.aimEnvelope(.025)>.999&&EmplacementRecoil.aimEnvelope(.065)>.999,"Cannon peak is lost between server ticks")
        h.assertTrue(EmplacementRecoil.aimEnvelope(.13)>.7&&EmplacementRecoil.aimEnvelope(.34)<.11,"Cannon failed to snap up and recover in stages")
        for(age in listOf(-1.0,0.0,.50,3.0,Double.NaN,Double.POSITIVE_INFINITY))
            h.assertTrue(EmplacementRecoil.aimEnvelope(age)==0.0,"Cannon retained stale/invalid recoil")
        val early=EmplacementRecoil.cameraKick(.05,.1);val later=EmplacementRecoil.cameraKick(.10,.1)
        h.assertTrue(early.y<-.8&&abs(early.x-later.x)>.08,"Camera impact has no visible vibration")
        h.assertTrue(EmplacementRecoil.cameraKick(.42,.1)==net.minecraft.world.phys.Vec2.ZERO,"View shake outlived its impulse")
        val d=setup(h,true);val tag=CompoundTag();d.saveWithoutId(tag)
        tag.putInt("HellpodShotSerial",1)
        // Use the entity's real persisted shot clock, including a fractional render sample.
        tag.putInt("HmgShotCooldown",19);d.load(tag)
        val half=d.gameplayAimDirection(.5f);val bore=d.boreDirection(.5f)
        h.assertTrue(Math.toDegrees(kotlin.math.acos(half.normalize().dot(bore).coerceIn(-1.0,1.0)))>4.0,"Frame interpolation suppressed the cannon kick")
        val actual=d.firingDirection(0,.5f)
        h.assertTrue(Math.toDegrees(kotlin.math.acos(actual.dot(bore).coerceIn(-1.0,1.0)))<=5.001,"New recoil exceeded the native firing cone")
        d.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="aim_packet",timeoutTicks=10)
    fun targetPacketRoundTripsAndInvalidPointsCannotFire(h:GameTestHelper) {
        val d=setup(h);val p=mount(h,d,"AimPacket")
        val packet=HmgInputPacket(d.id,37f,-12f,true,d.position().add(20.0,3.0,35.0));val buffer=FriendlyByteBuf(Unpooled.buffer())
        try {packet.encode(buffer);h.assertTrue(packet==HmgInputPacket.decode(buffer),"Input lost its world-space camera aim target")} finally {buffer.release()}
        d.acceptInput(p,0f,0f,true,Vec3(Double.NaN,0.0,0.0));d.tick()
        d.acceptInput(p,0f,0f,true,d.position().add(10000.0,0.0,0.0));d.tick()
        h.assertTrue(d.shotSerial==0&&d.handlingYaw.isFinite()&&d.handlingPitch.isFinite(),"Bad camera target contaminated handling or fired a round")
        p.discard();d.discard();h.succeed()
    }
}
