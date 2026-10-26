package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.config.HelldiversConfig
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.PacketDistributor
import java.util.function.Supplier

/** The server owns this world setting; ordinary clients can only read it. */
object AutoRetractionSettings {
    fun canEdit(player:ServerPlayer)=player.hasPermissions(2) || player.server.isSingleplayerOwner(player.gameProfile)
    fun update(player:ServerPlayer,enabled:Boolean):Boolean {
        if(!canEdit(player))return false
        HelldiversConfig.autoRetract.set(enabled)
        HelldiversConfig.SPEC.save()
        return true
    }
}

data class AutoRetractRequestPacket(val enabled:Boolean?) {
    fun encode(b:FriendlyByteBuf){b.writeBoolean(enabled!=null);enabled?.let{b.writeBoolean(it)}}
    companion object {
        fun decode(b:FriendlyByteBuf)=AutoRetractRequestPacket(if(b.readBoolean())b.readBoolean() else null)
        fun handle(p:AutoRetractRequestPacket,s:Supplier<NetworkEvent.Context>){
            val c=s.get()
            c.enqueueWork {
                c.sender?.let { player ->
                    val changed=p.enabled?.let{AutoRetractionSettings.update(player,it)}==true
                    val players=if(changed)player.server.playerList.players else listOf(player)
                    for(recipient in players)BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with{recipient},
                        AutoRetractStatePacket(HelldiversConfig.autoRetract.get(),AutoRetractionSettings.canEdit(recipient)))
                }
            }
            c.packetHandled=true
        }
    }
}

data class AutoRetractStatePacket(val enabled:Boolean,val editable:Boolean) {
    fun encode(b:FriendlyByteBuf){b.writeBoolean(enabled);b.writeBoolean(editable)}
    companion object {
        fun decode(b:FriendlyByteBuf)=AutoRetractStatePacket(b.readBoolean(),b.readBoolean())
        fun handle(p:AutoRetractStatePacket,s:Supplier<NetworkEvent.Context>){val c=s.get();c.enqueueWork{
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT){Runnable{
                if(HelldiversConfig.SPEC.isLoaded)HelldiversConfig.autoRetract.set(p.enabled)
                (net.minecraft.client.Minecraft.getInstance().screen as? com.example.sbwhelldiver.client.HelldiversConfigScreen)?.autoRetractReceived(p.enabled,p.editable)
            }}
        };c.packetHandled=true}
    }
}
