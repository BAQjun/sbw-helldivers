package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.stratagem.BeaconInput
import com.example.sbwhelldiver.item.BeaconItem
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraftforge.network.NetworkDirection
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.NetworkRegistry
import java.util.Optional
import java.util.UUID
import java.util.function.Supplier

data class BeaconKeyPacket(val hand: InteractionHand, val identity: UUID, val direction: Int) {
    fun encode(buffer: FriendlyByteBuf) { buffer.writeEnum(hand); buffer.writeUUID(identity); buffer.writeByte(direction) }
    companion object {
        fun decode(buffer: FriendlyByteBuf) = BeaconKeyPacket(buffer.readEnum(InteractionHand::class.java), buffer.readUUID(), buffer.readByte().toInt())
        fun handle(message: BeaconKeyPacket, supplier: Supplier<NetworkEvent.Context>) {
            val context = supplier.get()
            context.enqueueWork { context.sender?.let { BeaconInput.accept(it, message.hand, message.identity, message.direction) } }
            context.packetHandled = true
        }
    }
}

data class BeaconThrowPacket(val hand: InteractionHand, val identity: UUID) {
    fun encode(buffer: FriendlyByteBuf) { buffer.writeEnum(hand); buffer.writeUUID(identity) }
    companion object {
        fun decode(buffer: FriendlyByteBuf) = BeaconThrowPacket(buffer.readEnum(InteractionHand::class.java), buffer.readUUID())
        fun handle(message: BeaconThrowPacket, supplier: Supplier<NetworkEvent.Context>) {
            val context = supplier.get()
            context.enqueueWork { context.sender?.let { player ->
                (player.getItemInHand(message.hand).item as? BeaconItem)?.throwActivated(player, message.hand, message.identity)
            } }
            context.packetHandled = true
        }
    }
}

/** Owner-only result; exactly four slots retain their original order. */
data class BeaconFailurePacket(val stratagemId: String, val loadoutIds: List<String>) {
    fun encode(buffer: FriendlyByteBuf) {
        buffer.writeUtf(stratagemId, 64)
        repeat(4) { buffer.writeUtf(loadoutIds.getOrNull(it) ?: "", 64) }
    }
    companion object {
        fun decode(buffer: FriendlyByteBuf) = BeaconFailurePacket(buffer.readUtf(64), List(4) { buffer.readUtf(64) })
        fun handle(message: BeaconFailurePacket, supplier: Supplier<NetworkEvent.Context>) {
            val context = supplier.get()
            context.enqueueWork {
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT) {
                    Runnable { com.example.sbwhelldiver.client.StratagemClient.deploymentFailed(message.stratagemId, message.loadoutIds) }
                }
            }
            context.packetHandled = true
        }
    }
}

object BeaconNetwork {
    val CHANNEL = NetworkRegistry.newSimpleChannel(ResourceLocation(MOD_ID, "beacon"), { "9" }, { it == "9" }, { it == "9" })
    fun register() {
        CHANNEL.registerMessage(13,DestroyerSnapshotPacket::class.java,{p,b->p.encode(b)},DestroyerSnapshotPacket::decode,DestroyerSnapshotPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(14,DestroyerMenuPacket::class.java,{p,b->p.encode(b)},DestroyerMenuPacket::decode,DestroyerMenuPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(12,CooldownSnapshotPacket::class.java,{p,b->p.encode(b)},CooldownSnapshotPacket::decode,CooldownSnapshotPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(11,DropSteerPacket::class.java,{p,b->p.encode(b)},DropSteerPacket::decode,DropSteerPacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(8,DropPreparePacket::class.java,{p,b->p.encode(b)},DropPreparePacket::decode,DropPreparePacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(9,DropReadyPacket::class.java,{p,b->p.encode(b)},DropReadyPacket::decode,DropReadyPacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(10,DropCancelPacket::class.java,{p,b->p.encode(b)},DropCancelPacket::decode,DropCancelPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(7,MarkEnabledPacket::class.java,{p,b->p.encode(b)},MarkEnabledPacket::decode,MarkEnabledPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(6,MarkFeedbackPacket::class.java,{p,b->p.encode(b)},MarkFeedbackPacket::decode,MarkFeedbackPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(4,MarkRequestPacket::class.java,{p,b->p.encode(b)},MarkRequestPacket::decode,MarkRequestPacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(5,MarkSnapshotPacket::class.java,{p,b->p.encode(b)},MarkSnapshotPacket::decode,MarkSnapshotPacket::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(3,HmgInputPacket::class.java,{ packet,buf -> packet.encode(buf) },HmgInputPacket::decode,
            HmgInputPacket::handle,Optional.of(NetworkDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(0, BeaconKeyPacket::class.java, { packet, buf -> packet.encode(buf) }, BeaconKeyPacket::decode,
            BeaconKeyPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(1, BeaconThrowPacket::class.java, { packet, buf -> packet.encode(buf) }, BeaconThrowPacket::decode,
            BeaconThrowPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(2, BeaconFailurePacket::class.java, { packet, buf -> packet.encode(buf) }, BeaconFailurePacket::decode,
            BeaconFailurePacket::handle, Optional.of(NetworkDirection.PLAY_TO_CLIENT))
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener<net.minecraftforge.event.TickEvent.PlayerTickEvent> {
            if (it.phase == net.minecraftforge.event.TickEvent.Phase.START && it.player is net.minecraft.server.level.ServerPlayer)
                BeaconInput.observeHands(it.player as net.minecraft.server.level.ServerPlayer)
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener<net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent> {
            BeaconInput.logout(it.entity.uuid)
        }
    }
}
