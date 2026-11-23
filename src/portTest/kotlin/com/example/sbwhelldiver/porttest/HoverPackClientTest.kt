package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import org.lwjgl.glfw.GLFW
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.item.StratagemBackpacks
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.Difficulty
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent
import top.theillusivec4.curios.api.CuriosApi

@EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object HoverPackClientTest {
    private enum class Step { EQUIP, VANILLA_JUMP, WAIT_FOR_GROUND, LAUNCH, FLY, RECHARGE, RETEST }


    private val enabled = java.lang.Boolean.getBoolean("sbw_helldiver.hoverPackTests")
    private val launchedAt = System.nanoTime()
    private var creatingWorld = false
    @Volatile private var fixtureError: Throwable? = null
    private var failed = false
    private var setup = false
    private var step = Step.EQUIP
    private var elapsed = 0
    private var origin = Vec3.ZERO
    private var hoverStarted = 0
    private var hoverPosition = Vec3.ZERO
    private var flightAge = 0
    private var takeoffs = 0
    private var flightSounds = 0
    private var engineSound: SoundInstance? = null
    private var brakes = 0

    @SubscribeEvent
    fun sound(event: PlaySoundSourceEvent) {
        if (!enabled) return
        when (event.sound.location.path) {
            "sfx_hover_pack_launch" -> takeoffs++
            "sfx_hover_pack_loop" -> {
                flightSounds++
                engineSound = event.sound
            }
            "sfx_hover_pack_land" -> brakes++
        }
    }

    @SubscribeEvent
    fun tick(event: ClientTickEvent.Post) {
        if (!enabled || failed) return
        val mc = Minecraft.getInstance()
        try {
            check(System.nanoTime() - launchedAt < 150_000_000_000L) { "Client test timed out at $step" }
            fixtureError?.let { throw it }
            if (!creatingWorld && mc.screen is TitleScreen && mc.overlay == null) {
                creatingWorld = true
                mc.options.pauseOnLostFocus = false
                mc.options.cameraType = CameraType.THIRD_PERSON_BACK
                mc.createWorldOpenFlows().createFreshLevel("port-client-test",
                    LevelSettings("Hover Pack test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                        GameRules(), WorldDataConfiguration.DEFAULT), WorldOptions(5L, false, false),
                    { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },
                    mc.screen!!)
            }
            val player = mc.player ?: return
            val server = mc.singleplayerServer ?: return
            if (!setup) {
                setup = true
                server.execute {
                    try {
                        val level = server.overworld()
                        level.dayTime = 6000
                        level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, server)
                        for (x in -12..12) for (z in -12..36) {
                            level.setBlock(BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                        }
                        val host = checkNotNull(server.playerList.getPlayer(player.uuid))
                        host.teleportTo(level, .5, 80.0, .5, 0f, 0f)
                        val back = CuriosApi.getCuriosInventory(host).orElseThrow().getStacksHandler("back").orElseThrow().stacks
                        check(back.insertItem(0, ItemStack(ModItems.HOVER_PACK.get()), false).isEmpty)
                        host.setGameMode(GameType.SURVIVAL)
                    } catch (failure: Throwable) {
                        fixtureError = failure
                    }
                }
            }
            val item = ModItems.HOVER_PACK.get()
            val cooling = player.cooldowns.isOnCooldown(item)
            val jumping = mc.options.keyJump
            val forward = mc.options.keyUp
            if (mc.isPaused) return
            elapsed++
            when (step) {
                Step.EQUIP -> if (elapsed > 25 && player.onGround() && player.y > 79 &&
                    StratagemBackpacks.equipped(player)?.item === item) {
                    verifyInventoryModels(mc)
                    origin = player.position()
                    jumping.isDown = true
                    next(Step.VANILLA_JUMP)
                }
                Step.VANILLA_JUMP -> {
                    check(takeoffs == 0) { "Vanilla jumping started the pack" }
                    if (elapsed == 6) {
                        jumping.isDown = false
                        next(Step.WAIT_FOR_GROUND)
                    }
                }
                Step.WAIT_FOR_GROUND -> {
                    check(takeoffs == 0 && player.y - origin.y < 1.4 && !cooling)
                    if (elapsed > 24 && player.onGround()) {
                        origin = player.position()
                        pressBackpackKey(mc)
                        next(Step.LAUNCH)
                    }
                }
                Step.LAUNCH -> {
                    check(mc.screen == null) { "Backpack key opened another screen" }
                    if (player.y - origin.y > 1.45) next(Step.FLY)
                    check(elapsed < 15) { "Backpack key did not launch the hover pack" }
                }
                Step.FLY -> {
                    if (flightSounds == 1 && hoverStarted == 0) {
                        hoverStarted = elapsed
                        hoverPosition = player.position()
                        check(hoverPosition.y - origin.y in 5.4..6.5) { "Ascent stopped at ${hoverPosition.y - origin.y}" }
                        forward.isDown = true
                    }
                    if (hoverStarted > 0 && flightAge == 0) check(forward.isDown) { "Hover test lost its steering input" }
                    // Movement packets and the sound engine advance independently.
                    if (hoverStarted > 0 && flightAge == 0 && player.y < hoverPosition.y - .6) {
                        forward.isDown = false
                        flightAge = elapsed - hoverStarted
                        check(flightAge in 115..129) { "Expected six seconds of hover, got $flightAge ticks" }
                        val distance = player.z - hoverPosition.z
                        check(distance in 7.0..17.0) { "Hover steering was missing or too fast: $distance blocks" }
                    }
                    if (hoverStarted > 0 && flightAge == 0 && elapsed > hoverStarted + 6) {
                        check(player.y <= hoverPosition.y + .6) { "Hover climbed above its set altitude" }
                    }
                    if (cooling) {
                        check(player.y - origin.y < .6 && player.health == player.maxHealth) { "Cooldown or fall damage before landing" }
                        check(engineSound?.let(mc.soundManager::isActive) == false) {
                            "Hover engine loop continued after descent"
                        }
                        check(flightAge > 0 && takeoffs == 1 && flightSounds == 1 && brakes == 1) {
                            "Expected ascent, hover loop and descent sounds once, got $takeoffs/$flightSounds/$brakes"
                        }
                        next(Step.RECHARGE)
                    }
                    check(elapsed < 220) { "Hover never landed and recharged" }
                }
                Step.RECHARGE -> {
                    if (!cooling) {
                        check(elapsed in 235..246) { "Expected 12 seconds of cooldown, got $elapsed ticks" }
                        origin = player.position()
                        pressBackpackKey(mc)
                        next(Step.RETEST)
                    }
                    check(elapsed < 247)
                }
                Step.RETEST -> {
                    if (takeoffs == 2 && player.y - origin.y > 1.45) {
                        LOGGER.info("HOVER_PACK_CLIENT_PASSED: inventory models, backpack key activation, six seconds of hover, steering, native sampled audio, landing and 12-second recharge")
                        mc.stop()
                    }
                    check(elapsed < 15) { "Backpack key did not relaunch the hover pack" }
                }
            }
        } catch (failure: Throwable) {
            failed = true
            LOGGER.error("HOVER_PACK_CLIENT_FAILED at $step:$elapsed", failure)
            mc.stop()
        }
    }

    private fun pressBackpackKey(mc: Minecraft) {
        val window = mc.window.window
        mc.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_G, 0, GLFW.GLFW_PRESS, 0)
        mc.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_G, 0, GLFW.GLFW_RELEASE, 0)
    }

    private fun verifyInventoryModels(mc: Minecraft) {
        val packs = listOf(ModItems.GUARD_DOG_BACKPACK, ModItems.LASER_GUARD_DOG_BACKPACK,
            ModItems.STUN_GUARD_DOG_BACKPACK, ModItems.FLAME_GUARD_DOG_BACKPACK, ModItems.GAS_GUARD_DOG_BACKPACK,
            ModItems.HOVER_PACK)
        for (pack in packs) {
            val model = mc.itemRenderer.getModel(ItemStack(pack.get()), mc.level, mc.player, 0)
            val gui = model.applyTransform(ItemDisplayContext.GUI, PoseStack(), false)
            check(!gui.isCustomRenderer && gui.getQuads(null, null, net.minecraft.util.RandomSource.create()).isNotEmpty()) {
                "Empty or custom inventory model for ${pack.id}"
            }
            check(model.applyTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, PoseStack(), false).isCustomRenderer) {
                "Missing 3D held model for ${pack.id}"
            }
        }
    }

    private fun next(step: Step) {
        this.step = step
        elapsed = 0
    }
}
