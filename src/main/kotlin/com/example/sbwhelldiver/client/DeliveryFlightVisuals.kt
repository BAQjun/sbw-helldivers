package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.survival.MachineDeployment
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import java.util.WeakHashMap
import com.example.sbwhelldiver.survival.EquipmentDeploymentBeaconEntity
import com.example.sbwhelldiver.survival.MachineContainerEntity
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
            val nozzles=if(machine!=null)MachineMesh.nozzles(machine)else listOf(Vec3(-1.0,.02,-.8),Vec3(1.0,.02,-.8),Vec3(-1.0,.02,.8),Vec3(1.0,.02,.8))
            for(nozzle in nozzles)for(sample in 0..1) {
                // Machine models use north as their authored forward direction.
                val offset=nozzle.let{if(machine!=null)it.yRot(Math.PI.toFloat())else it}
                HellpodJetParticle.emit(entity,offset,sample,Vec3(0.0,-1.0,0.0))
            }
        }
        SoundTimeline.tick(entity)
    }
}
