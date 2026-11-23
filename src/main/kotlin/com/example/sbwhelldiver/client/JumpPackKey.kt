package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.item.HoverPack
import com.example.sbwhelldiver.item.HoverPackFlight.Phase
import com.example.sbwhelldiver.item.JumpPack
import com.example.sbwhelldiver.item.JumpPackFlight
import com.example.sbwhelldiver.item.StratagemBackpacks
import com.example.sbwhelldiver.network.BeaconNetwork
import com.example.sbwhelldiver.network.HoverPackInputPacket
import com.example.sbwhelldiver.network.JumpPackInputPacket
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.client.event.MovementInputUpdateEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod

@Mod.EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
internal object JumpPackKey {
    private var player: LocalPlayer? = null
    private var held = 0
    private var startedOnGround = false
    private var hoverInput: HoverPackInputPacket? = null
    private var hoverSentAt = 0
    private var suppressRepeatedJump = false

    @SubscribeEvent
    fun tick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        val minecraft = Minecraft.getInstance()
        val local = minecraft.player
        if (local !== player) {
            player = local
            held = 0
            hoverInput = null
            suppressRepeatedJump = false
        }
        val stack = local?.let(StratagemBackpacks::equipped)
        if (local != null && stack?.item is HoverPack) {
            tickHover(minecraft, local)
            held = 0
            return
        }
        hoverInput = null
        suppressRepeatedJump = false
        val options = minecraft.options
        if (local == null || !options.keyJump.isDown) {
            held = 0
            return
        }
        if (held > JumpPackFlight.HOLD_TICKS) return
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

    private fun tickHover(minecraft: Minecraft, local: LocalPlayer) {
        val options = minecraft.options
        val usable = minecraft.screen == null && JumpPackFlight.canFly(local)
        val pressed = usable && options.keyJump.isDown
        val previous = hoverInput
        suppressRepeatedJump = pressed && previous?.held == true
        val flying = HoverPackVisual.phase(local) != Phase.IDLE
        val steering = usable && (pressed || flying)
        val input = HoverPackInputPacket(pressed, if (steering) axis(options.keyUp, options.keyDown) else 0,
            if (steering) axis(options.keyLeft, options.keyRight) else 0)
        if ((input != previous && (pressed || flying || previous != null)) ||
            ((pressed || flying) && local.tickCount - hoverSentAt >= 4)) {
            BeaconNetwork.CHANNEL.sendToServer(input)
            hoverSentAt = local.tickCount
            hoverInput = input
        }
    }

    @SubscribeEvent
    fun suppressJumpRepeat(event: MovementInputUpdateEvent) {
        if (suppressRepeatedJump) event.input.jumping = false
    }

    private fun axis(positive: KeyMapping, negative: KeyMapping) =
        (if (positive.isDown) 1 else 0) - (if (negative.isDown) 1 else 0)
}
