package com.example.sbwhelldiver.survival
class TacticalTerminalItem(block:net.minecraft.world.level.block.Block,properties:Properties):net.minecraft.world.item.BlockItem(block,properties) {
    override fun initializeClient(consumer:java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions>) {
        consumer.accept(object:net.neoforged.neoforge.client.extensions.common.IClientItemExtensions {
            override fun getCustomRenderer()=com.example.sbwhelldiver.client.TacticalTerminalItemRenderer.INSTANCE
        })
    }
}
