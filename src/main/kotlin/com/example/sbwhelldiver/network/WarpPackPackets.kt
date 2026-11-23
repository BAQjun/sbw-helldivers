package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.client.WarpPackBurstVisual
import com.example.sbwhelldiver.platform.ClientDispatch
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.network.handling.IPayloadContext

internal data class WarpPackBurstPacket(val origin: Vec3, val destination: Vec3) {
    fun encode(buf: FriendlyByteBuf) {
        for (point in listOf(origin, destination)) {
            buf.writeDouble(point.x)
            buf.writeDouble(point.y)
            buf.writeDouble(point.z)
        }
    }

    fun send(level: ServerLevel) {
        val radiusSquared = 56.0 * 56.0
        for (viewer in level.players()) {
            if (viewer.distanceToSqr(origin) <= radiusSquared || viewer.distanceToSqr(destination) <= radiusSquared) {
                BeaconNetwork.CHANNEL.send(PacketTargets.PLAYER.with { viewer }, this)
            }
        }
    }

    companion object {
        fun decode(buf: FriendlyByteBuf) = WarpPackBurstPacket(readPoint(buf), readPoint(buf))

        private fun readPoint(buf: FriendlyByteBuf) = Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())

        fun handle(message: WarpPackBurstPacket, context: IPayloadContext) {
            context.enqueueWork {
                ClientDispatch.unsafeRunWhenOn(Dist.CLIENT) { Runnable { WarpPackBurstVisual.receive(message) } }
            }
        }
    }
}
