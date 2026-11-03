package com.example.sbwhelldiver.survival
import net.minecraft.world.item.ItemStack
import net.minecraftforge.common.capabilities.*
import net.minecraftforge.common.util.LazyOptional
import net.minecraftforge.energy.IEnergyStorage
class BeaconEnergyProvider(private val stack:ItemStack):ICapabilityProvider {
 private val storage=LazyOptional.of{object:IEnergyStorage {
 override fun receiveEnergy(amount:Int,simulate:Boolean):Int {val accepted=minOf(amount.coerceAtLeast(0),BeaconEnergy.capacity()-BeaconEnergy.stored(stack));if(!simulate)BeaconEnergy.set(stack,BeaconEnergy.stored(stack)+accepted);return accepted}
 override fun extractEnergy(amount:Int,simulate:Boolean)=0
 override fun getEnergyStored()=BeaconEnergy.stored(stack)
 override fun getMaxEnergyStored()=BeaconEnergy.capacity()
 override fun canExtract()=false
 override fun canReceive()=true
 }}
 override fun <T:Any?> getCapability(cap:Capability<T>,side:net.minecraft.core.Direction?):LazyOptional<T> = if(cap===ForgeCapabilities.ENERGY)storage.cast()else LazyOptional.empty()
}
