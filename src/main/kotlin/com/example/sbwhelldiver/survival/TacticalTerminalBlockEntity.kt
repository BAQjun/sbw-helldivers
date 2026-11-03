package com.example.sbwhelldiver.survival

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class TacticalTerminalBlockEntity(pos:BlockPos,state:BlockState):BlockEntity(MachineRegistry.TACTICAL_TERMINAL.get(),pos,state) {
    var screenOn = true
        private set

    fun setScreenEnabled(enabled: Boolean) {
        val world = level ?: return
        if (world.isClientSide || screenOn == enabled) return
        screenOn = enabled
        setChanged()
        world.sendBlockUpdated(blockPos, blockState, blockState, 3)
    }

    override fun saveAdditional(n:CompoundTag,r:HolderLookup.Provider) {
        super.saveAdditional(n,r)
        n.putBoolean("ScreenOff", !screenOn)
    }

    override fun loadAdditional(n:CompoundTag,r:HolderLookup.Provider) {
        super.loadAdditional(n,r)
        screenOn = !n.getBoolean("ScreenOff")
    }
    override fun getUpdateTag(r:HolderLookup.Provider)=saveWithoutMetadata(r)
    override fun getUpdatePacket()=ClientboundBlockEntityDataPacket.create(this)
}
