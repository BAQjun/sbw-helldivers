package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.client.JumpPackVisual
import com.example.sbwhelldiver.item.JumpPackFlight
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.PacketDistributor
import java.util.function.Supplier

internal data class JumpPackInputPacket(val forward: Int, val left: Int) {
    fun encode(buf: FriendlyByteBuf) {
        buf.writeByte(forward)
        buf.writeByte(left)
    }

    companion object {
        fun decode(buf: FriendlyByteBuf) = JumpPackInputPacket(buf.readByte().toInt(), buf.readByte().toInt())

        fun handle(message: JumpPackInputPacket, supplier: Supplier<NetworkEvent.Context>) {
            val context = supplier.get()
            context.enqueueWork {
                context.sender?.let { JumpPackFlight.start(it, message.forward, message.left) }
            }
            context.packetHandled = true
        }
    }
}

internal data class JumpPackThrustPacket(val entityId: Int, val braking: Boolean) {
    fun encode(buf: FriendlyByteBuf) {
        buf.writeVarInt(entityId)
        buf.writeBoolean(braking)
    }

    companion object {
        fun decode(buf: FriendlyByteBuf) = JumpPackThrustPacket(buf.readVarInt(), buf.readBoolean())

        fun handle(message: JumpPackThrustPacket, supplier: Supplier<NetworkEvent.Context>) {
            val context = supplier.get()
            context.enqueueWork {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT) {
                    Runnable { JumpPackVisual.receive(message) }
                }
            }
            context.packetHandled = true
        }

        fun send(player: ServerPlayer, braking: Boolean) {
            BeaconNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with { player }, JumpPackThrustPacket(player.id, braking))
        }
    }
}
