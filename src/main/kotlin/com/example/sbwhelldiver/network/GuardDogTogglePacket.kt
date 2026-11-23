package com.example.sbwhelldiver.network

import com.example.sbwhelldiver.item.StratagemBackpacks
import net.neoforged.neoforge.network.handling.IPayloadContext

object GuardDogTogglePacket {
    fun handle(context: IPayloadContext) {
        context.enqueueWork { context.sender?.let(StratagemBackpacks::interact) }
    }
}
