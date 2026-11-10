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
import net.minecraft.client.resources.sounds.TickableSoundInstance
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.*
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.EntityHitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent
import net.neoforged.neoforge.client.event.sound.SoundEvent.SoundSourceEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import kotlin.math.abs

@EventBusSubscriber(modid=MOD_ID, value=[Dist.CLIENT])
object FlameGuardDogClientTest {
    private val enabled = java.lang.Boolean.getBoolean("sbw_helldiver.flameGuardDogTests")
    private var started = 0L
    private var prepared = false
    private var picked = false
    private var equipped = false
    private var gaugeCameraTicks = 0
    private val playedSounds = java.util.concurrent.ConcurrentHashMap<String, SoundInstance>()
    @Volatile private var fullGaugeCaptured = false
    @Volatile private var ready = false
    @Volatile private var failure: Throwable? = null
    @Volatile private var stage = 0
    private var hits = 0
    private var chase = false
    private var groundFire = false
    private var rightRefill = false
    private var leftRefill = false
    private var wasIdle = false
    private var enemy: net.minecraft.world.entity.monster.Husk? = null
    private val pictures = mutableSetOf<String>()

    private fun capture(mc: Minecraft, name: String) {
        if (pictures.add(name)) Screenshot.grab(mc.gameDirectory, "flame-dog-$name.png", mc.mainRenderTarget) {}
    }

    @SubscribeEvent fun staticAudio(e: PlaySoundSourceEvent) = audio(e)
    @SubscribeEvent fun streamedAudio(e: PlayStreamingSourceEvent) = audio(e)

    private fun audio(e: SoundSourceEvent) {
        if (enabled && e.sound.location.namespace == MOD_ID && e.name.startsWith("sfx_flame_")) {
            playedSounds[e.name] = e.sound
        }
    }

    @SubscribeEvent fun gaugeCamera(e: ViewportEvent.ComputeCameraAngles) {
        if (!enabled || gaugeCameraTicks == 0) return
        e.yaw = 68f
        e.pitch = -17f
    }

    @SubscribeEvent fun gaugeDistance(e: CalculateDetachedCameraDistanceEvent) {
        if (enabled && gaugeCameraTicks > 0) e.distance = 1.17f
    }

    @SubscribeEvent fun damage(e: net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent) {
        if (!enabled || e.entity !== enemy || e.source.msgId != "sbw_helldiver.flame_jet") return
        if (abs(e.amount - FlameClock.DIRECT_DAMAGE_PER_JET * .25f) > .001f) {
            failure = IllegalStateException("Wrong direct flame damage ${e.amount}")
        }
        hits++
    }

    @SubscribeEvent fun server(e: ServerTickEvent.Post) {
        if (!enabled || !ready || failure != null || stage >= 4) return
        try {
            val world = e.server.overworld()
            val player = e.server.playerList.players.firstOrNull() ?: return
            val pack = GuardDogBackpack.equipped(player) ?: return
            val drone = GuardDogBackpack.active(pack)?.let(world::getEntity) as? GuardDogEntity
            when (stage) {
                0 -> {
                    if (!fullGaugeCaptured) return
                    check(GuardDogBackpack.isFlame(pack) && GuardDogBackpack.spareMags(pack) == 6)
                    GuardDogBackpack.updateAmmo(pack, 20)
                    GuardDogBackpack.toggle(player)
                    stage = 1
                }
                1, 2 -> {
                    if (drone == null) {
                        if (stage == 1) {
                            check(chase && hits > 0 && groundFire && rightRefill) { "chase=$chase hits=$hits fire=$groundFire right=$rightRefill" }
                            check(GuardDogBackpack.ammo(pack) == 80 && GuardDogBackpack.spareMags(pack) == 5)
                            GuardDogBackpack.updateAmmo(pack, 20)
                            GuardDogBackpack.toggle(player)
                            stage = 2
                        } else {
                            check(leftRefill && GuardDogBackpack.ammo(pack) == 80 && GuardDogBackpack.spareMags(pack) == 4)
                            repeat(4) { check(GuardDogBackpack.loadSpare(pack)) }
                            stage = 3
                        }
                    } else {
                        check(drone.isFlame)
                        if (drone.flight == GuardDogEntity.Flight.PATROL) {
                            if (!wasIdle && drone.phaseAge >= 20) {
                                check(drone.x < player.x - 1 && drone.z < player.z - 1) { "Not behind the player: ${drone.position()}" }
                                wasIdle = true
                                val target = EntityType.HUSK.create(world)!!
                                target.setNoAi(true)
                                target.getAttribute(Attributes.MAX_HEALTH)!!.baseValue = 10000.0
                                target.getAttribute(Attributes.ARMOR)!!.baseValue = 0.0
                                target.getAttribute(Attributes.KNOCKBACK_RESISTANCE)!!.baseValue = 1.0
                                target.health = 10000f
                                target.moveTo(5.0, 80.0, 12.5, 0f, 0f)
                                check(world.addFreshEntity(target))
                                enemy = target
                            }
                            chase = chase || drone.z > player.z + 5
                        }
                        if (drone.reload == GuardDogEntity.Reload.RIGHT) rightRefill = true
                        if (drone.reload == GuardDogEntity.Reload.LEFT) leftRefill = true
                        groundFire = groundFire || world.getEntitiesOfClass(GroundFireEntity::class.java, player.boundingBox.inflate(24.0)).isNotEmpty()
                    }
                }
                3 -> {
                    check(GuardDogBackpack.spareMags(pack) == 0)
                    stage = 4
                }
            }
        } catch (t: Throwable) { failure = t }
    }

    @SubscribeEvent fun tick(e: ClientTickEvent.Post) {
        if (!enabled) return
        val mc = Minecraft.getInstance()
        try {
            failure?.let { throw it }
            if (started == 0L && mc.screen is TitleScreen && mc.overlay == null) {
                started = System.currentTimeMillis()
                mc.options.pauseOnLostFocus = false
                mc.options.cameraType = CameraType.THIRD_PERSON_BACK
                mc.options.fov().set(65)
                mc.createWorldOpenFlows().createFreshLevel("port-client-test",
                    LevelSettings("Flame Guard Dog verification", GameType.CREATIVE, false, Difficulty.NORMAL, true, GameRules(), WorldDataConfiguration.DEFAULT),
                    WorldOptions(39L, false, false),
                    { it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() }, mc.screen!!)
            }
            check(started == 0L || System.currentTimeMillis() - started < 180000) { "Timed out: stage=$stage chase=$chase hits=$hits" }
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
                        for (x in -16..16) for (z in -16..32) {
                            world.setBlock(BlockPos(x,79,z), Blocks.SMOOTH_STONE.defaultBlockState(), 3)
                        }
                        world.setDefaultSpawnPos(BlockPos(0,80,-4),0f)
                        p.teleportTo(world,.5,80.0,-3.5,0f,-6f)
                        val marker = ModEntities.STRATAGEM_MARKER.get().create(world)!!
                        marker.moveTo(.5,80.0,.5,180f,0f)
                        marker.configure(Stratagems.get("flame_guard_dog")!!.dropId, GuardDogEntity.CALL_TICKS, p.uuid, groundY=80.0)
                        check(world.addFreshEntity(marker))
                        ready = true
                    } catch (t: Throwable) { failure = t }
                }
            }
            if (!ready) return
            val pod = level.entitiesForRendering().filterIsInstance<ItemDeliveryHellpodEntity>().firstOrNull()
            if (!picked && pod?.phase == AbstractHellpodEntity.Phase.ACTIVE && pod.phaseAge >= 18) {
                check(GuardDogBackpack.isFlame(pod.item(ItemDeliveryHellpodEntity.Side.FRONT)))
                check(pod.item(ItemDeliveryHellpodEntity.Side.REAR).isEmpty)
                val aim = pod.toWorld(ItemDeliveryHellpodEntity.Side.FRONT.mount).subtract(player.eyePosition)
                player.yRot = Math.toDegrees(kotlin.math.atan2(-aim.x, aim.z)).toFloat()
                player.xRot = Math.toDegrees(kotlin.math.atan2(-aim.y, aim.horizontalDistance())).toFloat()
                capture(mc, "delivery")
                check(mc.gameMode!!.interactAt(player,pod,EntityHitResult(pod),InteractionHand.MAIN_HAND).consumesAction())
                picked = true
            }
            if (!equipped && GuardDogBackpack.isFlame(player.mainHandItem)) {
                equipped = true
                player.yRot = 0f
                player.xRot = -6f
                mc.gameMode!!.useItem(player,InteractionHand.MAIN_HAND)
            }
            val pack = GuardDogBackpack.equipped(player)
            if (pack != null && (stage == 0 && !fullGaugeCaptured || stage >= 4 && GuardDogBackpack.spareMags(pack) == 0)) {
                if (++gaugeCameraTicks < 5) return
                capture(mc, if (stage == 0) "full-gauge" else "empty-gauge")
                gaugeCameraTicks = 0
                if (stage == 0) fullGaugeCaptured = true
            }
            val drone = level.entitiesForRendering().filterIsInstance<GuardDogEntity>().firstOrNull()
            if (drone?.isFlame == true) {
                if (drone.flight == GuardDogEntity.Flight.LAUNCH && drone.phaseAge >= 10) capture(mc,"launch")
                if (drone.flameState == FlameClock.State.SPRAYING && drone.phaseAge >= 30) capture(mc,"spray")
                if (drone.reload.changesMagazine && drone.phaseAge >= GuardDogEntity.TRANSITION_TICKS + 24) {
                    capture(mc, "refill-${drone.reload}")
                }
            }
            if (stage >= 4 && GuardDogBackpack.equipped(player)?.let(GuardDogBackpack::spareMags) == 0) {
                val shots = (1..4).map { "sfx_flame_fire_start_$it" }
                val expected = shots + listOf("sfx_flame_ignition_1", "sfx_flame_fire_stop_1")
                check(playedSounds.keys.containsAll(expected)) { "Missing sound channels: ${expected - playedSounds.keys}" }
                check(shots.drop(1).all { (playedSounds[it] as? TickableSoundInstance)?.isStopped == true }) {
                    "Spray loops still playing after docking"
                }
                LOGGER.info("FLAME_DOG_PASSED hits={} chase={} groundFire={} rightRefill={} leftRefill={} audio={}",
                    hits,chase,groundFire,rightRefill,leftRefill,expected)
                mc.stop()
            }
        } catch (t: Throwable) {
            LOGGER.error("FLAME_DOG_FAILED stage=$stage",t)
            mc.stop()
        }
    }
}
