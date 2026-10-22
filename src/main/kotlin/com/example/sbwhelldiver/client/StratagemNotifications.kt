package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.asset.AssetCatalog
import com.example.sbwhelldiver.stratagem.CallPhase
import com.example.sbwhelldiver.stratagem.StratagemDefinition
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.sounds.SoundEvent
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
    private data class AudioProbe(val effect: String, val sound: SimpleSoundInstance, var ticks: Int = 0)
    private val probes = mutableListOf<AudioProbe>()

    fun clear() { previous.clear(); completed.clear(); seconds.clear(); probes.clear(); audioStarted.clear(); audioAllocated.clear(); countedPhases.clear(); countedSeconds.clear() }

    fun tick(loadout: List<StratagemDefinition?>) {
        val mc = Minecraft.getInstance()
        if (mc.level == null) return
        val time = StratagemCooldownClient.now()
        val ids = loadout.mapNotNull { it?.id }.toSet()
        previous.keys.retainAll(ids); completed.keys.retainAll(ids); seconds.keys.retainAll(ids)
        var countdown = false
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
                    countdown = true
                    if (java.lang.Boolean.getBoolean("sbw_helldiver.cooldownSmokeTest")) {
                        countedPhases.add(v.phase)
                        countedSeconds.getOrPut(v.phase) { mutableSetOf() }.add(left)
                    }
                }
            } else seconds.remove(def.id)
            previous[def.id] = v
        }
        // The native cooldown switch default is a one-shot tick, separate from ready notification.
        // Several Eagles share a timer: do not stack copies on the same displayed boundary.
        if (countdown) play("stratagem_countdown")
        if (ready) play("stratagem_ready", 12)
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

    private fun play(effect: String, delay: Int = 0) {
        val sound = SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(AssetCatalog.sound(effect)), 1f, 1f)
        val manager = Minecraft.getInstance().soundManager
        if (delay == 0) manager.play(sound) else manager.playDelayed(sound, delay)
        if (java.lang.Boolean.getBoolean("sbw_helldiver.cooldownSmokeTest")) {
            audioStarted[effect] = (audioStarted[effect] ?: 0) + 1
            probes.add(AudioProbe(effect, sound))
        }
    }
}
