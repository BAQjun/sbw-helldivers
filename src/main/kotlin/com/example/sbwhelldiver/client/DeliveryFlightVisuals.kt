package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.survival.MachineDeployment
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import java.util.WeakHashMap
import com.example.sbwhelldiver.survival.EquipmentDeploymentBeaconEntity
import com.example.sbwhelldiver.survival.MachineContainerEntity
import com.example.sbwhelldiver.survival.EquipmentBeaconGeometry
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.*
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent

/** The existing hellpod sound recipes and nozzle sprites also drive equipment delivery. */
@EventBusSubscriber(modid=com.example.sbwhelldiver.MOD_ID,value=[Dist.CLIENT])
object DeliveryFlightVisuals {
    private data class State(var entry:Boolean=false,var brake:Boolean=false,var landed:Boolean=false)
    private val states=WeakHashMap<Entity,State>()
    // Tick-culling mods may suspend an incoming entity while it is above the
    // camera. Its trajectory, sound and exhaust must continue independently.
    @SubscribeEvent fun advance(event:ClientTickEvent.Post) {
        val level=net.minecraft.client.Minecraft.getInstance().level?:return
        for(e in level.entitiesForRendering())when(e) {
            is MachineContainerEntity->{e.sampleClientFlight();tick(e,e.phase==MachineContainerEntity.FALLING,e.braking,e.arrival,e.machine)}
            is EquipmentDeploymentBeaconEntity->{e.sampleClientFlight();tick(e,e.phase==EquipmentDeploymentBeaconEntity.PACKED,e.flightBraking,e.flightArrival)}
        }
    }
    fun tick(entity:Entity,falling:Boolean,braking:Boolean,arrival:Long,machine:MachineDeployment?=null) {
        val s=states.getOrPut(entity){State()};val now=entity.level().gameTime
        val airborne=falling&&now<arrival
        if(airborne&&!s.entry){s.entry=true;SoundTimeline.schedule(entity,SoundTimeline.cues(entity,"animation.hellpod.retro_landing_preview").filter{it.time<=.3},group="delivery-entry",catchUp=true)}
        if(airborne&&braking&&!s.brake){s.brake=true;SoundTimeline.schedule(entity,"animation.hellpod.retro_ignite",group="delivery-retro");SoundTimeline.schedule(entity,"animation.hellpod.audio_retro_loop_start",group="delivery-burn")}
        if(!airborne && s.entry&&!s.landed){
            s.landed=true
            SoundTimeline.cancel(entity,"delivery-entry");SoundTimeline.cancel(entity,"delivery-retro");SoundTimeline.cancel(entity,"delivery-burn")
            SoundTimeline.stopMatching(entity){it.startsWith("sfx_hellpod_entry_")||it.startsWith("sfx_hellpod_retro_")||it.startsWith("sfx_hellpod_thruster")}
            SoundTimeline.schedule(entity,"animation.hellpod.impact")
            HellpodLandingShake.impact(entity)
        }
        if(airborne&&braking) {
            val nozzles=if(machine!=null)MachineMesh.nozzles(machine).map{it to Vec3(0.0,-1.0,0.0)}else EquipmentBeaconGeometry.nozzles
            for((nozzle,direction) in nozzles)for(sample in 0..1) {
                // Machine models use north as their authored forward direction.
                val offset=nozzle.let{if(machine!=null)it.yRot(Math.PI.toFloat())else it}
                HellpodJetParticle.emit(entity,offset,sample,if(machine!=null)direction.yRot(Math.PI.toFloat())else direction)
            }
        }
        SoundTimeline.tick(entity)
    }
    private val flame=ResourceLocation.withDefaultNamespace("textures/particle/flame.png")
    var beaconJetFrames=0;private set
    var containerJetFrames=0;private set
    /** Attached geometry remains visible with shader packs and particle limit/culling mods. */
    fun drawJets(entity:Entity,partial:Float,p:PoseStack,b:MultiBufferSource) {
        val nozzles=when(entity){
            is MachineContainerEntity->{containerJetFrames++;MachineMesh.nozzles(entity.machine).map{it to Vec3(0.0,-1.0,0.0)}}
            is EquipmentDeploymentBeaconEntity->{beaconJetFrames++;EquipmentBeaconGeometry.nozzles}
            else->return
        }
        val vc=b.getBuffer(RenderType.entityTranslucentEmissive(flame));val pose=p.last()
        for((origin,axis)in nozzles) {
            val direction=axis.normalize()
            val length=2.8+.35*kotlin.math.sin((entity.level().gameTime+partial)*2.3+origin.x*7)
            val right=direction.cross(Vec3(0.0,0.0,1.0)).normalize().scale(.23)
            val other=direction.cross(right).normalize().scale(.23)
            for(side in listOf(right,other)) {
                val tip=origin.add(direction.scale(length))
                val vertices=listOf(origin.subtract(side),tip.subtract(side.scale(.2)),tip.add(side.scale(.2)),origin.add(side))
                val uvs=listOf(0f to 1f,0f to 0f,1f to 0f,1f to 1f)
                for(order in listOf(listOf(0,1,2,3),listOf(3,2,1,0)))for(i in order) {
                    val v=vertices[i];val uv=uvs[i]
                    vc.vertex(pose.pose(),v.x.toFloat(),v.y.toFloat(),v.z.toFloat()).color(1f,.96f,.8f,.95f).uv(uv.first,uv.second)
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(),0f,1f,0f).endVertex()
                }
            }
        }
    }
}
