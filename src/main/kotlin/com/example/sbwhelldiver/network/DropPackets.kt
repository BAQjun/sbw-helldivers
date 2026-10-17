package com.example.sbwhelldiver.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.NetworkEvent
import java.util.UUID
import java.util.function.Supplier

data class DropPreparePacket(val token:UUID,val dimension:ResourceLocation,val point:Vec3){
 fun encode(b:FriendlyByteBuf){b.writeUUID(token);b.writeResourceLocation(dimension);b.writeDouble(point.x);b.writeDouble(point.y);b.writeDouble(point.z)}
 companion object {
  fun decode(b:FriendlyByteBuf)=DropPreparePacket(b.readUUID(),b.readResourceLocation(),Vec3(b.readDouble(),b.readDouble(),b.readDouble()))
  fun handle(p:DropPreparePacket,s:Supplier<NetworkEvent.Context>){val c=s.get();c.enqueueWork{net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.PlayerDropClient.prepare(p)}}};c.packetHandled=true}
 }
}
data class DropReadyPacket(val token:UUID){
 fun encode(b:FriendlyByteBuf){b.writeUUID(token)}
 companion object {
  fun decode(b:FriendlyByteBuf)=DropReadyPacket(b.readUUID())
  fun handle(p:DropReadyPacket,s:Supplier<NetworkEvent.Context>){val c=s.get();c.enqueueWork{c.sender?.let{com.example.sbwhelldiver.entity.PlayerDrops.ready(it,p.token)}};c.packetHandled=true}
 }
}
data class DropCancelPacket(val token:UUID){
 fun encode(b:FriendlyByteBuf){b.writeUUID(token)}
 companion object {
  fun decode(b:FriendlyByteBuf)=DropCancelPacket(b.readUUID())
  fun handle(p:DropCancelPacket,s:Supplier<NetworkEvent.Context>){val c=s.get();c.enqueueWork{net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.PlayerDropClient.cancel(p.token)}}};c.packetHandled=true}
 }
}

data class DropSteerPacket(val id:Int,val forward:Float,val left:Float){
 fun encode(b:FriendlyByteBuf){b.writeVarInt(id);b.writeFloat(forward);b.writeFloat(left)}
 companion object {
  fun decode(b:FriendlyByteBuf)=DropSteerPacket(b.readVarInt(),b.readFloat(),b.readFloat())
  fun handle(p:DropSteerPacket,s:Supplier<NetworkEvent.Context>){val c=s.get();c.enqueueWork{c.sender?.let{player->
   (player.vehicle as? com.example.sbwhelldiver.entity.PlayerHellpodEntity)?.takeIf{it.id==p.id}?.acceptSteering(player,p.forward,p.left)
  }};c.packetHandled=true}
 }
}
