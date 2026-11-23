package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.item.StratagemBackpacks
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
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
object JumpPackClientTest {
    private enum class Step { EQUIP, TAP, WAIT_AFTER_TAP, HOLD, FLY, RECHARGE, RETEST }

    private val enabled = java.lang.Boolean.getBoolean("sbw_helldiver.jumpPackTests")
    private val launchedAt = System.nanoTime()
    private var creatingWorld = false
    @Volatile private var fixtureError: Throwable? = null
    private var setup = false
    private var step = Step.EQUIP
    private var elapsed = 0
    private var origin = Vec3.ZERO
    private var peak = 0.0
    private var takeoffs = 0
    private var flightSounds = 0
    private var photographedBraking = false
    private var brakes = 0

    @SubscribeEvent
    fun sound(event: PlaySoundSourceEvent) {
        if (!enabled) return
        when (event.sound.location.path) {
            "sfx_jump_pack_launch" -> takeoffs++
            "sfx_jump_pack_flight" -> flightSounds++
            "sfx_jump_pack_brake" -> brakes++
        }
    }

    @SubscribeEvent
    fun tick(event: ClientTickEvent.Post) {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        try {
            check(System.nanoTime() - launchedAt < 150_000_000_000L) { "Client test timed out at $step" }
            fixtureError?.let { throw it }
            if (!creatingWorld && mc.screen is TitleScreen && mc.overlay == null) {
                creatingWorld = true
                mc.options.pauseOnLostFocus = false
                mc.options.cameraType = CameraType.THIRD_PERSON_BACK
                mc.createWorldOpenFlows().createFreshLevel("port-client-test",
                    LevelSettings("Jump Pack test", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
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
                        for (x in -12..12) for (z in -12..24) {
                            level.setBlock(BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                        }
                        val host = checkNotNull(server.playerList.getPlayer(player.uuid))
                        host.teleportTo(level, .5, 80.0, .5, 0f, 0f)
                        val back = CuriosApi.getCuriosInventory(host).orElseThrow().getStacksHandler("back").orElseThrow().stacks
                        check(back.insertItem(0, ItemStack(ModItems.JUMP_PACK.get()), false).isEmpty)
                        host.setGameMode(GameType.SURVIVAL)
                    } catch (failure: Throwable) {
                        fixtureError = failure
                    }
                }
            }
            val item = ModItems.JUMP_PACK.get()
            val cooling = player.cooldowns.isOnCooldown(item)
            val jumping = mc.options.keyJump
            val forward = mc.options.keyUp
            if (mc.isPaused) return
            elapsed++
            when (step) {
                Step.EQUIP -> if (elapsed > 25 && player.onGround() && player.y > 79 &&
                    StratagemBackpacks.equipped(player)?.item === item) {
                    origin = player.position()
                    jumping.isDown = true
                    next(Step.TAP)
                }
                Step.TAP -> {
                    jumping.isDown = false
                    next(Step.WAIT_AFTER_TAP)
                }
                Step.WAIT_AFTER_TAP -> {
                    check(player.y - origin.y < 1.4 && !cooling && takeoffs == 0) { "Short tap triggered the pack" }
                    if (elapsed > 24 && player.onGround()) {
                        origin = player.position()
                        jumping.isDown = true
                        forward.isDown = true
                        next(Step.HOLD)
                    }
                }
                Step.HOLD -> {
                    peak = maxOf(peak, player.y - origin.y)
                    if (elapsed == 8) capture(mc, "launch")
                    if (elapsed >= 9) {
                        jumping.isDown = false
                        forward.isDown = false
                        next(Step.FLY)
                    }
                }
                Step.FLY -> {
                    peak = maxOf(peak, player.y - origin.y)
                    if (brakes > 0 && !photographedBraking) {
                        photographedBraking = true
                        capture(mc, "brake")
                    }
                    if (cooling) {
                        val distance = player.z - origin.z
                        check(peak >= 3.0 && distance >= 5.0 && kotlin.math.abs(player.x - origin.x) < 1.5) {
                            "Wrong launch trajectory: rise=$peak forward=$distance"
                        }
                        check(player.y - origin.y < .6 && player.health == player.maxHealth) {
                            "Cooldown started mid-air or braking allowed fall damage"
                        }
                        LOGGER.info("JUMP_PACK_CLIENT_FLIGHT rise={} forward={}", peak, distance)
                        next(Step.RECHARGE)
                    }
                    check(elapsed < 120) { "No landing cooldown, peak=$peak position=${player.position()}" }
                }
                Step.RECHARGE -> {
                    if (elapsed == 20) check(takeoffs == 1 && flightSounds == 1 && brakes == 1) {
                        "Expected one launch, flight and brake cue; got $takeoffs, $flightSounds, $brakes"
                    }
                    // A long press in cooldown does not launch.
                    if (elapsed == 25) jumping.isDown = true
                    if (elapsed == 33) jumping.isDown = false
                    if (elapsed <= 290) check(cooling && takeoffs == 1)
                    if (!cooling && elapsed > 290 && player.onGround()) {
                        check(elapsed in 297..305) { "Expected 300 cooldown ticks, got $elapsed" }
                        peak = 0.0
                        origin = player.position()
                        jumping.isDown = true
                        next(Step.RETEST)
                    }
                }
                Step.RETEST -> {
                    if (elapsed == 8) jumping.isDown = false
                    peak = maxOf(peak, player.y - origin.y)
                    if (peak >= 3.0 && takeoffs == 2) {
                        LOGGER.info("JUMP_PACK_CLIENT_PASSED: short tap, directional flight, three native cues, safe landing and 15-second recharge")
                        mc.stop()
                    }
                    check(elapsed < 50) { "Pack did not recharge" }
                }
            }
        } catch (failure: Throwable) {
            LOGGER.error("JUMP_PACK_CLIENT_FAILED at $step:$elapsed", failure)
            mc.stop()
        }
    }

    private fun capture(mc: Minecraft, phase: String) {
        Screenshot.grab(mc.gameDirectory, "jump-pack-$phase.png", mc.mainRenderTarget) {}
    }

    private fun next(value: Step) {
        step = value
        elapsed = 0
    }
}
