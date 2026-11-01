package com.example.sbwhelldiver.survival

import net.minecraft.core.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.energy.IEnergyStorage
import net.neoforged.neoforge.items.*

class SampleMachineBlockEntity(pos:BlockPos,state:BlockState):BlockEntity(MachineRegistry.MACHINE.get(),pos,state) {
    var root:BlockPos=pos
    var tearingDown=false
    var readyAt=0L
    var layoutVersion=MachineLayout.VERSION
    var energy=0;private set
    var progress=0;private set
    var duration=100;private set
    var workTicks=0L;private set
    var working=false;private set
    private var pending=ItemStack.EMPTY
    val machine get()=blockState.getValue(SampleMachineBlock.KIND)
    val facing get()=blockState.getValue(SampleMachineBlock.FACING)
    val capacity get()=energyCapacity()
    val inventory=object:ItemStackHandler(18) {override fun onContentsChanged(slot:Int){setChanged()}}
    fun master():SampleMachineBlockEntity? {
        if(tearingDown||isRemoved)return null
        if(root==blockPos)return this
        val l=level?:return null
        if(!l.hasChunkAt(root))return null
        val m=l.getBlockEntity(root) as? SampleMachineBlockEntity?:return null
        return m.takeIf{it.root==root && !it.tearingDown && it.machine==machine && it.facing==facing}
    }
    private val itemWrapper=object:IItemHandler {
        override fun getSlots()=9
        private fun index(slot:Int)=if(blockState.getValue(SampleMachineBlock.PORT)==MachineLayout.Port.OUTPUT)slot+9 else slot
        override fun getStackInSlot(slot:Int)=if(slot in 0..8)master()?.inventory?.getStackInSlot(index(slot))?:ItemStack.EMPTY else ItemStack.EMPTY
        override fun getSlotLimit(slot:Int)=64
        override fun isItemValid(slot:Int,stack:ItemStack)=slot in 0..8 && blockState.getValue(SampleMachineBlock.PORT)==MachineLayout.Port.INPUT && master()?.let{SampleRecipe.accepts(it.level!!,it.machine,stack)}==true
        override fun insertItem(slot:Int,stack:ItemStack,simulate:Boolean):ItemStack {
            if(!isItemValid(slot,stack))return stack
            return master()?.inventory?.insertItem(slot,stack,simulate)?:stack
        }
        override fun extractItem(slot:Int,amount:Int,simulate:Boolean):ItemStack {
            if(slot !in 0..8 || blockState.getValue(SampleMachineBlock.PORT)!=MachineLayout.Port.OUTPUT)return ItemStack.EMPTY
            return master()?.inventory?.extractItem(slot+9,amount,simulate)?:ItemStack.EMPTY
        }
    }
    private val energyWrapper=object:IEnergyStorage {
        override fun receiveEnergy(maxReceive:Int,simulate:Boolean):Int {
            val m=master()?:return 0;val n=minOf(maxReceive.coerceAtLeast(0),m.capacity-m.energy)
            if(!simulate&&n>0){m.energy+=n;m.setChanged()};return n
        }
        override fun extractEnergy(maxExtract:Int,simulate:Boolean)=0
        override fun getEnergyStored()=master()?.energy?:0
        override fun getMaxEnergyStored()=capacity
        override fun canExtract()=false
        override fun canReceive()=master()!=null
    }
    // The labelled port block is the connection point. Pipes may approach that
    // block from above, below or the side, as well as from the front panel.
    fun itemPort(side:Direction?):IItemHandler? = if(blockState.getValue(SampleMachineBlock.PORT) in listOf(MachineLayout.Port.INPUT,MachineLayout.Port.OUTPUT)&&master()!=null)itemWrapper else null
    fun energyPort(side:Direction?):IEnergyStorage? = if(blockState.getValue(SampleMachineBlock.PORT)==MachineLayout.Port.ENERGY&&master()!=null)energyWrapper else null
    fun insertFromHand(player:net.minecraft.world.entity.player.Player,hand:net.minecraft.world.InteractionHand):Boolean {
        if(blockState.getValue(SampleMachineBlock.PORT)!=MachineLayout.Port.INPUT)return false
        val stack=player.getItemInHand(hand)
        if(stack.isEmpty||!itemWrapper.isItemValid(0,stack))return false
        if(level?.isClientSide==true)return true
        val rest=ItemHandlerHelper.insertItemStacked(itemWrapper,stack.copy(),false)
        if(rest.count==stack.count)return false
        if(!player.isCreative)player.setItemInHand(hand,rest)
        return true
    }
    private fun outputFits(stack:ItemStack):Boolean {
        var left=stack.count
        for(i in 9..17){val s=inventory.getStackInSlot(i);if(s.isEmpty)left-=stack.maxStackSize else if(ItemStack.isSameItemSameComponents(s,stack))left-=s.maxStackSize-s.count}
        return left<=0
    }
    private fun insertOutput(stack:ItemStack) {
        var left=stack
        for(i in 9..17){left=inventory.insertItem(i,left,false);if(left.isEmpty)break}
        check(left.isEmpty)
    }
    fun serverTick() {
        val l=level?:return
        if(root!=blockPos) {
            if(l.gameTime%40L==0L && l.hasChunkAt(root) && master()==null)l.removeBlock(blockPos,false)
            return
        }
        if(layoutVersion<MachineLayout.VERSION && !MachineStructure.upgrade(this))return
        if(tearingDown || l.gameTime<readyAt)return
        if(l.gameTime%5L==0L)autoItems()
        val before=working;working=false
        if(pending.isEmpty && energy>=POWER_PER_TICK) {
            val recipes=SampleRecipe.forMachine(l,machine)
            for(r in recipes) {
                val slots=(0..8).filter{r.ingredient.test(inventory.getStackInSlot(it))}
                if(slots.sumOf{inventory.getStackInSlot(it).count}<r.count)continue
                // Reserve room for every possible roll before consuming the batch.
                if(r.outcomes.any{!outputFits(it.stack())})continue
                var count=r.count
                for(slot in slots){val n=minOf(count,inventory.getStackInSlot(slot).count);inventory.extractItem(slot,n,false);count-=n;if(count==0)break}
                pending=r.roll(l.random);duration=r.ticks;progress=0;setChanged();break
            }
        }
        if(!pending.isEmpty && outputFits(pending) && energy>=POWER_PER_TICK) {
            working=true;energy-=POWER_PER_TICK;progress++;workTicks++;setChanged()
            if(progress>=duration){insertOutput(pending);pending=ItemStack.EMPTY;progress=0}
        }
        if(before!=working || (working&&l.gameTime%10L==0L))sync()
    }
    private fun autoItems() {
        val l=level?:return
        fun neighbours(port:MachineLayout.Port):List<Pair<BlockPos,Direction>> {
            val anchor=blockPos.offset(MachineLayout.rotate(MachineLayout.portOffset(machine,port),facing))
            return (listOf(facing)+Direction.entries).distinct().map{anchor.relative(it) to it.opposite}.filter{(pos,_)->
                l.hasChunkAt(pos) && (l.getBlockEntity(pos) as? SampleMachineBlockEntity)?.root!=blockPos
            }
        }
        var budget=32
        for((inPos,side)in neighbours(MachineLayout.Port.INPUT)) {
            if(budget<=0)break
            val source=l.getCapability(Capabilities.ItemHandler.BLOCK,inPos,side)?:continue
            for(slot in 0 until source.slots) {
                val probe=source.extractItem(slot,budget,true)
                if(probe.isEmpty||!SampleRecipe.accepts(l,machine,probe))continue
                var accepted=probe.count
                var rest=probe
                for(i in 0..8){rest=inventory.insertItem(i,rest,true);if(rest.isEmpty)break}
                // Simulations across slots must account for their shared capacity only once.
                accepted-=rest.count
                if(accepted<=0)continue
                rest=source.extractItem(slot,accepted,false)
                val moved=rest.count
                for(i in 0..8){rest=inventory.insertItem(i,rest,false);if(rest.isEmpty)break}
                if(!rest.isEmpty){val returned=source.insertItem(slot,rest,false);if(!returned.isEmpty)drop(returned,inPos)}
                budget-=moved-rest.count;if(budget<=0)break
            }
        }
        val outPos=blockPos.offset(MachineLayout.rotate(MachineLayout.portOffset(machine,MachineLayout.Port.OUTPUT),facing)).relative(facing)
        if(!l.hasChunkAt(outPos))return
        val adjacent=neighbours(MachineLayout.Port.OUTPUT)
        val targets=adjacent.mapNotNull{(pos,side)->l.getCapability(Capabilities.ItemHandler.BLOCK,pos,side)}
        // A full or non-item block entity is never treated as empty air.
        val hasContainer=targets.isNotEmpty() || adjacent.any{l.getBlockEntity(it.first)!=null} || !l.getBlockState(outPos).getCollisionShape(l,outPos).isEmpty
        for(slot in 9..17) {
            val stack=inventory.getStackInSlot(slot);if(stack.isEmpty)continue
            if(targets.isNotEmpty()) {
                val copy=stack.copyWithCount(minOf(32,stack.count));var rest=copy
                for(target in targets){rest=ItemHandlerHelper.insertItemStacked(target,rest,false);if(rest.isEmpty)break}
                inventory.extractItem(slot,copy.count-rest.count,false)
            }else if(!hasContainer)drop(inventory.extractItem(slot,minOf(32,stack.count),false),outPos)
            break
        }
    }
    private fun drop(stack:ItemStack,pos:BlockPos){if(stack.isEmpty)return;val l=level?:return;l.addFreshEntity(ItemEntity(l,pos.x+.5,pos.y+.5,pos.z+.5,stack).also{it.setDeltaMovement(facing.stepX*.08,.03,facing.stepZ*.08)})}
    fun contents(r:HolderLookup.Provider=checkNotNull(level).registryAccess()):CompoundTag=CompoundTag().also{n->
        n.put("Inventory",inventory.serializeNBT(r));n.putInt("Energy",energy);n.putInt("Progress",progress);n.putInt("Duration",duration);n.putLong("WorkTicks",workTicks)
        if(!pending.isEmpty)n.put("Pending",pending.save(r))
    }
    fun restoreContents(n:CompoundTag) {
        val l=level?:return;inventory.deserializeNBT(l.registryAccess(),n.getCompound("Inventory"));energy=n.getInt("Energy").coerceIn(0,capacity)
        pending=ItemStack.parseOptional(l.registryAccess(),n.getCompound("Pending"));duration=n.getInt("Duration").coerceAtLeast(1);progress=n.getInt("Progress").coerceIn(0,duration-1);workTicks=n.getLong("WorkTicks");setChanged()
    }
    override fun saveAdditional(n:CompoundTag,r:HolderLookup.Provider){super.saveAdditional(n,r);n.putLong("Root",root.asLong());n.putInt("LayoutVersion",layoutVersion);n.putLong("ReadyAt",readyAt);if(root==blockPos)n.merge(contents(r));n.putBoolean("Working",working)}
    override fun loadAdditional(n:CompoundTag,r:HolderLookup.Provider){super.loadAdditional(n,r);root=if(n.contains("Root"))BlockPos.of(n.getLong("Root"))else blockPos;layoutVersion=n.getInt("LayoutVersion");readyAt=n.getLong("ReadyAt");inventory.deserializeNBT(r,n.getCompound("Inventory"));energy=n.getInt("Energy").coerceIn(0,capacity);pending=ItemStack.parseOptional(r,n.getCompound("Pending"));duration=n.getInt("Duration").coerceAtLeast(1);progress=n.getInt("Progress").coerceIn(0,duration-1);workTicks=n.getLong("WorkTicks");working=n.getBoolean("Working")}
    fun sync(){val l=level?:return;l.sendBlockUpdated(blockPos,blockState,blockState,3)}
    override fun getUpdateTag(r:HolderLookup.Provider)=saveWithoutMetadata(r)
    override fun getUpdatePacket()=ClientboundBlockEntityDataPacket.create(this)
    fun bounds():AABB=MachineLayout.containerBounds(machine).let{MachineLayout.rotate(it,facing)}.move(blockPos.x+.5,blockPos.y.toDouble(),blockPos.z+.5)
    companion object {
        const val POWER_PER_TICK=1500
        fun energyCapacity():Int {
            val item=com.atsuishio.superbwarfare.init.ModItems.LARGE_BATTERY_PACK.get()
            return (item as com.atsuishio.superbwarfare.item.EnergyStorageItem).getMaxEnergy(ItemStack(item))
        }
    }
}
