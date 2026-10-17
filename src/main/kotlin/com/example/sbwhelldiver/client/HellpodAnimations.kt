package com.example.sbwhelldiver.client

import com.atsuishio.superbwarfare.client.animation.AnimationPlayType
import com.atsuishio.superbwarfare.client.animation.entity.VehicleAnimationContext
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import java.util.WeakHashMap
import kotlin.math.*

/** SBW's third animation argument is fade ticks. Different names blend until explicitly stopped. */
object HellpodAnimations {
    private class State {
        var phase: Phase? = null
        var jet = 0
        var firing = false
        var spinning = false
        var reloading = false
        var serial = 0
        var entryStopped = false
        var entryStarted = false
        var impactStarted = false
        var playerRetroStarted=false
        var playerRetroLoop=false
    }
    private val states = WeakHashMap<AbstractHellpodEntity, State>()
    private const val IGNITE = "animation.hellpod.retro_ignite"
    private const val BURN = "animation.hellpod.retro_burn"
    private const val SHUTDOWN = "animation.hellpod.retro_shutdown"
    private const val DEPLOYED = "animation.helldiver.deployed"
    private const val DESTROYED = "animation.helldiver.destroyed"
    private const val RETRACTED = "animation.helldiver.retracted"

    fun tick(entity: AbstractHellpodEntity) {
        val s = states.getOrPut(entity) { State().also { it.serial = entity.shotSerial } }
        val flightTime = if (entity.scheduledFlight && !entity.flightSampleTime.isNaN()) entity.flightSampleTime else entity.level().gameTime.toDouble()
        val airborne = entity.phase == Phase.FALLING &&
            (!entity.scheduledFlight || flightTime < entity.impactDeadline)
        // Incoming audio must start on the first entity tick even if the distant model
        // has not yet been rendered and its animation context is still unavailable.
        if (airborne && !s.entryStarted) {
            s.entryStarted = true
            SoundTimeline.schedule(entity,
                if(entity is com.example.sbwhelldiver.entity.PlayerHellpodEntity && PlayerDropClient.belongsToLocalPlayer(entity)) PlayerDropAudio.entryCues
                else SoundTimeline.cues(entity, "animation.hellpod.retro_landing_preview").filter { it.time <= 0.3 },
                entity.phaseAge, group = "entry", catchUp = true)
        }
        if (!airborne && !s.entryStopped) {
            stopDescentAudio(entity)
            s.entryStopped = true
        }
        // A synchronized phase packet can arrive later than the shared trajectory deadline.
        // Trigger landing audio at the actual local collision time and never replay it on that packet.
        if (s.entryStarted && entity.scheduledFlight && flightTime >= entity.impactDeadline && !s.impactStarted) {
            s.impactStarted = true
            SoundTimeline.schedule(entity, "animation.hellpod.impact")
        }
        if(airborne && entity.braking && entity is com.example.sbwhelldiver.entity.PlayerHellpodEntity){
            if(!s.playerRetroStarted){
                SoundTimeline.schedule(entity,PlayerDropAudio.ignitionCues(entity),entity.brakeAge,group="player_retro",catchUp=true)
                s.playerRetroStarted=true
            }
            if(!s.playerRetroLoop){
                SoundTimeline.schedule(entity,"animation.hellpod.audio_retro_loop_start",group="player_retro")
                s.playerRetroLoop=true
            }
        }
        val ctx = entity.getAnimationInstance()?.context
        if (ctx == null) { SoundTimeline.tick(entity); return }
        if (s.phase != entity.phase) {
            val initial = s.phase == null
            if (entity.phase != Phase.FALLING) {
                stopDescentAudio(entity)
                if (s.phase == Phase.FALLING && entity.phase != Phase.IMPACT && !s.impactStarted) {
                    s.impactStarted = true
                    SoundTimeline.schedule(entity, "animation.hellpod.impact")
                }
            }
            stopPod(ctx, entity)
            if (s.firing) stopFiring(entity, ctx, s)
            if (s.spinning) ctx.animations.keys.firstOrNull { it.endsWith(".spin_down") }?.let {
                SoundTimeline.schedule(entity, it)
            }
            ctx.stopAnimation("Main", 0)
            ctx.animations.keys.filter { it.endsWith(".reload") || it.endsWith(".spin_up") || it.endsWith(".spin_down") }
                .forEach { ctx.stopAnimation(it, 0) }
            s.reloading = false
            s.spinning = false
            when (entity.phase) {
                Phase.FALLING -> {
                    ctx.playAnimation(entity.packedAnimation, AnimationPlayType.LOOP, 0)

                }
                Phase.IMPACT -> {
                    // Each pod stage already contains the complete packed pose.
                    // Playing multiple stages adds the elevator/lift offsets twice.
                    ctx.playAnimation("animation.hellpod.impact", AnimationPlayType.PLAY_ONCE_HOLD, 0)
                    stopDescentAudio(entity)
                    if (!s.impactStarted) {
                        s.impactStarted = true
                        SoundTimeline.schedule(entity, "animation.hellpod.impact", entity.phaseAge, catchUp = !initial)
                    }
                }
                Phase.DEPLOYING -> {
                    AnimationTiming.play(ctx, entity.deployAnimation, AnimationPlayType.PLAY_ONCE_HOLD, entity.phaseAge)
                    SoundTimeline.schedule(entity, entity.deployAnimation, entity.phaseAge, catchUp = !initial)
                }
                Phase.ACTIVE -> {
                    val hmg=entity as? com.example.sbwhelldiver.entity.HmgEmplacementEntity
                    if(hmg!=null && hmg.awaitingFirstMount)ctx.playAnimation("animation.hmg.waiting",AnimationPlayType.LOOP,0)
                    else ctx.playAnimation(DEPLOYED, AnimationPlayType.LOOP, 0)
                    if (initial) s.serial = entity.shotSerial
                }
                Phase.RETRACTING -> {
                    AnimationTiming.play(ctx, entity.retractAnimation, AnimationPlayType.PLAY_ONCE_HOLD, entity.phaseAge)
                    SoundTimeline.schedule(entity, entity.retractAnimation, entity.phaseAge, catchUp = !initial)
                }
                Phase.DONE -> ctx.playAnimation(RETRACTED, AnimationPlayType.LOOP, 0)
                Phase.DESTROYED -> ctx.playAnimation(DEPLOYED, AnimationPlayType.LOOP, 0)
                Phase.SPENT -> AnimationTiming.play(ctx, DESTROYED, AnimationPlayType.PLAY_ONCE_HOLD, entity.phaseAge)
            }
            s.phase = entity.phase
            s.jet = 0
        }
        if (airborne && entity.braking) {
            if (s.jet == 0) {
                ctx.stopAnimation(entity.packedAnimation, 0)
                val animation = if (entity.brakeAge >= AbstractHellpodEntity.IGNITE_TICKS) BURN else IGNITE
                AnimationTiming.play(ctx, animation, if (animation == BURN) AnimationPlayType.LOOP else AnimationPlayType.PLAY_ONCE_HOLD,
                    if (animation == IGNITE) entity.brakeAge else 0)
                // Outside listeners hear the incoming woosh; retro stages only drive visuals.
                s.jet = if (animation == BURN) 2 else 1
            }
            if (s.jet == 1 && entity.brakeAge >= AbstractHellpodEntity.IGNITE_TICKS) {
                ctx.stopAnimation(IGNITE, 0)
                ctx.playAnimation(BURN, AnimationPlayType.LOOP, 0)
                s.jet = 2
            }
            if(entity !is com.example.sbwhelldiver.entity.PlayerHellpodEntity || entity.brakeAge>=com.example.sbwhelldiver.entity.PlayerHellpodEntity.JET_START_TICKS)
            entity.profile.nozzles.forEachIndexed { index, outlet ->
                val direction = entity.profile.nozzleDirections[index]
                repeat(3) { sample -> HellpodJetParticle.emit(entity, outlet, sample, direction) }
                if (entity.tickCount % 2 == 0) HellpodJetParticle.emitSmoke(entity, outlet, direction)
            }
        }
        if(entity is SentryHellpodEntity && entity.profile.isTesla) TeslaTowerClient.tick(entity)
        if(entity is SentryHellpodEntity && entity.profile.isLaser) LaserSentryClient.tick(entity)
        if(entity is SentryHellpodEntity && entity.profile.isFlame) FlameSentryClient.tick(entity)
        if (entity.phase == Phase.ACTIVE && entity is SentryHellpodEntity && !entity.profile.isFlame && !entity.profile.isLaser && !entity.profile.isTesla) weapon(entity, ctx, s)
        if(entity is com.example.sbwhelldiver.entity.MineDispenserEntity)MineDispenserClient.tick(entity)
        if(entity is com.example.sbwhelldiver.entity.HmgEmplacementEntity)HmgClient.tickEffects(entity)
        SoundTimeline.tick(entity)
    }

    private fun stopDescentAudio(entity: AbstractHellpodEntity) {
        SoundTimeline.cancel(entity, "entry")
        SoundTimeline.cancel(entity, "player_retro")
        SoundTimeline.stopMatching(entity,if(entity is com.example.sbwhelldiver.entity.PlayerHellpodEntity) 2 else 0) {
            it.startsWith("sfx_hellpod_entry_") || it.startsWith("sfx_hellpod_player_") || it.startsWith("sfx_hellpod_retro") ||
                it.startsWith("sfx_hellpod_thruster")
        }
    }
    private fun stopPod(ctx: VehicleAnimationContext<*>, entity: AbstractHellpodEntity) {
        listOf(entity.packedAnimation, entity.deployAnimation, entity.retractAnimation,
            IGNITE, BURN, SHUTDOWN, "animation.hellpod.impact", DEPLOYED, RETRACTED, DESTROYED,
            "animation.hmg.waiting", "animation.hmg.first_mount")
            .forEach { ctx.stopAnimation(it, 0) }
    }
    private fun weapon(entity: SentryHellpodEntity, ctx: VehicleAnimationContext<*>, s: State) {
        val reload = ctx.animations.keys.firstOrNull { it.endsWith(".reload") }
        if (entity.reloading != s.reloading) {
            if (entity.reloading) {
                if (s.firing) stopFiring(entity, ctx, s)
                ctx.stopAnimation("Main", 0)
                reload?.let { AnimationTiming.play(ctx, it, AnimationPlayType.PLAY_ONCE_HOLD, entity.reloadAge) }
                // The original reload event is silent. Do not attach recorded or unrelated sounds.
            } else reload?.let { ctx.stopAnimation(it, 0) }
            s.reloading = entity.reloading
        }
        if (entity.profile.isGatling && entity.spinning != s.spinning) {
            val up = ctx.animations.keys.firstOrNull { it.endsWith(".spin_up") }
            val down = ctx.animations.keys.firstOrNull { it.endsWith(".spin_down") }
            up?.let { ctx.stopAnimation(it, 0) }
            down?.let { ctx.stopAnimation(it, 0) }
            val selected = if (entity.spinning) up else down
            selected?.let {
                ctx.playAnimation(it, AnimationPlayType.PLAY_ONCE_STOP, 0)
                SoundTimeline.schedule(entity, it)
            }
            s.spinning = entity.spinning
        }
        if (entity.profile.isRocket || entity.profile.isCannon || entity.profile.isMortar) {
            val count = (entity.shotSerial - s.serial).coerceIn(0, 4)
            repeat(count) {
                ctx.fire("Main", 0)
                SoundTimeline.schedule(entity, "animation.Main.fire")
            }
        } else if (entity.firingRecently != s.firing) {
            if (entity.firingRecently) {
                ctx.animations.keys.firstOrNull { it.endsWith(".fire_loop") }?.let {
                    ctx.playAnimation(it, AnimationPlayType.LOOP, 0)
                }
                SoundTimeline.schedule(entity, "animation.sentry.audio_fire_start", group = "fire_start")
                s.firing = true
            } else stopFiring(entity, ctx, s)
        }
        s.serial = entity.shotSerial
    }
    private fun stopFiring(entity: AbstractHellpodEntity, ctx: VehicleAnimationContext<*>, s: State) {
        ctx.animations.keys.filter { it.endsWith(".fire_loop") }.forEach { ctx.stopAnimation(it, 0) }
        SoundTimeline.cancel(entity, "fire_start")
        SoundTimeline.schedule(entity, "animation.sentry.audio_fire_stop")
        s.firing = false
    }

    fun clear() { states.clear(); SoundTimeline.clear();FlameSentryClient.clear();LaserSentryClient.clear();TeslaTowerClient.clear() }
}
