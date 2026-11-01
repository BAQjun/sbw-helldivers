package com.example.sbwhelldiver.survival

import com.example.sbwhelldiver.MOD_ID
import net.minecraft.core.registries.Registries
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.registries.DeferredRegister
import com.example.sbwhelldiver.init.ModBlocks

object MachineRegistry {
    val BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,MOD_ID)
    val MACHINE=BLOCK_ENTITIES.register("sample_machine"){_->BlockEntityType.Builder.of(::SampleMachineBlockEntity,ModBlocks.SAMPLE_MACHINE.get()).build(null)}
    fun capabilities(event:RegisterCapabilitiesEvent) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,MACHINE.get()){be,side->be.itemPort(side)}
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,MACHINE.get()){be,side->be.energyPort(side)}
    }
}
