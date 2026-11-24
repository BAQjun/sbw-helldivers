package com.example.sbwhelldiver.item

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModSounds
import com.example.sbwhelldiver.network.JumpPackThrustPacket
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.CustomData
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
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
internal object JumpPackFlight {
    private const val READY_AT = "JumpPackReadyAt"
    const val RECHARGE_TICKS = 15 * 20
    private const val LAUNCH_TICKS = 10
    private const val BRAKE_TICKS = 20
    private const val FLIGHT_LIMIT = 20 * 20
    private const val FORWARD_SHARE = .4
    private const val HORIZONTAL_BOOST = 1.5
    // Convert the extracted 40 and 60 accelerations by matching their ratio to gravity.
    private const val LAUNCH_ACCELERATION = 40.0 / 9.81 * .08
    private const val BRAKE_ACCELERATION = 60.0 / 9.81 * .08
    private const val BRAKE_BACKWARD_SHARE = .1
    private const val BRAKE_TRIGGER_SPEED = 3.0 / 20
    private const val BRAKE_MIN_SPEED = 1.0 / 20
    private const val MAX_LANDING_PROBE = 2.6

    private class Flight(
        val stack: ItemStack, val dimension: net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>,
        val started: Int, val direction: Vec3, var position: Vec3
    ) {
        var velocity: Vec3 = Vec3.ZERO
        var launchBlocked = false
        var brakeStarted = -1
    }

    private val flights = WeakHashMap<ServerPlayer, Flight>()

    fun canFly(player: Player): Boolean =
        player.isAlive && !player.isSpectator && !player.abilities.flying && !player.isPassenger &&
            !player.isFallFlying && !player.isInWaterOrBubble && !player.isInLava && !player.onClimbable()

    fun start(player: ServerPlayer, stack: ItemStack, forward: Int, left: Int) {
        if (player in flights || !player.onGround() || !canFly(player)) return
        if (readyAt(stack) > time(player) || player.cooldowns.isOnCooldown(stack.item)) return
        val yaw = Math.toRadians(player.yRot.toDouble())
        val direction = Vec3(left * cos(yaw) - forward * sin(yaw), 0.0, forward * cos(yaw) + left * sin(yaw)).normalize()
        if (!clearAbove(player)) return
        // Reserve the charge immediately so disconnects and abrupt world shutdowns cannot restore it.
        setReadyAt(stack, time(player) + FLIGHT_LIMIT + RECHARGE_TICKS)
        val flight = Flight(stack, player.level().dimension(), player.tickCount, direction, player.position())
        flights[player] = flight
        val launchSound = ModSounds.EVENTS.getValue("sfx_jump_pack_launch").get()
        player.level().playSound(null, player, launchSound, SoundSource.PLAYERS, .85f, 1f)
        thrust(player, flight, 0, player.deltaMovement)
    }

    @SubscribeEvent
    fun tick(event: PlayerTickEvent.Post) {
        val player = event.entity as? ServerPlayer ?: return
        val flight = flights[player] ?: return
        val age = player.tickCount - flight.started
        if (flight.dimension != player.level().dimension() || !canFly(player) ||
            StratagemBackpacks.equipped(player) !== flight.stack || age >= FLIGHT_LIMIT) {
            finish(player)
            return
        }
        if (age <= 0) return
        if (player.onGround() && (age >= LAUNCH_TICKS || flight.launchBlocked)) {
            finish(player)
            return
        }
        val position = player.position()
        val moved = position.subtract(flight.position)
        flight.position = position
        // During powered ascent, advance the commanded motion even if position packets arrive in a group.
        // Once the player is coasting, read the actual displacement to time the landing pulse.
        val velocity = if (age < LAUNCH_TICKS && !flight.launchBlocked) {
            val previous = flight.velocity
            Vec3(previous.x * .91, (previous.y - .08) * .98, previous.z * .91)
        } else if (moved.lengthSqr() > 1.0e-6 && moved.lengthSqr() < 16.0) {
            Vec3(moved.x * .91, (moved.y - .08) * .98, moved.z * .91)
        } else player.deltaMovement
        thrust(player, flight, age, velocity)
    }

    private fun thrust(player: ServerPlayer, flight: Flight, age: Int, velocity: Vec3) {
        if (age < LAUNCH_TICKS && !clearAbove(player)) {
            flight.launchBlocked = true
        }
        if (age < LAUNCH_TICKS && !flight.launchBlocked) {
            val progress = age.toDouble() / LAUNCH_TICKS
            val force = LAUNCH_ACCELERATION * (1.0 - progress * progress)
            val forward = if (flight.direction == Vec3.ZERO) 0.0 else FORWARD_SHARE
            val lift = if (age == 0) maxOf(velocity.y, .18) else velocity.y
            val horizontalThrust = force * forward * HORIZONTAL_BOOST
            applyVelocity(player, flight, velocity.x + flight.direction.x * horizontalThrust,
                lift + force * (1.0 - forward), velocity.z + flight.direction.z * horizontalThrust)
            if (age % 3 == 0) JumpPackThrustPacket.send(player, false)
            return
        }

        val descent = -velocity.y
        val probe = (descent * 2.4).coerceIn(.75, MAX_LANDING_PROBE)
        if ((descent < BRAKE_TRIGGER_SPEED && flight.brakeStarted == -1) ||
            descent <= 0.0 || !landingSurface(player, probe)) return
        if (flight.brakeStarted == -1) {
            flight.brakeStarted = age
            sound(player, "sfx_jump_pack_brake")
        }
        val brakingAge = age - flight.brakeStarted
        if (brakingAge >= BRAKE_TICKS) return
        if (brakingAge % 3 == 0) JumpPackThrustPacket.send(player, true)
        val horizontalSpeed = velocity.horizontalDistance()
        val horizontalScale = if (horizontalSpeed > 0.0) {
            (1.0 - BRAKE_ACCELERATION * BRAKE_BACKWARD_SHARE / horizontalSpeed).coerceIn(0.0, 1.0)
        } else 0.0
        val descentAfterBraking = maxOf(descent - BRAKE_ACCELERATION * (1.0 - BRAKE_BACKWARD_SHARE), BRAKE_MIN_SPEED)
        applyVelocity(player, flight, velocity.x * horizontalScale, -descentAfterBraking, velocity.z * horizontalScale)
        player.resetFallDistance()
    }

    private fun clearAbove(player: ServerPlayer) =
        player.level().noCollision(player, player.boundingBox.move(0.0, .18, 0.0))

    private fun sound(player: ServerPlayer, event: String) {
        player.level().playSound(null, player.x, player.y, player.z,
            ModSounds.EVENTS.getValue(event).get(), SoundSource.PLAYERS, .63f, 1f)
    }

    private fun applyVelocity(player: ServerPlayer, flight: Flight, x: Double, y: Double, z: Double) {
        val velocity = Vec3(x.coerceIn(-1.8, 1.8), y.coerceIn(-2.0, 1.6), z.coerceIn(-1.8, 1.8))
        flight.velocity = velocity
        player.deltaMovement = velocity
        player.connection.send(ClientboundSetEntityMotionPacket(player))
    }

    private fun landingSurface(player: ServerPlayer, depth: Double): Boolean {
        val box = player.boundingBox
        val feet = AABB(box.minX + .04, box.minY - depth, box.minZ + .04,
            box.maxX - .04, box.minY + .02, box.maxZ - .04)
        return player.level().getBlockCollisions(player, feet).iterator().hasNext()
    }

    @SubscribeEvent
    fun onFall(event: LivingFallEvent) {
        val player = event.entity as? ServerPlayer ?: return
        val flight = flights[player] ?: return
        if (player.level().dimension() != flight.dimension || StratagemBackpacks.equipped(player) !== flight.stack ||
            !canFly(player) || !landingSurface(player, MAX_LANDING_PROBE)) return
        if (flight.brakeStarted == -1) {
            sound(player, "sfx_jump_pack_brake")
            JumpPackThrustPacket.send(player, true)
        }
        event.isCanceled = true
        finish(player)
    }

    @SubscribeEvent
    fun logout(event: PlayerEvent.PlayerLoggedOutEvent) {
        val player = event.entity as? ServerPlayer ?: return
        finish(player)
    }

    private fun finish(player: ServerPlayer) {
        val flight = flights.remove(player) ?: return
        setReadyAt(flight.stack, time(player) + RECHARGE_TICKS)
        player.cooldowns.addCooldown(flight.stack.item, RECHARGE_TICKS)
    }

    private fun time(player: ServerPlayer) = player.serverLevel().server.overworld().gameTime

    internal fun readyAt(stack: ItemStack): Long = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getLong(READY_AT) ?: 0L
    private fun setReadyAt(stack: ItemStack, readyAt: Long) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack) { it.putLong(READY_AT, readyAt) }
    }
}
