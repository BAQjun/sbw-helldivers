package com.example.sbwhelldiver.network
import com.example.sbwhelldiver.marking.TargetMarks
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Supplier

data class MarkRequestPacket(val start:Vec3,val direction:Vec3,val cancel:Boolean){
 fun encode(b:RegistryFriendlyByteBuf){for(v in listOf(start,direction)){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z)};b.writeBoolean(cancel)}
 companion object {
  fun decode(b:RegistryFriendlyByteBuf)=MarkRequestPacket(Vec3(b.readDouble(),b.readDouble(),b.readDouble()),Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readBoolean())
  fun handle(p:MarkRequestPacket,s:IPayloadContext){val c=s;c.enqueueWork {c.sender?.let {TargetMarks.input(it,p.start,p.direction,p.cancel)}};}
 }
}
data class MarkSnapshotPacket(val dimension:ResourceLocation,val marks:List<TargetMarks.View>){
 fun encode(b:RegistryFriendlyByteBuf){b.writeResourceLocation(dimension);b.writeVarInt(marks.size);for(m in marks){
  b.writeUUID(m.owner);b.writeLong(m.serial);b.writeVarInt(m.entityId);b.writeBoolean(m.target!=null);m.target?.let(b::writeUUID)
  b.writeDouble(m.point.x);b.writeDouble(m.point.y);b.writeDouble(m.point.z);b.writeBoolean(m.hostile);net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.encode(b,m.name);b.writeVarInt(m.remaining);b.writeUtf(m.icon,192)
 }}
 companion object {
  fun decode(b:RegistryFriendlyByteBuf):MarkSnapshotPacket {
   val dimension=b.readResourceLocation();val count=b.readVarInt();require(count in 0..1024)
   return MarkSnapshotPacket(dimension,List(count){val owner=b.readUUID();val serial=b.readLong();val id=b.readVarInt();val target=if(b.readBoolean())b.readUUID() else null
    TargetMarks.View(owner,serial,id,target,Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readBoolean(),net.minecraft.network.chat.ComponentSerialization.STREAM_CODEC.decode(b),b.readVarInt(),b.readUtf(192))})
  }
  fun handle(p:MarkSnapshotPacket,s:IPayloadContext){val c=s;c.enqueueWork {
   com.example.sbwhelldiver.platform.ClientDispatch.unsafeRunWhenOn(net.neoforged.api.distmarker.Dist.CLIENT){Runnable {com.example.sbwhelldiver.client.TargetMarkClient.receive(p)}}
  };}
 }
}

/** Acknowledgement of a successful server action; misses and passive expiry are silent. */
data class MarkFeedbackPacket(val kind:Int){
 fun encode(b:RegistryFriendlyByteBuf){b.writeByte(kind)}
 companion object {
  fun decode(b:RegistryFriendlyByteBuf)=MarkFeedbackPacket(b.readUnsignedByte().toInt())
  fun handle(p:MarkFeedbackPacket,s:IPayloadContext){val c=s;c.enqueueWork {
   com.example.sbwhelldiver.platform.ClientDispatch.unsafeRunWhenOn(net.neoforged.api.distmarker.Dist.CLIENT){Runnable {com.example.sbwhelldiver.client.MarkAudio.play(p.kind)}}
  };}
 }
}

/** World marking permission is sent on login and every command change. */
data class MarkEnabledPacket(val enabled:Boolean){
 fun encode(b:RegistryFriendlyByteBuf){b.writeBoolean(enabled)}
 companion object {
  fun decode(b:RegistryFriendlyByteBuf)=MarkEnabledPacket(b.readBoolean())
  fun handle(p:MarkEnabledPacket,s:IPayloadContext){val c=s;c.enqueueWork{
   com.example.sbwhelldiver.platform.ClientDispatch.unsafeRunWhenOn(net.neoforged.api.distmarker.Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.TargetMarkClient.setEnabled(p.enabled)}}
  };}
 }
}
