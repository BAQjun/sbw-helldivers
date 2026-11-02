package com.example.sbwhelldiver.network

import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.UUID

data class TacticalTerminalOpenPacket(val pos:BlockPos,val token:UUID){
    fun encode(b:RegistryFriendlyByteBuf){b.writeBlockPos(pos);b.writeUUID(token)}
    companion object {
        fun decode(b:RegistryFriendlyByteBuf)=TacticalTerminalOpenPacket(b.readBlockPos(),b.readUUID())
        fun handle(p:TacticalTerminalOpenPacket,c:IPayloadContext){c.enqueueWork{
            com.example.sbwhelldiver.platform.ClientDispatch.unsafeRunWhenOn(net.neoforged.api.distmarker.Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.TacticalTerminalScreen.open(p)}}
        }}
    }
}
data class TacticalTerminalActionPacket(val pos:BlockPos,val token:UUID,val action:Int,val offer:String){
    fun encode(b:RegistryFriendlyByteBuf){b.writeBlockPos(pos);b.writeUUID(token);b.writeByte(action);b.writeUtf(offer,64)}
    companion object {
        fun decode(b:RegistryFriendlyByteBuf)=TacticalTerminalActionPacket(b.readBlockPos(),b.readUUID(),b.readByte().toInt(),b.readUtf(64))
        fun handle(p:TacticalTerminalActionPacket,c:IPayloadContext){c.enqueueWork{c.sender?.let{com.example.sbwhelldiver.survival.TacticalTerminalSessions.accept(it,p.pos,p.token,p.action,p.offer)}}}
    }
}

data class TacticalTerminalResultPacket(val token:UUID,val key:String){
    fun encode(b:RegistryFriendlyByteBuf){b.writeUUID(token);b.writeUtf(key,128)}
    companion object {
        fun decode(b:RegistryFriendlyByteBuf)=TacticalTerminalResultPacket(b.readUUID(),b.readUtf(128))
        fun handle(p:TacticalTerminalResultPacket,c:IPayloadContext){c.enqueueWork{
            com.example.sbwhelldiver.platform.ClientDispatch.unsafeRunWhenOn(net.neoforged.api.distmarker.Dist.CLIENT){Runnable{com.example.sbwhelldiver.client.TacticalTerminalScreen.result(p)}}
        }}
    }
}
