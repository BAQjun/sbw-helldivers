package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.HmgEmplacementEntity
import com.example.sbwhelldiver.network.BeaconNetwork
import com.example.sbwhelldiver.network.HmgInputPacket
import com.atsuishio.superbwarfare.client.animation.AnimationPlayType
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent
import net.neoforged.neoforge.client.event.RenderHandEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import net.neoforged.neoforge.client.gui.VanillaGuiLayers
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import org.joml.Vector4f
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.lwjgl.glfw.GLFW
import java.util.WeakHashMap
import kotlin.math.*

@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object HmgClient {
    private class Effects { var serial=0;var feeding=false;var caseSerial=-1;var mountPose=0 }
    private val states=WeakHashMap<HmgEmplacementEntity,Effects>()
    private var previous: HmgEmplacementEntity?=null
    private var projectedId=-1
    private var projectedX=0.0
    private var projectedY=0.0
    fun mounted()=Minecraft.getInstance().player?.vehicle as? HmgEmplacementEntity
    fun aiming(): Boolean {
        val mc=Minecraft.getInstance()
        return mounted()!=null && mc.screen==null && ((GLFW.glfwGetMouseButton(mc.window.window,GLFW.GLFW_MOUSE_BUTTON_RIGHT)==GLFW.GLFW_PRESS))
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    fun interaction(event: InputEvent.InteractionKeyMappingTriggered) {
        if(mounted()==null)return
        event.isCanceled=true;event.setSwingHand(false)
    }
    @SubscribeEvent fun hand(event: RenderHandEvent) { if(mounted()!=null)event.isCanceled=true }
    @SubscribeEvent fun fov(event: ViewportEvent.ComputeFov) {
        if(aiming())event.fov=Math.toDegrees(2.0*atan(tan(Math.toRadians(event.fov)*.5)/(mounted()?.aimZoom ?: 1.5)))
    }
    @SubscribeEvent fun tick(event: net.neoforged.neoforge.client.event.ClientTickEvent.Post) {
        val mc=Minecraft.getInstance();val pod=mounted()
        if(pod!=null) {
            val player=mc.player ?: return
            val fire=mc.screen==null && ((mc.mouseHandler.isMouseGrabbed && GLFW.glfwGetMouseButton(mc.window.window,GLFW.GLFW_MOUSE_BUTTON_LEFT)==GLFW.GLFW_PRESS))
            val camera=pod.getCameraPosition(1f,player,false,mc.options.cameraType.isFirstPerson)
            // The optical camera follows the actual sight. Mouse input remains an
            // independent desired direction so mechanical lag cannot lock turning.
            val aimDirection=if(pod is com.example.sbwhelldiver.entity.GrenadierBattlementEntity && aiming())player.getViewVector(1f)
                else pod.cameraAimDirection(1f,player,mc.options.cameraType.isFirstPerson)
            val target=traceAim(pod,camera,aimDirection)
            val angles=pod.aimAnglesAt(target)
            BeaconNetwork.CHANNEL.sendToServer(HmgInputPacket(pod.id,angles.x,angles.y,fire,target))
            player.yBodyRot=pod.weaponYaw
            // No hand use, breaking or repeated interaction packets while operating.
            mc.options.keyAttack.isDown=false;mc.options.keyUse.isDown=false
        } else if(previous!=null && previous?.isRemoved==false && mc.level!=null) {
            mc.options.keyAttack.isDown=false;mc.options.keyUse.isDown=false
        }
        previous=pod
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    fun overlay(event: RenderGuiLayerEvent.Pre) {
        if(mounted()==null)return
        val id=event.name
        if(id==VanillaGuiLayers.CROSSHAIR || id.namespace=="superbwarfare" || id.namespace=="tacz")event.isCanceled=true
    }
    @SubscribeEvent fun hud(event: net.neoforged.neoforge.client.event.RenderGuiEvent.Post) {
        val pod=mounted() ?: return
        val mc=Minecraft.getInstance();if(mc.options.hideGui)return
        val g=event.guiGraphics;val w=mc.window.guiScaledWidth;val h=mc.window.guiScaledHeight
        g.drawString(mc.font,Component.translatable("hud.sbw_helldiver.hmg_ammo",pod.ammoRemaining,pod.ammunitionCapacity),12,h-42,0xfff4e9ca.toInt(),true)
        if(aiming() && (pod !is com.example.sbwhelldiver.entity.GrenadierBattlementEntity || !pod.throughSight()))reticle(g,pod,w,h)
    }
    /** Project the gameplay firing solution with this world frame; never the mesh forward. */
    @SubscribeEvent fun projectAim(event: RenderLevelStageEvent) {
        if(event.stage!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return
        projectedId=-1
        val pod=mounted() ?: return
        if(!aiming())return
        val partial=event.partialTick.getGameTimeDeltaPartialTick(false)
        val point=pod.effectiveAimTarget(pod.nextBarrel,partial)
        val delta=point.subtract(event.camera.position)
        val clip=WorldHudProjection.matrix(event).transform(Vector4f(delta.x.toFloat(),delta.y.toFloat(),delta.z.toFloat(),1f))
        val mc=Minecraft.getInstance()
        val denominator=maxOf(.00001,abs(clip.w.toDouble()))
        projectedX=clip.x/denominator*mc.window.guiScaledWidth*.5
        projectedY=-clip.y/denominator*mc.window.guiScaledHeight*.5
        if(clip.w<=0f) {
            val length=hypot(projectedX,projectedY)
            if(length<.001) { projectedX=1e6;projectedY=0.0 }
            else {projectedX=projectedX/length*1e6;projectedY=projectedY/length*1e6}
        }
        projectedId=pod.id

    }
    private fun traceAim(pod:HmgEmplacementEntity,start:Vec3,direction:Vec3):Vec3 =
        com.example.sbwhelldiver.entity.EmplacementAimTrace.trace(pod,start,direction)
    private fun reticle(g: GuiGraphics,pod: HmgEmplacementEntity,w: Int,h: Int) {
        val centerX=w/2;val centerY=h/2
        val radius=minOf(w*.16,h*.16,64.0)
        var dx=if(projectedId==pod.id)projectedX else 0.0
        var dy=if(projectedId==pod.id)projectedY else 0.0
        val length=hypot(dx,dy)
        if(length>radius) { dx=dx/length*radius;dy=dy/length*radius }
        val spread=(16.0+minOf(20.0,hypot(dx,dy)*.33)).roundToInt()
        val white=0xfff5f5ee.toInt()
        fun bar(x:Int,y:Int,X:Int,Y:Int) { g.fill(x,y,X,Y,white) }
        bar(centerX-spread-10,centerY,centerX-spread,centerY+1)
        bar(centerX+spread,centerY,centerX+spread+10,centerY+1)
        bar(centerX,centerY,centerX+1,centerY+1)
        val x=centerX+dx.roundToInt();val y=centerY+dy.roundToInt()
        ring(g,x,y,white)
    }
    private fun ring(g:GuiGraphics,x:Int,y:Int,color:Int) {
        // Seven pixels across, one pixel stroke, fully hollow. No inner fill or shadow ring.
        for(dy in -3..3) {
            if(abs(dy)==3)g.fill(x-1,y+dy,x+2,y+dy+1,color)
            else {
                val dx=if(abs(dy)==2)2 else 3
                g.fill(x-dx,y+dy,x-dx+1,y+dy+1,color)
                g.fill(x+dx,y+dy,x+dx+1,y+dy+1,color)
            }
        }
    }
    fun tickEffects(pod:HmgEmplacementEntity) {
        val state=states.getOrPut(pod){Effects().also{it.serial=pod.shotSerial}}
        if(pod is com.example.sbwhelldiver.entity.GrenadierBattlementEntity) {
            val ctx=pod.getAnimationInstance()?.context
            if(pod.phase!=AbstractHellpodEntity.Phase.ACTIVE) {
                ctx?.stopAnimation("animation.grenadier.fire",0);state.serial=pod.shotSerial;return
            }
            if(pod.shotSerial!=state.serial) {
                ctx?.let {AnimationTiming.play(it,"animation.grenadier.fire",AnimationPlayType.PLAY_ONCE_STOP,pod.reloadAge)}
                SoundTimeline.schedule(pod,"animation.grenadier.fire_shot",pod.reloadAge,catchUp=true)
                val tip=pod.muzzlePosition(0);val axis=pod.firingDirection(0)
                repeat(3){pod.level().addParticle(ParticleTypes.SMOKE,tip.x,tip.y,tip.z,axis.x*.1,axis.y*.1+.02,axis.z*.1)}
                Minecraft.getInstance().particleEngine.createParticle(ParticleTypes.FLAME,tip.x,tip.y,tip.z,axis.x*.05,axis.y*.05,axis.z*.05)?.setLifetime(2)
            }
            state.serial=pod.shotSerial;return
        }
        if(pod is com.example.sbwhelldiver.entity.AntiTankEmplacementEntity) {
            val ctx=pod.getAnimationInstance()?.context
            if(pod.phase!=AbstractHellpodEntity.Phase.ACTIVE) {
                ctx?.stopAnimation("animation.at.reload",0);ctx?.stopAnimation("animation.at.fire_recoil",0)
                state.serial=pod.shotSerial;state.feeding=false;return
            }
            if(pod.shotSerial!=state.serial) {
                ctx?.let { AnimationTiming.play(it,"animation.at.fire_recoil",AnimationPlayType.PLAY_ONCE_STOP,pod.reloadAge) }
                SoundTimeline.schedule(pod,"animation.at.fire_shot",pod.reloadAge,catchUp=true)
                val tip=pod.muzzlePosition(0);val axis=pod.firingDirection(0)
                repeat(8){pod.level().addParticle(ParticleTypes.FLAME,tip.x,tip.y,tip.z,axis.x*.18,axis.y*.18,axis.z*.18)}
                repeat(6){pod.level().addParticle(ParticleTypes.SMOKE,tip.x,tip.y,tip.z,axis.x*.12,axis.y*.12+.03,axis.z*.12)}
            }
            if(pod.isMechanicallyFeeding&&!state.feeding) {
                ctx?.let { it.stopAnimation("animation.helldiver.deployed",0);AnimationTiming.play(it,"animation.at.reload",AnimationPlayType.PLAY_ONCE_HOLD,pod.mechanicalFeedAge) }
            }
            if(!pod.isMechanicallyFeeding&&state.feeding)ctx?.let {
                it.stopAnimation("animation.at.reload",0);it.playAnimation("animation.helldiver.deployed",AnimationPlayType.LOOP,0)
            }
            if(pod.shotSerial>0&&pod.reloadAge in 2..4&&state.caseSerial!=pod.shotSerial) {
                val axis=pod.boreDirection();val muzzle=pod.muzzlePosition(0)
                val port=muzzle.subtract(axis.scale(2.25)).add(0.0,.34,0.0)
                pod.level().addParticle(com.example.sbwhelldiver.init.ModParticles.CANNON_CASING.get(),port.x,port.y,port.z,-axis.x*.10,.50,-axis.z*.10)
                state.caseSerial=pod.shotSerial
            }
            state.feeding=pod.isMechanicallyFeeding;state.serial=pod.shotSerial;return
        }
        val ctx=pod.getAnimationInstance()?.context
        val pose=if(pod.phase!=AbstractHellpodEntity.Phase.ACTIVE)0 else if(pod.awaitingFirstMount)1 else if(pod.isFirstMountLevelling)2 else 3
        if(pose!=state.mountPose) {
            listOf("animation.hmg.waiting","animation.hmg.first_mount").forEach {ctx?.stopAnimation(it,0)}
            if(pose>0) {
                ctx?.stopAnimation("animation.helldiver.deployed",0)
                when(pose) {
                    1 -> ctx?.playAnimation("animation.hmg.waiting",AnimationPlayType.LOOP,0)
                    2 -> ctx?.let {AnimationTiming.play(it,"animation.hmg.first_mount",AnimationPlayType.PLAY_ONCE_HOLD,pod.firstMountAge)}
                    3 -> ctx?.playAnimation("animation.helldiver.deployed",AnimationPlayType.LOOP,0)
                }
            }
            state.mountPose=pose
        }
        val delta=(pod.shotSerial-state.serial).coerceIn(0,4)
        repeat(delta) { i ->
            val barrel=(pod.shotSerial-delta+i) and 1
            pod.getAnimationInstance()?.context?.playAnimation(if(barrel==0)"animation.hmg.left_recoil" else "animation.hmg.right_recoil",AnimationPlayType.PLAY_ONCE_STOP,0)
            SoundTimeline.schedule(pod,"animation.hmg.fire_shot")
            val port=pod.casingExitPosition(barrel)
            val velocity=pod.casingEjectionVelocity(barrel).scale(.90+pod.level().random.nextDouble()*.20)
            // The existing larger rifle case is a spinning 3D mesh, not a flat sprite.
            Minecraft.getInstance().particleEngine.createParticle(com.example.sbwhelldiver.init.ModParticles.GATLING_CASING.get(),
                port.x,port.y,port.z,velocity.x,velocity.y,velocity.z)
            val tip=pod.muzzlePosition(barrel);val axis=pod.firingDirection(barrel)
            Minecraft.getInstance().particleEngine.createParticle(ParticleTypes.FLAME,
                tip.x+axis.x*.08,tip.y+axis.y*.08,tip.z+axis.z*.08,
                axis.x*.04,axis.y*.04,axis.z*.04)?.let {flash ->
                flash.scale(.65f);flash.setLifetime(2)
            }
            repeat(2){pod.level().addParticle(ParticleTypes.SMOKE,tip.x,tip.y,tip.z,axis.x*.12,axis.y*.12+.02,axis.z*.12)}
        }
        state.serial=pod.shotSerial
    }
}
