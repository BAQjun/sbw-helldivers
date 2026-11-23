package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.item.JumpPack
import com.example.sbwhelldiver.item.JumpPackFlight
import com.example.sbwhelldiver.item.StratagemBackpacks
import com.example.sbwhelldiver.network.BeaconNetwork
import com.example.sbwhelldiver.network.JumpPackInputPacket
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
internal object JumpPackKey {
    private var player: LocalPlayer? = null
    private var held = 0
    private var startedOnGround = false

    @SubscribeEvent
    fun tick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        val minecraft = Minecraft.getInstance()
        val local = minecraft.player
        if (local !== player) {
            player = local
            held = 0
        }
        val options = minecraft.options
        if (local == null || !options.keyJump.isDown) {
            held = 0
            return
        }
        if (held > JumpPackFlight.HOLD_TICKS) return
        val stack = StratagemBackpacks.equipped(local)
        if (minecraft.screen != null || stack?.item !is JumpPack || !JumpPackFlight.canFly(local) ||
            local.cooldowns.isOnCooldown(stack.item)) {
            held = JumpPackFlight.HOLD_TICKS + 1
            return
        }
        if (held == 0) startedOnGround = local.onGround()
        held++
        if (held != JumpPackFlight.HOLD_TICKS || !startedOnGround) return
        BeaconNetwork.CHANNEL.sendToServer(
            JumpPackInputPacket(axis(options.keyUp, options.keyDown), axis(options.keyLeft, options.keyRight))
        )
    }

    private fun axis(positive: KeyMapping, negative: KeyMapping) =
        (if (positive.isDown) 1 else 0) - (if (negative.isDown) 1 else 0)
}
