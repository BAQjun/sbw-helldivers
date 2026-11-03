package com.example.sbwhelldiver.survival

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraftforge.energy.IEnergyStorage

class TacticalTerminalBlockEntity(pos:BlockPos,state:BlockState):BlockEntity(MachineRegistry.TACTICAL_TERMINAL.get(),pos,state) {
    var energy=0;private set
    var powered=false;private set
    fun master():TacticalTerminalBlockEntity? {
        val l=level?:return null
        if(isRemoved)return null
        val root=TacticalTerminalBlock.root(blockPos,blockState)
        return (l.getBlockEntity(root) as? TacticalTerminalBlockEntity)?.takeIf{!it.isRemoved&&it.blockState.`is`(blockState.block)&&it.blockState.getValue(TacticalTerminalBlock.PART)==0}
    }
    private val storage=object:IEnergyStorage {
        override fun receiveEnergy(maxReceive:Int,simulate:Boolean):Int {
            val m=master()?:return 0
            val amount=minOf(maxReceive.coerceAtLeast(0),(BeaconEnergy.capacity()-m.energy).coerceAtLeast(0))
            if(!simulate&&amount>0){m.energy+=amount;m.setChanged()}
            return amount
        }
        override fun extractEnergy(maxExtract:Int,simulate:Boolean)=0
        override fun getEnergyStored()=master()?.energy?:0
        override fun getMaxEnergyStored()=BeaconEnergy.capacity()
        override fun canExtract()=false
        override fun canReceive()=master()!=null
    }
    fun energyPort(side:Direction?)=storage
    fun tickServer(){
        val l=level?:return
        val previous=powered
        powered=energy>=100
        if(powered){energy-=100;setChanged()}
        if(previous!=powered){setChanged();l.sendBlockUpdated(blockPos,blockState,blockState,3)}
    }
    override fun saveAdditional(n:CompoundTag){super.saveAdditional(n);n.putInt("Energy",energy);n.putBoolean("Powered",powered)}
    override fun load(n:CompoundTag){super.load(n);energy=n.getInt("Energy").coerceIn(0,BeaconEnergy.capacity());powered=n.getBoolean("Powered")}
    override fun getRenderBoundingBox()=net.minecraft.world.phys.AABB(blockPos).inflate(2.0,3.0,2.0)
    override fun getUpdateTag()=saveWithoutMetadata()
    override fun getUpdatePacket()=ClientboundBlockEntityDataPacket.create(this)
    private var energyCapability=net.minecraftforge.common.util.LazyOptional.of{storage}
    override fun invalidateCaps(){super.invalidateCaps();energyCapability.invalidate();}
    override fun reviveCaps(){super.reviveCaps();energyCapability=net.minecraftforge.common.util.LazyOptional.of{storage};}
    override fun <T:Any?> getCapability(cap:net.minecraftforge.common.capabilities.Capability<T>,side:Direction?):net.minecraftforge.common.util.LazyOptional<T> {
        if(!isRemoved && cap===net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY )return energyCapability.cast()
        return super.getCapability(cap,side)
    }


}
