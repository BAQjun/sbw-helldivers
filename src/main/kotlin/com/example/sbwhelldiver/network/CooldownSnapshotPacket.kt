package com.example.sbwhelldiver.network

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

/** Owner-only, server-to-client snapshot. No client packet can write the ledger. */
data class CooldownSnapshotPacket(val serverTime: Long, val state: CompoundTag) {
    fun encode(buffer: FriendlyByteBuf) { buffer.writeLong(serverTime); buffer.writeNbt(state) }
    companion object {
        fun decode(buffer: FriendlyByteBuf) = CooldownSnapshotPacket(buffer.readLong(), buffer.readNbt() ?: CompoundTag())
        fun handle(packet: CooldownSnapshotPacket, supplier: Supplier<NetworkEvent.Context>) {
            val context = supplier.get()
            context.enqueueWork {
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT) {
                    Runnable { com.example.sbwhelldiver.client.StratagemCooldownClient.receive(packet) }
                }
            }
            context.packetHandled = true
        }
    }
}
