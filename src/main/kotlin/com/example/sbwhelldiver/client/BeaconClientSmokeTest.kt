package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.config.HelldiversConfig
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.example.sbwhelldiver.entity.StratagemMarker
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.menu.BeaconLoadoutMenu
import com.example.sbwhelldiver.stratagem.BeaconData
import com.example.sbwhelldiver.stratagem.Stratagems
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

/** Real menu clicks and network packets in an isolated world; disabled in normal games. */
@Mod.EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object BeaconClientSmokeTest {
    private val verifiedExplosiveModels = mutableSetOf<String>()
    private var hotbarAltVerified = false
    private val slidePositions = mutableListOf<Float>()
    private var monitorSlide = false
    private var monitorExit = false
    private val exitPositions = mutableListOf<Float>()
    private var exitVerified = false
    private var failureSeenAt = -1
    private var failureHudVerified = false
    @Volatile private var rejectedBall: com.example.sbwhelldiver.entity.StratagemBeacon? = null
    @SubscribeEvent fun frame(event: TickEvent.RenderTickEvent) {
        if (java.lang.Boolean.getBoolean("sbw_helldiver.beaconSmokeTest") && monitorSlide && event.phase == TickEvent.Phase.END) {
            StratagemClient.lastHudLayout?.let { slidePositions.add(it.x) }
        }
        if (java.lang.Boolean.getBoolean("sbw_helldiver.beaconSmokeTest") && monitorExit && event.phase == TickEvent.Phase.END) {
            StratagemClient.lastHudLayout?.let { exitPositions.add(it.x) }
        }
    }
    private var ticks = 0
    private var started = false
    @Volatile private var prepared = false
    private var opened = false
    private var moved = false
    private var closed = false
    private var inputIndex = 0
    private var inputProbeStage = 0
    private var inputBegan = -1
    private var thrown = false
    private var selectionStage = 0
    private var extraInputIndex = 0
    private var reselected = false
    private var switchedCanceled = false
    private var rightClickIgnored = false
    private var labelFixture: StratagemMarker? = null
    private var nearLabelSize = -1
    private var farLabelSize = -1
    private var offAimHidden = false
    private var hudWidthRatioAtScaleOne = 0f
    private var compactHudVerified = false
    private var countdownEndedAt = -1L
    private var sawZeroBeforeImpact = false
    private var sawMarker = false
    private var sawFalling = false
    private var sawImpact = false
    private var sawActive = false
    private val saved = mutableSetOf<String>()
    private var priorY = Double.NaN
    private var fallingChecks = 0
    private var clockRollbackInjected = false
    private var trajectoryError = 0.0
    private val heard = mutableSetOf<String>()
    private val deploymentSounds = mutableListOf<net.minecraft.client.resources.sounds.SoundInstance>()
    private var distantActivation: net.minecraft.client.resources.sounds.SoundInstance? = null
    private var distantActivationVerified = false
    private val inputSounds = mutableListOf<String>()
    private var vanillaInputClick = false
    private var invalidInputSounds = 0
    private var entryStarted = -1
    private var impactStarted = -1
    private var entryStartedNanos = 0L
    private var impactStartedNanos = 0L
    private var entryRange = 0
    private val entrySounds = mutableListOf<net.minecraft.client.resources.sounds.TickableSoundInstance>()
    private var stoppedDeadlineDelta = -1
    private var entrySound: net.minecraft.client.resources.sounds.SoundInstance? = null
    @SubscribeEvent fun sound(event: net.minecraftforge.client.event.sound.PlaySoundEvent) {
        if (java.lang.Boolean.getBoolean("sbw_helldiver.beaconSmokeTest")) event.sound?.let {
            val id = it.location.toString()
            heard.add(id)
            if (id == "sbw_helldiver:beacon_deploy") {
                deploymentSounds.add(it)
                if (ticks in 625..639) distantActivation = it
            }
            if (id == "sbw_helldiver:beacon_input_error") invalidInputSounds++
            if (id.startsWith("sbw_helldiver:beacon_arrow_input_")) inputSounds.add(id)
            if (ticks >= inputBegan && inputBegan > 0 && !thrown && id == "minecraft:ui.button.click") vanillaInputClick = true
            if (id.startsWith("sbw_helldiver:sfx_hellpod_entry_layer_") && it is net.minecraft.client.resources.sounds.TickableSoundInstance) entrySounds.add(it)
            if (id == "sbw_helldiver:sfx_hellpod_entry_layer_03" && entryStarted < 0) {
                entryStarted = ticks
                entryStartedNanos = System.nanoTime()
                entrySound = it
            }
            if (id == "sbw_helldiver:sfx_hellpod_impact") {
                check(impactStarted < 0) { "The synchronized phase replayed the landing audio" }
                impactStarted = ticks
                impactStartedNanos = System.nanoTime()
            }
        }
    }
    private fun screenshot(mc: Minecraft, name: String) {
        if (!saved.add(name)) return
        Screenshot.grab(mc.gameDirectory, "beacon_$name.png", mc.mainRenderTarget) { LOGGER.info("BEACON_SCREENSHOT: {}", it.string) }
    }
    @SubscribeEvent fun tick(event: TickEvent.ClientTickEvent) {
        if (!java.lang.Boolean.getBoolean("sbw_helldiver.beaconSmokeTest") || event.phase != TickEvent.Phase.END) return
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val server = mc.singleplayerServer ?: return
        if (!started) {
            started = true
            mc.options.hideGui = false
            mc.options.guiScale().set(3)
            mc.resizeDisplay()
            server.execute {
                val level = server.overworld()
                HelldiversConfig.dropDelayTicks.set(80)
                level.dayTime = 1000
                level.getEntitiesOfClass(AbstractHellpodEntity::class.java, AABB(500.0, 200.0, 500.0, 540.0, 1400.0, 540.0)).forEach { it.discard() }
                level.getEntitiesOfClass(StratagemMarker::class.java, AABB(500.0, 200.0, 500.0, 540.0, 1400.0, 540.0)).forEach { it.discard() }
                for (x in 503..535) for (z in 503..535) level.setBlock(BlockPos(x, 256, z), Blocks.BEDROCK.defaultBlockState(), 2)
                server.playerList.getPlayer(player.uuid)!!.let {
                    it.gameMode.changeGameModeForPlayer(GameType.CREATIVE)
                    it.teleportTo(level, 515.5, 257.0, 514.5, 0f, 65f)
                    it.inventory.clearContent(); it.inventory.selected = 0
                    it.connection.send(net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0))
                    val beacon = ItemStack(ModItems.BEACON.get()); BeaconData.ensureIdentity(beacon)
                    it.inventory.setItem(0, beacon)
                    listOf(ModItems.MACHINE_GUN_SENTRY, ModItems.GATLING_SENTRY, ModItems.ROCKET_SENTRY, ModItems.AUTOCANNON_SENTRY)
                        .forEachIndexed { index, item -> it.inventory.setItem(9 + index, ItemStack(item.get())) }
                    it.inventoryMenu.broadcastChanges()
                }
                LOGGER.info("BEACON_SMOKE_STARTED")
                prepared = true
            }
        }
        if (!prepared) return
        mc.mouseHandler.releaseMouse()
        ticks++
        if (!opened && ticks > 35 && player.mainHandItem.item == ModItems.BEACON.get()) {
            screenshot(mc, "held_inactive")
            opened = true
            server.execute {
                val actual = server.playerList.getPlayer(player.uuid)!!
                actual.isShiftKeyDown = true
                ModItems.BEACON.get().use(actual.level(), actual, InteractionHand.MAIN_HAND)
                actual.isShiftKeyDown = false
            }
        }
        if (opened && !moved && mc.screen is BeaconLoadoutScreen && player.containerMenu is BeaconLoadoutMenu) {
            moved = true
            repeat(4) { mc.gameMode!!.handleInventoryMouseClick(player.containerMenu.containerId, 4 + it, 0, ClickType.QUICK_MOVE, player) }
        }
        if (moved && !closed && mc.screen is BeaconLoadoutScreen && BeaconData.loadout(player.mainHandItem).filterNotNull().size == 4 && ticks > 60) {
            screenshot(mc, "loadout")
            closed = true; player.closeContainer()
            inputBegan = ticks + 25
            StratagemClient.testHold = true
        }
        if (closed && !thrown && inputBegan > 0 && ticks >= inputBegan && (ticks - inputBegan) % 15 == 0) {
            val code = Stratagems.get("machine_gun_sentry")!!.code
            if (inputProbeStage < 5) {
                when (inputProbeStage++) {
                    0 -> StratagemClient.input(code[0])
                    1 -> StratagemClient.input(code[1])
                    2 -> StratagemClient.testHold = false
                    3 -> { StratagemClient.testHold = true; StratagemClient.input(code[0]) }
                    4 -> StratagemClient.input(Stratagems.LEFT) // Invalid second arrow resets the sequence.
                }
            } else if (inputIndex < code.size) {
                StratagemClient.input(code[inputIndex++])
                if (inputIndex == 2) screenshot(mc, "hud_input")
            } else {
                when (selectionStage) {
                    0 -> if (BeaconData.selected(player.mainHandItem) != null) {
                        mc.gameMode!!.useItem(player, InteractionHand.MAIN_HAND)
                        rightClickIgnored = true
                        player.xRot = 0f; player.xRotO = 0f
                        selectionStage++
                    }
                    1 -> {
                        check(BeaconData.selected(player.mainHandItem)?.id == "machine_gun_sentry") { "Right click threw or cleared the beacon" }
                        screenshot(mc, "held_blue")
                        StratagemClient.testHold = false
                        selectionStage++
                    }
                    2 -> {
                        mc.options.cameraType = net.minecraft.client.CameraType.THIRD_PERSON_FRONT
                        selectionStage++
                    }
                    3 -> {
                        if (extraInputIndex == 0) {
                            screenshot(mc, "held_third_person")
                            mc.options.cameraType = net.minecraft.client.CameraType.FIRST_PERSON
                            StratagemClient.testHold = true
                        }
                        val next = Stratagems.get("gatling_sentry")!!.code
                        if (extraInputIndex < next.size) StratagemClient.input(next[extraInputIndex++])
                        else if (BeaconData.selected(player.mainHandItem)?.id == "gatling_sentry") {
                            reselected = true; StratagemClient.testHold = false
                            player.inventory.selected = 1
                            mc.connection!!.send(net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(1))
                            selectionStage++
                        }
                    }
                    4 -> {
                        check(BeaconData.selected(player.inventory.getItem(0)) == null) { "Held item switch did not clear activation" }
                        switchedCanceled = true
                        player.inventory.selected = 0
                        mc.connection!!.send(net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(0))
                        extraInputIndex = 0
                        selectionStage++
                    }
                    5 -> { StratagemClient.testHold = true; selectionStage++ }
                    6 -> {
                        if (extraInputIndex < code.size) StratagemClient.input(code[extraInputIndex++])
                        else if (BeaconData.selected(player.mainHandItem) != null) {
                            screenshot(mc, "activated")
                            player.xRot = 65f; player.xRotO = 65f
                            player.yRot = 0f; player.yRotO = 0f
                            // Send the test aim before the throw, rather than waiting for the next movement tick.
                            mc.connection!!.send(net.minecraft.network.protocol.game.ServerboundMovePlayerPacket.Rot(player.yRot, player.xRot, player.onGround()))
                            StratagemClient.testHold = false
                            val click = net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered(0, mc.options.keyAttack, InteractionHand.MAIN_HAND)
                            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(click)
                            check(click.isCanceled && !click.shouldSwingHand()) { "Beacon left click still attacks/mines" }
                            thrown = true
                            LOGGER.info("BEACON_NETWORK_ACTIVATION_PASSED: Alt reselect, held switch reset, right click ignored, Forge left click")
                        }
                    }
                }
            }
        }
        val marker = mc.level!!.entitiesForRendering().filterIsInstance<StratagemMarker>().firstOrNull { it.x in 503.0..535.0 && it.z in 503.0..535.0 }
        val pod = mc.level!!.entitiesForRendering().filterIsInstance<SentryHellpodEntity>().firstOrNull { it.x in 503.0..535.0 && it.z in 503.0..535.0 }
        if (marker != null && marker.elapsedTicks > 0) {
            sawMarker = true
            countdownEndedAt = marker.countdownEndsAt
            check(marker.duration == 80 && marker.landingGraceTicks == 20)
            if (marker.ticksLeft == 0 && mc.level!!.gameTime < marker.arrivalDeadline) {
                sawZeroBeforeImpact = true
                check(pod?.phase == AbstractHellpodEntity.Phase.FALLING) { "Pod landed at zero instead of one second later" }
            }
            check(marker.color == 0x27BAFF && marker.stratagem?.id == "machine_gun_sentry") { "Unexpected marker: id=${marker.stratagem?.id}, color=${marker.color}, pos=${marker.position()}, age=${marker.elapsedTicks}" }
            if (marker.elapsedTicks >= 23) screenshot(mc, "deployed_beam")
            if (marker.ticksLeft <= 45) screenshot(mc, "countdown")
        }
        if (pod != null) {
            if (pod.scheduledFlight && pod.flightSampleTime >= pod.impactDeadline && stoppedDeadlineDelta < 0) {
                check(entrySounds.size == 3 && entrySounds.all { it.isStopped && it.volume == 0f }) {
                    "Incoming layers continued playing when the shared path reached the ground: ${entrySounds.map { "${it.location}:stopped=${it.isStopped},volume=${it.volume}" }}, sample=${pod.flightSampleTime}, deadline=${pod.impactDeadline}, phase=${pod.phase}"
                }
                val savedFlight = net.minecraft.nbt.CompoundTag()
                (server.overworld().getEntity(pod.uuid) as? SentryHellpodEntity)?.saveWithoutId(savedFlight)
                check(savedFlight.getLong("ScheduledArrival")-savedFlight.getLong("ScheduledLaunch")==95L) { "Pod did not fly for 4.75 seconds" }
                check(countdownEndedAt >= 0 && pod.impactDeadline - countdownEndedAt == 20L) { "Pod arrival was not one second after countdown zero" }
                stoppedDeadlineDelta = (pod.flightSampleTime - pod.impactDeadline).toInt()
            }
            when (pod.phase) {
                AbstractHellpodEntity.Phase.FALLING -> {
                    sawFalling = true
                    if (pod.scheduledFlight && !pod.flightSampleTime.isNaN()) {
                        val expected = pod.scheduledHeight(pod.flightSampleTime)
                        val error = kotlin.math.abs(pod.y - expected)
                        trajectoryError = maxOf(trajectoryError, error)
                        check(error < 0.001) { "Client pod left the descent path by $error blocks" }
                        if (!priorY.isNaN()) check(pod.y <= priorY + 0.001) { "Pod bounced upward before arrival: $priorY -> ${pod.y}" }
                        priorY = pod.y; fallingChecks++
                        if (!clockRollbackInjected && fallingChecks >= 12) {
                            mc.level!!.setGameTime(mc.level!!.gameTime - 2)
                            clockRollbackInjected = true
                        }
                        if (pod.braking) {
                            server.execute {
                                val actual = server.playerList.getPlayer(player.uuid)!!
                                val cameraScale = if (pod.brakeAge >= 38) 0.55 else 1.0
                                val pitch = -Math.toDegrees(kotlin.math.atan2(pod.y - 2.5 - (257.0 + actual.eyeHeight), kotlin.math.sqrt(185.0) * cameraScale)).toFloat()
                                actual.teleportTo(server.overworld(), pod.x + 8.0 * cameraScale, 257.0, pod.z + 11.0 * cameraScale, 144f, pitch)
                            }
                            if (pod.brakeAge >= 24) screenshot(mc, "deceleration_jets_visible")
                            if (pod.brakeAge >= 35) screenshot(mc, "deceleration_jets_late")
                            if (pod.brakeAge >= 40) screenshot(mc, "deceleration_jets_close")
                        }
                    }
                }
                AbstractHellpodEntity.Phase.IMPACT -> { sawImpact = true; screenshot(mc, "impact") }
                AbstractHellpodEntity.Phase.ACTIVE -> {
                    sawActive = true
                    check(kotlin.math.abs(pod.y - (257.0 + pod.profile.podHeight - HelldiversConfig.effectiveBurialDepth())) < 0.001) {
                        "Pod rim missed the calibrated ground height: ${pod.y}"
                    }
                    screenshot(mc, "sentry_active")
                }
                else -> {}
            }
        }
        if (ticks == 600) {
            StratagemClient.testHold = true
            mc.options.guiScale().set(1); mc.resizeDisplay()
        }
        if (ticks == 605) {
            val panel = StratagemClient.lastHudLayout ?: error("Alt did not display the compact list")
            hudWidthRatioAtScaleOne = panel.width / mc.window.guiScaledWidth
            check(hudWidthRatioAtScaleOne in .16f.. .21f && panel.x < mc.window.guiScaledWidth * .04f)
        }
        if (ticks == 610) { mc.options.guiScale().set(3); mc.resizeDisplay() }
        if (ticks == 615) {
            val panel = StratagemClient.lastHudLayout ?: error("GUI scale change hid the compact list")
            val ratio = panel.width / mc.window.guiScaledWidth
            check(kotlin.math.abs(ratio - hudWidthRatioAtScaleOne) < .002f && ratio in .16f.. .21f)
            check(panel.height / mc.window.guiScaledHeight in .30f.. .39f)
            compactHudVerified = true
            screenshot(mc, "hud_compact")
        }
        if (ticks == 616) StratagemClient.testHold = false
        if (ticks == 620) {
            // Validate the resources without adding demonstration projectiles above the player.
            val rocket = ModEntities.SENTRY_ROCKET.get().create(mc.level!!)!!
            check(rocket.getModelInstance() != null) { "Rocket did not reuse the native model" }
            check(mc.entityRenderDispatcher.getRenderer(rocket) is SentryRocketRenderer)
            val shell = ModEntities.SENTRY_CANNON_SHELL.get().create(mc.level!!)!!
            check(shell.modelInstance != null) { "Cannon shell native model is missing" }
            check(mc.entityRenderDispatcher.getRenderer(shell) is SentryCannonShellRenderer)
            verifiedExplosiveModels.addAll(listOf("rocket", "cannon"))
        }
        if (thrown) check(mc.level!!.entitiesForRendering().none {
            it is com.example.sbwhelldiver.entity.SentryRocketEntity || it is com.example.sbwhelldiver.entity.SentryCannonShellEntity
        }) { "Calling the machine gun sentry generated an unexpected rocket/cannon shell in the sky" }
        if (ticks == 625) server.execute {
            val level = server.overworld(); val actual = server.playerList.getPlayer(player.uuid)!!
            val fixture = ModEntities.STRATAGEM_MARKER.get().create(level)!!
            fixture.setPos(actual.x + 30.0, actual.y, actual.z)
            fixture.configure(Stratagems.get("gatling_sentry")!!.dropId, 80, null, groundY = actual.y)
            val tag = net.minecraft.nbt.CompoundTag(); fixture.saveWithoutId(tag)
            tag.putBoolean("PodSpawned", true); fixture.load(tag)
            level.addFreshEntity(fixture); fixture.playActivationSound()
        }
        if (ticks == 637) {
            val sound = distantActivation ?: error("Beacon activation packet was not delivered 30 blocks away")
            check(deploymentSounds.size == 2 && sound.source == net.minecraft.sounds.SoundSource.PLAYERS)
            check(sound.sound.attenuationDistance == 64 && sound.volume > 0f && mc.soundManager.isActive(sound))
            val distance = player.position().distanceTo(net.minecraft.world.phys.Vec3(sound.x, sound.y, sound.z))
            check(distance in 29.8..30.2) { "Distant audio regression did not test a thrown-beacon distance: $distance" }
            distantActivationVerified = true
            LOGGER.info("BEACON_DEPLOY_AUDIO_PASSED: successful throw plays once; original cue has an active sound channel 30 blocks away with 64-block range")
        }
        if (ticks == 650) mc.mouseHandler.releaseMouse() // Do not let desktop mouse movement steer automated camera shots.
        if (ticks == 650) server.execute {
            val fixture = ModEntities.STRATAGEM_MARKER.get().create(server.overworld())!!
            fixture.setPos(600.5, 257.0, 600.5)
            fixture.configure(Stratagems.get("gatling_sentry")!!.dropId, 80, null, groundY = 257.0)
            // This secondary label fixture must not create a second drop or duplicate the main audio checks.
            val labelTag = net.minecraft.nbt.CompoundTag()
            fixture.saveWithoutId(labelTag); labelTag.putBoolean("PodSpawned", true); labelTag.putBoolean("ActivationPlayed", true); fixture.load(labelTag)
            server.overworld().addFreshEntity(fixture); labelFixture = fixture
            server.playerList.getPlayer(player.uuid)!!.teleportTo(server.overworld(), 600.5, 257.0, 610.5, 180f, 0f)
        }
        if (ticks == 665 || ticks == 690) {
            val label = BeaconCallHud.label ?: error("Aiming at the beam did not show the call HUD")
            check(label.marker.stratagem?.id == "gatling_sentry" && label.y == mc.window.guiScaledHeight / 2)
            check(kotlin.math.abs(label.x - mc.window.guiScaledWidth / 2) <= 2) { "Camera did not aim at the beam: x=${label.x}, centre=${mc.window.guiScaledWidth / 2}, yaw=${player.yRot}" }
            check(BeaconCallHud.monochromeIconCount == Stratagems.definitions.size && BeaconCallHud.TITLE_COLOR == 0xFFDB4A)
            check(kotlin.math.abs(label.distance - if (ticks == 665) 10.0 else 80.0) < .1) { "HUD distance did not follow the player's position: ${label.distance}" }
            if (ticks == 665) { nearLabelSize = label.iconSize; screenshot(mc, "call_label_near") }
            else { farLabelSize = label.iconSize; check(label.distance > 75); screenshot(mc, "call_label_far") }
        }
        if (ticks == 670) server.execute {
            server.playerList.getPlayer(player.uuid)!!.teleportTo(server.overworld(), 600.5, 257.0, 680.5, 180f, 0f)
        }
        if (ticks == 700) server.execute {
            server.playerList.getPlayer(player.uuid)!!.teleportTo(server.overworld(), 600.5, 257.0, 680.5, 165f, 0f)
        }
        if (ticks == 715) { offAimHidden = BeaconCallHud.label == null; screenshot(mc, "call_label_off_aim") }
        if (ticks == 750) {
            check(distantActivationVerified && deploymentSounds.size == 2) { "Beam activation sound was missing or replayed" }
            check(opened && moved && closed && inputIndex == 5 && thrown) { "Menu / code / throw did not finish: opened=$opened moved=$moved closed=$closed inputs=$inputIndex thrown=$thrown hand=${player.mainHandItem} slot=${player.inventory.selected} screen=${mc.screen}" }
            check(nearLabelSize == BeaconCallHud.ICON_SIZE && farLabelSize == nearLabelSize && offAimHidden) {
                "Call HUD visibility/scale failed: near=$nearLabelSize far=$farLabelSize offAim=$offAimHidden"
            }
            check(verifiedExplosiveModels == setOf("rocket", "cannon")) { "Explosive projectile visuals were not verified: $verifiedExplosiveModels" }
            check(compactHudVerified) { "Reference-sized Alt list was not verified across GUI scales" }
            check(reselected && switchedCanceled && rightClickIgnored) { "Reselect / held-switch / right-click rules failed" }
            check(BeaconModel.ready && sawMarker && sawFalling && sawImpact && sawActive) { "Beacon model / full deployment was not rendered: model=${BeaconModel.ready}, marker=$sawMarker, falling=$sawFalling, impact=$sawImpact, active=$sawActive, pathChecks=$fallingChecks" }
            check(sawZeroBeforeImpact) { "Client did not display zero during the final one-second flight" }
            check(clockRollbackInjected && fallingChecks >= 10 && trajectoryError < 0.001) { "Client descent was not verified over the countdown" }
            check(HellpodJetParticle.created > 50 && HellpodJetParticle.rendered > 20) { "Retro jets were not created and displayed" }
            check(heard.containsAll(listOf("sbw_helldiver:beacon_ready", "sbw_helldiver:beacon_deploy"))) { "Beacon audio was not played: $heard" }
            val expectedInputSounds = listOf(1, 2, 1, 1, 2, 3, 4, 5, 1, 2, 3, 4, 1, 2, 3, 4, 5).map { "sbw_helldiver:beacon_arrow_input_$it" }
            check(invalidInputSounds == 1) { "Wrong code must play one error, but Alt release / reselect / held switch must stay silent: $invalidInputSounds" }
            check(inputSounds == expectedInputSounds && !vanillaInputClick) {
                "Arrow audio order/reset failed: heard=$inputSounds, vanillaClick=$vanillaInputClick"
            }
            entryRange = entrySound?.sound?.attenuationDistance ?: 0
            check(entryStarted >= 0 && impactStarted - entryStarted in 94..96 && entryRange >= 768) {
                "Incoming woosh missed its arrival window or audible range: ticks=${impactStarted - entryStarted}, range=$entryRange"
            }
            check(heard.none { it.startsWith("sbw_helldiver:sfx_hellpod_retro") || it.startsWith("sbw_helldiver:sfx_hellpod_thruster") }) { "Player pod jet sounds were played for a sentry drop: $heard" }
            check(stoppedDeadlineDelta in 0..1 && heard.none { it == "sbw_helldiver:sfx_hellpod_entry_woosh_end_tail" }) {
                "Woosh stop missed impact or replayed the end tail: delay=$stoppedDeadlineDelta, heard=$heard"
            }
            check(HellpodJetParticle.whiteSmoke >= 20 && HellpodJetParticle.blackSmoke >= 20) { "White/black exhaust smoke was missing" }
            val testedBeacon = player.inventory.getItem(0)
            check(BeaconData.selected(testedBeacon) == null && BeaconData.loadout(testedBeacon).filterNotNull().size == 4) {
                "Throw lost loadout or activation was not cleared: slot0=$testedBeacon tag=${testedBeacon.tag} selectedSlot=${player.inventory.selected}"
            }
            server.execute {
                val actual = server.playerList.getPlayer(player.uuid)!!
                check(BeaconData.loadout(actual.inventory.getItem(0)).filterNotNull().size == 4)
                check((9..12).all { actual.inventory.getItem(it).isEmpty }) { "Loadout menu duplicated physical cards" }
                labelFixture?.discard()
                LOGGER.info("STRATAGEM_COMPACT_HUD_PASSED: left corner, readable enlarged reference proportions, identical footprint at GUI scales 1 and 3")
                LOGGER.info("BEACON_CALL_HUD_PASSED: white/black icon, yellow name, countdown and player distance beneath icon; crosshair-level anchor; identical near/far size; hidden off aim")
                LOGGER.info("SENTRY_EXPLOSIVE_VISUALS_PASSED: native rocket and cannon models and registered renderers loaded without adding demonstration entities")
                LOGGER.info("BEACON_NO_TEST_PROJECTILES_PASSED: no rocket or cannon shell appeared during the entire machine-gun summon")
                LOGGER.info("BEACON_CLIENT_SMOKE_PASSED: four-slot menu, Alt reselect, held-switch cancel, right-click ignored, left-click throw/reset, blue glow, unfolding/beam/countdown and deployed sentry")
                LOGGER.info("BEACON_DESCENT_PASSED: {} consecutive path checks; no upward bounce even after backwards clock correction; max error={}, jet particles={}/{}", fallingChecks, trajectoryError, HellpodJetParticle.created, HellpodJetParticle.rendered)
                LOGGER.info("BEACON_INCOMING_AUDIO_PASSED: {} ticks from woosh to impact, range={}, no retro/thruster sounds", impactStarted - entryStarted, entryRange)
                LOGGER.info("BEACON_COUNTDOWN_GRACE_PASSED: displayed integer 4 seconds, airborne 95 ticks (4.75 seconds), impact 20 ticks after zero, total 5 seconds")
                LOGGER.info("BEACON_ARRIVAL_WALL_TIME: {} seconds from woosh to impact; countdown={} ticks", (impactStartedNanos-entryStartedNanos)/1.0e9, HelldiversConfig.dropDelayTicks.get())
                LOGGER.info("BEACON_WOOSH_STOP_PASSED: all three layers stopped at ground deadline +{} ticks; no end tail", stoppedDeadlineDelta)
                LOGGER.info("BEACON_EXHAUST_SMOKE_PASSED: white={}, black={}", HellpodJetParticle.whiteSmoke, HellpodJetParticle.blackSmoke)
                LOGGER.info("BEACON_INPUT_ERROR_PASSED: user-confirmed incorrect-input cue played exactly once for the invalid arrow; cancel/reselect/switch silent")
                LOGGER.info("BEACON_INPUT_AUDIO_PASSED: ordered recordings {}, Alt release and invalid prefix reset to step 1; no vanilla click", inputSounds)
            }
        }
        if (ticks == 755) server.execute {
            val actual = server.playerList.getPlayer(player.uuid)!!
            actual.inventory.setItem(7, actual.inventory.getItem(0)); actual.inventory.setItem(0, ItemStack.EMPTY)
            actual.inventory.setItem(1, ItemStack(net.minecraft.world.item.Items.STICK)); actual.inventory.selected = 1
            actual.connection.send(net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(1))
            actual.inventoryMenu.broadcastChanges()
        }
        if (ticks == 765) {
            check(player.inventory.selected == 1 && player.inventory.getItem(7).item is com.example.sbwhelldiver.item.BeaconItem)
            slidePositions.clear(); monitorSlide = true
            StratagemClient.testHold = true
            StratagemClient.input(Stratagems.DOWN) // Slot packet MUST arrive before this very first arrow.
            check(player.inventory.selected == 7) { "Alt did not immediately select a beacon in the last hotbar slot" }
        }
        if (ticks in 768..777 && (ticks - 765) % 3 == 0) {
            StratagemClient.input(Stratagems.get("machine_gun_sentry")!!.code[(ticks - 765) / 3])
        }
        if (ticks == 785) {
            monitorSlide = false
            check(BeaconData.selected(player.mainHandItem)?.id == "machine_gun_sentry") { "First arrow was lost during the automatic slot switch" }
            check(slidePositions.size >= 4 && slidePositions.first() < 0 && slidePositions.last() > 0)
            check(slidePositions.zipWithNext().all { (a, b) -> b >= a - .001f }) { "HUD slid backwards or jumped between frames" }
            check(slidePositions.any { it > slidePositions.first() + 1 && it < slidePositions.last() - 1 }) { "List appeared instantly instead of sliding smoothly" }
            screenshot(mc, "hotbar_alt")
            hotbarAltVerified = true
            server.execute {
                val actual = server.playerList.getPlayer(player.uuid)!!
                check(actual.inventory.selected == 7 && BeaconData.selected(actual.mainHandItem)?.id == "machine_gun_sentry")
                LOGGER.info("BEACON_HOTBAR_ALT_PASSED: selected slot 7 from a stick; the first arrow and entire code reached the server")
                LOGGER.info("STRATAGEM_HUD_SLIDE_PASSED: {} frames, monotonically from {} to {}", slidePositions.size, slidePositions.first(), slidePositions.last())
            }
            exitPositions.clear(); monitorExit = true
            StratagemClient.testHold = false
            player.inventory.selected = 1
            mc.connection!!.send(net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(1))
        }
        if (ticks == 787) screenshot(mc, "hud_exit")
        if (ticks == 791) {
            monitorExit = false
            check(exitPositions.size >= 3 && exitPositions.first() > 0 && exitPositions.last() < -30f)
            check(exitPositions.zipWithNext().all { (a, b) -> b <= a + .001f }) { "Exit animation jumped or reversed" }
            check(StratagemClient.lastHudLayout == null && player.inventory.selected == 1) { "Exit continued rendering or changed the held item" }
            exitVerified = true
            LOGGER.info("STRATAGEM_HUD_EXIT_PASSED: {} frames from {} to {}; item switch and Alt release smoothly hid the retained list", exitPositions.size, exitPositions.first(), exitPositions.last())
        }
        if (ticks == 790) server.execute {
            val level = server.overworld(); val actual = server.playerList.getPlayer(player.uuid)!!
            for (x in 618..624) for (z in 677..683) level.setBlock(BlockPos(x, 256, z), Blocks.BEDROCK.defaultBlockState(), 2)
            for (x in 619..623) for (z in 678..682) level.setBlock(BlockPos(x, 260, z), Blocks.BEDROCK.defaultBlockState(), 2)
            val ball = com.example.sbwhelldiver.entity.StratagemBeacon(ModEntities.STRATAGEM_BEACON.get(), actual, level)
            val stack = actual.inventory.getItem(7).copy()
            BeaconData.activate(stack, Stratagems.get("rocket_sentry")!!)
            ball.setItem(stack); ball.setPos(621.5, 257.3, 680.5); ball.deltaMovement = net.minecraft.world.phys.Vec3(0.0, -0.4, 0.0)
            level.addFreshEntity(ball); rejectedBall = ball
        }
        if (ticks > 790 && StratagemClient.lastFailureRows == setOf(2) && failureSeenAt < 0) {
            check(!StratagemClient.holdingAlt() && player.inventory.selected == 1 && player.mainHandItem.item == net.minecraft.world.item.Items.STICK)
            check(rejectedBall?.deploymentFailed == true) { "Failure overlay appeared before the server rejected deployment" }
            failureSeenAt = ticks
        }
        if (failureSeenAt >= 0 && ticks == failureSeenAt + 8) {
            check(StratagemClient.lastHudLayout != null && StratagemClient.lastFailureRows == setOf(2))
            check(player.inventory.selected == 1 && StratagemClient.prefix.isEmpty()) { "Failure popup changed the held item or began an input session" }
            check(deploymentSounds.size == 2) { "Rejected landing played successful beam activation audio" }
            screenshot(mc, "deployment_failed")
            failureHudVerified = true
        }
        if (failureSeenAt >= 0 && ticks == failureSeenAt + 59) { exitPositions.clear(); monitorExit = true }
        if (failureSeenAt >= 0 && ticks == failureSeenAt + 68) {
            monitorExit = false
            check(exitPositions.size >= 3 && exitPositions.first() > 0 && exitPositions.last() < -30f)
            check(exitPositions.zipWithNext().all { (a, b) -> b <= a + .001f }) { "Failure list did not slide out smoothly" }
            check(exitVerified && hotbarAltVerified && failureHudVerified && StratagemClient.lastHudLayout == null && StratagemClient.lastFailureRows.isEmpty())
            check(player.inventory.selected == 1 && BeaconData.selected(player.inventory.getItem(7)) == null)
            LOGGER.info("BEACON_FAILURE_HUD_PASSED: real covered landing sent owner-only result; row 3 arrows replaced for three seconds; stick and input unchanged; popup expired")
            LOGGER.info("STRATAGEM_FAILURE_EXIT_PASSED: expiry kept failure rows until smoothly sliding offscreen; {} frames", exitPositions.size)
            mc.execute { mc.stop() }
        }
        if (ticks > 985) error("Beacon client verification did not finish: failureSeen=$failureSeenAt, ballFailed=${rejectedBall?.deploymentFailed}")
    }
}

