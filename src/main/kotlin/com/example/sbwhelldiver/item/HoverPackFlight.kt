package com.example.sbwhelldiver.item

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModSounds
import com.example.sbwhelldiver.network.HoverPackStatePacket
import net.minecraft.core.component.DataComponents
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.event.entity.living.LivingFallEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import java.util.WeakHashMap
import kotlin.math.cos
import kotlin.math.sin

@EventBusSubscriber(modid = MOD_ID)
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

    private data class Input(val forward: Int, val left: Int, val tick: Int)
    private class Flight(
        val stack: ItemStack, val dimension: ResourceKey<Level>, var altitude: Double, var since: Int, var input: Input
    ) {
        var phase = Phase.ASCENT
        var horizontal = Vec3.ZERO
    }

    private val flights = WeakHashMap<ServerPlayer, Flight>()

    fun input(player: ServerPlayer, forward: Int, left: Int) {
        if (forward !in -1..1 || left !in -1..1) return
        flights[player]?.input = Input(forward, left, player.tickCount)
    }

    fun start(player: ServerPlayer, stack: ItemStack, forward: Int, left: Int) {
        if (player in flights || !player.onGround() || !JumpPackFlight.canFly(player)) return
        if (readyAt(stack) > time(player) || player.cooldowns.isOnCooldown(stack.item) || !clearAbove(player, .18)) return

        val now = player.tickCount
        val flight = Flight(stack, player.level().dimension(), player.y + LIFT_HEIGHT, now, Input(forward, left, now))
        flights[player] = flight
        setReadyAt(stack, time(player) + ASCENT_TICKS + HOVER_TICKS + MAX_DESCENT_TICKS + COOLDOWN_TICKS)
        transition(player, flight, Phase.ASCENT)
        tickFlight(player, flight)
    }

    fun controlsFlight(player: ServerPlayer): Boolean = player in flights

    @SubscribeEvent
    fun tick(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        val flight = flights[player] ?: return
        if (flight.phase == Phase.ASCENT && player.tickCount == flight.since) return
        tickFlight(player, flight)
    }

    private fun tickFlight(player: ServerPlayer, flight: Flight) {
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

        val input = flight.input.takeIf { player.tickCount - it.tick <= 12 }
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

        val phaseAge = player.tickCount - flight.since
        if (phaseAge > 0 && phaseAge % 5 == 0) {
            HoverPackStatePacket.send(player, flight.phase, phaseAge)
        }
    }

    private fun transition(player: ServerPlayer, flight: Flight, phase: Phase) {
        flight.phase = phase
        flight.since = player.tickCount
        HoverPackStatePacket.send(player, phase, 0)
        when (phase) {
            Phase.ASCENT -> {
                val sound = ModSounds.EVENTS.getValue("sfx_hover_pack_launch").get()
                player.level().playSound(null, player, sound, SoundSource.PLAYERS, .56f, 1f)
            }
            Phase.DESCENT -> {
                val sound = ModSounds.EVENTS.getValue("sfx_hover_pack_land").get()
                player.level().playSound(null, player.x, player.y, player.z, sound, SoundSource.PLAYERS, .31f, 1f)
            }
            else -> Unit
        }
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
        finish(player)
    }

    private fun finish(player: ServerPlayer) {
        val flight = flights.remove(player) ?: return
        setReadyAt(flight.stack, time(player) + COOLDOWN_TICKS)
        player.cooldowns.addCooldown(flight.stack.item, COOLDOWN_TICKS)
        HoverPackStatePacket.send(player, Phase.IDLE, 0)
    }

    private fun time(player: ServerPlayer) = player.serverLevel().server.overworld().gameTime
    internal fun readyAt(stack: ItemStack) = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getLong(READY_AT) ?: 0L
    private fun setReadyAt(stack: ItemStack, readyAt: Long) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack) { it.putLong(READY_AT, readyAt) }
    }
}
