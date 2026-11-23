package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.client.WarpPackBurstVisual
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.PacketDistributor
import java.util.function.Supplier

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
                BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { viewer }, this)
            }
        }
    }

    companion object {
        fun decode(buf: FriendlyByteBuf) = WarpPackBurstPacket(readPoint(buf), readPoint(buf))

        private fun readPoint(buf: FriendlyByteBuf) = Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble())

        fun handle(message: WarpPackBurstPacket, supplier: Supplier<NetworkEvent.Context>) {
            val context = supplier.get()
            context.enqueueWork {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT) { Runnable { WarpPackBurstVisual.receive(message) } }
            }
            context.packetHandled = true
        }
    }
}
