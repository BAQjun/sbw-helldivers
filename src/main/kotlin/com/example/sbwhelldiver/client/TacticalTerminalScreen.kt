package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.init.ModSounds
import com.example.sbwhelldiver.network.*
import com.example.sbwhelldiver.stratagem.Stratagems
import com.example.sbwhelldiver.survival.*
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW

class TacticalTerminalScreen(private val state:TacticalTerminalOpenPacket):Screen(Component.translatable("block.sbw_helldiver.tactical_terminal")) {
    private var camera:ArmorStand?=null
    private var savedCamera:Entity?=null
    private var savedType=CameraType.FIRST_PERSON
    private var category:TerminalCategory?=null
    private var scroll=0
    private var held:String?=null
    private var heldTicks=0
    private var holdSound:SoundInstance?=null
    private var feedback:String?=null
    private var feedbackTicks=0
    private val widthUi=340;private val heightUi=156
    private val uiScale get():Float {
        val fov=(minecraft?.options?.fov()?.get()?:70).toDouble()
        return (height * 1.269 / (2.0 * .70 * kotlin.math.tan(Math.toRadians(fov/2))) / widthUi).toFloat().coerceIn(.35f,2.5f)
    }
    private val x0 get()=((width/uiScale-widthUi)/2).toInt()
    private val y0 get()=((height/uiScale-heightUi)/2).toInt()
    private fun block()=minecraft?.level?.getBlockEntity(state.pos) as? TacticalTerminalBlockEntity
    override fun init(){
        val mc=minecraft?:return;val be=block()?:return
        if(camera!=null)return
        savedCamera=mc.cameraEntity;savedType=mc.options.cameraType
        camera=ArmorStand(mc.level!!,0.0,0.0,0.0).also{it.isInvisible=true;it.isNoGravity=true}
        mc.options.cameraType=CameraType.FIRST_PERSON
        updateCamera(be);mc.cameraEntity=camera
    }
    private fun updateCamera(be:TacticalTerminalBlockEntity){
        val c=camera?:return;val dir=be.blockState.getValue(TacticalTerminalBlock.FACING);val right=dir.clockWise
        val center=Vec3(state.pos.x+.5+right.stepX*.5+dir.stepX*.171,state.pos.y+1.054,state.pos.z+.5+right.stepZ*.5+dir.stepZ*.171)
        val pos=center.add(dir.stepX*.495,.495,dir.stepZ*.495);val d=center.subtract(pos)
        val yaw=Math.toDegrees(kotlin.math.atan2(-d.x,d.z)).toFloat();val pitch=(-Math.toDegrees(kotlin.math.atan2(d.y,kotlin.math.sqrt(d.x*d.x+d.z*d.z)))).toFloat()
        c.moveTo(pos.x,pos.y-c.eyeHeight,pos.z,yaw,pitch);c.xo=c.x;c.yo=c.y;c.zo=c.z;c.yRotO=yaw;c.xRotO=pitch;c.yHeadRot=yaw;c.yHeadRotO=yaw;c.yBodyRot=yaw;c.yBodyRotO=yaw
    }
    override fun tick(){
        val mc=minecraft?:return;val be=block()
        if(be==null||!be.powered||mc.player?.isAlive!=true||mc.player!!.distanceToSqr(state.pos.x+.5,state.pos.y+.5,state.pos.z+.5)>36){onClose();return}
        updateCamera(be)
        if(feedbackTicks>0 && --feedbackTicks==0)feedback=null
        held?.let { id->
            heldTicks++
            if(heldTicks%5==0)send(1,id)
            val duration=TacticalTerminalHold.DURATION_TICKS.toInt()
            if(heldTicks==duration)stopHoldSound()
            if(heldTicks in duration..duration+10 && (heldTicks-duration)%2==0)send(2,id)
            if(heldTicks>=duration+10)cancel()
        }
    }
    override fun renderBackground(g:GuiGraphics){}
    override fun isPauseScreen()=false
    private fun entries()=TacticalTerminalOffers.all.filter{it.category==category}
    override fun render(g:GuiGraphics,mouseX:Int,mouseY:Int,partial:Float){
        val x=x0;val y=y0;val font=minecraft?.font?:return
        g.pose().pushPose();g.pose().scale(uiScale,uiScale,1f)
        g.fill(x,y,x+widthUi,y+heightUi,0xFA07131A.toInt());g.fill(x,y,x+widthUi,y+4,0xFFE2C122.toInt())
        g.drawString(font,Component.translatable("block.sbw_helldiver.tactical_terminal"),x+12,y+10,0xFFF3ECCE.toInt(),false)
        g.drawString(font,Component.translatable("gui.sbw_helldiver.destroyer_uses",DestroyerClient.callsRemaining),x+180,y+10,0xFFFFDD55.toInt(),false)
        if(category==null){
            drawRow(g,x+10,y+28,widthUi-20,26,"destroyer_call",Component.translatable("gui.sbw_helldiver.replenish"),TacticalTerminalOffers.destroyerCall,null,partial)
            TerminalCategory.entries.forEachIndexed{index,c->
                val yy=y+57+index*25;g.fill(x+10,yy,x+widthUi-10,yy+22,0xFF263239.toInt())
                g.drawString(font,Component.translatable("gui.sbw_helldiver.category.${c.name.lowercase()}"),x+20,yy+9,0xFFF0EEE0.toInt(),false)
                g.drawString(font,">",x+widthUi-26,yy+9,0xFFE5C62D.toInt(),false)
            }
        }else{
            g.drawString(font,Component.translatable("gui.sbw_helldiver.back"),x+12,y+28,0xFFE5C62D.toInt(),false)
            g.drawString(font,Component.translatable("gui.sbw_helldiver.category.${category!!.name.lowercase()}"),x+65,y+28,0xFFF0EEE0.toInt(),false)
            entries().drop(scroll).take(4).forEachIndexed{index,offer->
                val def=Stratagems.get(offer.id)?:return@forEachIndexed
                val item=BuiltInRegistries.ITEM.get(def.itemId)
                drawRow(g,x+10,y+38+index*29,widthUi-20,26,offer.id,Component.translatable(def.translation),offer.cost,ItemStack(item),partial)
            }
        }
        feedback?.let{g.pose().pushPose();g.pose().translate(0f,0f,400f);g.fill(x,y+heightUi-18,x+widthUi,y+heightUi,0xF007131A.toInt());g.drawString(font,Component.translatable(it),x+10,y+heightUi-13,if(it.endsWith("success"))0xFF99EEAA.toInt()else 0xFFFF7777.toInt(),false);g.pose().popPose()}
        g.pose().popPose()
    }
    private fun drawRow(g:GuiGraphics,x:Int,y:Int,w:Int,h:Int,id:String,label:Component,cost:SampleCost,icon:ItemStack?,partial:Float){
        val font=minecraft!!.font
        g.fill(x,y,x+w,y+h,0xFF263239.toInt())
        if(held==id){val progress=TacticalTerminalHold.maskProgress(heldTicks.toDouble()+partial);g.fill(x,y,x+(w*progress).toInt(),y+h,0x90FFFFFF.toInt())}
        if(icon!=null)g.renderItem(icon,x+4,y+6)
        val text=font.plainSubstrByWidth(label.string,145)
        g.drawString(font,text,x+(if(icon==null)8 else 24),y+9,0xFFF6F3E8.toInt(),false)
        val samples=listOf(ModItems.COMMON_SAMPLE.get() to cost.common,ModItems.RARE_SAMPLE.get() to cost.rare,ModItems.SUPER_SAMPLE.get() to cost.superSample)
        samples.forEachIndexed{index,(item,n)->if(n>0){val sx=x+w-114+index*38;g.renderItem(ItemStack(item),sx,y+6);g.drawString(font,n.toString(),sx+16,y+10,0xFFF3EEE0.toInt(),false)}}
    }
    override fun mouseClicked(mouseX:Double,mouseY:Double,button:Int):Boolean {
        if(button!=0)return super.mouseClicked(mouseX,mouseY,button)
        val x=mouseX/uiScale-x0;val y=mouseY/uiScale-y0
        if(x !in 10.0..(widthUi-10).toDouble())return true
        if(category==null){
            if(y in 28.0..54.0){start("destroyer_call");return true}
            for((i,c)in TerminalCategory.entries.withIndex())if(y>=57+i*25 && y<79+i*25){selectSound();category=c;scroll=0;return true}
        }else{
            if(y in 24.0..41.0){selectSound();cancel();category=null;scroll=0;return true}
            if(y>=38 && y<154){val index=((y-38)/29).toInt()+scroll;entries().getOrNull(index)?.let{start(it.id)}}
        }
        return true
    }
    override fun mouseReleased(x:Double,y:Double,button:Int):Boolean {if(button==0)cancel();return true}
    override fun mouseScrolled(x:Double,y:Double,vertical:Double):Boolean {if(category!=null){scroll=(scroll-if(vertical>0)1 else if(vertical<0)-1 else 0).coerceIn(0,(entries().size-4).coerceAtLeast(0));cancel()};return true}
    private fun start(id:String){cancel();selectSound();held=id;heldTicks=0;holdSound=uiSound("tactical_purchase_hold");send(0,id)}
    private fun uiSound(event:String):SoundInstance = SimpleSoundInstance.forUI(ModSounds.EVENTS.getValue(event).get(),1f).also{minecraft?.soundManager?.play(it)}
    private fun selectSound(){uiSound("tactical_select")}
    private fun stopHoldSound(){holdSound?.let{minecraft?.soundManager?.stop(it)};holdSound=null}
    private fun cancel(){stopHoldSound();held?.let{send(-1,it)};held=null;heldTicks=0}
    private fun send(action:Int,id:String=""){BeaconNetwork.CHANNEL.sendToServer(TacticalTerminalActionPacket(state.pos,state.token,action,id))}
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true};return super.keyPressed(key,scan,mods)}
    override fun onClose(){minecraft?.setScreen(null)}
    override fun removed(){
        cancel();send(-2)
        val mc=minecraft?:return;mc.cameraEntity=savedCamera?.takeIf{!it.isRemoved}?:mc.player;mc.options.cameraType=savedType;camera=null
    }
    companion object {
        fun result(p:TacticalTerminalResultPacket){val s=Minecraft.getInstance().screen as? TacticalTerminalScreen?:return;if(s.state.token!=p.token)return;s.cancel();if(p.key=="message.sbw_helldiver.terminal_success")s.uiSound("tactical_purchase_complete");s.feedback=p.key;s.feedbackTicks=60}
        fun open(p:TacticalTerminalOpenPacket){val mc=Minecraft.getInstance();if(mc.level?.getBlockEntity(p.pos) is TacticalTerminalBlockEntity)mc.setScreen(TacticalTerminalScreen(p))}}
}
