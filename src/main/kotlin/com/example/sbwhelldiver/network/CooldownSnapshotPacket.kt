package com.example.sbwhelldiver.network

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Supplier

/** Owner-only, server-to-client snapshot. No client packet can write the ledger. */
data class CooldownSnapshotPacket(val serverTime: Long, val state: CompoundTag) {
    fun encode(buffer: RegistryFriendlyByteBuf) { buffer.writeLong(serverTime); buffer.writeNbt(state) }
    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf) = CooldownSnapshotPacket(buffer.readLong(), buffer.readNbt() ?: CompoundTag())
        fun handle(packet: CooldownSnapshotPacket, supplier: IPayloadContext) {
            val context = supplier
            context.enqueueWork {
                com.example.sbwhelldiver.platform.ClientDispatch.unsafeRunWhenOn(net.neoforged.api.distmarker.Dist.CLIENT) {
                    Runnable { com.example.sbwhelldiver.client.StratagemCooldownClient.receive(packet) }
                }
            }

        }
    }
}
