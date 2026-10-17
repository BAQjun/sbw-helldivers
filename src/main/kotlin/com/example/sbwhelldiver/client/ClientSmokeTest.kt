package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.config.HelldiversConfig
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.example.sbwhelldiver.entity.HellpodCapEntity
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber

/** Opt-in development probe, in its own copied world; dormant in normal games. */
@EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object ClientSmokeTest {
    private var ticks = 0
    private var lastFrameNanos = 0L
    private val wreckFramesMs = mutableListOf<Double>()
    @SubscribeEvent fun profileWreckFrames(event: TickEvent.RenderTickEvent) {
        if (!java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest") || event.phase != TickEvent.Phase.START) return
        val now = System.nanoTime()
        if (ticks in 550..680 && lastFrameNanos > 0) {
            val elapsed = (now-lastFrameNanos)/1_000_000.0
            wreckFramesMs.add(elapsed)
            if (elapsed > 80) LOGGER.info("WRECK_LONG_FRAME: clientTick={}, ms={}",ticks,elapsed)
        }
        lastFrameNanos = now
    }
    private var started = false
    @Volatile private var wreckId: java.util.UUID? = null
    @Volatile private var holdWreckUntil = -1L
    private var sawNativeWreck = false
    private var sawSpentPod = false
    private var capturedNativeWreck = false
    @Volatile private var wreckExpired = false
    private val airborneCaps = mutableSetOf<java.util.UUID>()
    private val landedCaps = mutableSetOf<java.util.UUID>()
    private val bulletJoins = mutableMapOf<Boolean,Int>()
    val bulletSpawnMotion = java.util.concurrent.ConcurrentHashMap<Int, net.minecraft.world.phys.Vec3>()
    private val cannonSpawnMotion = java.util.concurrent.ConcurrentHashMap<Int, net.minecraft.world.phys.Vec3>()
    private val checkedCannonSpawns = mutableSetOf<Int>()
    @SubscribeEvent fun cannonJoin(event: net.minecraftforge.event.entity.EntityJoinLevelEvent) {
        if (!java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest") || event.level.isClientSide) return
        val shell = event.entity as? com.example.sbwhelldiver.entity.SentryCannonShellEntity ?: return
        cannonSpawnMotion[shell.id] = shell.deltaMovement
    }
    fun verifyCannonSpawn(shell: com.example.sbwhelldiver.entity.SentryCannonShellEntity) {
        if (checkedCannonSpawns.size >= 8 || shell.id in checkedCannonSpawns) return
        // Forge adds the entity to the client level before reading additional spawn data.
        // A later SBW motion packet may already include a gravity step before the first rendered frame.
        val decoded = shell.initialSpawnMotion ?: return
        val expected = cannonSpawnMotion[shell.id] ?: error("Missing server cannon spawn")
        check(decoded.distanceTo(expected) < 1e-9 && kotlin.math.abs(shell.getCustomGravity()-.03f)<1e-8)
        val exact = shell.deltaMovement
        shell.lerpMotion(exact.x.coerceIn(-3.9,3.9), exact.y.coerceIn(-3.9,3.9), exact.z.coerceIn(-3.9,3.9))
        check(shell.deltaMovement == exact) { "Cannon accepted vanilla's clipped velocity update" }
        shell.lerpMotion(exact.x.toFloat().toDouble(), exact.y.toFloat().toDouble(), exact.z.toFloat().toDouble())
        check(shell.deltaMovement.distanceTo(exact) < .000002) { "Native full-speed motion sync was rejected" }
        shell.lerpMotion(exact.x,exact.y,exact.z)
        checkedCannonSpawns.add(shell.id)
        if (checkedCannonSpawns.size == 8) LOGGER.info("CANNON_SPAWN_MOTION_PASSED: eight real shells use exact velocity/gravity, ignore clipped updates and accept native full-motion sync")
    }
    // Hold only this visual fixture briefly so frame/packet batching cannot skip the
    // wreck-material comparison. The production 0.3-second burn is verified server-side.
    @SubscribeEvent
    fun holdWreckMaterialFixture(event: TickEvent.ServerTickEvent) {
        if (!java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest") || event.phase != TickEvent.Phase.END) return
        val level = Minecraft.getInstance().singleplayerServer?.overworld() ?: return
        if (level.gameTime >= holdWreckUntil) return
        val wreck = wreckId?.let { level.getEntity(it) } as? SentryHellpodEntity ?: return
        if (wreck.phase == com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase.DESTROYED) wreck.health = 1f
    }

    @SubscribeEvent
    fun bulletJoin(event: net.minecraftforge.event.entity.EntityJoinLevelEvent) {
        if (!java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest") || event.entity.type != ModEntities.SENTRY_BULLET.get()) return
        val client = event.level.isClientSide
        if (!client && bulletSpawnMotion.size < 64) bulletSpawnMotion[event.entity.id] = event.entity.deltaMovement
        val n = bulletJoins[client] ?: 0
        if (n >= 8) return
        bulletJoins[client] = n+1
        LOGGER.info("BULLET_SPAWN_CHECK client={}: id={} pos={} velocity={}",client,event.entity.id,event.entity.position(),event.entity.deltaMovement)
    }
    @SubscribeEvent
    fun tick(event: TickEvent.ClientTickEvent) {
        if (!java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest") || event.phase != TickEvent.Phase.END) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val server = mc.singleplayerServer ?: return
        if (!started) {
            started = true
            mc.options.hideGui = true
            server.execute {
                val level = server.overworld()
                HelldiversConfig.sentryLifetimeTicks.set(400)
                HelldiversConfig.burialDepth.set(5.45)
                HelldiversConfig.capLifetimeTicks.set(600)
                level.getEntitiesOfClass(HellpodCapEntity::class.java,
                    net.minecraft.world.phys.AABB(500.0, 250.0, 500.0, 540.0, 300.0, 540.0)).forEach { it.discard() }
                level.dayTime = 1000
                for (x in 503..535) for (z in 507..536) level.setBlock(BlockPos(x, 256, z), Blocks.BEDROCK.defaultBlockState(), 2)
                val pods = listOf(ModEntities.MACHINE_GUN_SENTRY.get(), ModEntities.GATLING_SENTRY.get(),
                    ModEntities.ROCKET_SENTRY.get(), ModEntities.AUTOCANNON_SENTRY.get())
                pods.forEachIndexed { index, type ->
                    val px = 510.5 + 6 * index
                    val pod = type.create(level)!!
                    pod.ownerUUID = player.uuid
                    pod.moveTo(px, 290.0, 512.5, 0f, 0f)
                    level.addFreshEntity(pod)
                    val enemy = EntityType.HUSK.create(level)!!
                    enemy.isNoAi = true; enemy.isInvulnerable = true; enemy.isNoGravity = true
                    enemy.moveTo(px + if (index == 0) 4.0 else 0.0, 257.0 + if (index == 0) 2.0 else 0.0, 528.5, 0f, 0f)
                    level.addFreshEntity(enemy)
                }
                server.playerList.getPlayer(player.uuid)?.let {
                    it.gameMode.changeGameModeForPlayer(GameType.SPECTATOR)
                    it.teleportTo(level, 519.5, 263.0, 537.0, 180f, 18f)
                }
                LOGGER.info("CLIENT_SMOKE_STARTED")
            }
        }
        mc.mouseHandler.releaseMouse()
        ticks++
        if (ticks in 80..400) com.atsuishio.superbwarfare.client.ClientRenderHandler.bulletRenderOffset = player.eyePosition.add(50.0,20.0,-30.0)
        val caps = mc.level!!.entitiesForRendering().filterIsInstance<HellpodCapEntity>()
        caps.forEach { if (it.settled) landedCaps.add(it.uuid) else airborneCaps.add(it.uuid) }
        if (ticks == 150) server.execute {
            server.playerList.getPlayer(player.uuid)?.teleportTo(server.overworld(), 515.2, 261.0, 519.0, 145f, 24f)
        }
        if (ticks == 205) server.execute {
            server.playerList.getPlayer(player.uuid)?.teleportTo(server.overworld(), 511.45, 257.38, 513.9, 147f, 4f)
        }
        if (ticks == 240) server.execute {
            server.playerList.getPlayer(player.uuid)?.teleportTo(server.overworld(), 513.2, 257.05, 514.6, -90f, 0f)
        }
        if (ticks == 265) server.execute {
            server.playerList.getPlayer(player.uuid)?.teleportTo(server.overworld(), 526.8, 260.0, 516.8, 130f, 20f)
        }
        if (ticks == 335) server.execute {
            server.playerList.getPlayer(player.uuid)?.teleportTo(server.overworld(), 525.5, 257.35, 512.0, -90f, -5f)
        }
        if (ticks == 460) server.execute {
            server.playerList.getPlayer(player.uuid)?.teleportTo(server.overworld(), 519.5, 263.0, 537.0, 180f, 18f)
        }
        if (ticks in listOf(65, 75, 105, 180, 220, 250, 310, 350, 365, 375, 530, 760)) {
            Screenshot.grab(mc.gameDirectory, "sentry_test_$ticks.png", mc.mainRenderTarget) {
                LOGGER.info("CLIENT_SMOKE_SCREENSHOT: {}", it.string)
            }
            val pods = mc.level!!.entitiesForRendering().filterIsInstance<SentryHellpodEntity>()
            LOGGER.info("CLIENT_SMOKE_STATE {}: {}", ticks, pods.map { "${it.profile.id}:${it.phase}:age=${it.phaseAge}:reload=${it.reloading}:clip=${it.clipRemaining}" })
            LOGGER.info("CLIENT_SMOKE_AUDIO {}: {}", ticks, SoundTimeline.debugReport())
            LOGGER.info("CLIENT_SMOKE_CAPS {}: {}", ticks, caps.map { "${it.uuid}:settled=${it.settled}:pos=${it.position()}" })
        }
        if (ticks == 540) server.execute {
            val level = server.overworld()
            val wreck = ModEntities.AUTOCANNON_SENTRY.get().create(level)!!
            wreck.moveTo(520.5, 257.0955884, 522.5, 0f, 0f)
            val tag = net.minecraft.nbt.CompoundTag();wreck.saveWithoutId(tag)
            tag.putInt("HellpodPhase", com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase.ACTIVE.ordinal)
            tag.putDouble("HellpodRestY", wreck.y);tag.putBoolean("HellpodCapReleased", true)
            wreck.load(tag);level.addFreshEntity(wreck)
            wreckId = wreck.uuid
            server.playerList.getPlayer(player.uuid)?.teleportTo(level, 521.8, 258.5, 526.8, 165f, 15f)
        }
        if (ticks == 550) server.execute {
            val wreck = server.overworld().getEntity(requireNotNull(wreckId)) as SentryHellpodEntity
            wreck.hurt(server.overworld().damageSources().playerAttack(requireNotNull(server.playerList.getPlayer(player.uuid))), 100f)
            check(wreck.isWreck && wreck.health == 1f)
            holdWreckUntil = server.overworld().gameTime + 12
        }
        wreckId?.let { id ->
            val wreck = mc.level!!.entitiesForRendering().filterIsInstance<SentryHellpodEntity>().firstOrNull { it.uuid == id }
            if (wreck != null && wreck.phase == com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase.DESTROYED) {
                check(wreck.isWreck && wreck.health <= 1f && !wreck.firingRecently && !wreck.reloading)
                sawNativeWreck = true
                if (!capturedNativeWreck && HellpodRenderer.checkedIntactPodPass) {
                    capturedNativeWreck = true
                    Screenshot.grab(mc.gameDirectory, "sentry_wreck_pod_color.png", mc.mainRenderTarget) { LOGGER.info("WRECK_POD_COLOR_SCREENSHOT: {}", it.string) }
                }
            }
            if (wreck?.phase == com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase.SPENT) {
                check(!wreck.isWreck && wreck.health == 120f && wreck.boundingBox.ysize < .14)
                sawSpentPod = true
            }
        }
        if (ticks == 610) Screenshot.grab(mc.gameDirectory, "sentry_native_wreck.png", mc.mainRenderTarget) { LOGGER.info("WRECK_SCREENSHOT: {}", it.string) }
        if (ticks == 790) server.execute { wreckExpired = wreckId?.let { server.overworld().getEntity(it) == null } == true }
        if (ticks == 800) {
            check(HellpodRenderer.checkedWreckTextureCache && WreckTextureCache.preparedTextures.size == com.example.sbwhelldiver.asset.AssetCatalog.profiles.values.count { !it.isSupply })
            check(wreckFramesMs.isNotEmpty())
            val ordered = wreckFramesMs.sorted()
            LOGGER.info("WRECK_FRAME_PROFILE: frames={}, medianMs={}, p99Ms={}, maxMs={}",ordered.size,ordered[ordered.size/2],ordered[(ordered.size*.99).toInt().coerceAtMost(ordered.lastIndex)],ordered.last())
            check(checkedCannonSpawns.size >= 8 && SentryCannonShellRenderer.checkedVertices == 24) { "Cannon sync/geometry regressions were not exercised" }
            com.atsuishio.superbwarfare.client.ClientRenderHandler.bulletRenderOffset = null
            check(SentryBulletRenderer.checkedVertices == 24) { "Live tracer geometry was not verified" }
            check(SentryBulletRenderer.checkedSpawnMotion.size >= 8) { "Full-speed bullet spawn motion was not verified" }
            check(HellpodCapRenderer.Model.instance != null) { "Independent cap geometry was not loaded" }
            check(airborneCaps.size == 4 && landedCaps.size == 4 && caps.isEmpty()) {
                "Cap lifecycle failed: airborne=${airborneCaps.size}, landed=${landedCaps.size}, remaining=${caps.size}"
            }
            check(CasingParticle.created.keys.size == 3 && CasingParticle.grounded.keys.size == 3 && CasingParticle.rendered.size == 3) {
                "Casing particles failed: created=${CasingParticle.created}, landed=${CasingParticle.grounded}, rendered=${CasingParticle.rendered}"
            }
            LOGGER.info("CLIENT_CASINGS_PASSED: created={}, landed={}, rendered={}", CasingParticle.created, CasingParticle.grounded, CasingParticle.rendered)
            check(CasingParticle.machineGunThrown >= 20 && HellpodRenderer.checkedMachineGunShellShots >= 10) { "MG exit/trajectory was not verified" }
            LOGGER.info("MG_EJECTION_PASSED: {} cases cleared receiver; {} live rendered exit comparisons",CasingParticle.machineGunThrown,HellpodRenderer.checkedMachineGunShellShots)
            check(CasingParticle.cannonThrown >= 10) { "Cannon cases were not thrown clear of their port" }
            LOGGER.info("CANNON_EJECTION_PASSED: {} cases rose and moved clear of the top port", CasingParticle.cannonThrown)
            check(sawNativeWreck && sawSpentPod && wreckExpired && HellpodRenderer.checkedDestroyedPose && HellpodRenderer.checkedIntactPodPass) { "Wreck visual check: sawWreck=$sawNativeWreck sawSpent=$sawSpentPod expired=$wreckExpired closedPose=${HellpodRenderer.checkedDestroyedPose} intactColor=${HellpodRenderer.checkedIntactPodPass}" }
            LOGGER.info("SENTRY_NATIVE_WRECK_CLIENT_PASSED: native 1HP wreck burns and self-explodes; original-color pod renders separately; turret removed, eight iris blades closed, pod expires ten seconds later")
            SoundTimeline.verifySmokeTest()
            LOGGER.info("CLIENT_SMOKE_PASSED: 4 physical caps flew, landed and expired; cap audio, delayed cannon layers, fire stops and loop cleanup")
            LOGGER.info("CLIENT_SMOKE_COMPLETE: {}", SoundTimeline.debugReport())
            mc.stop()
        }
    }
}
