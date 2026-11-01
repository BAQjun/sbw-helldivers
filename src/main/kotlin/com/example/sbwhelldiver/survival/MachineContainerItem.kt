package com.example.sbwhelldiver.survival

import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionResult
import net.minecraft.world.item.*
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.server.level.ServerLevel

class MachineContainerItem(val machine:MachineDeployment,properties:Properties):Item(properties) {
    override fun useOn(c:UseOnContext):InteractionResult {
        val player=c.player?:return InteractionResult.FAIL
        val root=c.clickedPos.relative(c.clickedFace)
        val l=c.level
        if(l is ServerLevel) {
            val facing=MachineStructure.choose(l,root,machine,player.direction.opposite)?:run{player.displayClientMessage(net.minecraft.network.chat.Component.translatable("terminal.sbw_helldiver.no_space"),true);return InteractionResult.FAIL}
            val box=ModEntities.MACHINE_CONTAINER.get().create(l)?:return InteractionResult.FAIL
            box.configure(machine,root,facing,c.itemInHand.get(DataComponents.CUSTOM_DATA)?.copyTag()?:CompoundTag(),false)
            if(!l.addFreshEntity(box))return InteractionResult.FAIL
            if(!player.abilities.instabuild)c.itemInHand.shrink(1)
        }
        return InteractionResult.sidedSuccess(l.isClientSide)
    }
    companion object {
        fun stack(m:MachineDeployment,contents:CompoundTag=CompoundTag())=ItemStack(if(m==MachineDeployment.BIOPROCESSOR)ModItems.BIOPROCESSOR_CONTAINER.get()else ModItems.REFINERY_CONTAINER.get()).also {
            if(!contents.isEmpty)it.set(DataComponents.CUSTOM_DATA,CustomData.of(contents.copy()))
        }
    }
}
