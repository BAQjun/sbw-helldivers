package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.FlameClock
import com.example.sbwhelldiver.entity.FlameJet
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.example.sbwhelldiver.init.ModParticles
import net.minecraft.world.level.ClipContext
import java.util.WeakHashMap

object FlameSentryClient {
    private class State { var phase=FlameClock.State.OFF }
    private val states=WeakHashMap<SentryHellpodEntity,State>()
    var smokeSamples=0
        private set
    var flameSamples=0
        private set
    var sootSamples=0
        private set
    private fun cue(sentry: SentryHellpodEntity,event: String) {
        SoundTimeline.schedule(sentry,"animation.flame."+event,group="flame_"+event)
    }
    fun tick(sentry: SentryHellpodEntity) {
        val state=if(sentry.phase==Phase.ACTIVE)sentry.flameState else FlameClock.State.OFF
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
