package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.client.HoverPackVisual
import com.example.sbwhelldiver.item.HoverPackFlight
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.neoforged.api.distmarker.Dist
import com.example.sbwhelldiver.platform.ClientDispatch
import net.neoforged.neoforge.network.handling.IPayloadContext

internal data class HoverPackInputPacket(val held: Boolean, val forward: Int, val left: Int) {
    fun encode(buf: FriendlyByteBuf) {
        buf.writeBoolean(held)
        buf.writeByte(forward)
        buf.writeByte(left)
    }

    companion object {
        fun decode(buf: FriendlyByteBuf) = HoverPackInputPacket(buf.readBoolean(), buf.readByte().toInt(), buf.readByte().toInt())

        fun handle(message: HoverPackInputPacket, context: IPayloadContext) {
            context.enqueueWork {
                context.sender?.let { HoverPackFlight.input(it, message.held, message.forward, message.left) }
            }
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

        fun handle(message: HoverPackStatePacket, context: IPayloadContext) {
            context.enqueueWork {
                ClientDispatch.unsafeRunWhenOn(Dist.CLIENT) { Runnable { HoverPackVisual.receive(message) } }
            }
        }

        fun send(player: ServerPlayer, phase: HoverPackFlight.Phase, age: Int) {
            BeaconNetwork.CHANNEL.send(PacketTargets.TRACKING_ENTITY_AND_SELF.with { player }, HoverPackStatePacket(player.id, phase, age))
        }
    }
}
