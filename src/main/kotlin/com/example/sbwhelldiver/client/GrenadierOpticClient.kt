package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.GrenadierBattlementEntity
import com.atsuishio.superbwarfare.client.animation.AnimationHelper
import com.atsuishio.superbwarfare.event.ClientEventHandler
import com.mojang.math.Axis
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderType
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import software.bernie.geckolib.cache.`object`.GeoBone

/** Adapter to SBW's gun red-dot renderer; no second HUD crosshair or scope texture. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object GrenadierOpticClient {
    // Forge resolves this SRG name in both production and the development runtime.
    private val setCameraPosition by lazy {
        net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(net.minecraft.client.Camera::class.java,"m_90581_",net.minecraft.world.phys.Vec3::class.java)
    }
    private val cross=GeoBone(null,"cross",false,0.0,false,false)
    internal var nativeSightFrames=0;private set
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    fun camera(e:net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles) {
        val d=HmgClient.mounted() as? GrenadierBattlementEntity ?: return
        val mc=Minecraft.getInstance();val p=mc.player ?: return
        if(d.throughSight()) {
            val yaw=d.yRot+net.minecraft.util.Mth.wrapDegrees(p.yRot-d.yRot).coerceIn(-60f,60f)
            val pitch=p.xRot.coerceIn(-45f,35f)
            if(yaw!=p.yRot){p.yRot=yaw;p.yRotO=yaw}
            if(pitch!=p.xRot){p.xRot=pitch;p.xRotO=pitch}
        }
        // Resolve the final camera pose after the native seat/free-look camera layers.
        setCameraPosition.invoke(e.camera,d.getCameraPosition(e.partialTick.toFloat(),p,HmgClient.aiming(),mc.options.cameraType.isFirstPerson))
        val rotation=d.getCameraRotation(e.partialTick.toFloat(),p,HmgClient.aiming(),mc.options.cameraType.isFirstPerson)
        e.yaw=rotation.x;e.pitch=rotation.y;e.roll=0f
    }
    @SubscribeEvent fun render(e:RenderLevelStageEvent) {
        if(e.stage!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return
        val d=HmgClient.mounted() as? GrenadierBattlementEntity ?: return
        if(!d.throughSight())return
        val mc=Minecraft.getInstance();val player=mc.player ?: return
        val eye=d.opticalCameraPosition(e.partialTick)
        val delta=eye.subtract(e.camera.position);val pose=e.poseStack
        val buffers=mc.renderBuffers().bufferSource()
        val type=RenderType.entityCutoutNoCull(ResourceLocation("superbwarfare","textures/crosshair/dot.png"))
        val oldPos=ClientEventHandler.zoomPos;val oldTime=ClientEventHandler.zoomTime
        pose.pushPose()
        try {
            pose.translate(delta.x,delta.y,delta.z)
            pose.mulPose(Axis.YP.rotationDegrees(180f-d.interpolatedYaw(e.partialTick)))
            pose.mulPose(Axis.XP.rotationDegrees(-d.interpolatedPitch(e.partialTick)))
            // The native gun helper expects gun ADS state. Scope it to this pass
            // rather than changing the player's held gun or vehicle controls.
            ClientEventHandler.zoomPos=1.0;ClientEventHandler.zoomTime=1.0
            AnimationHelper.handleZoomCrossHair(buffers,type,"cross",pose,cross,buffers,
                0.0,0.0,.12,.0072f,255,24,12,255,"dot",false)
            nativeSightFrames++
        } finally {
            ClientEventHandler.zoomPos=oldPos;ClientEventHandler.zoomTime=oldTime;pose.popPose()
        }
    }
}
