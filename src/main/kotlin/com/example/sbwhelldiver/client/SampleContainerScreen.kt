package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.menu.SampleContainerMenu
import com.example.sbwhelldiver.survival.SampleStorage
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

class SampleContainerScreen(menu:SampleContainerMenu,inventory:Inventory,title:Component):AbstractContainerScreen<SampleContainerMenu>(menu,inventory,title){
    init{imageWidth=244;imageHeight=206;inventoryLabelY=112}
    override fun init(){
        super.init()
        for(i in 0..2)for(action in 0..3){
            val label=when(action){0->Component.translatable("gui.sbw_helldiver.sample_deposit");1->Component.literal("−1");2->Component.literal("−64");else->Component.translatable("gui.sbw_helldiver.sample_take")}
            val x=when(action){0->91;1->136;2->166;else->200};val w=when(action){0->43;1->28;2->32;else->36}
            addRenderableWidget(Button.builder(label){minecraft?.gameMode?.handleInventoryButtonClick(menu.containerId,i*4+action)}.bounds(leftPos+x,topPos+29+i*27,w,20).build())
        }
    }
    override fun renderBg(g:GuiGraphics,partial:Float,x:Int,y:Int){
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+imageHeight,0xFF121C22.toInt())
        g.fill(leftPos,topPos,leftPos+imageWidth,topPos+3,0xFFE3C72E.toInt())
        for(i in 0..2){
            g.renderItem(ItemStack(SampleStorage.type(i)),leftPos+8,topPos+30+i*27)
            val count=SampleStorage.count(menu.container,i).toString()
            val shown=if(font.width(count)>60)count.take(7)+"…" else count
            g.drawString(font,shown,leftPos+27,topPos+35+i*27,0xFFF2EDDD.toInt(),false)
        }
        for(slot in menu.slots)if(slot.x>=0){
            g.fill(leftPos+slot.x-1,topPos+slot.y-1,leftPos+slot.x+17,topPos+slot.y+17,0xFF697277.toInt())
            g.fill(leftPos+slot.x,topPos+slot.y,leftPos+slot.x+16,topPos+slot.y+16,0xFF253139.toInt())
        }
    }
    override fun renderLabels(g:GuiGraphics,x:Int,y:Int){g.drawString(font,title,8,9,0xFFF2EDDD.toInt(),false);g.drawString(font,playerInventoryTitle,8,112,0xFFC5C5BC.toInt(),false)}
    override fun render(g:GuiGraphics,x:Int,y:Int,partial:Float){
        super.render(g,x,y,partial);renderTooltip(g,x,y)
        for(i in 0..2)if(x in leftPos+8..leftPos+87&&y in topPos+29+i*27..topPos+50+i*27)
            g.renderTooltip(font,Component.translatable("tooltip.sbw_helldiver.sample_count",SampleStorage.type(i).description,SampleStorage.count(menu.container,i).toString()),x,y)
    }
}
