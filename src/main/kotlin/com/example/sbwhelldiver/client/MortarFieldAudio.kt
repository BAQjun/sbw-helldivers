package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.asset.AssetCatalog
import com.example.sbwhelldiver.entity.MortarFieldEntity
import com.google.gson.JsonParser
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.ceil

/** World impact audio follows the field, independently of the turret and the removed shell. */
@EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object MortarFieldAudio : ResourceManagerReloadListener {
    private class State(var nextCrackle: Int = 10)
    private val fields = WeakHashMap<MortarFieldEntity, State>()
    private val sounds = mutableListOf<FieldSound>()
    private var durations = emptyMap<String, Int>()
    private var currentLevel: net.minecraft.client.multiplayer.ClientLevel? = null
    private val started = mutableMapOf<String, Int>()
    private val activeChannels = mutableSetOf<String>()
    internal fun allocatedChannel(effect: String) = effect in activeChannels
    internal var gasFadeCompletions = 0
        private set
    internal fun startedCount(effect: String) = started[effect] ?: 0
    internal val activeGasSounds get() = sounds.count { it.gasAmbient && !it.isStopped }
    internal fun debugReport() = "started=$started gasSounds=$activeGasSounds gasStops=$gasFadeCompletions channels=$activeChannels"

    override fun onResourceManagerReload(manager: ResourceManager) {
        clear()
        val resource = manager.getResource(ResourceLocation.fromNamespaceAndPath(MOD_ID, "helldivers/audio.json")).orElseThrow()
        durations = resource.openAsReader().use { reader ->
            JsonParser.parseReader(reader).asJsonObject["effects"].asJsonObject.entrySet().associate { (name, value) ->
                name to ceil(value.asJsonObject["duration"].asDouble * 20).toInt()
            }
        }
    }
    private fun clear() {
        val manager = Minecraft.getInstance().soundManager
        sounds.forEach { it.finish(); manager.stop(it) }
        sounds.clear(); fields.clear()
    }
    private fun play(field: MortarFieldEntity, effect: String, gasAmbient: Boolean = false) {
        check(effect in AssetCatalog.audibleEffects && effect in durations) { "Missing native mortar impact layer: $effect" }
        val sound = FieldSound(field, effect, durations.getValue(effect), gasAmbient)
        sounds.add(sound)
        Minecraft.getInstance().soundManager.play(sound)
        if (java.lang.Boolean.getBoolean("sbw_helldiver.statusMortarSmokeTest") || java.lang.Boolean.getBoolean("sbw_helldiver.emsProbe")) started.merge(effect, 1, Int::plus)
    }
    @SubscribeEvent fun tick(event: net.neoforged.neoforge.client.event.ClientTickEvent.Post) {
        val level = Minecraft.getInstance().level
        if (level !== currentLevel) { clear(); currentLevel = level }
        if (level == null) return
        level.entitiesForRendering().filterIsInstance<MortarFieldEntity>().forEach { field ->
            if(field.kind==MortarFieldEntity.Kind.SMOKE || field.remainingTicks<=0)return@forEach
            val age = field.lifetimeTicks - field.remainingTicks
            val state = fields.getOrPut(field) {
                // Tracking an existing field must not replay the original impact or activation.
                val fresh = age <= 4
                if (field.kind == MortarFieldEntity.Kind.GAS) {
                    if (fresh && !field.mineCloud) for (layer in 1..6) play(field, "sfx_gas_mortar_impact_$layer")
                    if (field.remainingTicks > 0) play(field, "sfx_gas_mortar_loop", true)
                } else if (field.orbitalEms) {
                    if(fresh) for(layer in 1..2) play(field,"sfx_orbital_ems_impact_$layer")
                    play(field,"sfx_orbital_ems_loop")
                } else if(fresh) {
                    play(field,"sfx_ems_mortar_impact_1")
                    // Native field event 1541665272 has no delayed Play action.
                    for(layer in 1..4) play(field,"sfx_ems_mortar_field_$layer")
                }
                State(if(fresh)10 else age+3)
            }
            if(field.orbitalEms && age>=state.nextCrackle && field.remainingTicks>0) {
                play(field,"sfx_orbital_ems_crackle")
                state.nextCrackle=age+3+level.random.nextInt(8)
            }
        }
        val expired=fields.keys.filter { it.isRemoved || it.remainingTicks<=0 }
        for(field in expired) {
            if(field.orbitalEms && (field.remainingTicks<=1 || field.removalReason==net.minecraft.world.entity.Entity.RemovalReason.DISCARDED)) play(field,"sfx_orbital_ems_stop")
            fields.remove(field)
        }
        sounds.removeAll { it.isStopped }
    }
    private class FieldSound(field: MortarFieldEntity, val effect: String, private val duration: Int,
                             val gasAmbient: Boolean) : AbstractTickableSoundInstance(
        SoundEvent.createFixedRangeEvent(AssetCatalog.sound(effect), if (gasAmbient) 64f else 96f),
        SoundSource.NEUTRAL, RandomSource.create()) {
        private val reference = WeakReference(field)
        private val sourceLevel = field.level()
        private var age = 0
        private var fade = -1
        init {
            x = field.x; y = field.y; z = field.z
            volume = 1f; pitch = 1f; delay = 0
            // The original 20-second gas source exceeds the 15-second field lifetime.
            // Its native Stop action ends playback; no timed restart or extra release clip is needed.
            looping = effect == "sfx_orbital_ems_loop"
            if(looping) volume=.01f
        }
        fun finish() { volume = 0f; stop() }
        override fun tick() {
            if (isStopped) return
            if (Minecraft.getInstance().level !== sourceLevel) { finish(); return }
            age++
            if (age in 2..10 && Minecraft.getInstance().soundManager.isActive(this)) activeChannels.add(effect)
            if (gasAmbient) {
                val field = reference.get()
                if (fade < 0 && (field == null || field.isRemoved || field.remainingTicks <= 0)) fade = 30
                if (fade >= 0) {
                    volume = fade / 30f
                    if (fade-- <= 0) { gasFadeCompletions++; finish() }
                }
            }
            if(effect == "sfx_orbital_ems_loop") {
                val field=reference.get()
                if(fade<0 && (field==null || field.isRemoved || field.remainingTicks<=0)) fade=10
                volume=if(fade<0)(age/20f).coerceAtMost(1f) else (fade/10f).coerceAtLeast(0f)
                if(fade>=0 && fade--==0)finish()
            }
            // One-shot tails survive removal of the field or shell.
            if (!looping && age > duration + 20) finish()
        }
    }
}
