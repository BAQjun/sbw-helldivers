package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.network.*
import com.example.sbwhelldiver.survival.EquipmentDeploymentBeaconEntity
import net.minecraft.client.Minecraft
import net.minecraft.client.CameraType
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.decoration.ArmorStand
import org.lwjgl.glfw.GLFW

/** Input capture only. The menu itself is rendered on the terminal's real glass. */
class EquipmentTerminalScreen(var state:EquipmentTerminalStatePacket):Screen(Component.translatable("item.sbw_helldiver.machine_deployment_beacon_stratagem")) {
    private var savedCamera:Entity?=null
    private var savedType=CameraType.FIRST_PERSON
    private var camera:ArmorStand?=null
    private var closingFromServer=false
    fun beacon()=(minecraft?.level?.getEntity(state.entityId) as? EquipmentDeploymentBeaconEntity)?.takeIf{it.uuid==state.entityUuid}
    override fun init() {
        val mc=minecraft?:return;val b=beacon()?:return
        if(camera!=null)return
        savedCamera=mc.cameraEntity;savedType=mc.options.cameraType
        camera=ArmorStand(mc.level!!,0.0,0.0,0.0).also{it.isInvisible=true;it.isNoGravity=true}
        mc.options.cameraType=CameraType.FIRST_PERSON
        updateCamera(b);mc.cameraEntity=camera
    }
    private fun updateCamera(b:EquipmentDeploymentBeaconEntity) {
        val c=camera?:return;val center=b.screenCenter();val pos=center.add(b.screenNormal().scale(.48))
        // Camera#getEyePosition adds the stand eye height; keep the optical centre at pos.
        val delta=center.subtract(pos);val yaw=Math.toDegrees(kotlin.math.atan2(-delta.x,delta.z)).toFloat()
        val pitch=(-Math.toDegrees(kotlin.math.atan2(delta.y,kotlin.math.sqrt(delta.x*delta.x+delta.z*delta.z)))).toFloat()
        c.moveTo(pos.x,pos.y-c.eyeHeight,pos.z,yaw,pitch);c.xo=c.x;c.yo=c.y;c.zo=c.z;c.yRotO=yaw;c.xRotO=pitch
        c.yHeadRot=yaw;c.yHeadRotO=yaw;c.yBodyRot=yaw;c.yBodyRotO=yaw
    }
    override fun tick() {
        val b=beacon();val mc=minecraft?:return
        if(b==null||b.phase!=EquipmentDeploymentBeaconEntity.ACTIVE||mc.player?.isAlive!=true||mc.player!!.distanceToSqr(b)>36){onClose();return}
        updateCamera(b)
    }
    override fun render(g:GuiGraphics,x:Int,y:Int,partial:Float) {}
    override fun renderBackground(g:GuiGraphics) {}
    override fun isPauseScreen()=false
    override fun keyPressed(key:Int,scan:Int,mods:Int):Boolean {
        if(key==GLFW.GLFW_KEY_ESCAPE){onClose();return true}
        val mc=minecraft?:return true
        val dir=when {
            key==GLFW.GLFW_KEY_UP||mc.options.keyUp.matches(key,scan)->0
            key==GLFW.GLFW_KEY_RIGHT||mc.options.keyRight.matches(key,scan)->1
            key==GLFW.GLFW_KEY_DOWN||mc.options.keyDown.matches(key,scan)->2
            key==GLFW.GLFW_KEY_LEFT||mc.options.keyLeft.matches(key,scan)->3
            else->return true
        }
        send(dir);return true
    }
    private fun send(direction:Int){BeaconNetwork.CHANNEL.sendToServer(EquipmentTerminalInputPacket(state.entityId,state.entityUuid,state.token,direction))}
    override fun onClose() {minecraft?.setScreen(null)}
    override fun removed() {
        val mc=minecraft?:return
        if(!closingFromServer)send(-1)
        mc.cameraEntity=savedCamera?.takeIf{!it.isRemoved}?:mc.player;mc.options.cameraType=savedType
        camera=null
        mc.options.keyUp.isDown=false;mc.options.keyDown.isDown=false;mc.options.keyLeft.isDown=false;mc.options.keyRight.isDown=false
    }
    companion object {
        fun receive(p:EquipmentTerminalStatePacket) {
            val mc=Minecraft.getInstance();val current=mc.screen as? EquipmentTerminalScreen
            if(!p.open){
                if(current?.state?.entityUuid==p.entityUuid&&current.state.token==p.token){current.closingFromServer=true;mc.setScreen(null)}
                return
            }
            if(current?.state?.entityUuid==p.entityUuid&&current.state.token==p.token)current.state=p
            else if(mc.level?.getEntity(p.entityId)?.uuid==p.entityUuid)mc.setScreen(EquipmentTerminalScreen(p))
        }
    }
}

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid=com.example.sbwhelldiver.MOD_ID,value=[net.minecraftforge.api.distmarker.Dist.CLIENT])
object EquipmentTerminalView {
    @net.minecraftforge.eventbus.api.SubscribeEvent fun player(e:net.minecraftforge.client.event.RenderPlayerEvent.Pre){
        val mc=Minecraft.getInstance()
        if((mc.screen is EquipmentTerminalScreen || mc.screen is TacticalTerminalScreen) && e.entity===mc.player)e.isCanceled=true
    }
    @net.minecraftforge.eventbus.api.SubscribeEvent fun hand(e:net.minecraftforge.client.event.RenderHandEvent){if(Minecraft.getInstance().screen is EquipmentTerminalScreen || Minecraft.getInstance().screen is TacticalTerminalScreen)e.isCanceled=true}
    @net.minecraftforge.eventbus.api.SubscribeEvent fun overlay(e:net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre){
        if((Minecraft.getInstance().screen is EquipmentTerminalScreen || Minecraft.getInstance().screen is TacticalTerminalScreen) && (e.overlay.id().namespace in setOf("superbwarfare","tacz")||e.overlay.id()==net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.id()))e.isCanceled=true
    }
}
