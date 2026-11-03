package com.example.sbwhelldiver.network

import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier
import java.util.UUID

data class EquipmentTerminalInputPacket(val entityId:Int,val entityUuid:UUID,val token:UUID,val direction:Int) {
    fun encode(b:FriendlyByteBuf){b.writeVarInt(entityId);b.writeUUID(entityUuid);b.writeUUID(token);b.writeByte(direction)}
    companion object {
        fun decode(b:FriendlyByteBuf)=EquipmentTerminalInputPacket(b.readVarInt(),b.readUUID(),b.readUUID(),b.readByte().toInt())
        fun handle(p:EquipmentTerminalInputPacket,supplier:Supplier<NetworkEvent.Context>){val c=supplier.get();c.packetHandled=true;c.enqueueWork{c.sender?.let{player->
            (player.level().getEntity(p.entityId) as? com.example.sbwhelldiver.survival.EquipmentDeploymentBeaconEntity)?.takeIf{it.uuid==p.entityUuid}?.acceptInput(player,p.token,p.direction)
        }}}
    }
}
data class EquipmentTerminalStatePacket(val entityId:Int,val entityUuid:UUID,val token:UUID,val open:Boolean,val machine:Int,val prefix:String,val error:Int) {
    fun encode(b:FriendlyByteBuf){b.writeVarInt(entityId);b.writeUUID(entityUuid);b.writeUUID(token);b.writeBoolean(open);b.writeByte(machine);b.writeUtf(prefix,6);b.writeByte(error)}
    companion object {
        fun decode(b:FriendlyByteBuf)=EquipmentTerminalStatePacket(b.readVarInt(),b.readUUID(),b.readUUID(),b.readBoolean(),b.readByte().toInt(),b.readUtf(6),b.readByte().toInt())
        fun handle(p:EquipmentTerminalStatePacket,supplier:Supplier<NetworkEvent.Context>){val c=supplier.get();c.packetHandled=true;c.enqueueWork{
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.EquipmentTerminalScreen.receive(p)}}
        }}
    }
}
