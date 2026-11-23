package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.item.HoverPackFlight.Phase
import com.example.sbwhelldiver.network.BeaconNetwork
import com.example.sbwhelldiver.network.HoverPackInputPacket
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer

internal fun movementAxis(positive: KeyMapping, negative: KeyMapping): Int =
    (if (positive.isDown) 1 else 0) - (if (negative.isDown) 1 else 0)

internal object HoverPackSteering {
    private var player: LocalPlayer? = null
    private var lastInput: HoverPackInputPacket? = null
    private var lastSentAt = 0

    fun poll(minecraft: Minecraft) {
        val local = minecraft.player
        if (local !== player) {
            player = local
            lastInput = null
        }
        val phase = local?.let(HoverPackVisual::phase)
        if (local == null || (phase != Phase.ASCENT && phase != Phase.HOVER)) {
            lastInput = null
            return
        }

        val options = minecraft.options
        val input = if (minecraft.screen == null) {
            HoverPackInputPacket(movementAxis(options.keyUp, options.keyDown), movementAxis(options.keyLeft, options.keyRight))
        } else HoverPackInputPacket(0, 0)
        if (input != lastInput || local.tickCount - lastSentAt >= 4) {
            BeaconNetwork.CHANNEL.sendToServer(input)
            lastInput = input
            lastSentAt = local.tickCount
        }
    }
}
