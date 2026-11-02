package com.example.sbwhelldiver.client

import net.minecraft.world.entity.Entity
import java.util.WeakHashMap

/** One incoming/impact lifecycle for normal hellpods and equipment deliveries. */
object HellpodArrivalAudio {
    private class State(var incoming:Boolean=false,var impacted:Boolean=false)
    private val states=WeakHashMap<Entity,State>()
    fun incoming(entity:Entity,age:Int) {
        val state=states.getOrPut(entity){State()}
        if(state.incoming||state.impacted)return
        state.incoming=true
        SoundTimeline.schedule(entity,
            if(entity is com.example.sbwhelldiver.entity.PlayerHellpodEntity&&PlayerDropClient.belongsToLocalPlayer(entity))PlayerDropAudio.entryCues
            else SoundTimeline.cues(entity,"animation.hellpod.retro_landing_preview").filter{it.time<=.3},
            age,group="entry",catchUp=true)
    }
    fun stopDescent(entity:Entity) {
        SoundTimeline.cancel(entity,"entry")
        SoundTimeline.cancel(entity,"player_retro")
        SoundTimeline.stopMatching(entity,if(entity is com.example.sbwhelldiver.entity.PlayerHellpodEntity)2 else 0) {
            it.startsWith("sfx_hellpod_entry_")||it.startsWith("sfx_hellpod_player_")||it.startsWith("sfx_hellpod_retro")||it.startsWith("sfx_hellpod_thruster")
        }
    }
    fun impact(entity:Entity,age:Int=0,catchUp:Boolean=false) {
        val state=states.getOrPut(entity){State()}
        if(state.impacted)return
        state.impacted=true
        stopDescent(entity)
        SoundTimeline.schedule(entity,"animation.hellpod.impact",age,catchUp=catchUp)
    }
    fun clear(){states.clear()}
}
