package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.FlameClock
import com.example.sbwhelldiver.entity.GuardDogEntity
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.example.sbwhelldiver.init.ModParticles
import java.util.WeakHashMap

object FlameSentryClient {
    private class State { var phase=FlameClock.State.OFF }
    private val states=WeakHashMap<Entity,State>()
    var smokeSamples=0
        private set
    var flameSamples=0
        private set
    var sootSamples=0
        private set
    private fun cue(emitter: Entity, event: String) {
        val animation = "animation.flame.$event"
        val cues = if (emitter is GuardDogEntity) SoundTimeline.cues("flame_hellpod_v1", animation)
            else SoundTimeline.cues(emitter, animation)
        SoundTimeline.schedule(emitter, cues, group = "flame_$event")
    }
    fun tick(dog: GuardDogEntity) {
        updateAudio(dog,dog.flameState)
        SoundTimeline.tick(dog)
        if (dog.flameState == FlameClock.State.OFF) return
        FlameJetVisual.tick(dog,dog.flameState,GuardDogVisual.firingMuzzle(dog,1f),
            Vec3.directionFromRotation(dog.xRot,dog.yRot),GuardDogEntity.FLAME_RANGE)
    }
    private fun updateAudio(sentry: Entity, state: FlameClock.State) {
        val local=states.getOrPut(sentry) { State() }
        val previous=local.phase
        local.phase=state
        if(state!=previous) {
            if(previous==FlameClock.State.SPRAYING) { SoundTimeline.cancel(sentry,"flame_fire_start");cue(sentry,"fire_stop");cue(sentry,"cooldown_start") }
            if(state==FlameClock.State.IGNITING) cue(sentry,"ignition")
            if(state==FlameClock.State.SPRAYING) { cue(sentry,"cooldown_stop");cue(sentry,"fire_start") }
            if(state==FlameClock.State.OFF) {
                cue(sentry,"cooldown_stop")
            }
        }
    }
    fun tick(sentry: SentryHellpodEntity) {
        val state=if(sentry.phase==Phase.ACTIVE)sentry.flameState else FlameClock.State.OFF
        updateAudio(sentry,state)
        FlameJetVisual.tick(sentry)
        if(state==FlameClock.State.SPRAYING){flameSamples++;return}
        if(state!=FlameClock.State.IGNITING)return
        val muzzle=sentry.getShootPos("Main",1f);val axis=sentry.getShootVec("Main",1f).normalize()
        repeat(4){i->
            val p=muzzle.add(axis.scale(i*.12));val v=axis.scale(.12).add(0.0,.005,0.0)
            net.minecraft.client.Minecraft.getInstance().particleEngine.createParticle(ModParticles.IGNITION_SMOKE.get(),p.x,p.y,p.z,v.x,v.y,v.z);smokeSamples++
        }
    }
    fun clear() { states.clear() }
}
