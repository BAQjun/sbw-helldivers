package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.network.*
import net.minecraft.server.level.ServerLevel
import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile
import net.minecraft.world.phys.Vec3

/** Aircraft ordnance can cross several untracked chunks before the client sees its entity. */
object RocketTrails {
    private const val RANGE = 1024.0
    fun send(rocket: FastThrowableProjectile, from: Vec3, to: Vec3) {
        val level = rocket.level() as? ServerLevel ?: return
        if (rocket.exploded) return
        val travel = to.subtract(from)
        val lengthSqr = travel.lengthSqr()
        if (!lengthSqr.isFinite() || lengthSqr < 1e-8 || lengthSqr > 128.0 * 128.0) return
        val packet = RocketTrailPacket(from, to)
        for (player in level.players()) {
            val fraction = player.position().subtract(from).dot(travel).div(lengthSqr).coerceIn(0.0, 1.0)
            if (player.position().distanceToSqr(from.add(travel.scale(fraction))) <= RANGE * RANGE)
                BeaconNetwork.CHANNEL.send(PacketTargets.PLAYER.with { player }, packet)
        }
    }
}
