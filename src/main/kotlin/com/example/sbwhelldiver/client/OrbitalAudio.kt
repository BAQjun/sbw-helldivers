package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.StratagemMarker
import com.example.sbwhelldiver.init.ModSounds
import com.example.sbwhelldiver.stratagem.OrbitalAudioPosition
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import java.lang.ref.WeakReference
import java.util.WeakHashMap

@EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object OrbitalAudio {
    private val heard = WeakHashMap<StratagemMarker, Boolean>()
    private val sounds = mutableListOf<SpatialSound>()
    private var currentLevel: net.minecraft.client.multiplayer.ClientLevel? = null

    fun fire(source: Vec3) {
        if (Minecraft.getInstance().level == null) return
        play(SpatialSound("sfx_orbital_fire", source))
    }
    fun warp(source: Vec3, departing: Boolean) {
        if (Minecraft.getInstance().level == null) return
        play(SpatialSound(if (departing) "super_destroyer_warp_out" else "super_destroyer_warp_in", source))
    }
    private fun play(sound: SpatialSound) {
        sounds.add(sound)
        Minecraft.getInstance().soundManager.play(sound)
    }
    @SubscribeEvent fun tick(event: net.neoforged.neoforge.client.event.ClientTickEvent.Post) {
        val mc = Minecraft.getInstance()
        if (currentLevel !== mc.level) {
            sounds.forEach { it.finish(); mc.soundManager.stop(it) }
            sounds.clear(); heard.clear(); currentLevel = mc.level
        }
        val level = mc.level ?: return
        val listener = mc.gameRenderer.mainCamera.position
        for (marker in level.entitiesForRendering().filterIsInstance<StratagemMarker>()) {
            if (marker.isOrbitalLaser || marker.isOrbitalRailcannon || marker.isOrbitalGatling || marker.isOrbitalBarrage || marker.isOrbitalAirburst || marker.orbitalOrigin == null || marker.orbitalFiredAt < 0 || heard.containsKey(marker)) continue
            val progress = marker.orbitalProgress()
            if (progress in 0.0.. .95 && (1.0 - progress) * marker.orbitalFlightTicks <= 30 &&
                marker.position().distanceToSqr(listener) <= 128.0 * 128.0) {
                heard[marker] = true
                play(SpatialSound("sfx_orbital_incoming", marker.orbitalOrigin!!, marker))
            }
        }
        heard.keys.removeAll { it.isRemoved }
        sounds.removeAll { it.isStopped }
    }
    private class SpatialSound(key: String, private val source: Vec3, marker: StratagemMarker? = null) :
        AbstractTickableSoundInstance(ModSounds.EVENTS.getValue(key).get(), SoundSource.AMBIENT, RandomSource.create()) {
        private val reference = marker?.let { WeakReference(it) }
        private val sourceLevel = Minecraft.getInstance().level
        private var age = 0
        init {
            relative = false; looping = false; delay = 0; volume = 1f; pitch = 1f
            place(source, marker == null)
        }
        private fun place(at: Vec3, horizon: Boolean) {
            val p = OrbitalAudioPosition.project(Minecraft.getInstance().gameRenderer.mainCamera.position, at, horizon = horizon)
            x = p.x; y = p.y; z = p.z
        }
        fun finish() { volume = 0f; stop() }
        override fun tick() {
            if (Minecraft.getInstance().level !== sourceLevel) { finish(); return }
            if (reference == null) {
                // Recalculate against the listener so moving and turning retain the ship's world bearing.
                place(source, true)
                if (++age > 300) finish()
                return
            }
            val marker = reference.get()
            if (marker == null || marker.isRemoved) { finish(); return }
            val t = marker.orbitalProgress()
            if (t !in 0.0..1.05) { finish(); return }
            val listener = Minecraft.getInstance().gameRenderer.mainCamera.position
            val distance = marker.position().distanceTo(listener)
            val gain = (1.0 - distance / 128.0).coerceIn(0.0, 1.0)
            volume = ((.35 + .65 * t.coerceAtMost(1.0)) * gain).toFloat()
            place(source.lerp(marker.position(), t.coerceIn(0.0, 1.0)), false)
        }
    }
}
