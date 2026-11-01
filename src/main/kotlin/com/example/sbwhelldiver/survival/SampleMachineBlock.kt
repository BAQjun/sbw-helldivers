package com.example.sbwhelldiver.survival

import com.mojang.serialization.MapCodec
import net.minecraft.core.BlockPos
import net.minecraft.world.*
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.*
import net.minecraft.world.level.block.*
import net.minecraft.world.level.block.entity.*
import net.minecraft.world.level.block.state.*
import net.minecraft.world.level.block.state.properties.*
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.*

class SampleMachineBlock(properties:Properties):BaseEntityBlock(properties) {
    init { registerDefaultState(stateDefinition.any().setValue(FACING,net.minecraft.core.Direction.NORTH).setValue(KIND,MachineDeployment.BIOPROCESSOR).setValue(PORT,MachineLayout.Port.NONE).setValue(MASTER,false)) }
    override fun codec():MapCodec<out BaseEntityBlock> = CODEC
    override fun createBlockStateDefinition(b:StateDefinition.Builder<Block,BlockState>) { b.add(FACING,KIND,PORT,MASTER) }
    override fun newBlockEntity(pos:BlockPos,state:BlockState)=SampleMachineBlockEntity(pos,state)
    override fun getRenderShape(state:BlockState)=RenderShape.ENTITYBLOCK_ANIMATED
    override fun getShape(state:BlockState,level:BlockGetter,pos:BlockPos,context:CollisionContext):VoxelShape {
        val be=level.getBlockEntity(pos) as? SampleMachineBlockEntity ?: return Shapes.block()
        return MachineLayout.shapes(state.getValue(KIND),state.getValue(FACING))[pos.subtract(be.root)]?:Shapes.block()
    }
    override fun <T:BlockEntity> getTicker(level:Level,state:BlockState,type:BlockEntityType<T>):BlockEntityTicker<T>? =
        if(level.isClientSide)null else createTickerHelper(type,MachineRegistry.MACHINE.get(),BlockEntityTicker { l,p,s,be->be.serverTick() })
    override fun useItemOn(stack:ItemStack,state:BlockState,level:Level,pos:BlockPos,player:Player,hand:InteractionHand,hit:BlockHitResult):ItemInteractionResult {
        if(stack.`is`(com.atsuishio.superbwarfare.init.ModItems.CROWBAR.get()) && player.isShiftKeyDown) {
            if(!level.isClientSide) (level.getBlockEntity(pos) as? SampleMachineBlockEntity)?.master()?.let{MachineStructure.remove(it,true)}
            return ItemInteractionResult.sidedSuccess(level.isClientSide)
        }
        if((level.getBlockEntity(pos) as? SampleMachineBlockEntity)?.insertFromHand(player,hand)==true)return ItemInteractionResult.sidedSuccess(level.isClientSide)
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION
    }
    override fun useWithoutItem(state:BlockState,level:Level,pos:BlockPos,player:Player,hit:BlockHitResult):InteractionResult {
        return InteractionResult.PASS
    }
    override fun onRemove(state:BlockState,level:Level,pos:BlockPos,next:BlockState,moved:Boolean) {
        if(!next.`is`(this) && !level.isClientSide)(level.getBlockEntity(pos) as? SampleMachineBlockEntity)?.master()?.let{MachineStructure.remove(it,false)}
        super.onRemove(state,level,pos,next,moved)
    }
    companion object {
        val CODEC:MapCodec<SampleMachineBlock> = simpleCodec(::SampleMachineBlock)
        val FACING=BlockStateProperties.HORIZONTAL_FACING
        val KIND=EnumProperty.create("machine",MachineDeployment::class.java)
        val PORT=EnumProperty.create("port",MachineLayout.Port::class.java)
        val MASTER=BooleanProperty.create("master")
    }
}
