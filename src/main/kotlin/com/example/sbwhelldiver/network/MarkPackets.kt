package com.example.sbwhelldiver.network
import com.example.sbwhelldiver.marking.TargetMarks
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier

data class MarkRequestPacket(val start:Vec3,val direction:Vec3,val cancel:Boolean){
 fun encode(b:FriendlyByteBuf){for(v in listOf(start,direction)){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z)};b.writeBoolean(cancel)}
 companion object {
  fun decode(b:FriendlyByteBuf)=MarkRequestPacket(Vec3(b.readDouble(),b.readDouble(),b.readDouble()),Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readBoolean())
  fun handle(p:MarkRequestPacket,s:Supplier<NetworkEvent.Context>){val c=s.get();c.enqueueWork {c.sender?.let {TargetMarks.input(it,p.start,p.direction,p.cancel)}};c.packetHandled=true}
 }
}
data class MarkSnapshotPacket(val dimension:ResourceLocation,val marks:List<TargetMarks.View>){
 fun encode(b:FriendlyByteBuf){b.writeResourceLocation(dimension);b.writeVarInt(marks.size);for(m in marks){
  b.writeUUID(m.owner);b.writeLong(m.serial);b.writeVarInt(m.entityId);b.writeBoolean(m.target!=null);m.target?.let(b::writeUUID)
  b.writeDouble(m.point.x);b.writeDouble(m.point.y);b.writeDouble(m.point.z);b.writeBoolean(m.hostile);b.writeComponent(m.name);b.writeVarInt(m.remaining);b.writeUtf(m.icon,192)
 }}
 companion object {
  fun decode(b:FriendlyByteBuf):MarkSnapshotPacket {
   val dimension=b.readResourceLocation();val count=b.readVarInt();require(count in 0..1024)
   return MarkSnapshotPacket(dimension,List(count){val owner=b.readUUID();val serial=b.readLong();val id=b.readVarInt();val target=if(b.readBoolean())b.readUUID() else null
    TargetMarks.View(owner,serial,id,target,Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readBoolean(),b.readComponent(),b.readVarInt(),b.readUtf(192))})
  }
  fun handle(p:MarkSnapshotPacket,s:Supplier<NetworkEvent.Context>){val c=s.get();c.enqueueWork {
   net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT){Runnable {com.example.sbwhelldiver.client.TargetMarkClient.receive(p)}}
  };c.packetHandled=true}
 }
}

/** Acknowledgement of a successful server action; misses and passive expiry are silent. */
data class MarkFeedbackPacket(val kind:Int){
 fun encode(b:FriendlyByteBuf){b.writeByte(kind)}
 companion object {
  fun decode(b:FriendlyByteBuf)=MarkFeedbackPacket(b.readUnsignedByte().toInt())
  fun handle(p:MarkFeedbackPacket,s:Supplier<NetworkEvent.Context>){val c=s.get();c.enqueueWork {
   net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT){Runnable {com.example.sbwhelldiver.client.MarkAudio.play(p.kind)}}
  };c.packetHandled=true}
 }
}
