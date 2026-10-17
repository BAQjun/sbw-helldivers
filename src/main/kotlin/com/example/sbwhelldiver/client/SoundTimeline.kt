package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.asset.AssetCatalog
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.example.sbwhelldiver.entity.LaserClock
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.ceil

/** Plays the reviewed original event recipes; marker-only events remain silent. */
object SoundTimeline : ResourceManagerReloadListener {
    data class Cue(val time: Double, val effect: String, val locator: String)
    private data class Effect(val kind: String, val audible: Boolean, val fade: Int, val fadeOut: Int, val duration: Int,
        val stops: List<String>, val minimumLaserHeat: Float?, val requiresLaserFiring: Boolean, val requiresMineRotation: Boolean)
    private data class Pending(val due: Long, val cue: Cue, val group: String)
    private class State {
        val pending = mutableListOf<Pending>()
        val sounds = mutableListOf<NodeSound>()
    }
    private var effects = emptyMap<String, Effect>()
    private var models = emptyMap<String, Map<String, List<Cue>>>()
    private val states = WeakHashMap<AbstractHellpodEntity, State>()
    private val started = mutableMapOf<String, Int>()
    private val allocatedChannels = mutableSetOf<String>()
    internal fun startedCount(effect: String) = started[effect] ?: 0
    internal fun allocatedChannel(effect: String) = effect in allocatedChannels
    fun debugReport() = "effects=${effects.size}, models=${models.size}, started=$started, loops=" +
        states.values.sumOf { state -> state.sounds.count { !it.isStopped && it.isLooping } }

    override fun onResourceManagerReload(manager: ResourceManager) {
        clear()
        val resource = manager.getResource(ResourceLocation(MOD_ID, "helldivers/audio.json")).orElseThrow()
        val root = resource.openAsReader().use { JsonParser.parseReader(it).asJsonObject }
        effects = root["effects"].asJsonObject.entrySet().associate { (name, value) ->
            val e = value.asJsonObject
            name to Effect(e["kind"].asString, e["audible"].asBoolean,
                ceil(e["fade_in"].asDouble * 20).toInt(), ceil(e["fade_out"].asDouble * 20).toInt(), ceil(e["duration"].asDouble * 20).toInt(),
                e["stop_effects"].asJsonArray.map { it.asString },
                e.get("minimum_laser_heat")?.asFloat, e.get("requires_laser_firing")?.asBoolean ?: false,
                e.get("requires_mine_rotation")?.asBoolean ?: false)
        }
        models = root["models"].asJsonObject.entrySet().associate { (model, value) ->
            model to value.asJsonObject.entrySet().associate { (name, animation) ->
                name to animation.asJsonObject["events"].asJsonArray.map {
                    val c = it.asJsonObject
                    Cue(c["time"].asDouble, c["effect"].asString, c["locator"].asString)
                }
            }
        }
    }

    fun cues(entity: AbstractHellpodEntity, animation: String): List<Cue> =
        models[entity.profile.model]?.get(animation).orEmpty()

    fun schedule(entity: AbstractHellpodEntity, animation: String, age: Int = 0, group: String = animation, catchUp: Boolean = false) {
        schedule(entity, cues(entity, animation), age, group, catchUp)
    }
    fun schedule(entity: AbstractHellpodEntity, cues: List<Cue>, age: Int = 0, group: String = "manual", catchUp: Boolean = false) {
        val state = states.getOrPut(entity) { State() }
        val now = entity.tickCount.toLong()
        cues.forEach { cue ->
            val tick = ceil(cue.time * 20 - 1e-7).toInt()
            if (catchUp || age <= 2 || tick >= age) state.pending.add(Pending(now + (tick - age).coerceAtLeast(0), cue, group))
        }
        tick(entity)
    }
    fun cancel(entity: AbstractHellpodEntity, group: String) {
        states[entity]?.pending?.removeAll { it.group == group }
    }
    fun stopMatching(entity: AbstractHellpodEntity, predicate: (String) -> Boolean) {
        val state = states[entity] ?: return
        state.pending.removeAll { predicate(it.cue.effect) }
        state.sounds.filter { predicate(it.effect) }.forEach {
            it.finish()
            // Stop the OpenAL channel now, rather than waiting for the sound tick.
            Minecraft.getInstance().soundManager.stop(it)
        }
    }
    private fun play(entity: AbstractHellpodEntity, cue: Cue, state: State) {
        val definition = effects[cue.effect] ?: return
        // A Stop action targets existing instances, never this new event's pending Play action.
        definition.stops.forEach { effect -> state.sounds.filter { it.effect == effect }.forEach { it.release(definition.fadeOut) } }
        if (!definition.audible || cue.effect !in AssetCatalog.audibleEffects || !allowed(entity,definition)) return
        if (definition.kind == "loop" && state.sounds.any { it.effect == cue.effect && !it.isStopped && !it.releasing }) return
        val sound = NodeSound(entity, cue, definition)
        if (java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest") || java.lang.Boolean.getBoolean("sbw_helldiver.mortarSmokeTest") || java.lang.Boolean.getBoolean("sbw_helldiver.statusMortarSmokeTest") || java.lang.Boolean.getBoolean("sbw_helldiver.flameSmokeTest") || java.lang.Boolean.getBoolean("sbw_helldiver.shieldSmokeTest")) started.merge(cue.effect, 1, Int::plus)
        state.sounds.add(sound)
        Minecraft.getInstance().soundManager.play(sound)
    }
    fun tick(entity: AbstractHellpodEntity) {
        val state = states[entity] ?: return
        val ready = state.pending.filter { it.due <= entity.tickCount }
        state.pending.removeAll(ready.toSet())
        ready.forEach { play(entity, it.cue, state) }
        state.sounds.removeAll { it.isStopped }
    }
    fun clear() {
        states.values.forEach { s -> s.sounds.forEach { it.finish() } }
        states.clear()
    }

    fun verifySmokeTest() {
        check(started["sfx_hellpod_impact"] == 4) { "Missing impact events: $started" }
        check(started["sfx_hellpod_cap_release"] == 4) { "Missing cap releases: $started" }
        check((started["sfx_autocannon_fire_delayed"] ?: 0) > 0) { "Delayed cannon layer was canceled" }
        check(started["sfx_mg_fire_stop"] == 1 && started["sfx_gatling_fire_stop"] == 1) { "Missing fire stop events" }
        check(states.values.none { state -> state.sounds.any { !it.isStopped && it.isLooping } }) { "Orphaned loops" }
    }

    private fun position(entity: AbstractHellpodEntity, locator: String): Vec3 = when {
        locator=="emitter" && entity is com.example.sbwhelldiver.entity.ShieldGeneratorEntity -> entity.emitter()
        locator=="tesla_top" -> entity.position().add(0.0,entity.profile.aimHeight,0.0)
        locator.contains("muzzle") && entity is com.example.sbwhelldiver.entity.HmgEmplacementEntity -> entity.muzzlePosition(entity.nextBarrel)
        locator.contains("muzzle") && !entity.profile.isSupply -> entity.getShootPos("Main", 1f)
        locator.contains("pitch") || locator.contains("fold") || locator.contains("lift") ->
            entity.position().add(0.0, entity.profile.aimHeight, 0.0)
        else -> entity.position().add(0.0, 0.1, 0.0)
    }
    private fun allowed(entity: AbstractHellpodEntity, definition: Effect): Boolean {
        if(definition.requiresMineRotation && (entity !is com.example.sbwhelldiver.entity.MineDispenserEntity || !entity.spinning))return false
        val minimum=definition.minimumLaserHeat ?: return true
        val sentry=entity as? SentryHellpodEntity ?: return false
        return sentry.profile.isLaser && sentry.phase==AbstractHellpodEntity.Phase.ACTIVE &&
            sentry.laserHeat>=minimum-0.0001f && (!definition.requiresLaserFiring || sentry.laserState==LaserClock.State.FIRING)
    }
    private class NodeSound(entity: AbstractHellpodEntity, cue: Cue, private val definition: Effect) :
        AbstractTickableSoundInstance(SoundEvent.createVariableRangeEvent(AssetCatalog.sound(cue.effect)),
            SoundSource.NEUTRAL, RandomSource.create()) {
        val effect = cue.effect
        private val reference = WeakReference(entity)
        private val locator = cue.locator
        private val fixed = if (definition.kind != "loop" && locator.contains("muzzle")) position(entity, locator) else null
        private var age = 0
        private var releaseLeft = -1
        val releasing get() = releaseLeft >= 0
        private var releaseLength = 1
        private var releaseVolume = 1f
        init {
            looping = definition.kind == "loop"
            delay = 0
            // Variant gains are already in sounds.json; applying them here would square the gain.
            volume = if (definition.fade > 0) 0.001f else 1f
            updatePosition(entity)
        }
        private fun updatePosition(entity: AbstractHellpodEntity) {
            val p = fixed ?: position(entity, locator)
            x = p.x; y = p.y; z = p.z
        }
        fun finish() { volume = 0f; stop() }
        fun release(ticks: Int) {
            if (ticks <= 0) { stop(); return }
            releaseLeft = ticks
            releaseLength = ticks
            releaseVolume = volume
        }
        override fun tick() {
            if (isStopped) return
            val entity = reference.get()
            if (entity == null || entity.isRemoved || Minecraft.getInstance().level !== entity.level()) { stop(); return }
            // Native heat-dependent layers must also stop as the synced heat cools.
            if(!allowed(entity,definition)) { finish();return }
            updatePosition(entity)
            age++
            if(age in 2..10 && (java.lang.Boolean.getBoolean("sbw_helldiver.flameSmokeTest") || java.lang.Boolean.getBoolean("sbw_helldiver.shieldSmokeTest")) && Minecraft.getInstance().soundManager.isActive(this)) allocatedChannels.add(effect)
            if (releaseLeft >= 0) {
                volume = releaseVolume * releaseLeft-- / releaseLength
                if (releaseLeft < 0) stop()
                return
            }
            volume = if (definition.fade > 0) (age.toFloat() / definition.fade).coerceAtMost(1f) else 1f
            if (!looping && age > definition.duration + 20) stop()
        }
    }
}
