package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.client.ItemDeliveryRackRenderer
import com.example.sbwhelldiver.client.MachineMesh
import com.example.sbwhelldiver.client.SoundTimeline
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.HellpodCapEntity
import com.example.sbwhelldiver.entity.ItemDeliveryHellpodEntity
import com.example.sbwhelldiver.entity.ItemDeliveryHellpodEntity.Side
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent
import java.util.UUID
import kotlin.math.atan2

@EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object ItemDeliveryClientTest {
    private val enabled = java.lang.Boolean.getBoolean("sbw_helldiver.itemDeliveryTests")
    private val launch = System.currentTimeMillis()
    private var started = false
    private var ticks = 0
    private val mechanismSounds = mutableMapOf<String, Int>()
    private var sawBraking = false
    private var sawCap = false
    private var sawOpenRackRise = false
    private var sawOverlappingMotion = false
    private var destructionRequested = false
    private var previousRetractPose: ItemDeliveryRackRenderer.RackPose? = null
    @Volatile private var podId: UUID? = null
    @Volatile private var breakPodId: UUID? = null
    @Volatile private var passed = false
    @Volatile private var failure: Throwable? = null

    @SubscribeEvent
    fun sound(event: PlaySoundEvent) {
        if (enabled && (event.name.startsWith("sfx_item_delivery_") || event.name.startsWith("sfx_equipment_beacon_"))) mechanismSounds.merge(event.name, 1, Int::plus)
    }

    @SubscribeEvent
    fun tick(event: ClientTickEvent.Post) {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        try {
            check(System.currentTimeMillis() - launch < 240_000) { "Timed out" }
            failure?.let { throw it }
            if (!started && mc.screen is TitleScreen && mc.overlay == null) {
                started = true
                mc.options.pauseOnLostFocus = false
                mc.createWorldOpenFlows().createFreshLevel("port-client-test",
                    LevelSettings("Item delivery test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        GameRules(), WorldDataConfiguration.DEFAULT),
                    WorldOptions(5L, false, false),
                    { access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },
                    mc.screen!!)
            }
            val player = mc.player ?: return
            val clientLevel = mc.level ?: return
            val server = mc.singleplayerServer ?: return
            val level = server.overworld()
            ticks++
            val pod = clientLevel.entitiesForRendering().filterIsInstance<ItemDeliveryHellpodEntity>().firstOrNull { it.uuid == podId }
            if (pod?.braking == true && pod.phase == Phase.FALLING) sawBraking = true
            if (clientLevel.entitiesForRendering().any { it is HellpodCapEntity }) sawCap = true
            if (pod?.phase == Phase.DEPLOYING) {
                val pose = requireNotNull(ItemDeliveryRackRenderer.sample(pod, 0f))
                check(pose.frontShutter == 0f && pose.rearShutter == 0f) { "A shutter rose with the deploying rack" }
                if (!sawOpenRackRise && pose.liftY in -1.4f..-.4f) {
                    sawOpenRackRise = true
                    capture(mc, "item-delivery-open-lift.png")
                }
            }
            if (pod?.phase == Phase.RETRACTING) {
                val pose = requireNotNull(ItemDeliveryRackRenderer.sample(pod, 0f))
                previousRetractPose?.let { previous ->
                    check(pose.liftY <= previous.liftY && pose.rearShutter >= previous.rearShutter)
                    if (!sawOverlappingMotion && pose.liftY < previous.liftY && pose.rearShutter > maxOf(.25f, previous.rearShutter)) {
                        sawOverlappingMotion = true
                        LOGGER.info("ITEM_DELIVERY_SHUTTER_OVERLAP: liftY={}, rearShutter={}", pose.liftY, pose.rearShutter)
                        capture(mc, "item-delivery-shutter-overlap.png")
                    }
                }
                previousRetractPose = pose
            }
            if (ticks in 185..222 || ticks in 237..299) {
                val target = pod?.toWorld(if (ticks in 185..196) Vec3(.5, 1.4, -.405)
                    else if (ticks < 250) Side.FRONT.mount else Side.REAR.mount)
                if (target != null) {
                    val delta = target.subtract(player.eyePosition)
                    player.yRot = Math.toDegrees(atan2(-delta.x, delta.z)).toFloat()
                    player.xRot = Math.toDegrees(atan2(-delta.y, delta.horizontalDistance())).toFloat()
                    player.yRotO = player.yRot
                    player.xRotO = player.xRot
                }
            }
            when (ticks) {
                1 -> server.execute {
                    try {
                        level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, server)
                        level.dayTime = 6000
                        for (x in -13..13) for (z in -13..13) for (y in 76..79) {
                            level.setBlock(BlockPos(x, y, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                        }
                        level.setDefaultSpawnPos(BlockPos(0, 80, -5), 0f)
                        server.playerList.getPlayer(player.uuid)!!.teleportTo(level, .5, 80.0, -4.5, 0f, 0f)
                        podId = spawn(level, .5).uuid
                    } catch (e: Throwable) {
                        failure = e
                    }
                }
                175 -> {
                    check(sawBraking && sawCap && pod?.phase == Phase.ACTIVE) { "Shared landing or cap ejection missing" }
                    check(sawOpenRackRise) { "Open rack did not rise above the pod" }
                    check(MachineMesh.batchCount("item_delivery_rack") == 5)
                    check(SoundTimeline.startedCount("sfx_hellpod_cap_release") > 0)
                    check(SoundTimeline.startedCount("sfx_hellpod_impact") > 0)
                    checkSoundLayers("deploy", 1)
                    checkSoundLayers("shutter", 0)
                    capture(mc, "item-delivery-front.png")
                }
                193 -> {
                    val entity = requireNotNull(pod)
                    check(mc.gameMode!!.interactAt(player, entity, EntityHitResult(entity), InteractionHand.MAIN_HAND).consumesAction().not())
                }
                205 -> {
                    val entity = requireNotNull(pod)
                    check(Side.entries.all { !entity.item(it).isEmpty })
                    check(mc.gameMode!!.interactAt(player, entity, EntityHitResult(entity), InteractionHand.MAIN_HAND).consumesAction())
                }
                235 -> {
                    val entity = requireNotNull(pod)
                    check(entity.item(Side.FRONT).isEmpty && !entity.item(Side.REAR).isEmpty && entity.phase == Phase.ACTIVE)
                    val now = clientLevel.gameTime.toDouble()
                    check(entity.shieldAge(Side.FRONT, now) >= ItemDeliveryHellpodEntity.SHUTTER_TICKS &&
                        entity.shieldAge(Side.REAR, now) == 0f)
                    check(player.inventory.items.any { it.item == gun("ak_47").item })
                    checkSoundLayers("shutter", 1)
                    capture(mc, "item-delivery-front-empty.png")
                }
                248 -> server.execute {
                    server.playerList.getPlayer(player.uuid)!!.teleportTo(level, .5, 80.0, 4.5, 180f, 0f)
                }
                276 -> capture(mc, "item-delivery-rear.png")
                283 -> {
                    val entity = requireNotNull(pod)
                    check(mc.gameMode!!.interactAt(player, entity, EntityHitResult(entity), InteractionHand.MAIN_HAND).consumesAction())
                }
                303 -> {
                    check(pod?.phase == Phase.RETRACTING && player.inventory.items.any { it.item == gun("mk_14").item })
                    check(sawOverlappingMotion) { "The rear shutter must rise while the rack is lowering" }
                    checkSoundLayers("shutter", 2)
                    capture(mc, "item-delivery-retract.png")
                }
                373 -> {
                    check(pod?.phase == Phase.DONE)
                    checkSoundLayers("retract", 1)
                    check(mechanismSounds.keys.none { it.startsWith("sfx_equipment_beacon_") })
                    server.execute {
                        try {
                            breakPodId = spawn(level, 6.5).uuid
                        } catch (e: Throwable) {
                            failure = e
                        }
                    }
                }
            }
            val secondId = breakPodId
            if (secondId != null && !destructionRequested) {
                val second = clientLevel.entitiesForRendering().filterIsInstance<ItemDeliveryHellpodEntity>()
                    .firstOrNull { it.uuid == secondId }
                if (second?.phase == Phase.ACTIVE) {
                    destructionRequested = true
                    server.execute {
                        try {
                            val landedPod = level.getEntity(secondId) as ItemDeliveryHellpodEntity
                            check(landedPod.phase == Phase.ACTIVE)
                            val state = landedPod.saveWithoutId(CompoundTag())
                            check(Side.entries.all { state.contains(it.stackKey) })
                            landedPod.load(state)
                            val attacker = server.playerList.getPlayer(player.uuid)!!
                            check(landedPod.hurt(level.damageSources().playerAttack(attacker), 10000f))
                            check(landedPod.isRemoved)
                            val drops = level.getEntitiesOfClass(ItemEntity::class.java, AABB.ofSize(landedPod.position(), 6.0, 6.0, 6.0))
                            check(drops.size == 2 && listOf("ak_47", "mk_14").all { id -> drops.count { it.item.item == gun(id).item } == 1 })
                            passed = true
                        } catch (e: Throwable) {
                            failure = e
                        }
                    }
                }
                check(ticks < 750) { "Second pod did not deploy: " + second?.phase + ", height " + second?.y }
            }
            if (passed) {
                LOGGER.info("ITEM_DELIVERY_PASSED: landing, separate cap, both clicks, shutters, inventory, retraction, sounds and destruction drops")
                mc.stop()
            }
        } catch (e: Throwable) {
            LOGGER.error("ITEM_DELIVERY_FAILED at tick {}", ticks, e)
            mc.stop()
        }
    }

    private fun checkSoundLayers(action: String, times: Int) {
        check(listOf("a", "b").all { (mechanismSounds["sfx_item_delivery_" + action + "_" + it] ?: 0) == times }) {
            "Expected both $action layers $times times: $mechanismSounds"
        }
    }

    private fun spawn(level: ServerLevel, x: Double): ItemDeliveryHellpodEntity {
        val pod = ModEntities.ITEM_DELIVERY_HELLPOD.get().create(level)!!
        val tag = pod.saveWithoutId(CompoundTag())
        tag.put(Side.FRONT.stackKey, gun("ak_47").save(level.registryAccess()))
        tag.put(Side.REAR.stackKey, gun("mk_14").save(level.registryAccess()))
        pod.load(tag)
        val surfaceY = 80.0
        val spawnY = surfaceY + AbstractHellpodEntity.descentSpawnHeight() + AbstractHellpodEntity.ORIGIN_ABOVE_BOTTOM
        pod.moveTo(x, spawnY, .5, 180f, 0f)
        pod.scheduleArrival(surfaceY, level.gameTime + AbstractHellpodEntity.FALL_TICKS)
        val now = level.gameTime.toDouble()
        val approachSpeed = pod.scheduledHeight(now) - pod.scheduledHeight(now + 1)
        check(approachSpeed > 6) { "Item delivery test did not use the full-height sentry approach" }
        LOGGER.info("ITEM_DELIVERY_FLIGHT: startY={}, approachBlocksPerTick={}, flightTicks={}",
            spawnY, approachSpeed, AbstractHellpodEntity.FALL_TICKS)
        check(level.addFreshEntity(pod))
        return pod
    }

    private fun gun(id: String): ItemStack = ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("superbwarfare", id))).also {
        check(!it.isEmpty) { "Missing SBW item $id" }
    }

    private fun capture(mc: Minecraft, name: String) =
        Screenshot.grab(mc.gameDirectory, name, mc.mainRenderTarget) { LOGGER.info("ITEM_DELIVERY_SCREENSHOT {}: {}", name, it.string) }
}
