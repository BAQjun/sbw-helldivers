package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.survival.*
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.blockentity.*
import net.minecraft.client.renderer.entity.*
import net.minecraft.world.phys.Vec3
import java.util.WeakHashMap

class SampleMachineRenderer(context:BlockEntityRendererProvider.Context):BlockEntityRenderer<SampleMachineBlockEntity> {
    private data class Clock(var ticks:Long,var at:Long)
    private val clocks=WeakHashMap<SampleMachineBlockEntity,Clock>()
    override fun render(e:SampleMachineBlockEntity,partial:Float,p:PoseStack,b:MultiBufferSource,light:Int,overlay:Int) {
        if(e.root!=e.blockPos)return
        val now=e.level?.gameTime?:0;val c=clocks.getOrPut(e){Clock(e.workTicks,now)}
        if(c.ticks!=e.workTicks){c.ticks=e.workTicks;c.at=now}
        val time=((c.ticks+if(e.working)(now-c.at).coerceIn(0,10)+partial else 0f)/20f%4).toFloat()
        p.pushPose();p.translate(.5,0.0,.5);p.mulPose(Axis.YP.rotationDegrees(180f-e.facing.toYRot()))
        MachineMesh.draw(e.machine.id,"working",time,p,b,light);p.popPose()
    }
    override fun getViewDistance()=128
    override fun shouldRenderOffScreen(e:SampleMachineBlockEntity)=true
    override fun getRenderBoundingBox(e:SampleMachineBlockEntity)=e.bounds()
    override fun shouldRender(e:SampleMachineBlockEntity,camera:Vec3)=e.root==e.blockPos&&e.bounds().distanceToSqr(camera)<128.0*128.0
}
class MachineContainerRenderer(context:EntityRendererProvider.Context):EntityRenderer<MachineContainerEntity>(context) {
    override fun getTextureLocation(e:MachineContainerEntity)=BeaconModel.WHITE
    override fun shouldRender(e:MachineContainerEntity,f:net.minecraft.client.renderer.culling.Frustum,x:Double,y:Double,z:Double)=
        e.shouldRender(x,y,z)&&f.isVisible(e.boundingBox)
    override fun render(e:MachineContainerEntity,yaw:Float,partial:Float,p:PoseStack,b:MultiBufferSource,light:Int) {
        val time=if(e.phase==MachineContainerEntity.OPENING)((e.level().gameTime-e.phaseStart+partial)/20f).coerceIn(0f,2.4f)else 0f
        p.pushPose();p.mulPose(Axis.YP.rotationDegrees(180f-e.facing.toYRot()));MachineMesh.draw(e.machine.id+"_container","deploy",time,p,b,light)
        if(e.phase==MachineContainerEntity.FALLING&&e.braking)DeliveryFlightVisuals.drawJets(e,partial,p,b)
        p.popPose()
    }
}
