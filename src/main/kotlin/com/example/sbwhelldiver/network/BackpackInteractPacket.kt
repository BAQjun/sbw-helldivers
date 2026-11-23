package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.item.StratagemBackpacks
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

internal data class BackpackInteractPacket(val forward: Int, val left: Int) {
    fun encode(buf: FriendlyByteBuf) {
        buf.writeByte(forward)
        buf.writeByte(left)
    }

    companion object {
        fun decode(buf: FriendlyByteBuf) = BackpackInteractPacket(buf.readByte().toInt(), buf.readByte().toInt())

        fun handle(message: BackpackInteractPacket, supplier: Supplier<NetworkEvent.Context>) {
            val context = supplier.get()
            context.enqueueWork {
                context.sender?.let { StratagemBackpacks.interact(it, message.forward, message.left) }
            }
            context.packetHandled = true
        }
    }
}
