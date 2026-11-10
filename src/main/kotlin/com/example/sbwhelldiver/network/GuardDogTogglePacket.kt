package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.item.GuardDogBackpack
import net.neoforged.neoforge.network.handling.IPayloadContext

object GuardDogTogglePacket {
    fun handle(context: IPayloadContext) {
        context.enqueueWork { context.sender?.let(GuardDogBackpack::toggle) }
    }
}
