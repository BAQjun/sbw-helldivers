package com.example.sbwhelldiver.network
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Supplier
import com.example.sbwhelldiver.platform.ClientDispatch
import net.neoforged.api.distmarker.Dist

data class DestroyerSnapshotPacket(val serverTime:Long,val data:CompoundTag){
 fun encode(b:RegistryFriendlyByteBuf){b.writeLong(serverTime);b.writeNbt(data)}
 companion object{
  fun decode(b:RegistryFriendlyByteBuf)=DestroyerSnapshotPacket(b.readLong(),b.readNbt()?:CompoundTag())
  fun handle(p:DestroyerSnapshotPacket,s:IPayloadContext){val c=s;c.enqueueWork{ClientDispatch.unsafeRunWhenOn(Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.DestroyerClient.receive(p)}}};}
 }
}
data class DestroyerMenuPacket(val stage:Int){
 fun encode(b:RegistryFriendlyByteBuf){b.writeByte(stage)}
 companion object{
  fun decode(b:RegistryFriendlyByteBuf)=DestroyerMenuPacket(b.readUnsignedByte().toInt())
  fun handle(p:DestroyerMenuPacket,s:IPayloadContext){val c=s;c.enqueueWork{ClientDispatch.unsafeRunWhenOn(Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.StratagemClient.destroyerMenu(p.stage)}}};}
 }
}

/** Send the true bearing without depending on distant chunk or sound-source tracking. */
data class DestroyerWarpPacket(val position:net.minecraft.world.phys.Vec3,val departing:Boolean){
 fun encode(b:RegistryFriendlyByteBuf){b.writeDouble(position.x);b.writeDouble(position.y);b.writeDouble(position.z);b.writeBoolean(departing)}
 companion object{
  fun decode(b:RegistryFriendlyByteBuf)=DestroyerWarpPacket(net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readBoolean())
  fun handle(p:DestroyerWarpPacket,s:IPayloadContext){val c=s;c.enqueueWork{ClientDispatch.unsafeRunWhenOn(Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.OrbitalAudio.warp(p.position,p.departing)}}};}
 }
}

/** A distant cannon report, sent exactly when the orbital shot starts. */
data class DestroyerFirePacket(val position:net.minecraft.world.phys.Vec3){
 fun encode(b:RegistryFriendlyByteBuf){b.writeDouble(position.x);b.writeDouble(position.y);b.writeDouble(position.z)}
 companion object{
  fun decode(b:RegistryFriendlyByteBuf)=DestroyerFirePacket(net.minecraft.world.phys.Vec3(b.readDouble(),b.readDouble(),b.readDouble()))
  fun handle(p:DestroyerFirePacket,s:IPayloadContext){val c=s;c.enqueueWork{ClientDispatch.unsafeRunWhenOn(Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.OrbitalAudio.fire(p.position)}}};}
 }
}
