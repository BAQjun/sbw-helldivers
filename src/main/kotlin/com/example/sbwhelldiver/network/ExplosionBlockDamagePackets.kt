package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.config.HelldiversConfig
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.handling.IPayloadContext
import com.example.sbwhelldiver.network.PacketTargets
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
    fun encode(b:RegistryFriendlyByteBuf){b.writeBoolean(enabled!=null);enabled?.let{b.writeBoolean(it)}}
    companion object {
        fun decode(b:RegistryFriendlyByteBuf)=ExplosionBlockDamageRequestPacket(if(b.readBoolean())b.readBoolean() else null)
        fun handle(p:ExplosionBlockDamageRequestPacket,s:IPayloadContext){
            val c=s
            c.enqueueWork {
                c.sender?.let { player ->
                    val changed=p.enabled?.let{ExplosionBlockDamageSettings.update(player,it)}==true
                    val players=if(changed)player.server.playerList.players else listOf(player)
                    for(recipient in players)BeaconNetwork.CHANNEL.send(PacketTargets.PLAYER.with{recipient},
                        ExplosionBlockDamageStatePacket(HelldiversConfig.stratagemExplosionBlockDamage.get(),ExplosionBlockDamageSettings.canEdit(recipient)))
                }
            }

        }
    }
}

data class ExplosionBlockDamageStatePacket(val enabled:Boolean,val editable:Boolean) {
    fun encode(b:RegistryFriendlyByteBuf){b.writeBoolean(enabled);b.writeBoolean(editable)}
    companion object {
        fun decode(b:RegistryFriendlyByteBuf)=ExplosionBlockDamageStatePacket(b.readBoolean(),b.readBoolean())
        fun handle(p:ExplosionBlockDamageStatePacket,s:IPayloadContext){val c=s;c.enqueueWork{
            com.example.sbwhelldiver.platform.ClientDispatch.unsafeRunWhenOn(net.neoforged.api.distmarker.Dist.CLIENT){Runnable{
                if(HelldiversConfig.SPEC.isLoaded)HelldiversConfig.stratagemExplosionBlockDamage.set(p.enabled)
                (net.minecraft.client.Minecraft.getInstance().screen as? com.example.sbwhelldiver.client.HelldiversConfigScreen)?.explosionBlockDamageReceived(p.enabled,p.editable)
            }}
        };}
    }
}
