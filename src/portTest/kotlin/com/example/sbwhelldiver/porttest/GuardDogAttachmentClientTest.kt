package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.client.GuardDogVisual
import com.example.sbwhelldiver.client.GuardDogAudio
import com.example.sbwhelldiver.client.MachineMesh
import com.example.sbwhelldiver.client.SentryBulletRenderer
import com.example.sbwhelldiver.entity.GuardDogMotion
import com.example.sbwhelldiver.entity.GuardDogEntity
import com.example.sbwhelldiver.entity.SentryBulletEntity
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.item.GuardDogBackpack
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.abs

/** Real firing with walking bob, then both moving docking handoffs. Disposable world only. */
@EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
object GuardDogAttachmentClientTest {
    private val enabled = java.lang.Boolean.getBoolean("sbw_helldiver.guardDogAttachmentTests")
    private var started = 0L
    private var prepared = false
    @Volatile private var ready = false
    @Volatile private var failure: Throwable? = null
    @Volatile private var kind = 0
    @Volatile private var stage = 0
    private var phaseAt = 0L
    private var shots = 0
    private val beamFrames = intArrayOf(0, 0)
    private val dockFrames = intArrayOf(0, 0)
    private var maxMuzzleError = 0.0
    private var maxShotError = 0.0
    private val pictures = mutableSetOf<String>()
    private var lastPose: Pair<GuardDogEntity, Matrix4f>? = null
    private var dockStart = Vec3.ZERO
    private var walks = 0
    private var meshMuzzle: Vector3f? = null
    private var anchoredTracers = 0
    private var laserAudioFrames = 0
    private var recalledAudioChecked = false

    private fun screenshot(mc: Minecraft, name: String) {
        if (pictures.add(name)) Screenshot.grab(mc.gameDirectory, "dog-attachment-$name.png", mc.mainRenderTarget) {}
    }

    @SubscribeEvent fun shot(e: EntityJoinLevelEvent) {
        if (!enabled || !ready || e.level.isClientSide || e.entity !is SentryBulletEntity) return
        val bullet = e.entity as SentryBulletEntity
        val player = bullet.owner as? net.minecraft.server.level.ServerPlayer ?: return
        val drone = GuardDogBackpack.equipped(player)?.let(GuardDogBackpack::active)
            ?.let(player.serverLevel()::getEntity) as? GuardDogEntity ?: return
        val error = bullet.launchPosition.distanceTo(drone.muzzleOrigin())
        maxShotError = maxOf(maxShotError, error); shots++
        if (error > .001) failure = IllegalStateException("Bullet missed the authored muzzle by $error")
    }

    @SubscribeEvent fun server(e: ServerTickEvent.Post) {
        if (!enabled || !ready || stage >= 4) return
        try {
            val server = Minecraft.getInstance().singleplayerServer ?: return
            val world = server.overworld(); val p = server.playerList.players.firstOrNull() ?: return
            val pack = GuardDogBackpack.equipped(p) ?: return
            val drone = GuardDogBackpack.active(pack)?.let(world::getEntity) as? GuardDogEntity
            when (stage) {
                0 -> if (drone?.flight == GuardDogEntity.Flight.PATROL) { stage = 1; phaseAt = world.gameTime }
                1 -> {
                    if (kind == 0) GuardDogBackpack.updateAmmo(pack, GuardDogEntity.MAGAZINE)
                    // Keep the weapon in the first-person camera while the player walks.
                    // Real AI, aim, bullets and laser collision still run against the husk.
                    drone?.setPos(p.x + 1.3, p.y + 2.4, p.z + 4.0)
                    if (world.gameTime - phaseAt > 130) { drone?.recall(); stage = 2 }
                }
                2 -> if (drone?.flight == GuardDogEntity.Flight.DOCK) { dockStart = p.position(); stage = 3 }
                3 -> if (drone == null) {
                    check(p.position().distanceTo(dockStart) > 3.0) { "Docking scene did not move quickly" }
                    if (kind == 0) {
                        check(shots > 10) { "No actual MG firing: $shots" }
                        kind = 1; stage = 0
                        p.teleportTo(world, .5, 80.0, .5, 0f, -7f)
                        p.setItemSlot(EquipmentSlot.CHEST, ItemStack(ModItems.LASER_GUARD_DOG_BACKPACK.get()))
                        GuardDogBackpack.toggle(p)
                    } else { stage = 4; phaseAt = world.gameTime }
                }
            }
        } catch (t: Throwable) { failure = t }
    }

    @SubscribeEvent fun render(e: RenderLevelStageEvent) {
        if (!enabled || !ready) return
        val mc = Minecraft.getInstance(); val player = mc.player ?: return
        if (e.stage == RenderLevelStageEvent.Stage.AFTER_SKY) { lastPose = null; meshMuzzle = null }
        if (e.stage == RenderLevelStageEvent.Stage.AFTER_SKY && stage == 3) {
            // Inject a late tracking position well outside the former eight-block
            // search range. The owner-relative pack animation must remain visible.
            mc.level?.entitiesForRendering()?.filterIsInstance<GuardDogEntity>()?.firstOrNull()?.let {
                it.setPos(player.position().add(24.0, 0.0, 0.0))
                it.xOld = it.x; it.yOld = it.y; it.zOld = it.z
            }
        }
        if (e.stage != RenderLevelStageEvent.Stage.AFTER_LEVEL) return
        val (drone, pose) = lastPose ?: return
        if (drone.flight == GuardDogEntity.Flight.DOCK && drone.phaseAge > GuardDogEntity.CONTACT_TICK) {
            val root = pose.getTranslation(Vector3f())
            if (root.length() >= 8f) failure = IllegalStateException("Docked drone rendered at late tracking position: $root")
            dockFrames[kind]++
        }
        if (drone.flight != GuardDogEntity.Flight.PATROL || stage != 1) return
        val rendered = meshMuzzle ?: return
        val camera = e.camera.position
        val actual = GuardDogVisual.firingMuzzle(drone, e.partialTick.getGameTimeDeltaPartialTick(false)).subtract(camera)
        val error = actual.distanceTo(Vec3(rendered.x.toDouble(), rendered.y.toDouble(), rendered.z.toDouble()))
        maxMuzzleError = maxOf(maxMuzzleError, error)
        if (error > .01) failure = IllegalStateException("Visible muzzle / shot origin separated by $error")
        if (!drone.isLaser || drone.beamLength > 0f) beamFrames[kind]++
    }

    @SubscribeEvent fun tick(e: ClientTickEvent.Post) {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        try {
            failure?.let { throw it }
            if (started == 0L && mc.screen is TitleScreen && mc.overlay == null) {
                started = System.currentTimeMillis(); mc.options.pauseOnLostFocus = false
                mc.options.bobView().set(true); mc.options.cameraType = CameraType.FIRST_PERSON
                GuardDogVisual.poseObserver = { drone, pose -> if (drone != null) lastPose = drone to Matrix4f(pose) }
                MachineMesh.guardDogMuzzleObserver = { _, muzzle -> meshMuzzle = Vector3f(muzzle) }
                SentryBulletRenderer.droneTracerObserver = { bullet, start, _ ->
                    val partial = mc.timer.getGameTimeDeltaPartialTick(false)
                    val travel = bullet.getPosition(partial).distanceTo(bullet.launchPosition)
                    if (bullet.tickCount == 0 && travel < 3.1) {
                        val emitter = mc.level?.getEntity(bullet.emitterId) as? GuardDogEntity
                        if (emitter != null) {
                            val error = start.distanceTo(GuardDogVisual.firingMuzzle(emitter, partial))
                            check(error < .005) { "Newborn tracer detached from visible muzzle: $error" }
                            anchoredTracers++
                        }
                    }
                }
                check(GuardDogMotion.foldTime(false, 59f) == 0f)
                check(GuardDogMotion.carrierAge(true, true, 7f) == 0f)
                check(GuardDogMotion.carrierAge(true, false, 60f) == 67f)
                mc.createWorldOpenFlows().createFreshLevel("port-client-test", LevelSettings("Dog attachment verification",
                    GameType.CREATIVE, false, Difficulty.NORMAL, true, GameRules(), WorldDataConfiguration.DEFAULT),
                    WorldOptions(22L, false, false), { it.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() }, mc.screen!!)
            }
            check(started == 0L || System.currentTimeMillis() - started < 180000) { "Timed out at $kind/$stage" }
            val player = mc.player ?: return; val server = mc.singleplayerServer ?: return
            if (!prepared) { prepared = true; server.execute { try {
                val world = server.overworld(); val p = server.playerList.getPlayer(player.uuid)!!
                world.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server)
                world.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, server); world.dayTime = 6000
                for (x in -45..45) for (z in -20..140) world.setBlock(BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                world.setDefaultSpawnPos(BlockPos(0, 80, 0), 0f); p.teleportTo(world, .5, 80.0, .5, 0f, -7f)
                p.setItemSlot(EquipmentSlot.CHEST, ItemStack(ModItems.GUARD_DOG_BACKPACK.get()))
                val enemy = EntityType.HUSK.create(world)!!; enemy.setNoAi(true)
                enemy.getAttribute(Attributes.MAX_HEALTH)!!.baseValue = 1000000.0; enemy.health = 1000000f
                enemy.getAttribute(Attributes.KNOCKBACK_RESISTANCE)!!.baseValue = 1.0
                enemy.moveTo(1.8, 80.0, 45.0, 0f, 0f); check(world.addFreshEntity(enemy))
                GuardDogBackpack.toggle(p); ready = true
            } catch (t: Throwable) { failure = t } } }
            if (!ready) return
            mc.options.keyUp.setDown(stage == 1 || stage == 3)
            player.yRot = 0f; player.yRotO = 0f; player.xRot = -7f; player.xRotO = -7f
            mc.options.cameraType = if (stage == 1) CameraType.FIRST_PERSON else CameraType.THIRD_PERSON_BACK
            if (stage == 3) { player.deltaMovement = Vec3(0.0, 0.0, 1.0); walks++ }
            if (stage == 1 && beamFrames[kind] > 30) screenshot(mc, "$kind-walking-fire")
            if (stage == 3 && dockFrames[kind] > 5) screenshot(mc, "$kind-moving-fold")
            if (kind == 1) {
                val drone = mc.level?.entitiesForRendering()?.filterIsInstance<GuardDogEntity>()?.firstOrNull()
                if (drone != null && stage == 1 && drone.beamLength > 0f && drone.phaseAge > 15 &&
                    GuardDogAudio.firingSounds(drone).let { it.size == 4 && it.all(mc.soundManager::isActive) })
                    laserAudioFrames++
                if (drone?.flight == GuardDogEntity.Flight.DOCK && drone.phaseAge > 5) {
                    check(GuardDogAudio.firingSounds(drone).isEmpty()) { "Rover shooting audio survived recall" }
                    recalledAudioChecked = true
                }
                if (drone?.flight == GuardDogEntity.Flight.LAUNCH) {
                    if (drone.phaseAge in 2..6) screenshot(mc, "rover-unfold-first")
                    if (drone.phaseAge in 16..22) screenshot(mc, "rover-lift-second")
                }
                if (drone?.flight == GuardDogEntity.Flight.DOCK) {
                    if (drone.phaseAge in 52..59) screenshot(mc, "rover-down-before-fold")
                    if (drone.phaseAge in 64..66) screenshot(mc, "rover-final-fold")
                }
            }
            if (stage == 4) {
                player.deltaMovement = Vec3.ZERO
                if (mc.level!!.gameTime - phaseAt < 30) return
                screenshot(mc, "rover-docked")
                check(beamFrames.all { it > 30 }) { "Insufficient visible firing $beamFrames" }
                check(dockFrames.all { it > 10 }) { "Docking drone missing $dockFrames" }
                check(anchoredTracers > 0) { "No newborn visible tracer verified" }
                check(laserAudioFrames > 20 && recalledAudioChecked) { "Rover audio channels not verified: $laserAudioFrames/$recalledAudioChecked" }
                LOGGER.info("ROVER_AUDIO_PASSED fourChannelsFrames={} stoppedOnRecall={}", laserAudioFrames, recalledAudioChecked)
                LOGGER.info("DOG_ATTACHMENT_PASSED shots={} meshMuzzleError={} spawnError={} movingDockFrames={} walks={} anchoredTracers={}",
                    shots, maxMuzzleError, maxShotError, dockFrames.toList(), walks, anchoredTracers)
                stop(mc)
            }
        } catch (t: Throwable) { LOGGER.error("DOG_ATTACHMENT_FAILED $kind/$stage", t); stop(mc) }
    }
    private fun stop(mc: Minecraft) { mc.options.keyUp.setDown(false); GuardDogVisual.poseObserver = null;
        MachineMesh.guardDogMuzzleObserver = null; SentryBulletRenderer.droneTracerObserver = null; mc.stop() }
}
