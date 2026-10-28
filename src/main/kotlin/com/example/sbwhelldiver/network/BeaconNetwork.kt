package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.stratagem.BeaconInput
import com.example.sbwhelldiver.item.BeaconItem
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import com.example.sbwhelldiver.network.PacketDirection
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.Optional
import java.util.UUID
import java.util.function.Supplier

data class BeaconKeyPacket(val hand: InteractionHand, val identity: UUID, val direction: Int) {
    fun encode(buffer: RegistryFriendlyByteBuf) { buffer.writeEnum(hand); buffer.writeUUID(identity); buffer.writeByte(direction) }
    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf) = BeaconKeyPacket(buffer.readEnum(InteractionHand::class.java), buffer.readUUID(), buffer.readByte().toInt())
        fun handle(message: BeaconKeyPacket, supplier: IPayloadContext) {
            val context = supplier
            context.enqueueWork { context.sender?.let { BeaconInput.accept(it, message.hand, message.identity, message.direction) } }

        }
    }
}

data class BeaconThrowPacket(val hand: InteractionHand, val identity: UUID) {
    fun encode(buffer: RegistryFriendlyByteBuf) { buffer.writeEnum(hand); buffer.writeUUID(identity) }
    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf) = BeaconThrowPacket(buffer.readEnum(InteractionHand::class.java), buffer.readUUID())
        fun handle(message: BeaconThrowPacket, supplier: IPayloadContext) {
            val context = supplier
            context.enqueueWork { context.sender?.let { player ->
                (player.getItemInHand(message.hand).item as? BeaconItem)?.throwActivated(player, message.hand, message.identity)
            } }

        }
    }
}

/** Owner-only result; exactly four slots retain their original order. */
data class BeaconFailurePacket(val stratagemId: String, val loadoutIds: List<String>) {
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeUtf(stratagemId, 64)
        repeat(4) { buffer.writeUtf(loadoutIds.getOrNull(it) ?: "", 64) }
    }
    companion object {
        fun decode(buffer: RegistryFriendlyByteBuf) = BeaconFailurePacket(buffer.readUtf(64), List(4) { buffer.readUtf(64) })
        fun handle(message: BeaconFailurePacket, supplier: IPayloadContext) {
            val context = supplier
            context.enqueueWork {
                com.example.sbwhelldiver.platform.ClientDispatch.unsafeRunWhenOn(net.neoforged.api.distmarker.Dist.CLIENT) {
                    Runnable { com.example.sbwhelldiver.client.StratagemClient.deploymentFailed(message.stratagemId, message.loadoutIds) }
                }
            }

        }
    }
}

object BeaconNetwork {
    val CHANNEL = PacketChannel()
    fun register() {
        thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS.addListener<net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent> { CHANNEL.registerPayloads(it) }
        CHANNEL.registerMessage(21,ExplosionBlockDamageRequestPacket::class.java,{p,b->p.encode(b)},ExplosionBlockDamageRequestPacket::decode,ExplosionBlockDamageRequestPacket::handle,Optional.of(PacketDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(22,ExplosionBlockDamageStatePacket::class.java,{p,b->p.encode(b)},ExplosionBlockDamageStatePacket::decode,ExplosionBlockDamageStatePacket::handle,Optional.of(PacketDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(19,InfiniteAmmoRequestPacket::class.java,{p,b->p.encode(b)},InfiniteAmmoRequestPacket::decode,InfiniteAmmoRequestPacket::handle,Optional.of(PacketDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(20,InfiniteAmmoStatePacket::class.java,{p,b->p.encode(b)},InfiniteAmmoStatePacket::decode,InfiniteAmmoStatePacket::handle,Optional.of(PacketDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(18,DestroyerFirePacket::class.java,{p,b->p.encode(b)},DestroyerFirePacket::decode,DestroyerFirePacket::handle,Optional.of(PacketDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(17,DestroyerWarpPacket::class.java,{p,b->p.encode(b)},DestroyerWarpPacket::decode,DestroyerWarpPacket::handle,Optional.of(PacketDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(15,AutoRetractRequestPacket::class.java,{p,b->p.encode(b)},AutoRetractRequestPacket::decode,AutoRetractRequestPacket::handle,Optional.of(PacketDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(16,AutoRetractStatePacket::class.java,{p,b->p.encode(b)},AutoRetractStatePacket::decode,AutoRetractStatePacket::handle,Optional.of(PacketDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(13,DestroyerSnapshotPacket::class.java,{p,b->p.encode(b)},DestroyerSnapshotPacket::decode,DestroyerSnapshotPacket::handle,Optional.of(PacketDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(14,DestroyerMenuPacket::class.java,{p,b->p.encode(b)},DestroyerMenuPacket::decode,DestroyerMenuPacket::handle,Optional.of(PacketDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(12,CooldownSnapshotPacket::class.java,{p,b->p.encode(b)},CooldownSnapshotPacket::decode,CooldownSnapshotPacket::handle,Optional.of(PacketDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(11,DropSteerPacket::class.java,{p,b->p.encode(b)},DropSteerPacket::decode,DropSteerPacket::handle,Optional.of(PacketDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(8,DropPreparePacket::class.java,{p,b->p.encode(b)},DropPreparePacket::decode,DropPreparePacket::handle,Optional.of(PacketDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(9,DropReadyPacket::class.java,{p,b->p.encode(b)},DropReadyPacket::decode,DropReadyPacket::handle,Optional.of(PacketDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(10,DropCancelPacket::class.java,{p,b->p.encode(b)},DropCancelPacket::decode,DropCancelPacket::handle,Optional.of(PacketDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(7,MarkEnabledPacket::class.java,{p,b->p.encode(b)},MarkEnabledPacket::decode,MarkEnabledPacket::handle,Optional.of(PacketDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(6,MarkFeedbackPacket::class.java,{p,b->p.encode(b)},MarkFeedbackPacket::decode,MarkFeedbackPacket::handle,Optional.of(PacketDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(4,MarkRequestPacket::class.java,{p,b->p.encode(b)},MarkRequestPacket::decode,MarkRequestPacket::handle,Optional.of(PacketDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(5,MarkSnapshotPacket::class.java,{p,b->p.encode(b)},MarkSnapshotPacket::decode,MarkSnapshotPacket::handle,Optional.of(PacketDirection.PLAY_TO_CLIENT))
        CHANNEL.registerMessage(3,HmgInputPacket::class.java,{ packet,buf -> packet.encode(buf) },HmgInputPacket::decode,
            HmgInputPacket::handle,Optional.of(PacketDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(0, BeaconKeyPacket::class.java, { packet, buf -> packet.encode(buf) }, BeaconKeyPacket::decode,
            BeaconKeyPacket::handle, Optional.of(PacketDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(1, BeaconThrowPacket::class.java, { packet, buf -> packet.encode(buf) }, BeaconThrowPacket::decode,
            BeaconThrowPacket::handle, Optional.of(PacketDirection.PLAY_TO_SERVER))
        CHANNEL.registerMessage(2, BeaconFailurePacket::class.java, { packet, buf -> packet.encode(buf) }, BeaconFailurePacket::decode,
            BeaconFailurePacket::handle, Optional.of(PacketDirection.PLAY_TO_CLIENT))
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener<net.neoforged.neoforge.event.tick.PlayerTickEvent.Pre> {
            if (it.entity is net.minecraft.server.level.ServerPlayer)
                BeaconInput.observeHands(it.entity as net.minecraft.server.level.ServerPlayer)
        }
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener<net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent> {
            BeaconInput.logout(it.entity.uuid)
        }
    }
}
