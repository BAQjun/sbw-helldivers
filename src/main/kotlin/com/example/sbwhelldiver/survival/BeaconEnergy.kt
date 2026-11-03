package com.example.sbwhelldiver.survival
import net.minecraft.world.item.ItemStack
import net.minecraftforge.common.capabilities.ForgeCapabilities
object BeaconEnergy {
 const val THROW_COST=1000
 fun capacity():Int {val stack=ItemStack(com.atsuishio.superbwarfare.init.ModItems.SMALL_BATTERY_PACK.get());return stack.getCapability(ForgeCapabilities.ENERGY).resolve().map{it.maxEnergyStored}.orElse(100000)}
 fun stored(stack:ItemStack)=stack.tag?.getInt("Energy")?.coerceIn(0,capacity())?:0
 fun set(stack:ItemStack,value:Int){stack.orCreateTag.putInt("Energy",value.coerceIn(0,capacity()))}
 fun inherit(output:ItemStack,battery:ItemStack){set(output,battery.getCapability(ForgeCapabilities.ENERGY).resolve().map{it.energyStored}.orElse(battery.tag?.getInt("Energy")?:0))}
}
