package com.example.sbwhelldiver.network

import net.minecraft.core.BlockPos
import net.minecraft.network.FriendlyByteBuf
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier
import java.util.UUID

data class TacticalTerminalOpenPacket(val pos:BlockPos,val token:UUID){
    fun encode(b:FriendlyByteBuf){b.writeBlockPos(pos);b.writeUUID(token)}
    companion object {
        fun decode(b:FriendlyByteBuf)=TacticalTerminalOpenPacket(b.readBlockPos(),b.readUUID())
        fun handle(p:TacticalTerminalOpenPacket,supplier:Supplier<NetworkEvent.Context>){val c=supplier.get();c.packetHandled=true;c.enqueueWork{
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.TacticalTerminalScreen.open(p)}}
        }}
    }
}
data class TacticalTerminalActionPacket(val pos:BlockPos,val token:UUID,val action:Int,val offer:String){
    fun encode(b:FriendlyByteBuf){b.writeBlockPos(pos);b.writeUUID(token);b.writeByte(action);b.writeUtf(offer,64)}
    companion object {
        fun decode(b:FriendlyByteBuf)=TacticalTerminalActionPacket(b.readBlockPos(),b.readUUID(),b.readByte().toInt(),b.readUtf(64))
        fun handle(p:TacticalTerminalActionPacket,supplier:Supplier<NetworkEvent.Context>){val c=supplier.get();c.packetHandled=true;c.enqueueWork{c.sender?.let{com.example.sbwhelldiver.survival.TacticalTerminalSessions.accept(it,p.pos,p.token,p.action,p.offer)}}}
    }
}

data class TacticalTerminalResultPacket(val token:UUID,val key:String){
    fun encode(b:FriendlyByteBuf){b.writeUUID(token);b.writeUtf(key,128)}
    companion object {
        fun decode(b:FriendlyByteBuf)=TacticalTerminalResultPacket(b.readUUID(),b.readUtf(128))
        fun handle(p:TacticalTerminalResultPacket,supplier:Supplier<NetworkEvent.Context>){val c=supplier.get();c.packetHandled=true;c.enqueueWork{
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.TacticalTerminalScreen.result(p)}}
        }}
    }
}
