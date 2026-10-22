package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.example.sbwhelldiver.entity.HellpodCapEntity
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.entity.StratagemBeacon
import com.mojang.authlib.GameProfile
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

/** Real SBW projectiles, magazines and synchronized state, in an isolated Forge test world. */
@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
object SentryGameTests {
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 350)
    fun gatlingSweepsWithoutPausingOrBendingBullets(helper: GameTestHelper) {
        // Remove the visibility enclosure left by an earlier run of this fixture.
        for (x in 0..30) for (z in 0..30) for (y in 221..227) helper.setBlock(BlockPos(x,y,z),Blocks.AIR)
        for (x in 0..30) for (z in 0..30) helper.setBlock(BlockPos(x,220,z),Blocks.BEDROCK)
        val p = helper.absolutePos(BlockPos(15, 221, 15))
        val pod = ModEntities.GATLING_SENTRY.get().create(helper.level)!!
        pod.isInvulnerable = true // Keep this aim fixture immune to concurrent blast tests.
        pod.moveTo(p.x+.5,p.y+24.0+pod.profile.podHeight,p.z+.5,0f,0f)
        helper.level.addFreshEntity(pod)
        fun enemy(dx: Double, dz: Double) = EntityType.HUSK.create(helper.level)!!.also {
            it.isNoAi = true; it.isInvulnerable = true; it.isNoGravity = true
            it.moveTo(p.x+.5+dx,p.y.toDouble(),p.z+.5+dz,0f,0f)
            helper.level.addFreshEntity(it)
        }
        val first = enemy(0.0, 12.0)
        var second: net.minecraft.world.entity.LivingEntity? = null
        var changedAt = -1L
        var lastSerial = 0
        var sweepTicks = 0
        var sweepShots = 0
        var stoppedAt = -1L
        val listener = java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> { event ->
            if (event.level === helper.level && event.entity.type == ModEntities.SENTRY_BULLET.get() &&
                event.entity.position().distanceToSqr(pod.getShootPos("Main", 1f)) < .0001 && changedAt >= 0) {
                helper.assertTrue((event.entity as net.minecraft.world.entity.projectile.Projectile).owner === pod,
                    "Sentry projectile owner mismatch: expected sentry=${pod.id}, actual=${(event.entity as net.minecraft.world.entity.projectile.Projectile).owner}, projectile=${event.entity.id}")
                val actual = event.entity.deltaMovement.normalize()
                helper.assertTrue(actual.dot(pod.getShootVec("Main", 1f).normalize()) > .99,
                    "Sweeping Gatling bullet bent toward the new target instead of following the barrel")
                sweepShots++
            }
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener)
        helper.runAtTickTime(300) {
            helper.fail("Gatling sweep stalled: phase=${pod.phase}, shots=${pod.shotSerial}, ammo=${pod.clipRemaining}, spin=${pod.spinning}, speed=${pod.rotorClock.speed}, firstRemoved=${first.isRemoved}, firstAlive=${first.isAlive}, valid=${pod.validTarget(first)}, pos=${pod.position()}, target=${first.position()}, dist=${pod.distanceTo(first)}, ray=${helper.level.clip(net.minecraft.world.level.ClipContext(pod.position().add(0.0,pod.profile.aimHeight,0.0),first.boundingBox.center,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,pod))}, aim=${pod.turretAimYaw}/${pod.turretAimPitch}, dir=${pod.getShootVec("Main",1f)}, changed=$changedAt, stopped=$stoppedAt")
        }
        helper.onEachTick {
            if (changedAt < 0 && pod.shotSerial >= 15) {
                second = enemy(12.0, 0.0); first.discard()
                changedAt = helper.level.gameTime; lastSerial = pod.shotSerial
            } else if (changedAt >= 0 && stoppedAt < 0) {
                val elapsed = helper.level.gameTime - changedAt
                if (elapsed in 1..7) {
                    val targetVec = pod.getShootPos("Main", 1f).vectorTo(second!!.boundingBox.center).normalize()
                    helper.assertTrue(pod.getShootVec("Main", 1f).normalize().dot(targetVec) < kotlin.math.cos(Math.toRadians(4.0)),
                        "Test did not observe a meaningful target-switch angle")
                    helper.assertTrue(pod.shotSerial > lastSerial && pod.spinning && pod.firingRecently,
                        "Gatling paused firing while turning to another visible target")
                    helper.assertTrue(kotlin.math.abs(pod.rotorClock.speed - pod.vehicleWeaponRpm("Main") / 10.0) < .001,
                        "Rotor slowed down during target switch")
                    sweepTicks++
                }
                lastSerial = pod.shotSerial
                if (elapsed >= 20) {
                    second!!.discard(); stoppedAt = helper.level.gameTime
                    // Hide unrelated targets in neighbouring tests once both fixture targets are gone.
                    for (dx in -2..2) for (dy in -1..6) for (dz in -2..2) {
                        if (kotlin.math.abs(dx) == 2 || kotlin.math.abs(dz) == 2)
                            helper.level.setBlock(BlockPos.containing(pod.x+dx, pod.y+dy, pod.z+dz), Blocks.BEDROCK.defaultBlockState(), 2)
                    }
                }
            } else if (stoppedAt >= 0 && helper.level.gameTime - stoppedAt >= 5) {
                helper.assertTrue(pod.shotSerial == lastSerial && !pod.spinning && sweepTicks == 7 && sweepShots >= 7,
                    "Gatling continued without any visible target, or sweep regression was not exercised")
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener)
                pod.discard(); helper.succeed()
            }
        }
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun cannonBurstDeadlineSurvivesTargetGapsAndReload(helper: GameTestHelper) {
        val clock = com.example.sbwhelldiver.entity.BurstClock()
        fun shot(at: Long) = clock.advance(3,10,20.0,at)
        helper.assertTrue(shot(0)==1 && shot(10)==1 && shot(20)==1,"Initial cannon burst failed")
        for (at in 21L..39L) helper.assertTrue(shot(at)==0,"New target bypassed the global one-second pause at $at")
        val savedPause = clock.cooldownTicks(30)
        val restored = com.example.sbwhelldiver.entity.BurstClock()
        restored.restore(clock.remaining,savedPause,1000)
        helper.assertTrue((1000L..1009L).all { restored.advance(3,10,20.0,it)==0 } &&
            restored.advance(3,10,20.0,1010)==1,"Reload cleared the remaining cooldown")
        helper.assertTrue(shot(100)==1 && shot(100)==0 && shot(109)==0 && shot(110)==1,
            "Idle time banked shots or lost the interval inside a burst")
        helper.succeed()
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 350)
    fun cannonRetargetAndReloadPreserveGlobalCooldown(helper: GameTestHelper) {
        for (x in 0..30) for (z in 0..30) helper.setBlock(BlockPos(x,260,z),Blocks.BEDROCK)
        val p = helper.absolutePos(BlockPos(15,261,15))
        var pod = ModEntities.AUTOCANNON_SENTRY.get().create(helper.level)!!
        pod.isInvulnerable = true
        pod.moveTo(p.x+.5,p.y+24.0+pod.profile.podHeight,p.z+.5,0f,0f)
        helper.level.addFreshEntity(pod)
        fun enemy() = EntityType.HUSK.create(helper.level)!!.also {
            it.isNoAi = true; it.isInvulnerable = true; it.isNoGravity = true
            it.moveTo(p.x+.5,p.y.toDouble(),p.z+16.5,0f,0f);helper.level.addFreshEntity(it)
        }
        var target = enemy()
        val shotTimes = mutableListOf<Long>()
        val listener = java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> { event ->
            if (event.level === helper.level && event.entity.type == ModEntities.SENTRY_CANNON_SHELL.get() &&
                (event.entity as net.minecraft.world.entity.projectile.Projectile).owner === pod) shotTimes.add(helper.level.gameTime)
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener)
        var changedAt = -1L
        var restored = false
        fun walls(block: net.minecraft.world.level.block.Block) {
            for (dx in -3..3) for (dy in 0..7) for (dz in -3..3) {
                if (kotlin.math.abs(dx)==3 || kotlin.math.abs(dz)==3)
                    helper.level.setBlock(BlockPos.containing(p.x+.5+dx,p.y+dy.toDouble(),p.z+.5+dz),block.defaultBlockState(),2)
            }
        }
        helper.runAtTickTime(300) { helper.fail("Cannon retarget cooldown stalled: phase=${pod.phase}, shots=$shotTimes, changed=$changedAt, restored=$restored") }
        helper.onEachTick {
            if (shotTimes.size >= 3 && changedAt < 0) {
                changedAt = shotTimes[2]; target.discard(); walls(Blocks.BEDROCK)
            }
            if (changedAt >= 0) {
                val elapsed = helper.level.gameTime-changedAt
                if (elapsed == 4L) { walls(Blocks.AIR);target=enemy() }
                if (elapsed == 6L && !restored) {
                    val tag=CompoundTag();pod.saveWithoutId(tag)
                    helper.assertTrue(tag.getInt("CannonBurstRemaining")==0 &&
                        tag.getDouble("CannonCooldownTicks") in 13.0..14.0,"The actual cannon did not save its pending pause")
                    pod.discard()
                    pod=ModEntities.AUTOCANNON_SENTRY.get().create(helper.level)!!
                    pod.load(tag);helper.level.addFreshEntity(pod);restored=true
                }
                if (elapsed in 1..19) helper.assertTrue(shotTimes.size==3,
                    "Cannon bypassed global cooldown on target switch/reload at $elapsed ticks: $shotTimes")
                if (shotTimes.size >= 6) {
                    helper.assertTrue(restored && shotTimes[3]-shotTimes[2]>=20 &&
                        shotTimes[1]-shotTimes[0]==10L && shotTimes[2]-shotTimes[1]==10L &&
                        shotTimes[4]-shotTimes[3]==10L && shotTimes[5]-shotTimes[4]==10L,
                        "Real cannon projectiles lost their global burst timing: $shotTimes")
                    net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener)
                    target.discard();pod.discard();helper.succeed()
                }
            }
        }
    }

    private const val FLOOR = 128
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun requestedFiringRatesIncludeBurstPauses(helper: GameTestHelper) {
        val cannon = com.example.sbwhelldiver.entity.BurstClock()
        val rocket = com.example.sbwhelldiver.entity.ShotClock()
        val shotTimes = mutableListOf<Int>()
        var rockets = 0
        repeat(3600) { tick ->
            if (cannon.advance(3, 10, 20.0, tick.toLong()) > 0) shotTimes.add(tick)
            rockets += rocket.advance(90)
        }
        helper.assertTrue(shotTimes.take(7) == listOf(0,10,20,40,50,60,80), "Cannon did not follow the one-second burst / one-second pause pattern")
        helper.assertTrue(shotTimes.size == 270, "Cannon must fire at 0/10/20 ticks and pause 20 ticks (90 RPM overall); got ${shotTimes.size} in 3 minutes")
        helper.assertTrue(rockets == 270, "Rocket must average 90 RPM; got $rockets in 3 minutes")
        shotTimes.zipWithNext().forEachIndexed { index, (a, b) ->
            val allowed = if ((index + 1) % 3 == 0) 20..20 else 10..10
            helper.assertTrue(b - a in allowed, "Burst pattern lost its three-round spacing: ${b - a}")
        }
        cannon.reset()
        helper.assertTrue(cannon.advance(3, 10, 20.0, 3600) == 1, "Explicit reset did not start a fresh clock")
        helper.succeed()
    }
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun turretTurnRatesUseDegreesPerSecond(helper: GameTestHelper) {
        for (type in listOf(ModEntities.MACHINE_GUN_SENTRY.get(), ModEntities.GATLING_SENTRY.get(),
            ModEntities.ROCKET_SENTRY.get(), ModEntities.AUTOCANNON_SENTRY.get())) {
            val pod = type.create(helper.level)!!
            val perSecond = if (pod.profile.isRocket || pod.profile.isCannon) 48.0 else 160.0
            val perTick = perSecond / 20.0
            helper.assertTrue(kotlin.math.abs(pod.turretTurnYSpeed-perTick)<.0001 &&
                kotlin.math.abs(pod.turretTurnXSpeed-perTick)<.0001, "Turn rate is in degrees/tick, not degrees/second")
            val pitch = Math.toRadians(80.0)
            val direction = net.minecraft.world.phys.Vec3(0.0,kotlin.math.sin(pitch),-kotlin.math.cos(pitch))
            pod.turretYRot = 0f; pod.turretXRot = 0f
            pod.turretAutoAimFromVector(direction)
            helper.assertTrue(kotlin.math.abs(kotlin.math.abs(pod.turretYRot)-perTick)<.001 &&
                kotlin.math.abs(kotlin.math.abs(pod.turretXRot)-perTick)<.001, "Native aim ignored the requested per-tick turn rate")
            repeat(19) { pod.turretAutoAimFromVector(direction) }
            helper.assertTrue(kotlin.math.abs(kotlin.math.abs(pod.turretYRot)-perSecond)<.001,
                "${pod.profile.id} turned ${pod.turretYRot} degrees in one second, expected $perSecond")
        }
        helper.succeed()
    }
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun gatlingRotorMatchesRpm(helper: GameTestHelper) {
        for (rpm in listOf(700, 1600, 2400)) {
            val clock = com.example.sbwhelldiver.entity.RotorClock()
            repeat(15) { helper.assertTrue(clock.advance(true,rpm) == 0, "Gatling fired during windup") }
            val begin = clock.angle
            var slots = 0
            repeat(1200) {
                slots += clock.advance(true,rpm)
                helper.assertTrue(kotlin.math.abs(clock.lastSlotAngle % 120) < 1e-6, "Firing slot missed the upper barrel station")
            }
            helper.assertTrue(slots == rpm, "Expected $rpm firing slots per minute, got $slots")
            helper.assertTrue(kotlin.math.abs(clock.angle-begin-rpm*120) < 1e-4, "Rotor speed does not match three barrels")
            repeat(13) { helper.assertTrue(clock.advance(false,rpm) == 0, "Gatling fired while coasting") }
            helper.assertTrue(clock.speed == 0.0, "Rotor failed to stop")
        }
        helper.succeed()
    }
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 300)
    fun detachedCapFallsPersistsAndExpires(helper: GameTestHelper) {
        // Other fixtures place elevated platforms; a drop uses the highest surface.
        // Keep this cap's landing platform above them, including when reusing a test world.
        val capFloor = 340
        for (x in 0..30) for (z in 0..30) helper.setBlock(BlockPos(x, capFloor, z), Blocks.BEDROCK)
        val p = helper.absolutePos(BlockPos(15, capFloor + 1, 15))
        val pod = ModEntities.SUPPLY_HELLPOD.get().create(helper.level)!!
        pod.moveTo(p.x + 0.5, p.y + 5.0, p.z + 0.5, 0f, 0f)
        helper.level.addFreshEntity(pod)
        var cap: HellpodCapEntity? = null
        var savedOnGround = false
        var sawAirborne = false
        helper.onEachTick {
            if (cap == null && pod.capReleased) {
                val debris = helper.level.getEntitiesOfClass(HellpodCapEntity::class.java, pod.boundingBox.inflate(10.0))
                    .filter { it.sourcePodUUID == pod.uuid }
                helper.assertTrue(debris.size == 1, "Opening must spawn exactly one independent cap")
                cap = debris.single()
                val tag = CompoundTag()
                cap!!.saveWithoutId(tag)
                tag.putInt("GroundLifetimeTicks", 40)
                cap!!.load(tag)
                // Deleting the parent must neither delete nor reset the flying cap.
                pod.discard()
            }
            cap?.let { lid ->
                if (!lid.settled && !lid.isRemoved) sawAirborne = true
                if (lid.groundedAge == 15 && !savedOnGround) {
                    helper.assertTrue(sawAirborne && lid.settled, "Cap did not fly and land on a block")
                    helper.assertTrue(kotlin.math.abs(lid.y - p.y) < 0.001, "Cap is floating / embedded in the floor: ${lid.y}, expected ${p.y}")
                    helper.assertTrue(lid.position().distanceToSqr(pod.position()) > 1.0, "Cap did not clear the pod mouth")
                    val tag = CompoundTag()
                    lid.saveWithoutId(tag)
                    val restored = ModEntities.HELLPOD_CAP.get().create(helper.level)!!
                    restored.load(tag)
                    helper.assertTrue(restored.groundedAge == 15 && restored.groundLifetimeTicks == 40 && restored.sourcePodUUID == pod.uuid,
                        "Cap reload lost its lifetime / parent identity")
                    lid.discard(); helper.level.addFreshEntity(restored)
                    cap = restored
                    savedOnGround = true
                }
                if (lid == cap && lid.isRemoved) {
                    helper.assertTrue(savedOnGround && lid.groundedAge == 40, "Cap expired before its ground lifetime")
                    helper.assertBlockPresent(Blocks.BEDROCK, BlockPos(15, capFloor, 15))
                    helper.succeed()
                }
            }
        }
    }
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 700)
    fun thrownBeaconDeploysRocket(helper: GameTestHelper) {
        for (x in 0..30) for (z in 0..30) helper.setBlock(BlockPos(x, FLOOR, z), Blocks.BEDROCK)
        val p = helper.absolutePos(BlockPos(15, FLOOR + 1, 15))
        val player = FakePlayerFactory.get(helper.level, GameProfile(UUID.randomUUID(), "SentryTest"))
        player.moveTo(p.x + 0.5, p.y + 0.01, p.z + 0.5, 0f, 85f)
        val stack = ItemStack(ModItems.BEACON.get())
        com.example.sbwhelldiver.stratagem.BeaconData.setItems(stack, listOf(ItemStack(ModItems.ROCKET_SENTRY.get())))
        val identity = com.example.sbwhelldiver.stratagem.BeaconData.ensureIdentity(stack)
        player.setItemInHand(InteractionHand.MAIN_HAND, stack)
        com.example.sbwhelldiver.stratagem.Stratagems.get("rocket_sentry")!!.code.forEach {
            com.example.sbwhelldiver.stratagem.BeaconInput.accept(player, InteractionHand.MAIN_HAND, identity, it)
        }
        helper.assertTrue(com.example.sbwhelldiver.stratagem.BeaconData.selected(stack)?.id == "rocket_sentry", "Code did not activate the loaded rocket")
        (ModItems.BEACON.get() as com.example.sbwhelldiver.item.BeaconItem).throwActivated(player, InteractionHand.MAIN_HAND, identity)
        helper.assertTrue(player.mainHandItem.count == 1 && com.example.sbwhelldiver.stratagem.BeaconData.selected(stack) == null,
            "Throw must retain the reusable beacon and clear its activation")
        helper.assertTrue(helper.level.getEntitiesOfClass(StratagemBeacon::class.java, player.boundingBox.inflate(4.0)).size == 1, "Item spawned no beacon / duplicate beacons")
        helper.succeedWhen {
            val sentries = helper.level.getEntitiesOfClass(SentryHellpodEntity::class.java,
                net.minecraft.world.phys.AABB(p).inflate(24.0, 140.0, 24.0))
                .filter { it.type == ModEntities.ROCKET_SENTRY.get() && it.phase == Phase.ACTIVE }
            helper.assertTrue(sentries.size == 1, "Expected exactly one deployed rocket sentry")
            helper.assertTrue(sentries.single().ownerUUID == player.uuid, "Beacon owner was not retained")
            sentries.single().discard()
        }
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 700)
    fun wallBlocksFire(helper: GameTestHelper) {
        for (x in 0..30) for (z in 0..30) helper.setBlock(BlockPos(x, FLOOR, z), Blocks.BEDROCK)
        val p = helper.absolutePos(BlockPos(15, FLOOR + 1, 7))
        val enemy = EntityType.HUSK.create(helper.level)!!
        enemy.isNoAi = true; enemy.isInvulnerable = true
        enemy.moveTo(p.x + 0.5, p.y.toDouble(), p.z + 16.5, 0f, 0f)
        helper.level.addFreshEntity(enemy)
        // Other tests also contain enemies nearby: enclose the sentry so none are visible.
        for (y in 1..6) {
            for (x in 12..18) for (z in listOf(4, 10)) helper.setBlock(BlockPos(x, FLOOR + y, z), Blocks.BEDROCK)
            for (z in 4..10) for (x in listOf(12, 18)) helper.setBlock(BlockPos(x, FLOOR + y, z), Blocks.BEDROCK)
        }
        val pod = ModEntities.MACHINE_GUN_SENTRY.get().create(helper.level)!!
        pod.moveTo(p.x + 0.5, p.y + 24.0 + pod.profile.podHeight, p.z + 0.5, 0f, 0f)
        helper.level.addFreshEntity(pod)
        helper.runAtTickTime(300) {
            val wall = helper.absolutePos(BlockPos(15, FLOOR + 3, 10))
            val ray = helper.level.clip(net.minecraft.world.level.ClipContext(
                pod.position().add(0.0, pod.profile.aimHeight, 0.0), enemy.boundingBox.center,
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, pod))
            com.example.sbwhelldiver.LOGGER.info("LOS_PROBE pod={} enemy={} wall={} block={} ray={} nativeMuzzle={}",
                pod.position(), enemy.position(), wall, helper.level.getBlockState(wall), ray.type, pod.getShootPos("Main", 1f))
            helper.assertTrue(pod.phase == Phase.ACTIVE && pod.shotSerial == 0, "Blocked sentry: phase=${pod.phase}, shots=${pod.shotSerial}")
            for (x in 12..18) for (y in 1..6) helper.setBlock(BlockPos(x, FLOOR + y, 10), Blocks.AIR)
            helper.succeedWhen {
                helper.assertTrue(pod.shotSerial >= 5, "Sentry did not acquire target after wall removal")
                enemy.discard(); pod.discard()
            }
        }
    }
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 1600)
    fun machineGunLifecycle(helper: GameTestHelper) = lifecycle(helper, ModEntities.MACHINE_GUN_SENTRY.get(), 263, false)
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 1600)
    fun gatlingLifecycle(helper: GameTestHelper) = lifecycle(helper, ModEntities.GATLING_SENTRY.get(), 750, false)
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 1600)
    fun rocketLifecycle(helper: GameTestHelper) = lifecycle(helper, ModEntities.ROCKET_SENTRY.get(), 48, true)
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 1600)
    fun autocannonLifecycle(helper: GameTestHelper) = lifecycle(helper, ModEntities.AUTOCANNON_SENTRY.get(), 36, false)

    private fun lifecycle(helper: GameTestHelper, type: EntityType<SentryHellpodEntity>, expected: Int, rocket: Boolean) {
        for (x in 0..30) for (z in 0..30) helper.setBlock(BlockPos(x, FLOOR, z), Blocks.BEDROCK)
        val origin = helper.absolutePos(BlockPos(15, FLOOR, 7))
        val surface = helper.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, origin.x, origin.z)
        val enemy = EntityType.HUSK.create(helper.level)!!
        enemy.isNoAi = true
        enemy.isInvulnerable = true
        enemy.isNoGravity = true
        enemy.moveTo(origin.x + 0.5, surface.toDouble(), origin.z + 16.5, 0f, 0f)
        helper.level.addFreshEntity(enemy)
        val pod = type.create(helper.level)!!
        pod.moveTo(origin.x + 0.5, surface + 24.0 + pod.profile.podHeight, origin.z + 0.5, 0f, 0f)
        helper.level.addFreshEntity(pod)
        if (rocket || pod.profile.isCannon) {
            val gun = pod.getGunData("Main")!!
            helper.assertTrue(gun.get(com.atsuishio.superbwarfare.data.gun.GunProp.DAMAGE) == if (rocket) 80.0 else 70.0, "Native gun direct damage did not update")
            helper.assertTrue(gun.get(com.atsuishio.superbwarfare.data.gun.GunProp.EXPLOSION_DAMAGE) == 20.0, "Native blast damage did not update")
            helper.assertTrue(gun.get(com.atsuishio.superbwarfare.data.gun.GunProp.EXPLOSION_RADIUS) == if (rocket) 4.0 else 5.0, "Native gun outer radius did not update")
        }
        val phases = mutableSetOf<Phase>()
        var reloads = 0
        var wasReloading = false
        var persistenceChecked = false
        var physicalShots = 0
        var previousSerial = 0
        var capChecked = false
        val shotTimes = mutableListOf<Int>()
        helper.onEachTick {
            phases.add(pod.phase)
            if (pod.phase == Phase.IMPACT) {
                val savedFlight = CompoundTag(); pod.saveWithoutId(savedFlight)
                helper.assertTrue(savedFlight.getLong("ScheduledArrival")-savedFlight.getLong("ScheduledLaunch")==95L,
                    "${pod.profile.id} did not fly for exactly 4.75 seconds")
            }
            if (pod.phase == Phase.ACTIVE && pod.shotSerial == 0) {
                helper.assertTrue(pod.clipRemaining+pod.reserveRemaining == expected, "${pod.profile.id} has the wrong total ammunition")
            }
            if (pod.phase == Phase.DEPLOYING && !capChecked) {
                val caps = helper.level.getEntitiesOfClass(HellpodCapEntity::class.java, pod.boundingBox.inflate(10.0))
                    .filter { it.sourcePodUUID == pod.uuid }
                helper.assertTrue(pod.capReleased && caps.size == 1, "${pod.profile.id}: missing / duplicate cap")
                val saved = CompoundTag()
                pod.saveWithoutId(saved)
                val restored = type.create(helper.level)!!
                restored.load(saved)
                helper.assertTrue(restored.capReleased, "Pod reload would eject a duplicate cap")
                capChecked = true
            }
            val newShots = (pod.shotSerial - previousSerial).coerceAtLeast(0)
            physicalShots += newShots
            if (newShots > 0 && pod.profile.isGatling) {
                helper.assertTrue(kotlin.math.abs(pod.rotorClock.lastSlotAngle % 120) < 1e-6,
                    "Gatling fired with no barrel at the upper station")
            }
            repeat(newShots) { shotTimes.add(pod.tickCount) }
            previousSerial = pod.shotSerial
            if (pod.reloading && !wasReloading) reloads++
            wasReloading = pod.reloading
            if (pod.reloading && pod.reloadAge == 20) {
                val saved = CompoundTag()
                pod.saveWithoutId(saved)
                val restored = type.create(helper.level)!!
                restored.load(saved)
                helper.assertTrue(restored.reloading && restored.reloadAge == 20, "Reload stage did not survive save/load")
                helper.assertTrue(restored.reserveRemaining == pod.reserveRemaining, "Reload reserve changed after load")
            }
            helper.assertTrue(!pod.reloading || rocket, "Only the rocket sentry may reload")
            if (!persistenceChecked && pod.shotSerial >= 4 && pod.phase == Phase.ACTIVE) {
                val saved = CompoundTag()
                pod.saveWithoutId(saved)
                val restored = type.create(helper.level)!!
                restored.load(saved)
                helper.assertTrue(restored.phase == pod.phase, "Loaded phase changed")
                helper.assertTrue(restored.getGunData("Main")!!.ammo.get() == pod.getGunData("Main")!!.ammo.get(), "Saved magazine was refilled/lost")
                helper.assertTrue(restored.reserveRemaining == pod.reserveRemaining, "Saved rocket reserve changed")
                persistenceChecked = true
            }
            if (pod.phase == Phase.DONE) {
                helper.assertTrue(phases.containsAll(listOf(Phase.FALLING, Phase.IMPACT, Phase.DEPLOYING, Phase.ACTIVE, Phase.RETRACTING)), "Missing lifecycle phase: $phases")
                helper.assertTrue(physicalShots == expected, "Expected $expected actual rounds, got $physicalShots")
                helper.assertTrue(pod.ejectedCasingCount == if (rocket) 0 else expected,
                    "${pod.profile.id}: casing count ${pod.ejectedCasingCount} does not match actual rounds")
                if (pod.profile.isCannon) {
                    shotTimes.zipWithNext().forEachIndexed { index, (a, b) ->
                        val allowed = if ((index + 1) % 3 == 0) 20..20 else 10..10
                        helper.assertTrue(b - a in allowed, "Cannon burst spacing at shot $index was ${b - a}, expected $allowed")
                    }
                }
                helper.assertTrue(reloads == if (rocket) 2 else 0, "Unexpected reload count $reloads")
                helper.assertTrue(persistenceChecked, "Never exercised saved active state")
                helper.assertTrue(helper.getBlockState(BlockPos(15, FLOOR, 7)).`is`(Blocks.BEDROCK), "Drop changed the ground")
                enemy.discard()
                pod.discard()
                helper.succeed()
            }
        }
        helper.runAtTickTime(1500) {
            helper.fail("Lifecycle stalled: phase=${pod.phase}, shots=${pod.shotSerial}, ammo=${pod.clipRemaining}, aim=${pod.turretAimYaw}/${pod.turretAimPitch}, muzzle=${pod.getShootPos("Main", 1f)}, direction=${pod.getShootVec("Main", 1f)}")
        }
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun sentryRangesAndRocketMinimumApplyToActualTargets(helper: GameTestHelper) {
        val p = helper.absolutePos(BlockPos(15, 200, 15))
        for (type in listOf(ModEntities.MACHINE_GUN_SENTRY.get(), ModEntities.GATLING_SENTRY.get(),
            ModEntities.ROCKET_SENTRY.get(), ModEntities.AUTOCANNON_SENTRY.get(), ModEntities.MORTAR_SENTRY.get())) {
            val pod = type.create(helper.level)!!
            pod.moveTo(p.x + 0.5, p.y.toDouble(), p.z + 0.5)
            val max = if (pod.profile.isMortar) 125.0 else if (pod.profile.isRocket || pod.profile.isCannon) 100.0 else 75.0
            val min = if (pod.profile.isRocket || pod.profile.isMortar) 10.0 else 0.0
            helper.assertTrue(pod.getMaxHealth() == 120f && pod.health == 120f, "Sentry did not start with exactly 120 health")
            helper.assertTrue(pod.data().compute().seekInfo!!.maxSeekRange == max &&
                pod.data().compute().seekInfo!!.minSeekRange == min, "Native range/minimum did not match requested metres")
            val enemy = EntityType.HUSK.create(helper.level)!!
            fun at(distance: Double): Boolean {
                enemy.moveTo(pod.x, pod.y, pod.z + distance)
                return pod.validTarget(enemy)
            }
            helper.assertTrue(at(max) && !at(max + .01), "Target range boundary is wrong for ${pod.profile.id}")
            helper.assertTrue(at(5.0) == !(pod.profile.isRocket || pod.profile.isMortar), "Rocket/mortar must reject targets within 10m")
            helper.assertTrue(at(10.0), "Rocket must still engage at its 10m boundary")
        }
        helper.succeed()
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun turretTakesHalfExplosionDamageIncludingItsOwnBlast(helper: GameTestHelper) {
        val p = helper.absolutePos(BlockPos(15, 200, 15))
        val player = FakePlayerFactory.get(helper.level, GameProfile(UUID.randomUUID(), "OwnBlastTest"))
        val pod = ModEntities.AUTOCANNON_SENTRY.get().create(helper.level)!!
        pod.moveTo(p.x + .5, p.y.toDouble(), p.z + .5)
        pod.ownerUUID = player.uuid
        helper.level.addFreshEntity(pod)
        val shell = ModEntities.SENTRY_CANNON_SHELL.get().create(helper.level)!!
        shell.setOwner(player)
        shell.setPos(pod.x, pod.y, pod.z)
        com.example.sbwhelldiver.entity.SentryExplosion.explode(shell, pod.position(), 2.0, false)
        helper.assertTrue(pod.health == 110f, "Own 20-point explosion must take exactly 10 HP, got ${120f-pod.health}")
        // Repeat use of one projectile cannot cause a second explosion or another damage pass.
        com.example.sbwhelldiver.entity.SentryExplosion.explode(shell, pod.position(), 2.0, false)
        helper.assertTrue(pod.health == 110f, "Explosion was applied twice")
        pod.hurt(com.atsuishio.superbwarfare.init.ModDamageTypes.causeGunFireDamage(helper.level.registryAccess(), null, EntityType.HUSK.create(helper.level)!!), 10f)
        helper.assertTrue(pod.health == 100f, "Explosion resistance incorrectly also reduced direct damage")
        val other = ModEntities.MACHINE_GUN_SENTRY.get().create(helper.level)!!
        val vanillaBlast = helper.level.damageSources().explosion(null, null)
        other.hurt(vanillaBlast, 20f)
        helper.assertTrue(other.health == 110f, "Vanilla explosions did not use the same 50% resistance")
        for (type in listOf(ModEntities.MACHINE_GUN_SENTRY.get(), ModEntities.GATLING_SENTRY.get(), ModEntities.ROCKET_SENTRY.get(), ModEntities.AUTOCANNON_SENTRY.get(), ModEntities.MORTAR_SENTRY.get())) {
            val sentry = type.create(helper.level)!!
            sentry.hurt(vanillaBlast, 20f)
            helper.assertTrue(sentry.health == 110f, "Every sentry must resist 50% explosion damage")
        }
        val mortar = ModEntities.MORTAR_SENTRY.get().create(helper.level)!!
        mortar.moveTo(p.x + 40.5, p.y.toDouble(), p.z + .5); helper.level.addFreshEntity(mortar)
        val ownRound = ModEntities.MORTAR_SHELL.get().create(helper.level)!!
        ownRound.setOwner(mortar); ownRound.setPos(mortar.position())
        ownRound.causeExplode(mortar.position())
        helper.assertTrue(mortar.health == 88f, "Mortar own 64-point blast must deal 32 damage after resistance")
        mortar.discard()
        pod.discard();helper.succeed()
    }

    @JvmStatic @GameTest(templateNamespace = "sbw_helldiver_flame", template = "sentry_test", timeoutTicks = 240)
    fun destroyedSentriesSelfExplodeAndLeaveIntactPods(helper: GameTestHelper) {
        val p = helper.absolutePos(BlockPos(15, 200, 15))
        val pods = listOf(ModEntities.MACHINE_GUN_SENTRY.get(), ModEntities.GATLING_SENTRY.get(),
            ModEntities.ROCKET_SENTRY.get(), ModEntities.AUTOCANNON_SENTRY.get()).mapIndexed { i, type ->
            type.create(helper.level)!!.also { pod ->
                pod.moveTo(p.x + i * 12.0, p.y.toDouble(), p.z.toDouble())
                val tag = CompoundTag();pod.saveWithoutId(tag)
                tag.putInt("HellpodPhase", Phase.ACTIVE.ordinal);tag.putBoolean("HellpodCapReleased", true)
                tag.putDouble("HellpodRestY", pod.y);pod.load(tag)
                helper.level.addFreshEntity(pod)
                pod.health = 5f
                pod.onHurt(20f, null, false)
                helper.assertTrue(pod.health == 5f && !pod.isWreck, "Native burn destroyed a working low-health sentry")
                pod.hurt(com.atsuishio.superbwarfare.init.ModDamageTypes.causeGunFireDamage(helper.level.registryAccess(), null, EntityType.HUSK.create(helper.level)!!), 100f)
                helper.assertTrue(pod.isWreck && pod.health == 1f && pod.phase == Phase.DESTROYED && !pod.isRemoved,
                    "Lethal hit did not immediately produce a native wreck at 1 HP")
            }
        }.toMutableList()
        val witnesses = pods.map { pod ->
            EntityType.VILLAGER.create(helper.level)!!.also {
                it.moveTo(pod.x+3, pod.y, pod.z);it.isNoAi=true;it.isNoGravity=true
                helper.level.addFreshEntity(it)
            }
        }
        var restoredWreck = false
        var restoredPod = false
        var sawNativeBurn = false
        val healthAfterBlast = FloatArray(4) { 20f }
        helper.onEachTick {
            for ((i,pod) in pods.withIndex()) {
                helper.assertTrue(pod.phase in listOf(Phase.DESTROYED, Phase.SPENT) && pod.shotSerial == 0 && !pod.reloading,
                    "Destroyed sentry resumed fire or played its normal retraction lifecycle")
                if (pod.phase == Phase.DESTROYED) {
                    helper.assertTrue(pod.isWreck && pod.health <= 1f && pod.phaseAge <= 7 && !pod.isRemoved,
                        "Native wreck failed to drain health and self-explode quickly")
                    if (pod.health < 1f) sawNativeBurn = true
                } else {
                    helper.assertTrue(!pod.isWreck && pod.health == 120f && pod.boundingBox.ysize < .14,
                        "Explosion destroyed/darkened the pod or left an invisible turret collider")
                    pod.hurt(helper.level.damageSources().explosion(null,null), 1000f)
                    helper.assertTrue(pod.health == 120f && (pod.phaseAge >= 208 || !pod.isRemoved),
                        "Surviving pod did not resist subsequent explosion damage")
                    if (pod.phaseAge == 4) {
                        helper.assertTrue(witnesses[i].health < 20f, "Native self-explosion was skipped")
                        healthAfterBlast[i] = witnesses[i].health
                    }
                    if (pod.phaseAge >= 20) helper.assertTrue(witnesses[i].health == healthAfterBlast[i],
                        "Native self-explosion repeated after the turret was gone")
                    if (pod.phaseAge < 208) helper.assertTrue(!pod.isRemoved, "Pod disappeared before ten seconds after iris closure")
                }
            }
            if (!restoredWreck && pods[0].phase == Phase.DESTROYED && pods[0].health < 1f && pods[0].health > -120f) {
                val tag=CompoundTag();pods[0].saveWithoutId(tag)
                val hp=pods[0].health;val age=pods[0].phaseAge
                pods[0].discard()
                val replacement=ModEntities.MACHINE_GUN_SENTRY.get().create(helper.level)!!
                replacement.load(tag);helper.level.addFreshEntity(replacement);pods[0]=replacement
                helper.assertTrue(replacement.phase==Phase.DESTROYED && replacement.health==hp && replacement.phaseAge==age,
                    "Reload reset the native wreck's burn progress")
                restoredWreck=true
            }
            if (!restoredPod && pods[0].phase == Phase.SPENT && pods[0].phaseAge >= 60) {
                val tag=CompoundTag();pods[0].saveWithoutId(tag);val age=pods[0].phaseAge
                pods[0].discard()
                val replacement=ModEntities.MACHINE_GUN_SENTRY.get().create(helper.level)!!
                replacement.load(tag);helper.level.addFreshEntity(replacement);pods[0]=replacement
                helper.assertTrue(replacement.phase==Phase.SPENT && !replacement.isWreck && replacement.health==120f && replacement.phaseAge==age,
                    "Reload respawned an exploded turret or reset the pod's expiry")
                restoredPod=true
            }
            if (pods.all { it.phase == Phase.SPENT && it.phaseAge >= 208 }) {
                helper.assertTrue(sawNativeBurn && restoredWreck && restoredPod && pods.all { it.isRemoved && it.phaseAge == 208 },
                    "Pod expiry was not exactly ten seconds after iris closure")
                witnesses.forEach { it.discard() };helper.succeed()
            }
        }
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 340)
    fun lifetimeRetractionAndClosedPodTenSecondExpiry(helper: GameTestHelper) {
        val p = helper.absolutePos(BlockPos(15, 230, 15))
        var pod = ModEntities.AUTOCANNON_SENTRY.get().create(helper.level)!!
        pod.moveTo(p.x.toDouble(), p.y.toDouble(), p.z.toDouble())
        val tag = CompoundTag();pod.saveWithoutId(tag)
        tag.putInt("HellpodPhase", Phase.ACTIVE.ordinal);tag.putBoolean("HellpodCapReleased", true)
        tag.putInt("HellpodActiveTicks", 2998);tag.putDouble("HellpodRestY", pod.y)
        pod.load(tag);helper.level.addFreshEntity(pod)
        helper.assertTrue(com.example.sbwhelldiver.config.HelldiversConfig.effectiveSentryLifetimeTicks() == 3000,
            "Default/legacy 10-minute setting did not use 150 seconds")
        var sawBeforeDeadline = false
        var sawRetracting = false
        var restored = false
        helper.onEachTick {
            if (pod.phase == Phase.ACTIVE) {
                val saved = CompoundTag();pod.saveWithoutId(saved)
                helper.assertTrue(saved.getInt("HellpodActiveTicks") in 2998..2999, "Sentry exceeded its 150-second deployed lifetime")
                if (saved.getInt("HellpodActiveTicks") == 2999) sawBeforeDeadline = true
            }
            if (pod.phase == Phase.RETRACTING) {
                val saved = CompoundTag();pod.saveWithoutId(saved)
                helper.assertTrue(saved.getInt("HellpodActiveTicks") == 3000 && !pod.isWreck && pod.health == 120f,
                    "Normal timeout incorrectly destroyed the sentry or fired after 150 seconds")
                sawRetracting = true
            }
            if (pod.phase == Phase.DONE) {
                helper.assertTrue(sawBeforeDeadline && sawRetracting && !pod.isWreck, "Timeout did not finish its ordinary retract animation")
                if (!restored && pod.phaseAge >= 70) {
                    val saved = CompoundTag();pod.saveWithoutId(saved)
                    val age = pod.phaseAge;pod.discard()
                    pod = ModEntities.AUTOCANNON_SENTRY.get().create(helper.level)!!
                    pod.load(saved);helper.level.addFreshEntity(pod)
                    helper.assertTrue(pod.phaseAge == age, "Closed pod reload reset its linger timer")
                    restored = true
                }
                if (pod.phaseAge < 200) helper.assertTrue(!pod.isRemoved, "Closed pod vanished before ten seconds")
                else {
                    helper.assertTrue(pod.phaseAge == 200 && pod.isRemoved && restored, "Closed pod did not disappear after ten seconds")
                    helper.succeed()
                }
            }
        }
    }
}



