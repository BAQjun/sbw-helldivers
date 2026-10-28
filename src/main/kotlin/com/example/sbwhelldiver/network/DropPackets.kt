package com.example.sbwhelldiver.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID
import java.util.function.Supplier

data class DropPreparePacket(val token:UUID,val dimension:ResourceLocation,val point:Vec3){
 fun encode(b:RegistryFriendlyByteBuf){b.writeUUID(token);b.writeResourceLocation(dimension);b.writeDouble(point.x);b.writeDouble(point.y);b.writeDouble(point.z)}
 companion object {
  fun decode(b:RegistryFriendlyByteBuf)=DropPreparePacket(b.readUUID(),b.readResourceLocation(),Vec3(b.readDouble(),b.readDouble(),b.readDouble()))
  fun handle(p:DropPreparePacket,s:IPayloadContext){val c=s;c.enqueueWork{com.example.sbwhelldiver.platform.ClientDispatch.unsafeRunWhenOn(net.neoforged.api.distmarker.Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.PlayerDropClient.prepare(p)}}};}
 }
}
data class DropReadyPacket(val token:UUID){
 fun encode(b:RegistryFriendlyByteBuf){b.writeUUID(token)}
 companion object {
  fun decode(b:RegistryFriendlyByteBuf)=DropReadyPacket(b.readUUID())
  fun handle(p:DropReadyPacket,s:IPayloadContext){val c=s;c.enqueueWork{c.sender?.let{com.example.sbwhelldiver.entity.PlayerDrops.ready(it,p.token)}};}
 }
}
data class DropCancelPacket(val token:UUID){
 fun encode(b:RegistryFriendlyByteBuf){b.writeUUID(token)}
 companion object {
  fun decode(b:RegistryFriendlyByteBuf)=DropCancelPacket(b.readUUID())
  fun handle(p:DropCancelPacket,s:IPayloadContext){val c=s;c.enqueueWork{com.example.sbwhelldiver.platform.ClientDispatch.unsafeRunWhenOn(net.neoforged.api.distmarker.Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.PlayerDropClient.cancel(p.token)}}};}
 }
}

data class DropSteerPacket(val id:Int,val forward:Float,val left:Float){
 fun encode(b:RegistryFriendlyByteBuf){b.writeVarInt(id);b.writeFloat(forward);b.writeFloat(left)}
 companion object {
  fun decode(b:RegistryFriendlyByteBuf)=DropSteerPacket(b.readVarInt(),b.readFloat(),b.readFloat())
  fun handle(p:DropSteerPacket,s:IPayloadContext){val c=s;c.enqueueWork{c.sender?.let{player->
   (player.vehicle as? com.example.sbwhelldiver.entity.PlayerHellpodEntity)?.takeIf{it.id==p.id}?.acceptSteering(player,p.forward,p.left)
  }};}
 }
}
