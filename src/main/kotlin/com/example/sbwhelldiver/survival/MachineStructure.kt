package com.example.sbwhelldiver.survival

import com.example.sbwhelldiver.init.ModBlocks
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3

object MachineStructure {
    /** Reconcile previously deployed test machines without losing a reserved batch. */
    fun upgrade(master:SampleMachineBlockEntity):Boolean {
        val level=master.level as? ServerLevel?:return false
        val root=master.blockPos;val target=MachineLayout.shapes(master.machine,master.facing)
        val positions=BlockPos.betweenClosed(root.offset(-3,0,-3),root.offset(3,7,3)).map{it.immutable()}
        if(positions.any{!level.hasChunkAt(it)})return false
        val old=positions.filter{(level.getBlockEntity(it) as? SampleMachineBlockEntity)?.root==root}
        if(target.keys.any{offset->val pos=root.offset(offset);pos !in old && !level.getBlockState(pos).canBeReplaced()}) {
            remove(master,true);return false
        }
        for(pos in old)if(pos.subtract(root) !in target.keys){
            (level.getBlockEntity(pos) as? SampleMachineBlockEntity)?.tearingDown=true
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3)
        }
        for(offset in target.keys) {
            val pos=root.offset(offset)
            val port=MachineLayout.Port.entries.firstOrNull{it!=MachineLayout.Port.NONE&&MachineLayout.rotate(MachineLayout.portOffset(master.machine,it),master.facing)==offset}?:MachineLayout.Port.NONE
            val state=master.blockState.setValue(SampleMachineBlock.MASTER,offset==BlockPos.ZERO).setValue(SampleMachineBlock.PORT,port)
            if(level.getBlockState(pos)!=state)level.setBlock(pos,state,3)
            (level.getBlockEntity(pos) as SampleMachineBlockEntity).let{it.root=root;it.setChanged();it.sync()}

        }
        master.layoutVersion=MachineLayout.VERSION;master.setChanged();master.sync();return true
    }
    private val order=listOf(Direction.EAST,Direction.SOUTH,Direction.WEST,Direction.NORTH)
    fun choose(level:ServerLevel,root:BlockPos,m:MachineDeployment,preferred:Direction,ignore:Entity?=null):Direction? =
        (listOf(preferred)+order).distinct().firstOrNull{space(level,root,m,it,ignore)}
    fun space(level:ServerLevel,root:BlockPos,m:MachineDeployment,f:Direction,ignore:Entity?=null):Boolean {
        val parts=MachineLayout.shapes(m,f)
        val bounds=MachineLayout.containerBounds(m).let{MachineLayout.rotate(it,f)}.move(root.x+.5,root.y.toDouble(),root.z+.5)
        if(root.y<level.minBuildHeight || bounds.maxY>level.maxBuildHeight || !level.worldBorder.isWithinBounds(bounds))return false
        for(offset in parts.keys) {
            val pos=root.offset(offset)
            if(!level.hasChunkAt(pos))return false
            val s=level.getBlockState(pos)
            if(!s.canBeReplaced() || !s.fluidState.isEmpty)return false
            if(offset.y==0) {
                val below=pos.below()
                if(!level.getBlockState(below).isFaceSturdy(level,below,Direction.UP))return false
            }
        }
        // Closed shells must also have room. Opening does not overwrite a newly placed obstacle.
        val lo=BlockPos.containing(bounds.minX+.001,bounds.minY+.001,bounds.minZ+.001)
        val hi=BlockPos.containing(bounds.maxX-.001,bounds.maxY-.001,bounds.maxZ-.001)
        for(p in BlockPos.betweenClosed(lo,hi)) {
            if(!level.hasChunkAt(p))return false
            val s=level.getBlockState(p)
            if(!s.canBeReplaced() || !s.fluidState.isEmpty)return false
        }
        return level.getEntities(ignore,bounds){it !is net.minecraft.world.entity.player.Player && it !is EquipmentDeploymentBeaconEntity && it !is com.example.sbwhelldiver.entity.StratagemMarker && it.isPickable}.isEmpty()
    }
    fun place(level:ServerLevel,root:BlockPos,m:MachineDeployment,f:Direction,payload:CompoundTag,ignore:Entity?):SampleMachineBlockEntity? {
        if(!space(level,root,m,f,ignore))return null
        val parts=MachineLayout.shapes(m,f)
        val placed=ArrayList<BlockPos>()
        for(offset in parts.keys.sortedByDescending{it==BlockPos.ZERO}) {
            val pos=root.offset(offset)
            val port=MachineLayout.Port.entries.firstOrNull{it!=MachineLayout.Port.NONE && MachineLayout.rotate(MachineLayout.portOffset(m,it),f)==offset}?:MachineLayout.Port.NONE
            val state=ModBlocks.SAMPLE_MACHINE.get().defaultBlockState().setValue(SampleMachineBlock.KIND,m).setValue(SampleMachineBlock.FACING,f).setValue(SampleMachineBlock.PORT,port).setValue(SampleMachineBlock.MASTER,offset==BlockPos.ZERO)
            if(!level.setBlock(pos,state,3)) {
                placed.forEach{p->(level.getBlockEntity(p) as? SampleMachineBlockEntity)?.tearingDown=true}
                placed.forEach{level.setBlock(it,Blocks.AIR.defaultBlockState(),3)}
                return null
            }
            placed.add(pos)
            (level.getBlockEntity(pos) as SampleMachineBlockEntity).let{it.root=root;it.setChanged();it.sync()}

        }
        return (level.getBlockEntity(root) as SampleMachineBlockEntity).also{it.restoreContents(payload);it.readyAt=level.gameTime+48;it.sync()}
    }
    fun remove(master:SampleMachineBlockEntity,pack:Boolean) {
        val level=master.level as? ServerLevel?:return
        if(master.tearingDown)return
        val positions=BlockPos.betweenClosed(master.blockPos.offset(-3,0,-3),master.blockPos.offset(3,7,3))
            .map{it.immutable()}.filter{(level.getBlockEntity(it) as? SampleMachineBlockEntity)?.root==master.blockPos}
        val stack=if(pack)MachineContainerItem.stack(master.machine,master.contents())else null
        positions.forEach{(level.getBlockEntity(it) as? SampleMachineBlockEntity)?.tearingDown=true}
        // Remove the root last so callbacks can always see the re-entry guard.
        for(pos in positions.sortedBy{it==master.blockPos})if(level.getBlockState(pos).`is`(ModBlocks.SAMPLE_MACHINE.get())) {
            level.setBlock(pos,Blocks.AIR.defaultBlockState(),3)
        }
        if(stack!=null)level.addFreshEntity(ItemEntity(level,master.blockPos.x+.5,master.blockPos.y+.5,master.blockPos.z+.5,stack))
    }
}
