package com.example.sbwhelldiver.gametest

import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent

@Mod.EventBusSubscriber(modid="sbw_helldiver",bus=Mod.EventBusSubscriber.Bus.MOD,value=[Dist.CLIENT])
object TaczClientTestBootstrap {
    @SubscribeEvent fun setup(event:FMLClientSetupEvent) {
        MinecraftForge.EVENT_BUS.register(TaczClientProjectilesProbe::class.java)
    }
}
