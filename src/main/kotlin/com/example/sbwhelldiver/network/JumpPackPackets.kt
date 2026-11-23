package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.client.JumpPackVisual
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import com.example.sbwhelldiver.platform.ClientDispatch
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.network.handling.IPayloadContext

internal data class JumpPackThrustPacket(val entityId: Int, val braking: Boolean) {
    fun encode(buf: FriendlyByteBuf) {
        buf.writeVarInt(entityId)
        buf.writeBoolean(braking)
    }

    companion object {
        fun decode(buf: FriendlyByteBuf) = JumpPackThrustPacket(buf.readVarInt(), buf.readBoolean())

        fun handle(message: JumpPackThrustPacket, context: IPayloadContext) {
            context.enqueueWork {
                ClientDispatch.unsafeRunWhenOn(Dist.CLIENT) {
                    Runnable { JumpPackVisual.receive(message) }
                }
            }
        }

        fun send(player: ServerPlayer, braking: Boolean) {
            BeaconNetwork.CHANNEL.send(PacketTargets.TRACKING_ENTITY_AND_SELF.with { player }, JumpPackThrustPacket(player.id, braking))
        }
    }
}
