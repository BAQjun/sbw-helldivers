package com.example.sbwhelldiver.survival

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModBlocks
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.common.util.TriState
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent

@EventBusSubscriber(modid=MOD_ID)
object MachineInteractions {
    @SubscribeEvent
    fun crowbar(event:PlayerInteractEvent.RightClickBlock) {
        if(!event.entity.isShiftKeyDown || !event.itemStack.`is`(com.atsuishio.superbwarfare.init.ModItems.CROWBAR.get()))return
        if(!event.level.getBlockState(event.pos).`is`(ModBlocks.SAMPLE_MACHINE.get()))return
        // Sneaking normally skips block interaction. Keep packing in the block's
        // authoritative handler, while allowing it through on both client and server.
        event.setUseBlock(TriState.TRUE)
        event.setUseItem(TriState.FALSE)
    }
}
