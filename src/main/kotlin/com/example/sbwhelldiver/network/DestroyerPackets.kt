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
