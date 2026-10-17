package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.entity.MineDispenserEntity
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import java.util.WeakHashMap
object MineDispenserClient {
    private val started=WeakHashMap<MineDispenserEntity,Boolean>()
    private val destroyed=WeakHashMap<MineDispenserEntity,Boolean>()
    fun tick(entity: MineDispenserEntity) {
        if(entity.phase==AbstractHellpodEntity.Phase.SPENT) {
            if(destroyed.put(entity,true)==null) {
                SoundTimeline.cancel(entity,"mine_spin")
                SoundTimeline.stopMatching(entity) { it.startsWith("sfx_ap_mine_spin_") || it.startsWith("sfx_ap_mine_deploy_") }
                started.remove(entity)
            }
            return
        }
        val was=started[entity] ?: false
        if(entity.spinning && !was) {
            SoundTimeline.schedule(entity,"animation.minefield.spin",(entity.spreadAge-1).coerceAtLeast(0),"mine_spin",false)
            started[entity]=true
        }
        if(!entity.spinning && was) {
            SoundTimeline.cancel(entity,"mine_spin")
            // Deployment audio follows its original marker and keeps its native tail.
            SoundTimeline.stopMatching(entity) { it.startsWith("sfx_ap_mine_spin_") }
            started.remove(entity)
        }
    }
}
