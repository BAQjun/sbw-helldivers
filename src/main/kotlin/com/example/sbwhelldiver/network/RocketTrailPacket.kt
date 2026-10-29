package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.platform.ClientDispatch
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.network.handling.IPayloadContext

/** One bounded flight segment, including the last segment up to the actual collision. */
data class RocketTrailPacket(val from: Vec3, val to: Vec3) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeDouble(from.x); buffer.writeDouble(from.y); buffer.writeDouble(from.z)
        buffer.writeDouble(to.x); buffer.writeDouble(to.y); buffer.writeDouble(to.z)
    }
    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf) = RocketTrailPacket(
            Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()),
            Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()))
        fun handle(packet: RocketTrailPacket, context: IPayloadContext) {
            context.enqueueWork {
                ClientDispatch.unsafeRunWhenOn(Dist.CLIENT) {
                    Runnable { com.example.sbwhelldiver.client.RocketTrailVisual.emit(packet.from, packet.to) }
                }
            }
        }
    }
}
