package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.entity.HmgEmplacementEntity
import net.minecraft.network.RegistryFriendlyByteBuf
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Supplier

/** Only the actual passenger may command the stand they are riding. */
data class HmgInputPacket(val entityId: Int,val yaw: Float,val pitch: Float,val fire: Boolean,val aimPoint:net.minecraft.world.phys.Vec3) {
    fun encode(buf: RegistryFriendlyByteBuf) { buf.writeVarInt(entityId);buf.writeFloat(yaw);buf.writeFloat(pitch);buf.writeBoolean(fire);buf.writeDouble(aimPoint.x);buf.writeDouble(aimPoint.y);buf.writeDouble(aimPoint.z) }
    companion object {
        fun decode(buf: RegistryFriendlyByteBuf)=HmgInputPacket(buf.readVarInt(),buf.readFloat(),buf.readFloat(),buf.readBoolean(),net.minecraft.world.phys.Vec3(buf.readDouble(),buf.readDouble(),buf.readDouble()))
        fun handle(packet: HmgInputPacket,supplier: IPayloadContext) {
            val ctx=supplier;ctx.enqueueWork {
                val player=ctx.sender ?: return@enqueueWork
                val turret=player.vehicle as? HmgEmplacementEntity ?: return@enqueueWork
                if(turret.id==packet.entityId)turret.acceptInput(player,packet.yaw,packet.pitch,packet.fire,packet.aimPoint)
            };
        }
    }
}
