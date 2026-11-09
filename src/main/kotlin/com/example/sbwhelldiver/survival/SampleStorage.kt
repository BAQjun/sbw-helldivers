package com.example.sbwhelldiver.survival

import com.example.sbwhelldiver.init.ModItems
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.math.BigInteger
import java.util.UUID

/** Counts are decimal integers, independent of stack limits and fixed-width overflow. */
object SampleStorage {
    private const val KEY="SampleStorage"
    private val keys=listOf("Common","Rare","Super")
    fun type(index:Int):Item=when(index){0->ModItems.COMMON_SAMPLE.get();1->ModItems.RARE_SAMPLE.get();else->ModItems.SUPER_SAMPLE.get()}
    fun index(item:Item)=(0..2).firstOrNull{type(it)==item}?:-1
    fun isContainer(stack:ItemStack)=stack.item is SampleContainerItem
    private fun data(stack:ItemStack)=stack.tag?.copy()?:CompoundTag()
    private fun change(stack:ItemStack,update:(CompoundTag)->Unit){
        val root=data(stack);val n=root.getCompound(KEY);update(n);root.put(KEY,n);stack.tag=root
    }
    fun count(stack:ItemStack,index:Int):BigInteger {
        if(!isContainer(stack)||index !in 0..2)return BigInteger.ZERO
        val raw=data(stack).getCompound(KEY).getString(keys[index])
        return raw.takeIf{it.isNotEmpty()&&it.all{c->c in '0'..'9'}}?.toBigIntegerOrNull()?:BigInteger.ZERO
    }
    fun add(stack:ItemStack,index:Int,amount:BigInteger){require(index in 0..2 && amount.signum()>=0);change(stack){it.putString(keys[index],(count(stack,index)+amount).toString())}}
    fun take(stack:ItemStack,index:Int,amount:Int):Int {
        if(index !in 0..2||amount<=0)return 0
        val before=count(stack,index);val n=before.min(BigInteger.valueOf(amount.toLong())).toInt()
        if(n>0)change(stack){it.putString(keys[index],(before-BigInteger.valueOf(n.toLong())).toString())}
        return n
    }
    fun identity(stack:ItemStack):UUID {
        val n=data(stack).getCompound(KEY)
        if(n.hasUUID("Id"))return n.getUUID("Id")
        return UUID.randomUUID().also{id->change(stack){it.putUUID("Id",id)}}
    }
    fun matches(stack:ItemStack,id:UUID)=isContainer(stack)&&data(stack).getCompound(KEY).let{it.hasUUID("Id")&&it.getUUID("Id")==id}
    fun automatic(stack:ItemStack)=data(stack).getCompound(KEY).getBoolean("Automatic")
    fun toggle(stack:ItemStack):Boolean {val enabled=!automatic(stack);change(stack){it.putBoolean("Automatic",enabled)};return enabled}
    fun carried(player:Player)=(0 until player.inventory.containerSize).map{player.inventory.getItem(it)}.filter(::isContainer)
    fun collect(player:Player,container:ItemStack):Boolean {
        var changed=false
        for(slot in 0 until player.inventory.containerSize){
            val stack=player.inventory.getItem(slot);val index=index(stack.item)
            if(!stack.isEmpty&&index>=0){add(container,index,BigInteger.valueOf(stack.count.toLong()));player.inventory.setItem(slot,ItemStack.EMPTY);changed=true}
        }
        if(changed)player.inventory.setChanged()
        return changed
    }
    fun deposit(player:Player,container:ItemStack,index:Int){
        for(slot in 0 until player.inventory.containerSize){val stack=player.inventory.getItem(slot)
            if(stack.`is`(type(index))){add(container,index,BigInteger.valueOf(stack.count.toLong()));player.inventory.setItem(slot,ItemStack.EMPTY)}}
        player.inventory.setChanged()
    }
    fun withdraw(player:Player,container:ItemStack,index:Int,limit:Int){
        var remaining=count(container,index).min(BigInteger.valueOf(limit.toLong())).toInt()
        // Fill actual inventory slots; never drop an unlimited quantity on the ground.
        for(slot in 0..35){
            if(remaining<=0)break
            val current=player.inventory.getItem(slot)
            val room=if(current.isEmpty)64 else if(current.`is`(type(index))&&ItemStack.isSameItemSameTags(current,ItemStack(type(index))))current.maxStackSize-current.count else 0
            val n=minOf(room,remaining);if(n<=0)continue
            if(current.isEmpty)player.inventory.setItem(slot,ItemStack(type(index),n))else current.grow(n)
            check(take(container,index,n)==n);remaining-=n
        }
        player.inventory.setChanged()
    }
    fun total(player:Player,index:Int):BigInteger {
        val loose=(0 until player.inventory.containerSize).sumOf{player.inventory.getItem(it).let{s->if(s.`is`(type(index)))s.count else 0}}
        return carried(player).fold(BigInteger.valueOf(loose.toLong())){sum,s->sum+count(s,index)}
    }
    /** One server-thread transaction after validating all currencies and offer limits. */
    fun consume(player:Player,index:Int,amount:Int){
        require(total(player,index)>=BigInteger.valueOf(amount.toLong()))
        var left=amount
        for(slot in 0 until player.inventory.containerSize){val stack=player.inventory.getItem(slot);if(stack.`is`(type(index))){val n=minOf(left,stack.count);stack.shrink(n);left-=n}}
        for(stack in carried(player)){left-=take(stack,index,left);if(left==0)break}
        check(left==0);player.inventory.setChanged()
    }
    fun receiver(machine:SampleMachineBlockEntity):Pair<ServerPlayer,ItemStack>? {
        val owner=machine.owner?:return null;val binding=machine.boundContainer?:return null
        val player=machine.level?.server?.playerList?.getPlayer(owner)?:return null
        return carried(player).firstOrNull{matches(it,binding)}?.let{player to it}
    }
    fun send(machine:SampleMachineBlockEntity,stack:ItemStack):Boolean {
        val index=index(stack.item);if(index<0||stack.isEmpty)return false
        val (player,container)=receiver(machine)?:return false
        add(container,index,BigInteger.valueOf(stack.count.toLong()));player.inventory.setChanged();return true
    }
}
