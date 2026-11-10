package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.item.GuardDogBackpack
import com.example.sbwhelldiver.stratagem.Stratagems
import net.minecraft.client.*
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.*
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Husk
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.EntityHitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent

@EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object GasGuardDogClientTest {
    private val enabled = java.lang.Boolean.getBoolean("sbw_helldiver.gasGuardDogTests")
    private var started = 0L
    private var prepared = false
    private var picked = false
    private var equipped = false
    @Volatile private var failure: Throwable? = null
    @Volatile private var stage = 0
    private var idleTicks = 0
    private var idleAmmo = 0
    private var exposureTicks = 0
    private var visibleTicks = 0
    @Volatile private var spraySound: SoundInstance? = null
    private var sprayAudioStarted = false
    private var sprayAudioStopped = false
    private var secondLaunch = false
    private var rightRefill = false
    private var leftRefill = false
    private var nearby: net.minecraft.world.entity.animal.Pig? = null
    private val targets = mutableListOf<Husk>()
    private val poisoned = mutableSetOf<Husk>()
    private val captures = mutableSetOf<String>()

    @SubscribeEvent
    fun audio(event: PlayStreamingSourceEvent) {
        if (enabled && event.sound.location.namespace == MOD_ID && event.sound.location.path == "gas_dog_spray")
            spraySound = event.sound
    }

    @SubscribeEvent
    fun server(event: ServerTickEvent.Post) {
        if (!enabled || failure != null || stage >= 4) return
        try {
            val world = event.server.overworld()
            val player = event.server.playerList.players.firstOrNull() ?: return
            val pack = GuardDogBackpack.equipped(player) ?: return
            if (!GuardDogBackpack.isGas(pack)) return
            val dog = GuardDogBackpack.active(pack)?.let(world::getEntity) as? GuardDogEntity
            when (stage) {
                0 -> {
                    check(GuardDogBackpack.ammo(pack) == 100 && GuardDogBackpack.spareMags(pack) == 6)
                    for (x in listOf(-6.0, 6.0)) {
                        val enemy = EntityType.HUSK.create(world)!!
                        enemy.setNoAi(true)
                        enemy.getAttribute(Attributes.MAX_HEALTH)!!.baseValue = 1000.0
                        enemy.health = 1000f
                        enemy.moveTo(x, 80.0, 9.0, 0f, 0f)
                        check(world.addFreshEntity(enemy))
                        targets += enemy
                    }
                    val bystander = EntityType.PIG.create(world)!!
                    bystander.setNoAi(true)
                    bystander.moveTo(-4.5, 80.0, 9.0, 0f, 0f)
                    check(world.addFreshEntity(bystander))
                    nearby = bystander
                    GuardDogBackpack.toggle(player)
                    stage = 1
                }
                1 -> {
                    check(dog?.isGas == true)
                    if (dog.gasSpraying) exposureTicks++
                    for (enemy in targets) {
                        val effect = enemy.getEffect(ModEffects.GAS) ?: continue
                        if (poisoned.add(enemy)) {
                            check(exposureTicks >= GuardDogGas.EXPOSURE_TICKS) { "Poison applied after only $exposureTicks spray ticks" }
                            check(effect.duration in 179..180)
                            check(enemy.health == 1000f) { "Spray dealt direct damage" }
                            check(enemy.hasEffect(ModEffects.CONFUSION))
                            LOGGER.info("GAS_DOG_TAGGED target={} duration={} sprayTicks={}", enemy.id, effect.duration, exposureTicks)
                            exposureTicks = 0
                        }
                    }
                    if (poisoned.size == targets.size && !dog.gasSpraying) {
                        if (idleTicks == 0) idleAmmo = GuardDogBackpack.ammo(pack)
                        if (++idleTicks >= 12) {
                            val expectedUse = GuardDogGas.EXPOSURE_TICKS * targets.size / GuardDogEntity.GAS_INTERVAL_TICKS
                            check(idleAmmo <= GuardDogEntity.GAS_MAGAZINE - expectedUse && GuardDogBackpack.ammo(pack) == idleAmmo) {
                                "Expected sustained fuel use followed by idle, got $idleAmmo"
                            }
                            check(nearby?.hasEffect(ModEffects.GAS) == true) { "Nearby entity did not receive gas" }
                            nearby?.discard()
                            targets.forEach(Husk::discard)
                            GuardDogBackpack.updateAmmo(pack, 0)
                            stage = 2
                        }
                    }
                }
                2 -> {
                    if (dog?.reload == GuardDogEntity.Reload.RIGHT) rightRefill = true
                    if (dog == null) {
                        check(rightRefill && GuardDogBackpack.ammo(pack) == 100 && GuardDogBackpack.spareMags(pack) == 5)
                        GuardDogBackpack.toggle(player)
                        check(GuardDogBackpack.active(pack) != null)
                        stage = 3
                    }
                }
                3 -> {
                    if (!secondLaunch && dog?.flight == GuardDogEntity.Flight.PATROL) {
                        secondLaunch = true
                        GuardDogBackpack.updateAmmo(pack, 0)
                    }
                    if (dog?.reload == GuardDogEntity.Reload.LEFT) leftRefill = true
                    if (dog == null) {
                        check(leftRefill && GuardDogBackpack.ammo(pack) == 100 && GuardDogBackpack.spareMags(pack) == 4)
                        stage = 4
                    }
                }
            }
        } catch (t: Throwable) { failure = t }
    }

    @SubscribeEvent
    fun client(event: ClientTickEvent.Post) {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        try {
            failure?.let { throw it }
            if (started == 0L && mc.screen is TitleScreen && mc.overlay == null) {
                started = System.currentTimeMillis()
                mc.options.pauseOnLostFocus = false
                mc.options.cameraType = CameraType.THIRD_PERSON_BACK
                mc.options.fov().set(65)
                mc.createWorldOpenFlows().createFreshLevel(
                    "port-client-test",
                    LevelSettings("Gas Guard Dog verification", GameType.CREATIVE, false, Difficulty.NORMAL, true, GameRules(), WorldDataConfiguration.DEFAULT),
                    WorldOptions(39L, false, false),
                    { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },
                    mc.screen!!
                )
            }
            check(started == 0L || System.currentTimeMillis() - started < 210000) {
                "Timed out: stage=$stage tagged=${poisoned.size} picked=$picked equipped=$equipped"
            }
            val player = mc.player ?: return
            val level = mc.level ?: return
            val server = mc.singleplayerServer ?: return
            if (!prepared) {
                prepared = true
                server.execute {
                    try {
                        val world = server.overworld()
                        val p = server.playerList.getPlayer(player.uuid)!!
                        world.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server)
                        world.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false, server)
                        world.dayTime = 6000
                        for (x in -16..16) for (z in -16..24)
                            world.setBlock(BlockPos(x, 79, z), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                        world.setDefaultSpawnPos(BlockPos(0, 80, -4), 0f)
                        p.teleportTo(world, .5, 80.0, -3.5, 0f, -6f)
                        val marker = ModEntities.STRATAGEM_MARKER.get().create(world)!!
                        marker.moveTo(.5, 80.0, .5, 180f, 0f)
                        marker.configure(Stratagems.get("gas_guard_dog")!!.dropId, GuardDogEntity.CALL_TICKS, p.uuid, groundY = 80.0)
                        check(world.addFreshEntity(marker))
                    } catch (t: Throwable) { failure = t }
                }
            }
            val pod = level.entitiesForRendering().filterIsInstance<ItemDeliveryHellpodEntity>().firstOrNull()
            if (!picked && pod?.phase == AbstractHellpodEntity.Phase.ACTIVE && pod.phaseAge >= 18) {
                check(GuardDogBackpack.isGas(pod.item(ItemDeliveryHellpodEntity.Side.FRONT)))
                check(pod.item(ItemDeliveryHellpodEntity.Side.REAR).isEmpty)
                val aim = pod.toWorld(ItemDeliveryHellpodEntity.Side.FRONT.mount).subtract(player.eyePosition)
                player.yRot = Math.toDegrees(kotlin.math.atan2(-aim.x, aim.z)).toFloat()
                player.xRot = Math.toDegrees(kotlin.math.atan2(-aim.y, aim.horizontalDistance())).toFloat()
                capture(mc, "delivery")
                check(mc.gameMode!!.interactAt(player, pod, EntityHitResult(pod), InteractionHand.MAIN_HAND).consumesAction())
                picked = true
            }
            if (!equipped && GuardDogBackpack.isGas(player.mainHandItem)) {
                equipped = true
                player.yRot = 0f
                player.xRot = -6f
                mc.gameMode!!.useItem(player, InteractionHand.MAIN_HAND)
            }
            val dog = level.entitiesForRendering().filterIsInstance<GuardDogEntity>().firstOrNull()
            if (dog?.isGas == true) {
                if (dog.flight == GuardDogEntity.Flight.LAUNCH && dog.phaseAge >= 20) capture(mc, "launch")
                val sound = spraySound
                if (dog.gasSpraying) {
                    if (++visibleTicks >= GuardDogGas.EXPOSURE_TICKS * 3 / 4) capture(mc, "spray")
                    if (sound != null && mc.soundManager.isActive(sound))
                        sprayAudioStarted = true
                } else {
                    visibleTicks = 0
                    if (sprayAudioStarted && sound != null && !mc.soundManager.isActive(sound)) sprayAudioStopped = true
                }
                if (dog.reload.changesMagazine && dog.phaseAge >= GuardDogEntity.TRANSITION_TICKS + 20)
                    capture(mc, "refill-${dog.reload}")
            }
            if (stage == 4) {
                check("spray" in captures) { "Client never received spray state" }
                check(sprayAudioStarted && sprayAudioStopped) { "Spray audio did not start and stop" }
                LOGGER.info("GAS_DOG_PASSED targets={} rightRefill={} leftRefill={} audio={}",
                    poisoned.size, rightRefill, leftRefill, sprayAudioStarted && sprayAudioStopped)
                mc.stop()
            }
        } catch (t: Throwable) {
            LOGGER.error("GAS_DOG_FAILED stage=$stage", t)
            mc.stop()
        }
    }

    private fun capture(mc: Minecraft, name: String) {
        if (captures.add(name)) Screenshot.grab(mc.gameDirectory, "gas-dog-$name.png", mc.mainRenderTarget) {}
    }
}
