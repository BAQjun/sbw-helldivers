package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.MOD_ID
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.Optional

internal val IPayloadContext.sender: ServerPlayer? get() = player() as? ServerPlayer

enum class PacketDirection { PLAY_TO_CLIENT, PLAY_TO_SERVER }

/** Each message has its own NeoForge payload type and a fixed, validated direction. */
class PacketChannel {
    private data class Message<T: Any>(val kind: CustomPacketPayload.Type<Message<T>>, val value: T): CustomPacketPayload {
        override fun type() = kind
    }
    private class Registration<T: Any>(
        val clazz: Class<T>, val direction: PacketDirection,
        val encode: (T, RegistryFriendlyByteBuf) -> Unit,
        val decode: (RegistryFriendlyByteBuf) -> T,
        val handle: (T, IPayloadContext) -> Unit
    ) {
        val kind = CustomPacketPayload.Type<Message<T>>(ResourceLocation.fromNamespaceAndPath(MOD_ID,
            clazz.simpleName.replace(Regex("([a-z])([A-Z])"), "$1_$2").lowercase()))
        val codec: StreamCodec<RegistryFriendlyByteBuf, Message<T>> = object: StreamCodec<RegistryFriendlyByteBuf, Message<T>> {
            override fun encode(buf: RegistryFriendlyByteBuf, value: Message<T>) = this@Registration.encode(value.value, buf)
            override fun decode(buf: RegistryFriendlyByteBuf) = Message(kind, this@Registration.decode(buf))
        }
        fun register(event: RegisterPayloadHandlersEvent) {
            val registrar = event.registrar("1")
            if (direction == PacketDirection.PLAY_TO_SERVER) registrar.playToServer(kind, codec) { payload, context -> handle(payload.value, context) }
            else registrar.playToClient(kind, codec) { payload, context -> handle(payload.value, context) }
        }
        fun wrap(value: Any): CustomPacketPayload = Message(kind, clazz.cast(value))
    }
    private val registrations = linkedMapOf<Class<*>, Registration<*>>()
    fun <T: Any> registerMessage(id: Int, clazz: Class<T>, encoder: (T, RegistryFriendlyByteBuf) -> Unit,
        decoder: (RegistryFriendlyByteBuf) -> T, handler: (T, IPayloadContext) -> Unit, direction: Optional<PacketDirection>) {
        check(registrations.put(clazz, Registration(clazz, direction.orElseThrow(), encoder, decoder, handler)) == null)
    }
    fun registerPayloads(event: RegisterPayloadHandlersEvent) = registrations.values.forEach { it.register(event) }
    private fun wrap(value: Any) = checkNotNull(registrations[value.javaClass]) { "Unregistered message ${value.javaClass}" }.wrap(value)
    fun sendToServer(value: Any) = PacketDistributor.sendToServer(wrap(value))
    fun send(target: PacketTargets.Target, value: Any) = target.send(wrap(value))
}

/** Targets are resolved at send time, keeping dimension broadcasts on the server. */
object PacketTargets {
    fun interface Target { fun send(payload: CustomPacketPayload) }
    object PLAYER { fun with(player: () -> ServerPlayer) = Target { PacketDistributor.sendToPlayer(player(), it) } }
    object DIMENSION {
        fun with(dimension: () -> net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>) = Target { payload ->
            val server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer()
            server?.getLevel(dimension())?.let { level -> PacketDistributor.sendToPlayersInDimension(level, payload) }
        }
    }
    object ALL { fun noArg() = Target { PacketDistributor.sendToAllPlayers(it) } }
}
