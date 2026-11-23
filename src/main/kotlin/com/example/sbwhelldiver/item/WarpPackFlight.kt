package com.example.sbwhelldiver.item

import com.example.sbwhelldiver.network.WarpPackBurstPacket
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import com.example.sbwhelldiver.init.ModSounds
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

internal object WarpPackFlight {
    const val FULL = 1000
    private const val COST = 330
    private const val RECHARGE = 3
    private const val ENERGY = "WarpPackEnergy"
    private const val USED_AT = "WarpPackUsedAt"
    private const val MAX_DISTANCE = 10.0
    private const val LANDING_STEP = .5

    enum class Phase(val color: Int) {
        SAFE(0xC080FF), RISK(0xFFD34F), LETHAL(0xFF4649)
    }

    fun phase(energy: Int): Phase = when {
        energy > 850 -> Phase.SAFE
        energy >= 560 -> Phase.RISK
        else -> Phase.LETHAL
    }

    fun energy(stack: ItemStack, gameTime: Long?): Int {
        val data = stack.tag ?: return FULL
        if (!data.contains(ENERGY, Tag.TAG_INT.toInt())) return FULL
        val usedAt = data.getLong(USED_AT)
        return recharge(data.getInt(ENERGY), (gameTime ?: usedAt) - usedAt)
    }

    internal fun recharge(energy: Int, ticks: Long): Int =
        (energy.coerceIn(0, FULL) + ticks.coerceIn(0L, 334L).toInt() * RECHARGE).coerceAtMost(FULL)

    fun start(player: ServerPlayer, pack: ItemStack) {
        if (!JumpPackFlight.canFly(player)) return
        val level = player.serverLevel()
        val origin = player.position()
        val destination = findLanding(player, origin) ?: return
        val gameTime = level.server.overworld().gameTime
        val energy = energy(pack, gameTime)
        val phase = phase(energy)
        val remaining = (energy - COST).coerceAtLeast(0)
        val fallDistance = player.fallDistance

        player.connection.teleport(destination.x, destination.y, destination.z, player.yRot, player.xRot)
        player.connection.resetPosition()
        player.fallDistance = fallDistance
        pack.orCreateTag.putInt(ENERGY, remaining)
        pack.orCreateTag.putLong(USED_AT, gameTime)

        val burst = WarpPackBurstPacket(origin.add(0.0, .95, 0.0), destination.add(0.0, .95, 0.0))
        burst.send(level)
        level.playSound(null, burst.origin.x, burst.origin.y, burst.origin.z,
            ModSounds.EVENTS.getValue("sfx_warp_pack_depart").get(), SoundSource.PLAYERS, .47f, 1f)
        level.playSound(null, burst.destination.x, burst.destination.y, burst.destination.z,
            ModSounds.EVENTS.getValue("sfx_warp_pack_arrive").get(), SoundSource.PLAYERS, .44f, 1f)

        when (phase) {
            Phase.SAFE -> Unit
            Phase.RISK -> {
                if (player.health <= 8f) {
                    killWithWarpMessage(player)
                } else {
                    player.health -= 8f
                    if (player.random.nextFloat() < .2f) player.remainingFireTicks = maxOf(player.remainingFireTicks, 60)
                }
            }
            Phase.LETHAL -> {
                pack.shrink(1)
                killWithWarpMessage(player)
            }
        }
    }

    private fun killWithWarpMessage(player: ServerPlayer) {
        val source = object : DamageSource(player.damageSources().genericKill().typeHolder()) {
            override fun getLocalizedDeathMessage(victim: LivingEntity): Component =
                Component.translatable(
                    "death.attack.sbw_helldiver.warp_pack",
                    victim.displayName,
                    Component.literal("Eli Anderson").withStyle(ChatFormatting.BOLD),
                )
        }
        player.hurt(source, Float.MAX_VALUE)
    }

    private fun findLanding(player: ServerPlayer, origin: Vec3): Vec3? {
        val level = player.serverLevel()
        val yaw = Math.toRadians(player.yRot.toDouble())
        val direction = Vec3(-sin(yaw), 0.0, cos(yaw))
        val box = player.boundingBox
        val steps = (MAX_DISTANCE / LANDING_STEP).toInt()
        for (step in steps downTo 1) {
            val offset = direction.scale(step * LANDING_STEP)
            val target = origin.add(offset)
            val moved = box.move(offset)
            val min = BlockPos.containing(moved.minX, moved.minY, moved.minZ)
            val max = BlockPos.containing(moved.maxX, moved.maxY, moved.maxZ)
            if (target.y < level.minBuildHeight || !level.worldBorder.isWithinBounds(moved) ||
                !level.hasChunkAt(min) || !level.hasChunkAt(max) ||
                !level.hasChunkAt(BlockPos(min.x, min.y, max.z)) ||
                !level.hasChunkAt(BlockPos(max.x, max.y, min.z))) continue
            if (level.noCollision(player, moved)) return target
        }
        return null
    }
}
