package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.item.StratagemBackpacks
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
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
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent
import kotlin.math.abs
import top.theillusivec4.curios.api.CuriosApi

@EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object WarpPackClientTest {
    private enum class Step { EQUIP, TAP, RELEASE, SAFE, KEEP_HOLDING, WAIT_RISK, RISK, WAIT_LETHAL, LETHAL, DONE }

    private val enabled = java.lang.Boolean.getBoolean("sbw_helldiver.warpPackTests")
    private val started = System.nanoTime()
    private var creating = false
    private var prepared = false
    private var step = Step.EQUIP
    private var elapsed = 0
    private var origin = Vec3.ZERO
    private var burstAt = -100L
    private var departures = 0
    private var arrivals = 0
    private val captures = mutableSetOf<String>()
    private var failed = false
    @Volatile private var preparationError: Throwable? = null

    @SubscribeEvent
    fun tick(event: ClientTickEvent.Post) {
        if (!enabled || failed) return
        val mc = Minecraft.getInstance()
        try {
            check(System.nanoTime() - started < 130_000_000_000L) { "Warp test timed out" }
            preparationError?.let { throw it }
            if (!creating && mc.screen is TitleScreen && mc.overlay == null) {
                creating = true
                mc.options.pauseOnLostFocus = false
                mc.options.cameraType = CameraType.THIRD_PERSON_BACK
                mc.createWorldOpenFlows().createFreshLevel("port-client-test",
                    LevelSettings("Warp Pack test", GameType.CREATIVE, false, Difficulty.NORMAL, true,
                        GameRules(), WorldDataConfiguration.DEFAULT), WorldOptions(5L, false, false),
                    { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },
                    mc.screen!!)
            }

            val player = mc.player ?: return
            val clientLevel = mc.level ?: return
            val server = mc.singleplayerServer ?: return
            if (!prepared) {
                prepared = true
                server.execute {
                    try {
                        val level = server.overworld()
                        level.dayTime = 13000
                        level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, server)
                        level.gameRules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server)
                        for (x in -5..12) for (z in -5..35) {
                            level.setBlock(BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                        }
                        for (z in -4..34 step 4) {
                            level.setBlock(BlockPos(-4, 79, z), Blocks.SHROOMLIGHT.defaultBlockState(), 3)
                            level.setBlock(BlockPos(8, 79, z), Blocks.SHROOMLIGHT.defaultBlockState(), 3)
                        }
                        val host = checkNotNull(server.playerList.getPlayer(player.uuid))
                        host.teleportTo(level, .5, 80.0, .5, 0f, 0f)
                        val back = CuriosApi.getCuriosInventory(host).orElseThrow().getStacksHandler("back").orElseThrow().stacks
                        check(back.insertItem(0, ItemStack(ModItems.WARP_PACK.get()), false).isEmpty)
                        host.setGameMode(GameType.SURVIVAL)
                    } catch (failure: Throwable) {
                        preparationError = failure
                    }
                }
            }

            if (mc.isPaused) return
            elapsed++
            val jump = mc.options.keyJump
            val pack = StratagemBackpacks.equipped(player)
            when (step) {
                Step.EQUIP -> if (elapsed > 30 && player.onGround() && player.y > 79 && pack?.item === ModItems.WARP_PACK.get()) {
                    val model = mc.itemRenderer.getModel(pack, clientLevel, player, 0)
                    val gui = model.applyTransform(ItemDisplayContext.GUI, PoseStack(), false)
                    check(!gui.isCustomRenderer && gui.getQuads(null, null, net.minecraft.util.RandomSource.create()).isNotEmpty())
                    check(model.applyTransform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, PoseStack(), false).isCustomRenderer)
                    capture(mc, "warp-pack-mounted")
                    origin = player.position()
                    jump.isDown = true
                    next(Step.TAP)
                }
                Step.TAP -> {
                    jump.isDown = false
                    next(Step.RELEASE)
                }
                Step.RELEASE -> {
                    check(player.position().subtract(origin).horizontalDistanceSqr() < 1.0e-6)
                    if (elapsed > 24 && player.onGround()) {
                        mc.options.cameraType = CameraType.FIRST_PERSON
                        val camera = ArmorStand(clientLevel, 8.5, 82.6, 5.5)
                        camera.yRot = 90f
                        camera.xRot = 19f
                        camera.setYHeadRot(90f)
                        camera.setYBodyRot(90f)
                        camera.setOldPosAndRot()
                        camera.yHeadRotO = 90f
                        mc.setCameraEntity(camera)
                        jump.isDown = true
                        next(Step.SAFE)
                    }
                }
                Step.SAFE -> {
                    if (player.z - origin.z > 8) {
                        check(abs(player.z - origin.z - 10) < .01 && abs(player.x - origin.x) < .01)
                        check(player.health == player.maxHealth)
                        burstAt = clientLevel.gameTime
                        next(Step.KEEP_HOLDING)
                    }
                    check(elapsed <= 18) { "Holding jump did not warp the player" }
                }
                Step.KEEP_HOLDING -> {
                    check(abs(player.z - origin.z - 10) < .01) { "A single hold triggered multiple warps" }
                    if (elapsed == 22) {
                        val tag = checkNotNull(pack?.get(DataComponents.CUSTOM_DATA)).copyTag()
                        check(tag.getInt("WarpPackEnergy") == 670) { "First warp did not cost 33%" }
                        val age = clientLevel.gameTime - tag.getLong("WarpPackUsedAt")
                        val expectedBar = kotlin.math.round((670 + 3 * age) * 13.0 / 1000).toInt()
                        check(pack!!.item.getBarWidth(pack) == expectedBar) { "Item bar did not recharge" }
                        jump.isDown = false
                        origin = player.position()
                        next(Step.WAIT_RISK)
                    }
                }
                Step.WAIT_RISK -> if (elapsed >= 3) {
                    jump.isDown = true
                    next(Step.RISK)
                }
                Step.RISK -> {
                    if (player.z - origin.z > 8 && player.health < player.maxHealth) {
                        check(abs(player.z - origin.z - 10) < .01 && player.health == 12f) { "Risk warp did not cost exactly 8 HP" }
                        jump.isDown = false
                        origin = player.position()
                        next(Step.WAIT_LETHAL)
                    }
                    check(elapsed <= 20) { "Risk warp did not finish" }
                }
                Step.WAIT_LETHAL -> if (elapsed >= 3) {
                    jump.isDown = true
                    next(Step.LETHAL)
                }
                Step.LETHAL -> {
                    if (!player.isAlive) {
                        check(abs(player.z - origin.z - 10) < .01) { "The lethal warp did not move the player" }
                        jump.isDown = false
                        next(Step.DONE)
                    }
                    check(elapsed <= 23) { "Low-energy warp did not kill the player" }
                }
                Step.DONE -> if (elapsed >= 6) {
                    check(departures >= 2 && arrivals >= 2) { "Missing warp sound: $departures departures and $arrivals arrivals" }
                    check("warp-departure-ring" in captures && "warp-release-flash" in captures)
                    LOGGER.info("WARP_PACK_CLIENT_PASSED: Curios, item transforms, tap and hold, 10-block travel, 33% cost, recharge, risk damage, lethal warp, spatial audio and capture")
                    mc.stop()
                }
            }
        } catch (failure: Throwable) {
            failed = true
            LOGGER.error("WARP_PACK_CLIENT_FAILED at $step:$elapsed", failure)
            mc.stop()
        }
    }

    @SubscribeEvent
    fun sound(event: PlaySoundSourceEvent) {
        if (!enabled) return
        when (event.sound.location.path) {
            "sfx_warp_pack_depart" -> departures++
            "sfx_warp_pack_arrive" -> arrivals++
        }
    }

    @SubscribeEvent
    fun captureBurst(event: RenderGuiEvent.Post) {
        if (!enabled || step != Step.KEEP_HOLDING) return
        val mc = Minecraft.getInstance()
        val age = (mc.level?.gameTime ?: return) - burstAt
        if (age in 1L..5L) capture(mc, "warp-departure-ring")
        if (age in 3L..8L) capture(mc, "warp-release-flash")
        if (age in 7L..13L) capture(mc, "warp-residual-sparks")
    }

    private fun capture(mc: Minecraft, name: String) {
        if (captures.add(name)) Screenshot.grab(mc.gameDirectory, "$name.png", mc.mainRenderTarget) {}
    }

    private fun next(value: Step) {
        step = value
        elapsed = 0
    }
}
