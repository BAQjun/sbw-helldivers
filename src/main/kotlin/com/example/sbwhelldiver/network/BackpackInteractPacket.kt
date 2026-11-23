package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.item.StratagemBackpacks
import net.minecraft.network.FriendlyByteBuf
import net.neoforged.neoforge.network.handling.IPayloadContext

internal data class BackpackInteractPacket(val forward: Int, val left: Int) {
    fun encode(buf: FriendlyByteBuf) {
        buf.writeByte(forward)
        buf.writeByte(left)
    }

    companion object {
        fun decode(buf: FriendlyByteBuf) = BackpackInteractPacket(buf.readByte().toInt(), buf.readByte().toInt())

        fun handle(message: BackpackInteractPacket, context: IPayloadContext) {
            context.enqueueWork {
                context.sender?.let { StratagemBackpacks.interact(it, message.forward, message.left) }
            }
        }
    }
}
