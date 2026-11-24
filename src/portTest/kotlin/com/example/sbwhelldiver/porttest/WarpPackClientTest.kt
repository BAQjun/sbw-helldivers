package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import org.lwjgl.glfw.GLFW
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.item.StratagemBackpacks
import net.minecraft.server.MinecraftServer
import java.util.UUID
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.contents.TranslatableContents
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
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderGuiEvent
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent
import kotlin.math.abs
import top.theillusivec4.curios.api.CuriosApi

@EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object WarpPackClientTest {
    private enum class Step { EQUIP, VANILLA_JUMP, RELEASE, SAFE, CHECK_SINGLE_WARP, WAIT_RISK, RISK, WAIT_LETHAL, LETHAL, DONE }

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
    private var deathMessage: Component? = null
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
                        level.gameRules.getRule(GameRules.RULE_KEEPINVENTORY).set(true, server)
                        for (x in -5..12) for (z in -5..35) {
                            level.setBlock(BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                        }
                        // Three 10-block sections: ascent, mixed steps, and a blocked rise.
                        val surfaceHeights = listOf(
                            81, 81, 82, 82, 82, 83, 83, 83, 84, 84,
                            83, 83, 84, 84, 85, 85, 86, 86, 86, 86,
                            87, 87, 89, 89, 89, 90, 90, 90, 90, 90,
                        )
                        for ((index, surfaceY) in surfaceHeights.withIndex()) {
                            for (x in -1..1) for (y in 80 until surfaceY) {
                                level.setBlock(BlockPos(x, y, index + 1), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                            }
                        }
                        // A thin wall on the ascent and one on the descent.
                        for (x in -1..1) {
                            for (y in 82..84) {
                                level.setBlock(BlockPos(x, y, 3), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                            }
                            for (y in 83..85) {
                                level.setBlock(BlockPos(x, y, 11), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                            }
                        }
                        // A flat lane with a thin wall followed by a two-block-thick wall.
                        for (x in 4..12) for (y in 80..82) for (z in listOf(4, 7, 8)) {
                            level.setBlock(BlockPos(x, y, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                        }
                        for (z in -4..34 step 4) {
                            level.setBlock(BlockPos(-4, 79, z), Blocks.SHROOMLIGHT.defaultBlockState(), 3)
                            level.setBlock(BlockPos(8, 79, z), Blocks.SHROOMLIGHT.defaultBlockState(), 3)
                        }
                        val host = checkNotNull(server.playerList.getPlayer(player.uuid))
                        val back = CuriosApi.getCuriosInventory(host).orElseThrow().getStacksHandler("back").orElseThrow().stacks
                        check(host.isCreative)
                        for (energy in listOf(670, 330)) {
                            host.teleportTo(level, .5, 80.0, .5, 0f, 0f)
                            val testPack = ItemStack(ModItems.WARP_PACK.get())
                            writeEnergy(testPack, level.gameTime, energy)
                            check(back.insertItem(0, testPack, false).isEmpty)
                            StratagemBackpacks.interact(host)
                            check(abs(host.z - 10.5) < .01 && abs(host.y - 84.0) < .01) { "Creative warp did not complete" }
                            check(host.isAlive && host.health == host.maxHealth && !host.isOnFire) {
                                "Creative warp inflicted harm at energy $energy"
                            }
                            check(back.getStackInSlot(0).isEmpty == (energy == 330)) { "Creative warp changed the lethal pack cost" }
                            back.extractItem(0, 1, false)
                        }
                        fun checkWallLane(startY: Double, yaw: Float, expectedZ: Double) {
                            host.teleportTo(level, 5.5, startY, .5, yaw, 0f)
                            host.setOnGround(false)
                            check(back.insertItem(0, ItemStack(ModItems.WARP_PACK.get()), false).isEmpty)
                            StratagemBackpacks.interact(host)
                            check(abs(host.z - expectedZ) < .01 && abs(host.y - startY) < .01) {
                                "Expected to cross the thin wall and stop at the thick one, reached " + host.position()
                            }
                            back.extractItem(0, 1, false)
                        }
                        checkWallLane(80.0, 0f, 6.5)
                        checkWallLane(81.0, 0f, 6.5)
                        checkWallLane(80.0, -45f, .5 + 8.5 / kotlin.math.sqrt(2.0))

                        host.teleportTo(level, .5, 80.0, .5, 0f, 0f)
                        check(back.insertItem(0, ItemStack(ModItems.WARP_PACK.get()), false).isEmpty)
                        host.inventory.setItem(8, ItemStack(ModItems.WARP_PACK.get()))
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
                    next(Step.VANILLA_JUMP)
                }
                Step.VANILLA_JUMP -> {
                    check(player.position().subtract(origin).horizontalDistanceSqr() < 1.0e-6)
                    if (elapsed == 6) {
                        jump.isDown = false
                        next(Step.RELEASE)
                    }
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
                        pressBackpackKey(mc)
                        next(Step.SAFE)
                    }
                }
                Step.SAFE -> {
                    if (player.z - origin.z > 8) {
                        check(abs(player.z - origin.z - 10) < .01 && abs(player.x - origin.x) < .01) {
                            "Expected 10 blocks forward from $origin, reached " + player.position() + " (yaw " + player.yRot + ")"
                        }
                        check(abs(player.y - origin.y - 4) < .01) { "Expected to land four blocks higher" }
                        check(player.health == player.maxHealth)
                        burstAt = clientLevel.gameTime
                        next(Step.CHECK_SINGLE_WARP)
                    }
                    check(elapsed <= 18) { "Backpack key did not warp the player" }
                }
                Step.CHECK_SINGLE_WARP -> {
                    check(abs(player.z - origin.z - 10) < .01) { "A single key press triggered multiple warps" }
                    if (elapsed == 22) {
                        val tag = checkNotNull(pack?.get(DataComponents.CUSTOM_DATA)).copyTag()
                        check(tag.getInt("WarpPackEnergy") == 670) { "First warp did not cost 33%" }
                        val age = clientLevel.gameTime - tag.getLong("WarpPackUsedAt")
                        capture(mc, "warp-risk-hud")
                        val expectedBar = kotlin.math.round((670 + 3 * age) * 13.0 / 1000).toInt()
                        check(pack!!.item.getBarWidth(pack) == expectedBar) { "Item bar did not recharge" }
                        origin = player.position()
                        mc.setCameraEntity(player)
                        setEnergy(server, player.uuid, 670)
                        next(Step.WAIT_RISK)
                    }
                }
                Step.WAIT_RISK -> {
                    if (pack?.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getInt("WarpPackEnergy") == 670) {
                        pressBackpackKey(mc)
                        next(Step.RISK)
                    }
                    check(elapsed < 30) { "The test did not receive the risk energy state" }
                }
                Step.RISK -> {
                    if (player.z - origin.z > 8 && player.health < player.maxHealth) {
                        check(abs(player.z - origin.z - 10) < .01)
                        check(abs(player.y - origin.y - 2) < .01) { "Expected the mixed steps to end two blocks higher" }
                        check(player.health == 12f || (player.isOnFire && player.health == 11f)) {
                            "Risk warp did not cost 8 HP before vanilla fire damage"
                        }
                        origin = player.position()
                        setEnergy(server, player.uuid, 330)
                        next(Step.WAIT_LETHAL)
                    }
                    check(elapsed <= 20) {
                        "Risk warp did not finish: delta=${player.position().subtract(origin)}, health=${player.health}, " +
                            "energy=${pack?.get(DataComponents.CUSTOM_DATA)?.copyTag()}, " +
                            "sounds=$departures/$arrivals, screen=${mc.screen}"
                    }
                }
                Step.WAIT_LETHAL -> {
                    if (pack?.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getInt("WarpPackEnergy") == 330) {
                        pressBackpackKey(mc)
                        next(Step.LETHAL)
                    }
                    check(elapsed < 30) { "The test did not receive the lethal energy state" }
                }
                Step.LETHAL -> {
                    if (!player.isAlive) {
                        check(abs(player.z - origin.z - 2) < .01 && abs(player.y - origin.y - 1) < .01) {
                            "The warp did not stop before the two-block rise"
                        }
                        next(Step.DONE)
                    }
                    check(elapsed <= 23) {
                        "Lethal warp did not kill the player: delta=${player.position().subtract(origin)}, health=${player.health}, " +
                            "energy=${pack?.get(DataComponents.CUSTOM_DATA)?.copyTag()}, " +
                            "sounds=$departures/$arrivals, screen=${mc.screen}"
                    }
                }
                Step.DONE -> if (elapsed >= 6) {
                    check(pack == null) { "The used Warp Pack survived lethal activation with Keep Inventory enabled" }
                    check(player.inventory.getItem(8).item === ModItems.WARP_PACK.get()) { "The spare Warp Pack was destroyed" }
                    val message = checkNotNull(deathMessage) { "The lethal warp did not broadcast its death message" }
                    val contents = message.contents as TranslatableContents
                    val victim = contents.args[0] as Component
                    val major = contents.args[1] as Component
                    check(victim.string == player.displayName?.string && major.string == "Eli Anderson" && major.style.isBold)
                    LOGGER.info("WARP_PACK_DEATH_MESSAGE_PASSED: {}", message.string)
                    check(departures >= 2 && arrivals >= 2) { "Missing warp sound: $departures departures and $arrivals arrivals" }
                    check("warp-departure-ring" in captures && "warp-release-flash" in captures)
                    LOGGER.info("WARP_PACK_CLIENT_PASSED: Curios, item transforms, backpack key activation, thin walls on slopes and on flat ground, thick walls at several angles and heights, stopping before a two-block rise, 33% cost, recharge, Creative immunity, Survival risk damage, lethal warp consumes only the equipped pack, spatial audio and capture")
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
    fun deathBroadcast(event: ClientChatReceivedEvent.System) {
        if (!enabled || event.isOverlay) return
        val contents = event.message.contents as? TranslatableContents ?: return
        if (contents.key == "death.attack.sbw_helldiver.warp_pack") deathMessage = event.message
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
        if (!enabled || step != Step.CHECK_SINGLE_WARP) return
        val mc = Minecraft.getInstance()
        val age = (mc.level?.gameTime ?: return) - burstAt
        if (age in 1L..5L) capture(mc, "warp-departure-ring")
        if (age in 3L..8L) capture(mc, "warp-release-flash")
        if (age in 7L..13L) capture(mc, "warp-residual-sparks")
    }

    private fun setEnergy(server: MinecraftServer, playerId: UUID, energy: Int) {
        server.execute {
            try {
                val player = checkNotNull(server.playerList.getPlayer(playerId))
                val pack = checkNotNull(StratagemBackpacks.equipped(player))
                writeEnergy(pack, server.overworld().gameTime, energy)
            } catch (failure: Throwable) {
                preparationError = failure
            }
        }
    }

    private fun writeEnergy(pack: ItemStack, gameTime: Long, energy: Int) {
        CustomData.update(DataComponents.CUSTOM_DATA, pack) {
            it.putInt("WarpPackEnergy", energy)
            it.putLong("WarpPackUsedAt", gameTime)
        }
    }

    private fun pressBackpackKey(mc: Minecraft) {
        val window = mc.window.window
        mc.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_G, 0, GLFW.GLFW_PRESS, 0)
        mc.keyboardHandler.keyPress(window, GLFW.GLFW_KEY_G, 0, GLFW.GLFW_RELEASE, 0)
    }

    private fun capture(mc: Minecraft, name: String) {
        if (captures.add(name)) Screenshot.grab(mc.gameDirectory, "$name.png", mc.mainRenderTarget) {}
    }

    private fun next(value: Step) {
        step = value
        elapsed = 0
    }
}
