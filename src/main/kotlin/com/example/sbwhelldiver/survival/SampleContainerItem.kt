package com.example.sbwhelldiver.survival

import com.example.sbwhelldiver.menu.SampleContainerMenu
import com.example.sbwhelldiver.item.ItemSource
import com.example.sbwhelldiver.item.StratagemBackpacks
import com.example.sbwhelldiver.item.UtilitySlot
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.*
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.*
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level

class SampleContainerItem(properties:Properties):Item(properties) {
    override fun initializeClient(consumer:java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions>){
        consumer.accept(object:net.neoforged.neoforge.client.extensions.common.IClientItemExtensions {
            override fun getCustomRenderer()=com.example.sbwhelldiver.client.SampleContainerItemRenderer.INSTANCE
        })
    }
    override fun use(level:Level,player:Player,hand:InteractionHand):InteractionResultHolder<ItemStack> {
        val stack=player.getItemInHand(hand)
        if(player is ServerPlayer){
            if(player.isShiftKeyDown){
                SampleStorage.identity(stack)
                toggle(player,stack)
            }else open(player,ItemSource.of(hand))
        }
        return InteractionResultHolder.sidedSuccess(stack,level.isClientSide)
    }
    override fun useOn(context:UseOnContext):InteractionResult {
        val player=context.player?:return InteractionResult.PASS
        if(!player.isShiftKeyDown)return use(context.level,player,context.hand).result
        if(player is ServerPlayer){
            val machine=(context.level.getBlockEntity(context.clickedPos) as? SampleMachineBlockEntity)?.master()
            if(machine!=null){
                if(machine.owner!=player.uuid){player.displayClientMessage(Component.translatable("message.sbw_helldiver.sample_owner"),true);return InteractionResult.FAIL}
                val id=SampleStorage.identity(context.itemInHand)
                machine.boundContainer=if(machine.boundContainer==id)null else id;machine.setChanged()
                player.displayClientMessage(Component.translatable(if(machine.boundContainer==null)"message.sbw_helldiver.sample_unbound" else "message.sbw_helldiver.sample_bound"),true)
            }else toggle(player,context.itemInHand)
        }
        return InteractionResult.sidedSuccess(context.level.isClientSide)
    }
    fun open(player: ServerPlayer, source: ItemSource) {
        val stack = source.stack(player, UtilitySlot.SAMPLES)
        if (stack.item !== this) return
        SampleStorage.identity(stack)
        player.openMenu(SimpleMenuProvider({ id, inventory, _ ->
            SampleContainerMenu(id, inventory, source)
        }, stack.hoverName)) { it.writeEnum(source) }
    }

    private fun toggle(player:Player,stack:ItemStack){
        val on=SampleStorage.toggle(stack)
        player.displayClientMessage(Component.translatable(if(on)"message.sbw_helldiver.sample_auto_on" else "message.sbw_helldiver.sample_auto_off"),true)
        if(on)SampleStorage.collect(player,stack)
    }
    override fun inventoryTick(stack:ItemStack,level:Level,entity:Entity,slot:Int,selected:Boolean){
        if(entity is ServerPlayer && level.gameTime % 10L == 0L) collectAutomatically(entity, stack)
    }
    internal fun collectAutomatically(player: ServerPlayer, stack: ItemStack) {
        if (SampleStorage.automatic(stack) && (player.containerMenu as? SampleContainerMenu)?.container !== stack)
            SampleStorage.collect(player, stack)
    }
    override fun appendHoverText(stack:ItemStack,context:TooltipContext,lines:MutableList<Component>,flag:TooltipFlag){
        for(i in 0..2)lines.add(Component.translatable("tooltip.sbw_helldiver.sample_count",SampleStorage.type(i).description,SampleStorage.count(stack,i).toString()))
        lines.add(Component.translatable("tooltip.sbw_helldiver.sample_controls"))
        if (StratagemBackpacks.usesCurios)
            lines.add(Component.translatable("tooltip.sbw_helldiver.sample_curio", Component.keybind("key.sbw_helldiver.sample_container")))
    }
}
