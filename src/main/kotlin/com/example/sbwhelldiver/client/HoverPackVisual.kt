package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.init.ModSounds
import com.example.sbwhelldiver.item.HoverPack
import com.example.sbwhelldiver.item.HoverPackFlight.Phase
import com.example.sbwhelldiver.item.StratagemBackpacks
import com.example.sbwhelldiver.network.HoverPackStatePacket
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal object HoverPackVisual {
    private data class State(val phase: Phase, val started: Int, val received: Int)
    private val states = WeakHashMap<AbstractClientPlayer, State>()
    private val engines = WeakHashMap<AbstractClientPlayer, WeakReference<EngineSound>>()

    private fun state(player: AbstractClientPlayer) =
        states[player]?.takeIf { player.tickCount - it.received < 15 }

    fun phase(player: AbstractClientPlayer) = state(player)?.phase ?: Phase.IDLE

    private class EngineSound(private val player: AbstractClientPlayer) : AbstractTickableSoundInstance(
        ModSounds.EVENTS.getValue("sfx_hover_pack_loop").get(), SoundSource.PLAYERS, RandomSource.create()
    ) {
        private val fullVolume = .24f

        init {
            looping = true
            volume = .025f
            follow()
        }

        private fun follow() {
            x = player.x
            y = player.y
            z = player.z
        }

        override fun tick() {
            follow()
            val phase = phase(player)
            if (phase == Phase.IDLE || phase == Phase.DESCENT || !player.isAlive || player.isRemoved ||
                Minecraft.getInstance().level !== player.level() || StratagemBackpacks.equipped(player)?.item !is HoverPack) {
                volume -= .12f
                if (volume <= 0f) {
                    stop()
                    if (engines[player]?.get() === this) engines.remove(player)
                }
            } else {
                volume = (volume + .025f).coerceAtMost(fullVolume)
            }
        }
    }

    fun receive(packet: HoverPackStatePacket) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.level?.getEntity(packet.entityId) as? AbstractClientPlayer ?: return
        if (packet.phase == Phase.IDLE) {
            states.remove(player)
            return
        }
        val old = state(player)
        val now = player.tickCount
        val started = if (old?.phase == packet.phase) old.started else now - packet.age
        states[player] = State(packet.phase, started, now)
        if (packet.phase == Phase.HOVER && engines[player]?.get() == null) {
            val sound = EngineSound(player)
            engines[player] = WeakReference(sound)
            minecraft.soundManager.play(sound)
        }
    }

    fun draw(pose: PoseStack, buffers: MultiBufferSource, light: Int,
             player: AbstractClientPlayer? = null, partial: Float = 0f, age: Float = 0f) {
        val state = player?.let(::state)
        val elapsed = if (state == null || player == null) 0f else (player.tickCount - state.started + partial).coerceAtLeast(0f)
        val (clip, time, power) = when (state?.phase) {
            Phase.ASCENT -> Triple("jump", elapsed / 20f, .97f)
            Phase.HOVER -> Triple("hover_idle", (elapsed / 20f) % .5f, .65f)
            Phase.DESCENT -> Triple("land", elapsed / 20f, .56f * (1f - elapsed / 24f).coerceIn(0f, 1f))
            else -> Triple("idle", 0f, 0f)
        }
        MachineMesh.drawHoverPack(clip, time, pose, buffers, light, power > 0f) { nozzle, axis ->
            GuardDogThrusters.capture(pose, nozzle, axis, power, age, 3.4f, 1.7f)
        }
    }

    val itemRenderer by lazy {
        val minecraft = Minecraft.getInstance()
        object : BlockEntityWithoutLevelRenderer(minecraft.blockEntityRenderDispatcher, minecraft.entityModels) {
            override fun renderByItem(stack: ItemStack, context: ItemDisplayContext, pose: PoseStack,
                                      buffers: MultiBufferSource, light: Int, overlay: Int) {
                pose.pushPose()
                pose.translate(.5, .5, .5)
                pose.scale(1.65f, 1.65f, 1.65f)
                pose.translate(0.0, -.065, .016)
                draw(pose, buffers, light)
                pose.popPose()
            }
        }
    }
}
