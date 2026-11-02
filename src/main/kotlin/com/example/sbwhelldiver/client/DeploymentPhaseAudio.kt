package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.survival.EquipmentDeploymentBeaconEntity
import com.example.sbwhelldiver.survival.MachineContainerEntity
import net.minecraft.world.entity.Entity
import java.util.WeakHashMap

/** Adapted mechanical recipes use the same synced start clock as the rendered animation. */
object DeploymentPhaseAudio {
    private data class Phase(val value:Int,val start:Long)
    private val phases=WeakHashMap<Entity,Phase>()
    fun tick(entity:Entity) {
        val phase=when(entity) {
            is EquipmentDeploymentBeaconEntity->Phase(entity.phase,entity.phaseStart)
            is MachineContainerEntity->Phase(entity.phase,entity.phaseStart)
            else->return
        }
        if(phases.put(entity,phase)==phase)return
        SoundTimeline.cancel(entity,"equipment-motion")
        SoundTimeline.stopMatching(entity){it.startsWith("sfx_equipment_beacon_")||it=="sfx_emplacement_retract_mechanism"}
        val animation=when(entity) {
            is EquipmentDeploymentBeaconEntity->when(phase.value) {
                EquipmentDeploymentBeaconEntity.DEPLOYING->"beacon.deploy"
                EquipmentDeploymentBeaconEntity.RETRACTING->"beacon.retract"
                else->return
            }
            is MachineContainerEntity->if(phase.value==MachineContainerEntity.OPENING)"container.deploy" else return
            else->return
        }
        val age=(entity.level().gameTime-phase.start).coerceAtLeast(0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        // Joining/tracking midway through an animation must not replay earlier motions.
        val cues=SoundTimeline.cues("equipment_delivery",animation).filter{kotlin.math.ceil(it.time*20-1e-7).toInt()>=age || age<=2}
        SoundTimeline.schedule(entity,cues,age,"equipment-motion")
    }
}
