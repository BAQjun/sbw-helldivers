package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.client.HoverPackVisual
import com.example.sbwhelldiver.item.HoverPackFlight
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.PacketDistributor
import java.util.function.Supplier

internal data class HoverPackInputPacket(val forward: Int, val left: Int) {
    fun encode(buf: FriendlyByteBuf) {
        buf.writeByte(forward)
        buf.writeByte(left)
    }

    companion object {
        fun decode(buf: FriendlyByteBuf) = HoverPackInputPacket(buf.readByte().toInt(), buf.readByte().toInt())

        fun handle(message: HoverPackInputPacket, supplier: Supplier<NetworkEvent.Context>) {
            val context = supplier.get()
            context.enqueueWork {
                context.sender?.let { HoverPackFlight.input(it, message.forward, message.left) }
            }
            context.packetHandled = true
        }
    }
}

internal data class HoverPackStatePacket(val entityId: Int, val phase: HoverPackFlight.Phase, val age: Int) {
    fun encode(buf: FriendlyByteBuf) {
        buf.writeVarInt(entityId)
        buf.writeEnum(phase)
        buf.writeVarInt(age)
    }

    companion object {
        fun decode(buf: FriendlyByteBuf) =
            HoverPackStatePacket(buf.readVarInt(), buf.readEnum(HoverPackFlight.Phase::class.java), buf.readVarInt())

        fun handle(message: HoverPackStatePacket, supplier: Supplier<NetworkEvent.Context>) {
            val context = supplier.get()
            context.enqueueWork {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT) { Runnable { HoverPackVisual.receive(message) } }
            }
            context.packetHandled = true
        }

        fun send(player: ServerPlayer, phase: HoverPackFlight.Phase, age: Int) {
            BeaconNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with { player }, HoverPackStatePacket(player.id, phase, age))
        }
    }
}
