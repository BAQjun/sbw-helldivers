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
    override fun initializeClient(consumer:java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions>) {
        consumer.accept(object:net.neoforged.neoforge.client.extensions.common.IClientItemExtensions {
            override fun getCustomRenderer()=com.example.sbwhelldiver.client.MachineContainerItemRenderer.INSTANCE
        })
    }
    override fun useOn(c:UseOnContext):InteractionResult {
        val player=c.player?:return InteractionResult.FAIL
        val root=c.clickedPos.relative(c.clickedFace)
        val l=c.level
        if(l is ServerLevel) {
            val facing=MachineStructure.choose(l,root,machine,player.direction.opposite)?:run{player.displayClientMessage(net.minecraft.network.chat.Component.translatable("terminal.sbw_helldiver.no_space"),true);return InteractionResult.FAIL}
            val box=ModEntities.MACHINE_CONTAINER.get().create(l)?:return InteractionResult.FAIL
            val contents=c.itemInHand.get(DataComponents.CUSTOM_DATA)?.copyTag()?:CompoundTag()
            if(!contents.hasUUID("MachineOwner")||contents.getUUID("MachineOwner")!=player.uuid){contents.putUUID("MachineOwner",player.uuid);contents.remove("BoundSampleContainer")}
            box.configure(machine,root,facing,contents,false)
            if(!l.addFreshEntity(box))return InteractionResult.FAIL
            val sound=net.minecraft.world.level.block.Blocks.IRON_BLOCK.defaultBlockState().getSoundType(l,root,player)
            l.playSound(null,root,sound.placeSound,net.minecraft.sounds.SoundSource.BLOCKS,(sound.volume+1f)/2f,sound.pitch*.8f)
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
