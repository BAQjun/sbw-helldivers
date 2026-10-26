package com.example.sbwhelldiver.network
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier
import net.minecraftforge.fml.DistExecutor
import net.minecraftforge.api.distmarker.Dist

data class DestroyerSnapshotPacket(val serverTime:Long,val data:CompoundTag){
 fun encode(b:FriendlyByteBuf){b.writeLong(serverTime);b.writeNbt(data)}
 companion object{
  fun decode(b:FriendlyByteBuf)=DestroyerSnapshotPacket(b.readLong(),b.readNbt()?:CompoundTag())
  fun handle(p:DestroyerSnapshotPacket,s:Supplier<NetworkEvent.Context>){val c=s.get();c.enqueueWork{DistExecutor.unsafeRunWhenOn(Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.DestroyerClient.receive(p)}}};c.packetHandled=true}
 }
}
data class DestroyerMenuPacket(val stage:Int){
 fun encode(b:FriendlyByteBuf){b.writeByte(stage)}
 companion object{
  fun decode(b:FriendlyByteBuf)=DestroyerMenuPacket(b.readUnsignedByte().toInt())
  fun handle(p:DestroyerMenuPacket,s:Supplier<NetworkEvent.Context>){val c=s.get();c.enqueueWork{DistExecutor.unsafeRunWhenOn(Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.StratagemClient.destroyerMenu(p.stage)}}};c.packetHandled=true}
 }
}

/** Send the true bearing without depending on distant chunk or sound-source tracking. */
data class DestroyerWarpPacket(val position:net.minecraft.world.phys.Vec3,val departing:Boolean){
 fun encode(b:FriendlyByteBuf){b.writeDouble(position.x);b.writeDouble(position.y);b.writeDouble(position.z);b.writeBoolean(departing)}
 companion object{
  fun decode(b:FriendlyByteBuf)=DestroyerWarpPacket(net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readBoolean())
  fun handle(p:DestroyerWarpPacket,s:Supplier<NetworkEvent.Context>){val c=s.get();c.enqueueWork{DistExecutor.unsafeRunWhenOn(Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.OrbitalAudio.warp(p.position,p.departing)}}};c.packetHandled=true}
 }
}

/** A distant cannon report, sent exactly when the orbital shot starts. */
data class DestroyerFirePacket(val position:net.minecraft.world.phys.Vec3){
 fun encode(b:FriendlyByteBuf){b.writeDouble(position.x);b.writeDouble(position.y);b.writeDouble(position.z)}
 companion object{
  fun decode(b:FriendlyByteBuf)=DestroyerFirePacket(net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()))
  fun handle(p:DestroyerFirePacket,s:Supplier<NetworkEvent.Context>){val c=s.get();c.enqueueWork{DistExecutor.unsafeRunWhenOn(Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.OrbitalAudio.fire(p.position)}}};c.packetHandled=true}
 }
}
