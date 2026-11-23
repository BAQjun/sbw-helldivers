package com.example.sbwhelldiver.network
import com.example.sbwhelldiver.item.StratagemBackpacks
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier
object GuardDogTogglePacket {
 fun handle(supplier:Supplier<NetworkEvent.Context>) { val ctx=supplier.get();ctx.enqueueWork{ctx.sender?.let(StratagemBackpacks::interact)};ctx.packetHandled=true }
}
