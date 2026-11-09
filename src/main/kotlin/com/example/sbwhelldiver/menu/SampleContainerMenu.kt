package com.example.sbwhelldiver.menu

import com.example.sbwhelldiver.init.ModMenus
import com.example.sbwhelldiver.survival.SampleStorage
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.*
import net.minecraft.world.item.ItemStack

class SampleContainerMenu(id:Int,val inventory:Inventory,val hand:InteractionHand):AbstractContainerMenu(ModMenus.SAMPLE_CONTAINER.get(),id){
    private val sourceSlot=if(hand==InteractionHand.MAIN_HAND)inventory.selected else 40
    private val opened=inventory.getItem(sourceSlot)
    val container get()=inventory.getItem(sourceSlot)
    init {
        for(row in 0..2)for(col in 0..8)addSlot(playerSlot(9+row*9+col,8+col*18,124+row*18))
        for(col in 0..8)addSlot(playerSlot(col,8+col*18,182))
        // Also synchronise the off-hand item's custom data through the vanilla menu protocol.
        if(sourceSlot==40)addSlot(playerSlot(40,-1000,-1000))
    }
    private fun playerSlot(index:Int,x:Int,y:Int)=object:Slot(inventory,index,x,y){
        override fun mayPickup(player:Player)=index!=sourceSlot
        override fun mayPlace(stack:ItemStack)=index!=sourceSlot
    }
    override fun stillValid(player:Player)=player.isAlive&&container===opened&&SampleStorage.isContainer(container)
    override fun clicked(slotId:Int,button:Int,type:ClickType,player:Player){
        if(type==ClickType.SWAP&&button==sourceSlot)return
        super.clicked(slotId,button,type,player)
    }
    override fun clickMenuButton(player:Player,id:Int):Boolean {
        if(player.level().isClientSide||!stillValid(player)||id !in 0..11)return false
        val index=id/4
        when(id%4){0->SampleStorage.deposit(player,container,index);1->SampleStorage.withdraw(player,container,index,1);2->SampleStorage.withdraw(player,container,index,64);3->SampleStorage.withdraw(player,container,index,2304)}
        broadcastChanges();return true
    }
    override fun quickMoveStack(player:Player,index:Int):ItemStack {
        if(player.level().isClientSide||!stillValid(player))return ItemStack.EMPTY
        val slot=slots.getOrNull(index)?:return ItemStack.EMPTY
        if(!slot.mayPickup(player))return ItemStack.EMPTY
        val stack=slot.item;val sample=SampleStorage.index(stack.item)
        if(sample<0||stack.isEmpty)return ItemStack.EMPTY
        val before=stack.copy();SampleStorage.deposit(player,container,sample);broadcastChanges();return before
    }
}
