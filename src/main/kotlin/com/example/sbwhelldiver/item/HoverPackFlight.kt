package com.example.sbwhelldiver.item

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModSounds
import com.example.sbwhelldiver.network.HoverPackStatePacket
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.event.entity.living.LivingFallEvent
import net.minecraftforge.event.entity.player.PlayerEvent
import net.minecraftforge.event.TickEvent
import java.util.WeakHashMap
import kotlin.math.cos
import kotlin.math.sin

@Mod.EventBusSubscriber(modid = MOD_ID)
object HoverPackFlight {
    const val HOVER_TICKS = 6 * 20
    const val COOLDOWN_TICKS = 12 * 20
    private const val ASCENT_TICKS = 20
    private const val MAX_DESCENT_TICKS = 75
    private const val LIFT_HEIGHT = 6.0
    private const val ASCENT_SPEED = .37
    private const val HOVER_SPEED = .105
    private const val DESCENT_SPEED = .29
    private const val READY_AT = "HoverPackReadyAt"

    enum class Phase { IDLE, ASCENT, HOVER, DESCENT }

    private data class Input(val held: Boolean, val forward: Int, val left: Int, val tick: Int)
    private data class Charge(val stack: ItemStack, val tick: Int, val floor: Double)
    private class Flight(val stack: ItemStack, val dimension: ResourceKey<Level>, var altitude: Double, var since: Int) {
        var phase = Phase.ASCENT
        var horizontal = Vec3.ZERO
    }

    private val inputs = WeakHashMap<ServerPlayer, Input>()
    private val charges = WeakHashMap<ServerPlayer, Charge>()
    private val flights = WeakHashMap<ServerPlayer, Flight>()

    fun input(player: ServerPlayer, held: Boolean, forward: Int, left: Int) {
        if (forward !in -1..1 || left !in -1..1) return
        val stack = StratagemBackpacks.equipped(player)?.takeIf { it.item is HoverPack } ?: return
        val previous = inputs.put(player, Input(held, forward, left, player.tickCount))
        if (!held) {
            charges.remove(player)
        } else if (previous?.held != true && player !in flights && player.onGround() && JumpPackFlight.canFly(player) &&
            readyAt(stack) <= time(player) && !player.cooldowns.isOnCooldown(stack.item)) {
            charges[player] = Charge(stack, player.tickCount, player.y)
        }
    }

    fun controlsFlight(player: ServerPlayer): Boolean = player in flights

    @SubscribeEvent
    fun tick(event: TickEvent.PlayerTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val player = event.player as? ServerPlayer ?: return
        val input = inputs[player]?.takeIf { player.tickCount - it.tick <= 12 }
        val flight = flights[player]
        if (flight != null) {
            tickFlight(player, flight, input)
            return
        }
        val charge = charges[player] ?: return
        if (input?.held != true || !JumpPackFlight.canFly(player) || StratagemBackpacks.equipped(player) !== charge.stack ||
            player.y - charge.floor !in -.5..1.65) {
            charges.remove(player)
            return
        }
        if (player.tickCount - charge.tick < JumpPackFlight.HOLD_TICKS) return
        charges.remove(player)
        if (!clearAbove(player, .18)) return
        val state = Flight(charge.stack, player.level().dimension(), charge.floor + LIFT_HEIGHT, player.tickCount)
        flights[player] = state
        setReadyAt(charge.stack, time(player) + ASCENT_TICKS + HOVER_TICKS + MAX_DESCENT_TICKS + COOLDOWN_TICKS)
        transition(player, state, Phase.ASCENT)
        tickFlight(player, state, input)
    }

    private fun tickFlight(player: ServerPlayer, flight: Flight, input: Input?) {
        if (player.level().dimension() != flight.dimension || !JumpPackFlight.canFly(player) ||
            StratagemBackpacks.equipped(player) !== flight.stack) {
            finish(player)
            return
        }
        val age = player.tickCount - flight.since
        val error = flight.altitude - player.y
        if (flight.phase == Phase.ASCENT && (age >= ASCENT_TICKS || error <= .07 || !clearAbove(player, .14))) {
            flight.altitude = player.y
            transition(player, flight, Phase.HOVER)
        } else if (flight.phase == Phase.HOVER && age >= HOVER_TICKS) {
            transition(player, flight, Phase.DESCENT)
        } else if (flight.phase == Phase.DESCENT && (player.onGround() || age >= MAX_DESCENT_TICKS)) {
            finish(player)
            return
        }

        val desired = if (input == null || flight.phase == Phase.DESCENT) Vec3.ZERO else {
            val yaw = Math.toRadians(player.yRot.toDouble())
            Vec3(input.left * cos(yaw) - input.forward * sin(yaw), 0.0,
                input.forward * cos(yaw) + input.left * sin(yaw)).normalize().scale(HOVER_SPEED)
        }
        flight.horizontal = flight.horizontal.lerp(desired, .44)
        val lift = when (flight.phase) {
            Phase.ASCENT -> error.coerceIn(.06, ASCENT_SPEED)
            Phase.HOVER -> (flight.altitude - player.y).times(.45).coerceIn(-.16, .16)
                .let { if (it > 0 && !clearAbove(player, it)) 0.0 else it }
            Phase.DESCENT -> -DESCENT_SPEED
            Phase.IDLE -> 0.0
        }
        player.deltaMovement = Vec3(flight.horizontal.x, lift, flight.horizontal.z)
        player.resetFallDistance()
        player.connection.send(ClientboundSetEntityMotionPacket(player))

        if ((player.tickCount - flight.since) % 5 == 0) {
            HoverPackStatePacket.send(player, flight.phase, player.tickCount - flight.since)
        }
    }

    private fun transition(player: ServerPlayer, flight: Flight, phase: Phase) {
        flight.phase = phase
        flight.since = player.tickCount
        val cue = when (phase) {
            Phase.ASCENT -> "sfx_hover_pack_launch"
            Phase.DESCENT -> "sfx_hover_pack_land"
            else -> return
        }
        player.level().playSound(null, player.x, player.y, player.z, ModSounds.EVENTS.getValue(cue).get(),
            SoundSource.PLAYERS, if (phase == Phase.ASCENT) .65f else .55f, 1f)
    }

    private fun clearAbove(player: ServerPlayer, rise: Double) =
        player.level().noCollision(player, player.boundingBox.move(0.0, rise, 0.0))

    @SubscribeEvent
    fun fall(event: LivingFallEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val flight = flights[player] ?: return
        if (player.level().dimension() == flight.dimension && StratagemBackpacks.equipped(player) === flight.stack &&
            JumpPackFlight.canFly(player)) {
            event.isCanceled = true
            if (flight.phase == Phase.DESCENT) finish(player)
        }
    }

    @SubscribeEvent
    fun logout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        inputs.remove(player)
        charges.remove(player)
        finish(player)
    }

    private fun finish(player: ServerPlayer) {
        val flight = flights.remove(player) ?: return
        setReadyAt(flight.stack, time(player) + COOLDOWN_TICKS)
        player.cooldowns.addCooldown(flight.stack.item, COOLDOWN_TICKS)
        HoverPackStatePacket.send(player, Phase.IDLE, 0)
    }

    private fun time(player: ServerPlayer) = player.serverLevel().server.overworld().gameTime
    private fun readyAt(stack: ItemStack) = stack.tag?.getLong(READY_AT) ?: 0L
    private fun setReadyAt(stack: ItemStack, readyAt: Long) {
        stack.orCreateTag.putLong(READY_AT, readyAt)
    }
}
