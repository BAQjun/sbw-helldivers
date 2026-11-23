package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.init.ModSounds
import com.example.sbwhelldiver.item.JumpPack
import com.example.sbwhelldiver.item.StratagemBackpacks
import com.example.sbwhelldiver.network.JumpPackThrustPacket
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.joml.Vector3f
import java.lang.ref.WeakReference
import java.util.WeakHashMap

internal object JumpPackVisual {
    private data class Thrust(val tick: Int, val braking: Boolean)
    private val exhausts = WeakHashMap<AbstractClientPlayer, Thrust>()
    private val flightSounds = WeakHashMap<AbstractClientPlayer, WeakReference<FlightSound>>()

    private class FlightSound(private val player: AbstractClientPlayer) : AbstractTickableSoundInstance(
        ModSounds.EVENTS.getValue("sfx_jump_pack_flight").get(), SoundSource.PLAYERS, RandomSource.create()
    ) {
        private var age = 0
        var fading = false

        init { updatePosition() }

        private fun updatePosition() {
            x = player.x
            y = player.y
            z = player.z
        }

        override fun tick() {
            updatePosition()
            age++
            if (player.isRemoved || !player.isAlive || Minecraft.getInstance().level !== player.level() ||
                StratagemBackpacks.equipped(player)?.item !is JumpPack || (age > 5 && player.onGround())) fading = true
            if (fading) {
                volume -= .25f
                if (volume <= 0f) stop()
            }
            if ((age > 155 || isStopped) && flightSounds[player]?.get() === this) flightSounds.remove(player)
        }
    }

    fun receive(packet: JumpPackThrustPacket) {
        val minecraft = Minecraft.getInstance()
        val player = minecraft.level?.getEntity(packet.entityId) as? AbstractClientPlayer ?: return
        if (packet.braking) {
            flightSounds[player]?.get()?.fading = true
        } else if (flightSounds[player]?.get() == null) {
            val sound = FlightSound(player)
            flightSounds[player] = WeakReference(sound)
            minecraft.soundManager.play(sound)
        }
        exhausts[player] = Thrust(player.tickCount, packet.braking)
    }

    fun draw(pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        MachineMesh.draw("jump_pack", "", 0f, pose, buffers, light)
    }

    fun exhaust(pose: PoseStack, player: AbstractClientPlayer, partial: Float, age: Float) {
        val state = exhausts[player] ?: return
        val elapsed = player.tickCount - state.tick + partial
        if (elapsed >= 6f) {
            exhausts.remove(player)
            return
        }
        val power = if (state.braking) .68f else 1f
        val fade = 1f - ((elapsed - 3f) / 3f).coerceIn(0f, 1f)
        MachineMesh.jumpPackNozzles().forEach { nozzle ->
            GuardDogThrusters.capture(pose, Vector3f(nozzle.x.toFloat(), nozzle.y.toFloat(), nozzle.z.toFloat()),
                Vector3f(0f, -1f, 0f), power * fade, age, 3.4f, 1.7f)
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
                pose.translate(0.0, -.040, -.054)
                draw(pose, buffers, light)
                pose.popPose()
            }
        }
    }
}
