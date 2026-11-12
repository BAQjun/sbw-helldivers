package com.example.sbwhelldiver.network
import com.example.sbwhelldiver.item.GuardDogBackpack
import net.minecraftforge.network.NetworkEvent
import java.util.function.Supplier
object GuardDogTogglePacket {
 fun handle(supplier:Supplier<NetworkEvent.Context>) { val ctx=supplier.get();ctx.enqueueWork{ctx.sender?.let(GuardDogBackpack::toggle)};ctx.packetHandled=true }
}
