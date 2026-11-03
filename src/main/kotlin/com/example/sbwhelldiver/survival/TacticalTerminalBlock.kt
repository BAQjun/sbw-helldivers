package com.example.sbwhelldiver.survival

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.BaseEntityBlock
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.IntegerProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes

/** A two-wide, one-deep, two-high terminal. Part zero is the left lower block. */
class TacticalTerminalBlock(properties:Properties):BaseEntityBlock(properties) {
    init { registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(PART,0)) }
    override fun createBlockStateDefinition(b:StateDefinition.Builder<Block,BlockState>){b.add(FACING,PART)}
    override fun newBlockEntity(pos:BlockPos,state:BlockState)=TacticalTerminalBlockEntity(pos,state)
    override fun getRenderShape(state:BlockState)=RenderShape.ENTITYBLOCK_ANIMATED
    override fun getShape(state:BlockState,level:BlockGetter,pos:BlockPos,context:CollisionContext)=Shapes.block()
    override fun getStateForPlacement(ctx:BlockPlaceContext):BlockState? {
        val level=ctx.level;val root=ctx.clickedPos;val facing=ctx.horizontalDirection.opposite
        if((0..3).any{part->val p=position(root,facing,part);!level.isInWorldBounds(p)||!level.getBlockState(p).canBeReplaced(ctx)})return null
        return defaultBlockState().setValue(FACING,facing)
    }
    override fun setPlacedBy(level:Level,pos:BlockPos,state:BlockState,placer:net.minecraft.world.entity.LivingEntity?,stack:net.minecraft.world.item.ItemStack){
        super.setPlacedBy(level,pos,state,placer,stack)
        if(!level.isClientSide)for(part in 1..3)level.setBlock(position(pos,state.getValue(FACING),part),state.setValue(PART,part),3)
    }
    override fun <T:BlockEntity> getTicker(level:Level,state:BlockState,type:BlockEntityType<T>):BlockEntityTicker<T>? =
        if(level.isClientSide||state.getValue(PART)!=0)null else createTickerHelper(type,MachineRegistry.TACTICAL_TERMINAL.get(),BlockEntityTicker{_,_,_,be->be.tickServer()})
    override fun use(state:BlockState,level:Level,pos:BlockPos,player:Player,hand:net.minecraft.world.InteractionHand,hit:BlockHitResult):InteractionResult {
        if(hit.direction!=state.getValue(FACING) && hit.direction!=Direction.UP)return InteractionResult.PASS
        val be=level.getBlockEntity(root(pos,state)) as? TacticalTerminalBlockEntity?:return InteractionResult.PASS
        if(level.isClientSide)return InteractionResult.SUCCESS
        if(be.powered && player is net.minecraft.server.level.ServerPlayer)TacticalTerminalSessions.open(player,be)
        return InteractionResult.CONSUME
    }
    override fun onRemove(state:BlockState,level:Level,pos:BlockPos,next:BlockState,moved:Boolean){
        if(!next.`is`(this)&&!level.isClientSide){
            val root=root(pos,state);val facing=state.getValue(FACING);val key=level.dimension().location().toString() to root
            if(removing.add(key))try {
                for(part in 0..3){val other=position(root,facing,part);if(other!=pos){val s=level.getBlockState(other);if(s.`is`(this)&&s.getValue(FACING)==facing&&s.getValue(PART)==part)level.removeBlock(other,false)}}
            } finally { removing.remove(key) }
        }
        super.onRemove(state,level,pos,next,moved)
    }
    companion object {
        private val removing=mutableSetOf<Pair<String,BlockPos>>()
        val FACING=BlockStateProperties.HORIZONTAL_FACING
        val PART=IntegerProperty.create("part",0,3)
        fun position(root:BlockPos,facing:Direction,part:Int)=root.relative(facing.clockWise,part%2).above(part/2)
        fun root(pos:BlockPos,state:BlockState):BlockPos {val part=state.getValue(PART);return pos.relative(state.getValue(FACING).counterClockWise,part%2).below(part/2)}
    }
}
