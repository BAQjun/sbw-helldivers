package com.example.sbwhelldiver.survival

import com.atsuishio.superbwarfare.init.ModDataComponents
import com.atsuishio.superbwarfare.item.EnergyStorageItem
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.capabilities.Capabilities

object BeaconEnergy {
    const val THROW_COST=1000
    fun capacity():Int {
        val battery=ItemStack(com.atsuishio.superbwarfare.init.ModItems.SMALL_BATTERY_PACK.get())
        return (battery.item as EnergyStorageItem).getMaxEnergy(battery)
    }
    fun stored(stack:ItemStack)=stack.getOrDefault(ModDataComponents.ENERGY,0).coerceIn(0,capacity())
    fun set(stack:ItemStack,value:Int){stack.set(ModDataComponents.ENERGY,value.coerceIn(0,capacity()))}
    fun inherit(output:ItemStack,battery:ItemStack) {
        set(output,battery.getCapability(Capabilities.EnergyStorage.ITEM)?.energyStored ?: battery.getOrDefault(ModDataComponents.ENERGY,0))
    }
}
