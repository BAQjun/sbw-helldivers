package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.example.sbwhelldiver.entity.TeslaClock
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.client.Minecraft
import net.minecraft.nbt.CompoundTag
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber

/** Controlled, client-only visual fixture: actual Minecraft draw/flush, no gameplay changes. */
@EventBusSubscriber(modid=MOD_ID, value=[Dist.CLIENT])
object TeslaClientSmokeTest {
    val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.teslaSmokeTest")
    private var ticks=0
    private var reloaded=false
    private var reloadRequested=false
    private var fixture:SentryHellpodEntity?=null
    private val before=mutableSetOf<String>()
    private val after=mutableSetOf<String>()
    fun rendered(charging:Boolean, arc:Boolean, attack:Boolean) {
        val seen=if(reloaded)after else before
        if(charging)seen.add("charge")
        if(arc)seen.add(if(attack)"attack" else "idle")
    }

    @SubscribeEvent fun tick(event:TickEvent.ClientTickEvent) {
        if(!enabled || event.phase!=TickEvent.Phase.END)return
        val mc=Minecraft.getInstance()
        mc.options.pauseOnLostFocus=false
        val level=mc.level ?: return
        if(mc.overlay!=null || mc.isPaused)return
        if(reloadRequested && !reloaded)return
        ticks++
        if(fixture==null) {
            check(TeslaRenderTypes.isReady()) { "Tesla shaders not registered by KotlinForForge" }
            fixture=ModEntities.TESLA_TOWER.get().create(level)!!
            mc.options.hideGui=true
            LOGGER.info("TESLA_GPU_STARTED: both shaders registered")
        }
        val pod=fixture!!
        // Remain in front of the real game camera, independent of AI, packets or target lifetime.
        val camera=mc.gameRenderer.mainCamera
        val view=camera.lookVector
        val pos=camera.position.add(view.x*10.0,view.y*10.0+2.0,view.z*10.0)
        pod.setPos(pos.x,pos.y-pod.profile.aimHeight,pos.z)
        val charge=if(reloaded)ticks%80<40 else ticks in 60..119
        val tag=CompoundTag();pod.saveWithoutId(tag)
        tag.putInt("HellpodPhase",Phase.ACTIVE.ordinal)
        tag.putInt("HellpodPhaseTicks",100)
        tag.putBoolean("SentryInitialized",true)
        tag.putDouble("HellpodRestY",pod.y)
        tag.putInt("TeslaState",if(charge)TeslaClock.State.CHARGING.ordinal else TeslaClock.State.IDLE.ordinal)
        tag.putInt("TeslaAge",ticks%20)
        pod.load(tag);pod.tickCount=ticks
        if(!charge) {
            val arc=pod.teslaArcData
            arc.putInt("Serial",ticks)
            arc.putLong("Time",level.gameTime)
            arc.putBoolean("Attack",reloaded || ticks>=120)
            arc.putInt("Count",2)
            val origin=pod.teslaOrigin()
            listOf(origin.add(-2.0,0.0,0.0),origin.add(2.0,-1.0,0.0)).forEachIndexed { i,p ->
                arc.putDouble("X$i",p.x);arc.putDouble("Y$i",p.y);arc.putDouble("Z$i",p.z)
            }
        }
        TeslaTowerClient.tick(pod)
        if(ticks==180) {
            check(before.containsAll(listOf("idle","charge","attack"))) { "Missing rendered phases before reload: $before" }
            LOGGER.info("TESLA_GPU_BEFORE_RELOAD_PASSED: {}",before)
            reloadRequested=true
            mc.reloadResourcePacks().thenRun {
                check(TeslaRenderTypes.isReady()) { "Tesla shaders missing after reload" }
                reloaded=true
                LOGGER.info("TESLA_GPU_RELOADED: both shaders registered again")
            }
        }
        if(ticks==300) {
            check(reloaded && after.containsAll(listOf("charge","attack"))) { "Missing rendered phases after reload: $after" }
            LOGGER.info("TESLA_GPU_PASSED: before={}, after={}, actual draw calls flushed without crash",before,after)
            mc.stop()
        }
    }

    @SubscribeEvent fun draw(event:RenderLevelStageEvent) {
        if(!enabled || event.stage!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return
        val pod=fixture ?: return
        val mc=Minecraft.getInstance()
        if(mc.overlay!=null)return
        val pose=event.poseStack
        val camera=mc.gameRenderer.mainCamera.position
        pose.pushPose()
        try {
            pose.translate(pod.x-camera.x,pod.y-camera.y,pod.z-camera.z)
            val buffers=mc.renderBuffers().bufferSource()
            mc.entityRenderDispatcher.render(pod,0.0,0.0,0.0,0f,mc.frameTime,pose,buffers,15728880)
            buffers.endBatch()
            val shot=when(ticks) {30->"idle";115->"charge";130->"impact";else->null}
            if(shot!=null && captured.add(shot))net.minecraft.client.Screenshot.grab(
                mc.gameDirectory,"tesla_0323_$shot.png",mc.mainRenderTarget) {
                    LOGGER.info("TESLA_GPU_SCREENSHOT: {}",it.string)
                }
        } finally { pose.popPose() }
    }
    private val captured=mutableSetOf<String>()
}
