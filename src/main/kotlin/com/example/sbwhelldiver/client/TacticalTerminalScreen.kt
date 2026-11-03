package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.init.ModSounds
import com.example.sbwhelldiver.network.*
import com.example.sbwhelldiver.survival.*
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.client.resources.sounds.SoundInstance
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import org.lwjgl.glfw.GLFW

class TacticalTerminalScreen(private val state:TacticalTerminalOpenPacket):Screen(Component.translatable("block.sbw_helldiver.tactical_terminal")) {
    private var camera:ArmorStand?=null
    private var savedCamera:Entity?=null
    private var savedType=CameraType.FIRST_PERSON
    internal var category:TerminalCategory?=null
    internal var scroll=0
    internal var held:String?=null
    internal var heldTicks=0
    private var holdSound:SoundInstance?=null
    internal var feedback:String?=null
    private var feedbackTicks=0
    internal val terminalPos get()=state.pos
    internal var pointer:Pair<Double,Double>?=null
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
        val c=camera?:return
        val center=TacticalTerminalDisplay.center(be)
        val pos=center.add(TacticalTerminalDisplay.normal(be).scale(.70));val d=center.subtract(pos)
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
    // Capture input only; the block renderer draws the menu on the actual glass.
    override fun render(g:GuiGraphics,mouseX:Int,mouseY:Int,partial:Float){
        pointer=block()?.let{TacticalTerminalDisplay.pointer(it,mouseX.toDouble(),mouseY.toDouble())}
    }
    override fun mouseClicked(mouseX:Double,mouseY:Double,button:Int):Boolean {
        if(button!=0)return super.mouseClicked(mouseX,mouseY,button)
        val point=block()?.let{TacticalTerminalDisplay.pointer(it,mouseX,mouseY)}?:return true
        val (x,y)=point
        if(x !in 10.0..330.0)return true
        if(category==null){
            if(y in 28.0..54.0){start("destroyer_call");return true}
            for((i,c)in TerminalCategory.entries.withIndex())if(y>=57+i*25 && y<79+i*25){selectSound();category=c;scroll=0;return true}
        }else{
            if(y in 24.0..37.0){selectSound();cancel();category=null;scroll=0;return true}
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
