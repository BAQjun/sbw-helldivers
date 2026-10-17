package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.BurstClock
import com.example.sbwhelldiver.asset.AssetCatalog
import com.example.sbwhelldiver.entity.MortarBallistics
import com.example.sbwhelldiver.entity.MortarShellEntity
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
object MortarGameTests {
    @JvmStatic @GameTest(template="sentry_test",timeoutTicks=10)
    fun highArcSolvesNativeGravityAtAllRanges(helper: GameTestHelper) {
        val start=Vec3(0.0,3.0,0.0)
        for (distance in listOf(5.0,25.0,75.0,125.0)) for (height in listOf(-2.0,0.0,8.0)) {
            val target=Vec3(distance,height,0.0)
            val shot=MortarBallistics.solve(start,target) ?: throw IllegalStateException("Missing mortar arc $distance/$height")
            helper.assertTrue(kotlin.math.abs(shot.velocity.length()-MortarBallistics.SPEED)<1e-7,"Mortar speed solver changed muzzle velocity")
            val n=kotlin.math.floor(shot.flightTicks).toInt()
            val t=shot.flightTicks-n
            val actual=MortarBallistics.position(start,shot.velocity,n).lerp(MortarBallistics.position(start,shot.velocity,n+1),t)
            helper.assertTrue(actual.distanceTo(target)<.02,"Discrete native arc misses target: $actual/$target")
            helper.assertTrue(shot.velocity.y>shot.velocity.horizontalDistance(),"Low arc selected instead of high arc")
        }
        helper.assertTrue(MortarBallistics.solve(start,Vec3(250.0,0.0,0.0))==null,"Impossible mortar shot accepted")
        helper.succeed()
    }

    @JvmStatic @GameTest(template="sentry_test",timeoutTicks=10)
    fun requestedMortarRateAndScatterDisk(helper: GameTestHelper) {
        val profile = AssetCatalog.profiles.getValue("mortar_sentry")
        helper.assertTrue(profile.totalAmmo==54 && profile.burstSize==4,"Mortar capacity or four-round volley changed")
        val clock = BurstClock(); val times = mutableListOf<Int>()
        repeat(3600) { tick -> if(clock.advance(profile.burstSize,profile.burstInterval,profile.burstPause,tick.toLong())>0)times.add(tick) }
        helper.assertTrue(times.size==96,"Mortar must average 32 RPM across volley boundaries")
        helper.assertTrue(times.take(8)==listOf(0,15,30,45,150,165,180,195),"Mortar lost its rapid four-shot volley and compensating cooldown")
        val center=Vec3(100.0,200.0,100.0);val random=java.util.Random(23L)
        var squaredSum=0.0; val quadrants=IntArray(4)
        repeat(10000) {
            val sampled=MortarBallistics.scatterPoint(center,random.nextDouble(),random.nextDouble()).subtract(center)
            helper.assertTrue(sampled.y==0.0 && sampled.horizontalDistance()<=10.0,"Scatter escaped its horizontal radius")
            squaredSum+=sampled.horizontalDistanceSqr()
            quadrants[(if(sampled.x<0)2 else 0)+(if(sampled.z<0)1 else 0)]++
        }
        helper.assertTrue(squaredSum/10000 in 49.0..51.0 && quadrants.all { it in 2250..2750 },"Scatter is not uniform over the disk")
        helper.succeed()
    }

    @JvmStatic @GameTest(template="sentry_test",timeoutTicks=650)
    fun mortarShootsOverWallKeepsScatteredSalvoAndRetracts(helper: GameTestHelper) {
        for(x in 0..48) for(z in 0..48) helper.setBlock(BlockPos(x,120,z),Blocks.BEDROCK)
        val pos=helper.absolutePos(BlockPos(15,121,15))
        val pod=ModEntities.MORTAR_SENTRY.get().create(helper.level)!!
        pod.isInvulnerable=true
        pod.moveTo(pos.x+.5,pos.y+24.0+pod.profile.podHeight,pos.z+.5,0f,0f)
        helper.level.addFreshEntity(pod)
        val enemy=EntityType.HUSK.create(helper.level)!!.also {
            it.isNoAi=true;it.isInvulnerable=true
            it.moveTo(pos.x+.5,pos.y.toDouble(),pos.z+20.5,0f,0f);helper.level.addFreshEntity(it)
        }
        val expected=enemy.boundingBox.center
        for(x in 10..20) for(y in 121..125) helper.setBlock(BlockPos(x,y,25),Blocks.BEDROCK)
        val rounds=mutableListOf<MortarShellEntity>();val launches=mutableMapOf<Int,Pair<Vec3,Vec3>>();val times=mutableListOf<Long>()
        var loaded=false;var firstGone=false;var retracted=false
        var lockedYaw=0f;var lockedPitch=0f;var lockedMuzzle: Vec3? = null
        val listener=java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> { e ->
            val shell=e.entity as? MortarShellEntity
            if(e.level===helper.level && shell!=null && shell.owner===pod) {
                helper.assertTrue(shell.position().distanceTo(pod.getShootPos("Main",1f))<1e-5,"Shell does not originate at the bore")
                if (rounds.isEmpty()) {
                    lockedYaw=pod.turretYRot;lockedPitch=pod.turretXRot;lockedMuzzle=pod.getShootPos("Main",1f)
                }
                helper.assertTrue(kotlin.math.abs(pod.turretYRot-lockedYaw)<1e-5 && kotlin.math.abs(pod.turretXRot-lockedPitch)<1e-5,"Mortar moved its barrel between rounds of one volley")
                helper.assertTrue(shell.position().distanceTo(lockedMuzzle!!)<1e-5,"Fixed barrel changed its launch point during the volley")
                val saved = net.minecraft.nbt.CompoundTag(); pod.saveWithoutId(saved)
                val locked = Vec3(saved.getDouble("MortarTargetX"), saved.getDouble("MortarTargetY"), saved.getDouble("MortarTargetZ"))
                helper.assertTrue(locked.distanceTo(expected)<1e-5,"Four-shot salvo changed its locked center")
                val sampled = Vec3(saved.getDouble("MortarShotX"), saved.getDouble("MortarShotY"), saved.getDouble("MortarShotZ"))
                helper.assertTrue(sampled.subtract(expected).horizontalDistance()<=10.0,"Sampled mortar impact exceeded its 10m disk")
                rounds.add(shell);launches[shell.id]=shell.position() to shell.deltaMovement;times.add(helper.level.gameTime)
            }
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener)
        helper.onEachTick {
            if(!loaded && pod.phase==AbstractHellpodEntity.Phase.ACTIVE) {
                helper.assertTrue(pod.clipRemaining==54 && pod.reserveRemaining==0,"Wrong mortar capacity")
                pod.modifyGunData("Main") { it.ammo.set(4) };loaded=true
                helper.assertTrue(pod.validTarget(enemy),"Mortar wrongly requires direct line of sight through the wall")
            }
            if(!firstGone && rounds.isNotEmpty()) { enemy.discard();firstGone=true }
            if(rounds.size in 1..3) helper.assertTrue(kotlin.math.abs(pod.turretYRot-lockedYaw)<1e-5 && kotlin.math.abs(pod.turretXRot-lockedPitch)<1e-5,"Mortar aimed between shots instead of freezing its four-shot barrel pose")
            for(shell in rounds.filter { !it.isRemoved && it.tickCount in 1..60 }) {
                val launch=launches.getValue(shell.id)
                val planned=MortarBallistics.position(launch.first,launch.second,shell.tickCount)
                helper.assertTrue(shell.position().distanceTo(planned)<.005,"Mortar homed or diverged from its native trajectory: ${shell.position()}/$planned")
            }
            if(pod.phase==AbstractHellpodEntity.Phase.RETRACTING)retracted=true
            if(rounds.size==4 && rounds.all { it.impactPosition!=null } && pod.phase==AbstractHellpodEntity.Phase.DONE) {
                helper.assertTrue(times.zipWithNext().all { it.second-it.first == 15L },"Mortar four-round volley must fire every 0.75 seconds: $times")
                helper.assertTrue(rounds.all { val d=it.impactPosition!!.subtract(expected);d.horizontalDistance()<=10.15 },"Salvo impact exceeded its 10m locked-center scatter: ${rounds.map { it.impactPosition }}")
                helper.assertTrue(rounds.indices.all { i -> (0 until i).all { j -> rounds[i].impactPosition!!.distanceTo(rounds[j].impactPosition!!)>.01 } },"Mortar salvo failed to produce four distinct random impact points")
                helper.assertTrue(launches.values.map { it.second }.distinct().size==4,"Four fixed-barrel rounds reused one flight velocity")
                helper.assertTrue(retracted && !pod.isWreck && !pod.reloading,"Empty mortar failed its normal retirement")
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener)
                helper.runAfterDelay(202) { helper.assertTrue(pod.isRemoved,"Mortar pod did not expire ten seconds after closing");helper.succeed() }
            }
        }
        helper.runAtTickTime(645) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener);helper.fail("Mortar stalled: phase=${pod.phase}, shots=${pod.shotSerial}, pitch=${pod.turretAimPitch}/${pod.turretXRot}, rounds=${rounds.map { it.impactPosition }}, direction=${pod.getShootVec("Main",1f)}") }
    }
}
