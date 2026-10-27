package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.config.HelldiversConfig
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.network.NetworkEvent
import net.minecraftforge.network.PacketDistributor
import java.util.function.Supplier

/** The server owns this world setting; ordinary clients can only read it. */
object ExplosionBlockDamageSettings {
    fun canEdit(player:ServerPlayer)=player.hasPermissions(2) || player.server.isSingleplayerOwner(player.gameProfile)
    fun update(player:ServerPlayer,enabled:Boolean):Boolean {
        if(!canEdit(player))return false
        HelldiversConfig.stratagemExplosionBlockDamage.set(enabled)
        HelldiversConfig.SPEC.save()
        return true
    }
}

data class ExplosionBlockDamageRequestPacket(val enabled:Boolean?) {
    fun encode(b:FriendlyByteBuf){b.writeBoolean(enabled!=null);enabled?.let{b.writeBoolean(it)}}
    companion object {
        fun decode(b:FriendlyByteBuf)=ExplosionBlockDamageRequestPacket(if(b.readBoolean())b.readBoolean() else null)
        fun handle(p:ExplosionBlockDamageRequestPacket,s:Supplier<NetworkEvent.Context>){
            val c=s.get()
            c.enqueueWork {
                c.sender?.let { player ->
                    val changed=p.enabled?.let{ExplosionBlockDamageSettings.update(player,it)}==true
                    val players=if(changed)player.server.playerList.players else listOf(player)
                    for(recipient in players)BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with{recipient},
                        ExplosionBlockDamageStatePacket(HelldiversConfig.stratagemExplosionBlockDamage.get(),ExplosionBlockDamageSettings.canEdit(recipient)))
                }
            }
            c.packetHandled=true
        }
    }
}

data class ExplosionBlockDamageStatePacket(val enabled:Boolean,val editable:Boolean) {
    fun encode(b:FriendlyByteBuf){b.writeBoolean(enabled);b.writeBoolean(editable)}
    companion object {
        fun decode(b:FriendlyByteBuf)=ExplosionBlockDamageStatePacket(b.readBoolean(),b.readBoolean())
        fun handle(p:ExplosionBlockDamageStatePacket,s:Supplier<NetworkEvent.Context>){val c=s.get();c.enqueueWork{
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT){Runnable{
                if(HelldiversConfig.SPEC.isLoaded)HelldiversConfig.stratagemExplosionBlockDamage.set(p.enabled)
                (net.minecraft.client.Minecraft.getInstance().screen as? com.example.sbwhelldiver.client.HelldiversConfigScreen)?.explosionBlockDamageReceived(p.enabled,p.editable)
            }}
        };c.packetHandled=true}
    }
}
