package com.example.sbwhelldiver.survival

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.energy.IEnergyStorage

class TacticalTerminalBlockEntity(pos:BlockPos,state:BlockState):BlockEntity(MachineRegistry.TACTICAL_TERMINAL.get(),pos,state) {
    var energy=0;private set
    var powered=false;private set
    fun master():TacticalTerminalBlockEntity? {
        val l=level?:return null
        val root=TacticalTerminalBlock.root(blockPos,blockState)
        return (l.getBlockEntity(root) as? TacticalTerminalBlockEntity)?.takeIf{it.blockState.`is`(blockState.block)&&it.blockState.getValue(TacticalTerminalBlock.PART)==0}
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
    override fun saveAdditional(n:CompoundTag,r:HolderLookup.Provider){super.saveAdditional(n,r);n.putInt("Energy",energy);n.putBoolean("Powered",powered)}
    override fun loadAdditional(n:CompoundTag,r:HolderLookup.Provider){super.loadAdditional(n,r);energy=n.getInt("Energy").coerceIn(0,BeaconEnergy.capacity());powered=n.getBoolean("Powered")}
    override fun getUpdateTag(r:HolderLookup.Provider)=saveWithoutMetadata(r)
    override fun getUpdatePacket()=ClientboundBlockEntityDataPacket.create(this)
}
