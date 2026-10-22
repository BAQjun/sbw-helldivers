package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.asset.AssetCatalog
import com.example.sbwhelldiver.stratagem.CallPhase
import com.example.sbwhelldiver.stratagem.StratagemDefinition
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import kotlin.math.ceil

/** Client-only presentation: never changes server cooldowns or consumes a call. */
object StratagemNotifications {
    private const val ARROW_TICKS = 3
    private const val READY_HOLD_TICKS = 20
    private val previous = mutableMapOf<String, StratagemCooldownClient.Visual>()
    private val completed = mutableMapOf<String, Double>()
    private val seconds = mutableMapOf<String, Pair<String, Int>>()
    internal val audioStarted = mutableMapOf<String, Int>()
    internal val audioAllocated = mutableSetOf<String>()
    internal val countedPhases = mutableSetOf<CallPhase>()
    internal val countedSeconds = mutableMapOf<CallPhase, MutableSet<Int>>()
    internal val audioFaded = mutableSetOf<String>()
    private data class AudioProbe(val effect: String, val sound: UiSound, var ticks: Int = 0)
    private val probes = mutableListOf<AudioProbe>()
    private data class InboundCue(val sound: UiSound, val calls: Map<String, Long>)
    private val inbound = mutableListOf<InboundCue>()

    fun clear() { inbound.forEach { it.sound.finish() }; inbound.clear(); previous.clear(); completed.clear(); seconds.clear(); probes.clear(); audioStarted.clear(); audioAllocated.clear(); countedPhases.clear(); countedSeconds.clear(); audioFaded.clear() }

    fun tick(loadout: List<StratagemDefinition?>) {
        val mc = Minecraft.getInstance()
        if (mc.level == null) return
        val time = StratagemCooldownClient.now()
        val ids = loadout.mapNotNull { it?.id }.toSet()
        previous.keys.retainAll(ids); completed.keys.retainAll(ids); seconds.keys.retainAll(ids)
        val countdowns = mutableMapOf<String, MutableMap<String, Long>>()
        var ready = false
        for (def in loadout.filterNotNull()) {
            val v = StratagemCooldownClient.visual(def.id)
            val old = previous[def.id]
            // Observe a real expiration, never animate all already-ready entries on login.
            if (old?.phase == CallPhase.COOLDOWN && old.end <= time &&
                v.phase == CallPhase.READY && !v.blocked) {
                completed[def.id] = time
                ready = true
            }
            if (v.phase != CallPhase.READY || v.blocked) completed.remove(def.id)
            completed[def.id]?.let { if (time - it >= def.code.size * ARROW_TICKS + READY_HOLD_TICKS) completed.remove(def.id) }
            val left = ceil((v.end - time) / 20.0).toInt()
            val counting = left in 1..3 && (v.phase == CallPhase.ARRIVING ||
                v.phase == CallPhase.COOLDOWN && (v.remaining != 0 || v.rearming))
            if (counting && left > 0) {
                val stamp = "${v.phase}:${v.end}" to left
                if (seconds.put(def.id, stamp) != stamp) {
                    val group = if (v.phase == CallPhase.ARRIVING) "inbound" else "cooldown"
                    countdowns.getOrPut("stratagem_${group}_$left") { mutableMapOf() }[def.id] = v.end
                    if (java.lang.Boolean.getBoolean("sbw_helldiver.cooldownSmokeTest")) {
                        countedPhases.add(v.phase)
                        countedSeconds.getOrPut(v.phase) { mutableSetOf() }.add(left)
                    }
                }
            } else seconds.remove(def.id)
            previous[def.id] = v
        }
        // Native cooldown event stops the inbound container with a 100 ms linear fade.
        // A shared cue remains audible while any of the calls that started it is inbound.
        inbound.removeAll { cue ->
            if (cue.sound.isStopped || cue.sound.age > 60) true
            else if (cue.calls.none { (id, end) -> id in ids &&
                    StratagemCooldownClient.visual(id).let { it.phase == CallPhase.ARRIVING && it.end == end } }) {
                cue.sound.release(); true
            } else false
        }
        // Native inbound/cooldown groups each have distinct _3_sec, _2_sec and _1_sec branches.
        // Several Eagles share a timer: deduplicate identical cues, retaining distinct phases.
        countdowns.forEach { (effect, calls) ->
            val sound = play(effect)
            if (effect.startsWith("stratagem_inbound_")) inbound.add(InboundCue(sound, calls))
        }
        if (ready) play("stratagem_ready")
        probes.forEach { it.ticks++; if (it.ticks in 2..20 && mc.soundManager.isActive(it.sound)) audioAllocated.add(it.effect) }
        probes.removeAll { it.ticks > 24 }
    }

    fun visible(def: StratagemDefinition, partial: Float): Boolean {
        val v = StratagemCooldownClient.visual(def.id, partial)
        val time = StratagemCooldownClient.now(partial)
        return StratagemCooldownClient.automaticNotice(def.id, partial) ||
            v.phase == CallPhase.COOLDOWN && !v.rearming && time - v.start in 0.0..60.0 ||
            completed[def.id]?.let { time - it < def.code.size * ARROW_TICKS + READY_HOLD_TICKS } == true
    }

    fun revealedArrows(def: StratagemDefinition, partial: Float = 0f): Int? = completed[def.id]?.let {
        ((StratagemCooldownClient.now(partial) - it) / ARROW_TICKS).toInt().coerceIn(0, def.code.size)
    }

    private fun play(effect: String): UiSound {
        val sound = UiSound(effect)
        val manager = Minecraft.getInstance().soundManager
        manager.play(sound)
        if (java.lang.Boolean.getBoolean("sbw_helldiver.cooldownSmokeTest")) {
            audioStarted[effect] = (audioStarted[effect] ?: 0) + 1
            probes.add(AudioProbe(effect, sound))
        }
        return sound
    }

    private class UiSound(private val effect: String) : AbstractTickableSoundInstance(
        SoundEvent.createVariableRangeEvent(AssetCatalog.sound(effect)), SoundSource.MASTER, RandomSource.create()) {
        var age = 0; private set
        private var releaseLeft = -1
        init { relative = true; attenuation = SoundInstance.Attenuation.NONE; volume = 1f; pitch = 1f }
        fun release() { if (releaseLeft < 0) releaseLeft = 2 }
        fun finish() { volume = 0f; stop() }
        override fun tick() {
            age++
            if (releaseLeft > 0) {
                volume = --releaseLeft / 2f
                if (releaseLeft == 0) {
                    finish()
                    if (java.lang.Boolean.getBoolean("sbw_helldiver.cooldownSmokeTest")) audioFaded.add(effect)
                }
            }
            if (age > 80) finish()
        }
    }
}
