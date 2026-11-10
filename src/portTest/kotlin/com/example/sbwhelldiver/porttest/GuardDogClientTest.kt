package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.client.GuardDogVisual
import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.GuardDogEntity
import com.example.sbwhelldiver.entity.ItemDeliveryHellpodEntity
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.item.GuardDogBackpack
import com.example.sbwhelldiver.stratagem.Stratagems
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.Util
import net.minecraft.client.CameraType
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Mth
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import top.theillusivec4.curios.api.CuriosApi
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Husk
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.EntityHitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import org.lwjgl.glfw.GLFW
import kotlin.math.abs
import kotlin.math.atan2

@EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
object GuardDogClientTest {
    private enum class Stage {
        DROP, PICKUP, VERIFY_CURIOS, EQUIP, DEPLOY, FIRE, REARM, RELAUNCH, DAMAGE, REPAIR, CHEST_LAUNCH, LEFT_RETURN, EMPTY_LAUNCH, EMPTY_RETURN,
        REMOVE_CHEST, CHECK_CHEST_REMOVAL, REMOVE_BACK, CHECK_BACK_REMOVAL, DONE
    }

    private val enabled = java.lang.Boolean.getBoolean("sbw_helldiver.guardDogTests")
    private var startedAt = 0L
    private var queued = false
    private var triedEquip = false
    private val captured = mutableSetOf<GuardDogEntity.Flight>()
    @Volatile private var stage = Stage.DROP
    private val recordedReloads = mutableSetOf<String>()
    private var backSlotCount = 0
    private var growTick = 0L
    private var recordFrom = Long.MAX_VALUE
    private var recordedTick = Long.MIN_VALUE
    private var frameIndex = 0
    private val motionFrames = mutableMapOf<GuardDogEntity.Flight, Int>()
    private var equipAt = Long.MAX_VALUE
    @Volatile private var failure: Throwable? = null
    @Volatile private var running = false
    @Volatile private var pressG = false
    @Volatile private var finished = false
    private lateinit var husk: Husk
    private var returnSeen = false
    private var dockSeen = false
    private var repairStarted = 0L
    private var phaseStartPosition: net.minecraft.world.phys.Vec3? = null
    private var patrolStartYaw: Float? = null
    private var sawDistantReturn = false
    private var sawDistantDock = false
    private lateinit var removedDrone: GuardDogEntity
    private var removedAt = 0L
    private var launchMoved = false
    private var dockMoved = false
    private val gunSounds = java.util.concurrent.atomic.AtomicInteger()
    private var handoffChecks = 0
    private var maximumHandoffStep = 0.0
    private data class RenderedPose(val entity: Int?, val phase: GuardDogEntity.Flight?, val age: Double,
        val time: Double, val position: net.minecraft.world.phys.Vec3)
    private var renderedPose: RenderedPose? = null
    private var previousRenderedPose: RenderedPose? = null
    private var poseLog: java.io.PrintWriter? = null
    private val magazineSides = mutableSetOf<String>()
    private var magazineLog: java.io.PrintWriter? = null

    private fun observeMagazine(side: String, time: Float, source: org.joml.Matrix4f,
        carried: org.joml.Matrix4f, socket: org.joml.Matrix4f, actual: org.joml.Matrix4f) {
        try {
            fun difference(a: org.joml.Matrix4f, b: org.joml.Matrix4f): Float {
                val points = listOf(org.joml.Vector3f(), org.joml.Vector3f(.014f, .171f, .074f),
                    org.joml.Vector3f(-.014f, -.015f, 0f))
                return points.maxOf { a.transformPosition(org.joml.Vector3f(it))
                    .distance(b.transformPosition(org.joml.Vector3f(it))) }
            }
            val first = GuardDogVisual.magazineTransferPose(source, carried, socket, .30f)
            val last = GuardDogVisual.magazineTransferPose(source, carried, socket, .90f)
            check(difference(first, source) < .00001f) { "Magazine jumped away from rack at pickup: $side" }
            check(difference(last, socket) < .00001f) { "Magazine missed its final slot: $side" }
            check(difference(GuardDogVisual.magazineTransferPose(source, carried, socket, .3001f), source) < .0001f)
            check(difference(GuardDogVisual.magazineTransferPose(source, carried, socket, .8999f), socket) < .0001f)
            check(actual.isFinite) { "Invalid magazine transform: $side at $time" }
            val position = actual.getTranslation(org.joml.Vector3f())
            magazineLog?.println("$side,$time,${position.x},${position.y},${position.z}")
            if (magazineSides.add(side)) LOGGER.info("GUARD_DOG_MAGAZINE_HANDOFF: {} rack pickup and seated transform are continuous", side)
        } catch (e: Throwable) { failure = e }
    }


    @SubscribeEvent
    fun sound(event: net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent) {
        if (!enabled || event.sound.location.toString() != "sbw_helldiver:guard_dog_fire") return
        try {
            check(event.sound.sound.location.toString() == "superbwarfare:gun/qbz_191/qbz_191_fire_3p")
            gunSounds.incrementAndGet()
            LOGGER.info("GUARD_DOG_GUN_SOUND: decoded sound attached to a playback channel")
        } catch (e: Throwable) { failure = e }
    }

    private fun observePose(drone: GuardDogEntity?, pose: org.joml.Matrix4f) {
        if (com.example.sbwhelldiver.client.ShaderPackCompat.shadowPass()) return
        val mc = Minecraft.getInstance()
        val level = mc.level ?: return
        val player = mc.player ?: return
        if (GuardDogBackpack.equipped(player) == null || mc.screen != null) return
        val partial = mc.timer.getGameTimeDeltaPartialTick(false)
        val camera = mc.gameRenderer.mainCamera.position
        val position = pose.getTranslation(org.joml.Vector3f())
        val world = net.minecraft.world.phys.Vec3(position.x.toDouble(), position.y.toDouble(), position.z.toDouble()).add(camera)
        val age = drone?.let { it.phaseAge + partial.toDouble() } ?: 0.0
        val sample = RenderedPose(drone?.id, drone?.flight, age, level.gameTime + partial.toDouble(), world)
        renderedPose = sample
        if (stage == Stage.DAMAGE && drone?.flight == GuardDogEntity.Flight.DOCK && !sawDistantDock) {
            sawDistantDock = true
            LOGGER.info("GUARD_DOG_DISTANT_DOCK: rendered the docking animation after recall")
        }
        val atFlightEnd = drone?.flight == GuardDogEntity.Flight.LAUNCH && age in 66.0..68.0
        val atDockStart = drone?.flight == GuardDogEntity.Flight.DOCK && age < 1.0
        if (drone != null && (atFlightEnd || atDockStart)) {
            val t = partial.toDouble()
            val expected = net.minecraft.world.phys.Vec3(
                net.minecraft.util.Mth.lerp(t, drone.xOld, drone.x),
                net.minecraft.util.Mth.lerp(t, drone.yOld, drone.y) + GuardDogEntity.MODEL_HEIGHT,
                net.minecraft.util.Mth.lerp(t, drone.zOld, drone.z))
            val scale = pose.getScale(org.joml.Vector3f())
            val error = world.distanceTo(expected)
            if (error > .04 || kotlin.math.abs(scale.x - 1f) > .01f ||
                kotlin.math.abs(scale.y - 1f) > .01f || kotlin.math.abs(scale.z - 1f) > .01f) {
                failure = IllegalStateException("Rendered flight handoff mismatch: phase=${drone.flight}, age=$age, error=$error, scale=$scale")
            }
        }
    }

    @SubscribeEvent
    fun tick(event: ClientTickEvent.Post) {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        try {
            failure?.let { throw it }
            if (startedAt == 0L && mc.screen is TitleScreen && mc.overlay == null) {
                startedAt = System.currentTimeMillis()
                GuardDogVisual.poseObserver = ::observePose
                GuardDogVisual.magazineObserver = ::observeMagazine
                magazineLog = mc.gameDirectory.resolve("guard-dog-magazine-motion.csv").printWriter().also {
                    it.println("side,time,x,y,z")
                }
                mc.options.fov().set(40)
                poseLog = mc.gameDirectory.resolve("guard-dog-rendered-motion.csv").printWriter().also {
                    it.println("time,entity,phase,age,x,y,z")
                }
                mc.options.pauseOnLostFocus = false
                mc.options.cameraType = CameraType.THIRD_PERSON_BACK
                mc.createWorldOpenFlows().createFreshLevel("port-client-test",
                    LevelSettings("Ballistic Guard Dog test", GameType.CREATIVE, false, Difficulty.NORMAL, true,
                        GameRules(), WorldDataConfiguration.DEFAULT), WorldOptions(5L, false, false),
                    { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },
                    mc.screen!!)
            }
            check(startedAt == 0L || System.currentTimeMillis() - startedAt < 150_000) { "Timed out at " + stage }
            val player = mc.player ?: return
            val clientLevel = mc.level ?: return
            val server = mc.singleplayerServer ?: return
            if (!queued) {
                queued = true
                server.execute {
                    try {
                        prepare(server.overworld(), server.playerList.getPlayer(player.uuid)!!)
                        running = true
                    } catch (e: Throwable) { failure = e }
                }
            }
            if (!running) return
            val pod = clientLevel.entitiesForRendering().filterIsInstance<ItemDeliveryHellpodEntity>().firstOrNull()
            if (pod != null && pod.phase == Phase.ACTIVE && equipAt == Long.MAX_VALUE) {
                val target = pod.toWorld(ItemDeliveryHellpodEntity.Side.FRONT.mount)
                val delta = target.subtract(player.eyePosition)
                player.yRot = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
                player.xRot = Math.toDegrees(atan2(-delta.y, delta.horizontalDistance())).toFloat()
                player.yRotO = player.yRot
                player.xRotO = player.xRot
                check(pod.item(ItemDeliveryHellpodEntity.Side.FRONT).item is GuardDogBackpack)
                check(pod.item(ItemDeliveryHellpodEntity.Side.REAR).isEmpty)
                capture(mc, "guard-dog-delivery.png")
                check(mc.gameMode!!.interactAt(player, pod, EntityHitResult(pod), InteractionHand.MAIN_HAND).consumesAction())
                equipAt = clientLevel.gameTime + 12
            }
            if (!triedEquip && clientLevel.gameTime >= equipAt && player.mainHandItem.item is GuardDogBackpack) {
                triedEquip = true
                mc.gameMode!!.useItem(player, InteractionHand.MAIN_HAND)
            }
            if (triedEquip && mc.screen is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) {
                player.closeContainer()
                return
            }
            val drone = clientLevel.entitiesForRendering().filterIsInstance<GuardDogEntity>().firstOrNull()
            val walking = stage in setOf(Stage.DEPLOY, Stage.REARM) &&
                drone?.flight in setOf(GuardDogEntity.Flight.LAUNCH, GuardDogEntity.Flight.DOCK) &&
                drone!!.phaseAge in 2..41
            val patrolWalking = stage == Stage.RELAUNCH && drone?.flight == GuardDogEntity.Flight.PATROL &&
                drone.phaseAge in 32..68
            if (walking) player.yRot += .8f
            if (patrolWalking) player.yRot += 3f
            mc.options.keyUp.setDown(patrolWalking || walking && drone?.flight == GuardDogEntity.Flight.LAUNCH)
            if (!sawDistantReturn && stage == Stage.DAMAGE && drone?.flight == GuardDogEntity.Flight.RETURN &&
                drone.distanceToSqr(player) > 35.0 * 35.0) {
                check(drone.shouldRenderAtSqrDistance(48.0 * 48.0))
                sawDistantReturn = true
                LOGGER.info("GUARD_DOG_DISTANT_RETURN: client tracked the off-screen approach")
            }
            mc.options.keyDown.setDown(walking && drone?.flight == GuardDogEntity.Flight.DOCK)
            if (drone?.flight == GuardDogEntity.Flight.DOCK && drone.reload.changesMagazine) {
                val time = drone.phaseAge - GuardDogEntity.TRANSITION_TICKS
                for (key in listOf(0, 5, 6, 7, 9, 11, 12, 15, 18, 24, 30)) {
                    val name = "guard-dog-reload-" + drone.reload.name.lowercase() + "-" + key
                    if (time >= key && recordedReloads.add(name)) capture(mc, "$name.png")
                }
            }
            if (drone != null && drone.phaseAge >= 12 && captured.add(drone.flight)) {
                capture(mc, "guard-dog-flight-" + drone.flight.name.lowercase() + ".png")
            }
            if (pressG && mc.screen == null && GuardDogBackpack.equipped(player) != null) {
                if (stage == Stage.EQUIP && recordFrom == Long.MAX_VALUE) {
                    player.xRot = -14f
                    player.xRotO = -14f
                    recordFrom = clientLevel.gameTime
                }
                if (stage == Stage.EQUIP && clientLevel.gameTime < recordFrom + 15) return
                pressG = false
                capture(mc, "guard-dog-equipped-" + stage + ".png")
                KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G))
                GuardDogVisual.pollKey()
            }
            if (finished) {
                check(motionFrames.getOrDefault(GuardDogEntity.Flight.LAUNCH, 0) >= 20 &&
                    motionFrames.getOrDefault(GuardDogEntity.Flight.DOCK, 0) >= 20) {
                    "The third-person recording missed a transition: $motionFrames"
                }
                check(magazineSides == setOf("reload_l", "reload_r")) { "Missing magazine handoff coverage: $magazineSides" }
                check(gunSounds.get() >= 2) { "Gunfire did not reach a decoded playback channel" }
                check(handoffChecks >= 9) { "Missing rendered handoffs: $handoffChecks" }
                check(sawDistantReturn && sawDistantDock) { "The distant recall did not reach a rendered dock" }
                poseLog?.close()
                magazineLog?.close()
                GuardDogVisual.poseObserver = null
                GuardDogVisual.magazineObserver = null
                LOGGER.info("GUARD_DOG_RENDER_HANDOFFS: {}, maximum displacement {}, gun sound channels {}",
                    handoffChecks, maximumHandoffStep, gunSounds.get())
                LOGGER.info("GUARD_DOG_MOTION: {} frames, {}", frameIndex, motionFrames)
                LOGGER.info("GUARD_DOG_PASSED: delivery, equipment, combat, right-rear formation, distant recall, reloads, both unequip paths")
                mc.stop()
                return
            }
            server.execute {
                if (failure != null) return@execute
                try { step(server.overworld(), server.playerList.getPlayer(player.uuid)!!) }
                catch (e: Throwable) { failure = e }
            }
        } catch (e: Throwable) {
            mc.options.keyUp.setDown(false)
            mc.options.keyDown.setDown(false)
            poseLog?.close()
            magazineLog?.close()
            GuardDogVisual.poseObserver = null
            GuardDogVisual.magazineObserver = null
            LOGGER.error("GUARD_DOG_FAILED at {}", stage, e)
            mc.stop()
        }
    }

    @SubscribeEvent
    fun recordFrame(event: RenderFrameEvent.Post) {
        if (!enabled || recordFrom == Long.MAX_VALUE || stage.ordinal > Stage.EMPTY_RETURN.ordinal) return
        val mc = Minecraft.getInstance()
        if (mc.screen != null || mc.options.cameraType.isFirstPerson) return
        val level = mc.level ?: return
        val tick = level.gameTime
        if (tick == recordedTick) return
        level.entitiesForRendering().filterIsInstance<GuardDogEntity>().firstOrNull()?.flight?.let {
            motionFrames[it] = motionFrames.getOrDefault(it, 0) + 1
        }
        recordedTick = tick
        renderedPose?.let { current ->
            val previous = previousRenderedPose
            poseLog?.println("${current.time},${current.entity},${current.phase},${current.age},${current.position.x},${current.position.y},${current.position.z}")
            if (previous != null && current.time > previous.time && current.time - previous.time <= 2.0 &&
                (previous.entity != current.entity || previous.phase != current.phase) &&
                (previous.phase == null || current.phase == null ||
                    previous.phase == GuardDogEntity.Flight.LAUNCH || current.phase == GuardDogEntity.Flight.DOCK)) {
                val distance = current.position.distanceTo(previous.position)
                handoffChecks++
                maximumHandoffStep = maxOf(maximumHandoffStep, distance)
                if (distance > .45 * (current.time - previous.time) + .05) {
                    failure = IllegalStateException("Visible coordinate snap: $previous -> $current, distance=$distance")
                }
            }
            previousRenderedPose = current
        }
        if (tick % 3L != 0L || renderedPose?.phase !in setOf(GuardDogEntity.Flight.LAUNCH, GuardDogEntity.Flight.DOCK)) return
        val dir = mc.gameDirectory.resolve("screenshots/guard-dog-motion-frames")
        if (frameIndex == 0) dir.mkdirs()
        val file = dir.resolve("frame-%04d.png".format(frameIndex++))
        val image = Screenshot.takeScreenshot(mc.mainRenderTarget)
        Util.ioPool().execute { image.use { it.writeToFile(file) } }
    }

    private fun prepare(level: ServerLevel, player: ServerPlayer) {
        level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, level.server)
        level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.server)
        level.dayTime = 6000
        for (x in -17..17) for (z in -17..17) {
            level.setBlock(BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
        }
        level.setDefaultSpawnPos(BlockPos(0, 80, -4), 0f)
        player.teleportTo(level, .5, 80.0, -3.5, 0f, 0f)
        val marker = ModEntities.STRATAGEM_MARKER.get().create(level)!!
        marker.moveTo(.5, 80.0, .5, 180f, 0f)
        marker.configure(Stratagems.get("guard_dog")!!.dropId, GuardDogEntity.CALL_TICKS, player.uuid, groundY = 80.0)
        check(level.addFreshEntity(marker))
        LOGGER.info("GUARD_DOG_DROP: marker at {}", marker.position())
        stage = Stage.PICKUP
    }

    private fun step(level: ServerLevel, player: ServerPlayer) {
        val pack = GuardDogBackpack.equipped(player)
        val drone = pack?.let(GuardDogBackpack::active)?.let(level::getEntity) as? GuardDogEntity
        when (stage) {
            Stage.DROP, Stage.DONE -> Unit
            Stage.PICKUP -> {
                val equipped = pack ?: return
                check(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty)
                val curios = CuriosApi.getCuriosInventory(player).orElseThrow()
                val back = curios.getStacksHandler("back").orElseThrow().stacks
                check(back.getStackInSlot(0) === equipped)
                backSlotCount = back.slots
                growTick = level.gameTime
                curios.growSlotType("back", 1)
                stage = Stage.VERIFY_CURIOS
            }
            Stage.VERIFY_CURIOS -> {
                if (level.gameTime <= growTick) return
                val curios = CuriosApi.getCuriosInventory(player).orElseThrow()
                val back = curios.getStacksHandler("back").orElseThrow().stacks
                if (back.slots == backSlotCount && level.gameTime < growTick + 10) return
                check(back.slots == backSlotCount + 1) { "Extra Curios slot did not activate" }
                val second = ItemStack(ModItems.GUARD_DOG_BACKPACK.get())
                check(back.insertItem(backSlotCount, second, true).count == second.count)
                check(!player.inventoryMenu.getSlot(6).mayPlace(second))
                check(pack != null && GuardDogBackpack.ammo(pack) == 45 && GuardDogBackpack.health(pack) == 100f)
                LOGGER.info("GUARD_DOG_CURIOS: equipped in back; another back slot and chest both refused a second backpack")
                stage = Stage.EQUIP
                pressG = true
            }
            Stage.EQUIP -> if (drone?.flight == GuardDogEntity.Flight.LAUNCH) {
                check(drone.belongsTo(player))
                phaseStartPosition = player.position()
                GuardDogBackpack.updateAmmo(pack, 2)
                husk = EntityType.HUSK.create(level)!!
                husk.setNoAi(true)
                husk.getAttribute(Attributes.MAX_HEALTH)!!.baseValue = 100.0
                husk.getAttribute(Attributes.ARMOR)!!.baseValue = 0.0
                husk.health = 100f
                husk.moveTo(3.5, 80.0, -1.5, 0f, 0f)
                check(level.addFreshEntity(husk))
                stage = Stage.DEPLOY
            }
            Stage.DEPLOY -> if (drone?.flight == GuardDogEntity.Flight.PATROL) {
                launchMoved = player.position().distanceToSqr(phaseStartPosition!!) > .16
                check(launchMoved) { "Player did not move during launch" }
                LOGGER.info("GUARD_DOG_LAUNCHED: {}", drone.position())
                stage = Stage.FIRE
            }
            Stage.FIRE -> {
                if (husk.health < 100f) {
                    check(husk.health == 90f) { "Expected exactly 10 damage, got " + (100f - husk.health) }
                    LOGGER.info("GUARD_DOG_HIT: {}", husk.health)
                    stage = Stage.REARM
                }
            }
            Stage.REARM -> {
                if (drone?.flight == GuardDogEntity.Flight.RETURN) returnSeen = true
                if (drone?.flight == GuardDogEntity.Flight.DOCK) {
                    check(drone.reload == GuardDogEntity.Reload.RIGHT)
                    if (!dockSeen) phaseStartPosition = player.position()
                    dockSeen = true
                    dockMoved = dockMoved || player.position().distanceToSqr(phaseStartPosition!!) > .16
                }
                if (drone == null && pack != null && GuardDogBackpack.ammo(pack) == 45) {
                    check(dockMoved) { "Player did not move during docking" }
                    check(GuardDogBackpack.spareMags(pack) == 7)
                    check(returnSeen && dockSeen) { "Ammunition return skipped a flight phase" }
                    husk.discard()
                    returnSeen = false
                    dockSeen = false
                    stage = Stage.RELAUNCH
                    pressG = true
                }
            }
            Stage.RELAUNCH -> if (drone?.flight == GuardDogEntity.Flight.PATROL && drone.phaseAge >= 24) {
                val startYaw = patrolStartYaw
                if (startYaw != null && drone.phaseAge < 94) return
                val offset = drone.position().subtract(player.position()).yRot(player.yBodyRot * Mth.DEG_TO_RAD)
                check(offset.x in -2.55..-1.85 && offset.z in -1.95..-1.25) {
                    "Guard Dog missed the right-rear formation: $offset"
                }
                if (startYaw == null) {
                    patrolStartYaw = player.yBodyRot
                    phaseStartPosition = player.position()
                    return
                }
                check(abs(Mth.wrapDegrees(player.yBodyRot - startYaw)) >= 45f)
                check(player.position().distanceToSqr(phaseStartPosition!!) > 1.0)
                LOGGER.info("GUARD_DOG_RIGHT_REAR: maintained formation before and after a moving turn, offset {}", offset)

                val farBehind = player.position().add(net.minecraft.world.phys.Vec3(0.0, 3.0, -42.0)
                    .yRot(-player.yBodyRot * Mth.DEG_TO_RAD))
                drone.setPos(farBehind)
                check(drone.hurt(level.damageSources().generic(), 101f))
                check(GuardDogBackpack.health(pack) == 0f)
                returnSeen = drone.flight == GuardDogEntity.Flight.RETURN
                stage = Stage.DAMAGE
            }
            Stage.DAMAGE -> {
                if (drone?.flight == GuardDogEntity.Flight.RETURN) returnSeen = true
                if (drone?.flight == GuardDogEntity.Flight.DOCK) {
                    check(drone.reload == GuardDogEntity.Reload.NONE)
                    dockSeen = true
                }
                if (drone == null && pack != null) {
                    check(GuardDogBackpack.spareMags(pack) == 7)
                    check(returnSeen && dockSeen) { "Damage return skipped a flight phase: return=$returnSeen dock=$dockSeen" }
                    repairStarted = level.gameTime
                    stage = Stage.REPAIR
                }
            }
            Stage.REPAIR -> if (pack != null && GuardDogBackpack.health(pack) == 100f) {
                check(level.gameTime - repairStarted in 48..62)
                val back = CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("back").orElseThrow().stacks
                player.inventoryMenu.carried = back.extractItem(0, 1, false)
                val chest = player.inventoryMenu.getSlot(6)
                check(chest.mayPlace(player.inventoryMenu.carried))
                player.inventoryMenu.clicked(6, 0, ClickType.PICKUP, player)
                check(player.getItemBySlot(EquipmentSlot.CHEST).item is GuardDogBackpack)
                check(!back.isItemValid(0, ItemStack(ModItems.GUARD_DOG_BACKPACK.get())))
                LOGGER.info("GUARD_DOG_CHEST: equipped via the armor slot; Curios refused a second backpack")
                stage = Stage.CHEST_LAUNCH
                pressG = true
            }
            Stage.CHEST_LAUNCH -> if (drone?.flight == GuardDogEntity.Flight.PATROL) {
                GuardDogBackpack.updateAmmo(pack, 12)
                drone.recall()
                dockSeen = false
                stage = Stage.LEFT_RETURN
            }
            Stage.LEFT_RETURN -> {
                if (drone?.flight == GuardDogEntity.Flight.DOCK) {
                    check(drone.reload == GuardDogEntity.Reload.LEFT)
                    dockSeen = true
                }
                if (drone == null && pack != null && GuardDogBackpack.spareMags(pack) == 6) {
                    check(dockSeen && GuardDogBackpack.ammo(pack) == GuardDogEntity.MAGAZINE)
                    repeat(6) { check(GuardDogBackpack.loadSpare(pack)) }
                    check(!GuardDogBackpack.loadSpare(pack))
                    stage = Stage.EMPTY_LAUNCH
                    pressG = true
                }
            }
            Stage.EMPTY_LAUNCH -> if (drone?.flight == GuardDogEntity.Flight.PATROL) {
                GuardDogBackpack.updateAmmo(pack, 8)
                drone.recall()
                dockSeen = false
                stage = Stage.EMPTY_RETURN
            }
            Stage.EMPTY_RETURN -> {
                if (drone?.flight == GuardDogEntity.Flight.DOCK) {
                    check(drone.reload == GuardDogEntity.Reload.EMPTY)
                    check(GuardDogBackpack.ammo(pack) == 8)
                    dockSeen = true
                }
                if (drone == null && pack != null) {
                    check(dockSeen && GuardDogBackpack.spareMags(pack) == 0 && GuardDogBackpack.ammo(pack) == 8)
                    stage = Stage.REMOVE_CHEST
                    pressG = true
                }
            }
            Stage.REMOVE_CHEST -> if (drone?.flight == GuardDogEntity.Flight.PATROL) {
                player.inventoryMenu.clicked(6, 0, ClickType.PICKUP, player)
                check(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty && player.inventoryMenu.carried.item is GuardDogBackpack)
                removedDrone = drone
                removedAt = level.gameTime
                stage = Stage.CHECK_CHEST_REMOVAL
            }
            Stage.CHECK_CHEST_REMOVAL -> {
                if (!removedDrone.isRemoved && level.gameTime <= removedAt + 1) return
                val carried = player.inventoryMenu.carried
                check(removedDrone.isRemoved && removedDrone.flight == GuardDogEntity.Flight.PATROL)
                check(GuardDogBackpack.active(carried) == null && GuardDogBackpack.ammo(carried) == 8)
                val back = CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("back").orElseThrow().stacks
                check(back.insertItem(0, carried, false).isEmpty)
                player.inventoryMenu.carried = ItemStack.EMPTY
                LOGGER.info("GUARD_DOG_UNEQUIP_CHEST: discarded an active patrol drone and cleared the carried backpack")
                stage = Stage.REMOVE_BACK
                pressG = true
            }
            Stage.REMOVE_BACK -> if (drone?.flight == GuardDogEntity.Flight.PATROL) {
                drone.recall()
                val back = CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("back").orElseThrow().stacks
                player.inventoryMenu.carried = back.extractItem(0, 1, false)
                removedDrone = drone
                removedAt = level.gameTime
                stage = Stage.CHECK_BACK_REMOVAL
            }
            Stage.CHECK_BACK_REMOVAL -> {
                if (!removedDrone.isRemoved && level.gameTime <= removedAt + 1) return
                val carried = player.inventoryMenu.carried
                check(removedDrone.isRemoved && removedDrone.flight == GuardDogEntity.Flight.RETURN)
                check(GuardDogBackpack.active(carried) == null && GuardDogBackpack.ammo(carried) == 8)
                LOGGER.info("GUARD_DOG_UNEQUIP_BACK: discarded a returning drone without docking or reloading")
                stage = Stage.DONE
                finished = true
            }
        }
    }

    private fun capture(mc: Minecraft, name: String) =
        Screenshot.grab(mc.gameDirectory, name, mc.mainRenderTarget) { LOGGER.info("GUARD_DOG_SCREENSHOT {}: {}", name, it.string) }
}
