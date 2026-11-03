package com.example.sbwhelldiver.survival

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModBlocks
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.eventbus.api.Event.Result
import net.minecraftforge.event.entity.player.PlayerInteractEvent

@EventBusSubscriber(modid=MOD_ID)
object MachineInteractions {
    @SubscribeEvent
    fun crowbar(event:PlayerInteractEvent.RightClickBlock) {
        if(!event.entity.isShiftKeyDown || !event.itemStack.`is`(com.atsuishio.superbwarfare.init.ModItems.CROWBAR.get()))return
        if(!event.level.getBlockState(event.pos).`is`(ModBlocks.SAMPLE_MACHINE.get()))return
        // Sneaking normally skips block interaction. Keep packing in the block's
        // authoritative handler, while allowing it through on both client and server.
        event.setUseBlock(Result.ALLOW)
        event.setUseItem(Result.DENY)
    }
}
