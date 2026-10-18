package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.LaserClock
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.WeakHashMap

object LaserSentryClient {
    private class State { var phase=LaserClock.State.OFF;var alarm=false;var hotFiring=false;var exploded=false }
    private val states=WeakHashMap<SentryHellpodEntity,State>()
    private fun cue(sentry: SentryHellpodEntity, event: String) = SoundTimeline.schedule(sentry,"animation.laser.$event",group="laser_$event")
    fun tick(sentry: SentryHellpodEntity) {
        val s=states.getOrPut(sentry) { State() }
        val state=if(sentry.phase==Phase.ACTIVE)sentry.laserState else LaserClock.State.OFF
        if(state!=s.phase) {
            if(s.phase==LaserClock.State.FIRING) { SoundTimeline.cancel(sentry,"laser_fire_start");cue(sentry,"fire_stop") }
            if(s.phase==LaserClock.State.WARMING) { SoundTimeline.cancel(sentry,"laser_warmup");cue(sentry,"warmup_stop") }
            if(state==LaserClock.State.WARMING) cue(sentry,"warmup")
            if(state==LaserClock.State.FIRING) cue(sentry,"fire_start")
            if(state==LaserClock.State.OVERLOADED) { cue(sentry,"overheat");sentry.getAnimationInstance()?.context?.playAnimation("animation.laser.overheat",com.atsuishio.superbwarfare.client.animation.AnimationPlayType.PLAY_ONCE_HOLD,0) }
            if(s.phase==LaserClock.State.OVERLOADED && sentry.phase==Phase.SPENT && !s.exploded) { cue(sentry,"self_destruct");s.exploded=true }
            s.phase=state
        }
        val alarm=sentry.phase==Phase.ACTIVE && sentry.laserAlarm
        if(alarm!=s.alarm) { if(alarm)cue(sentry,"alarm_start") else { SoundTimeline.cancel(sentry,"laser_alarm_start");cue(sentry,"alarm_stop") };s.alarm=alarm }
        val hotFiring=alarm && state==LaserClock.State.FIRING
        if(hotFiring!=s.hotFiring) {
            if(hotFiring)cue(sentry,"heat_fire_start") else {
                SoundTimeline.cancel(sentry,"laser_heat_fire_start");cue(sentry,"heat_fire_stop")
            }
            s.hotFiring=hotFiring
        }
        if(alarm)LaserPostVisual.overheat(sentry,if(state==LaserClock.State.OVERLOADED)5 else 2)
        if(state==LaserClock.State.FIRING && sentry.beamLength<199f && sentry.tickCount%2==0) {
            val p=beamOrigin(sentry,1f).add(sentry.getShootVec("Main",1f).normalize().scale(sentry.beamLength.toDouble()))
            sentry.level().addParticle(ParticleTypes.END_ROD,p.x,p.y,p.z,0.0,.01,0.0)
        }
    }
    fun beamOrigin(sentry: SentryHellpodEntity,partial: Float): Vec3 =
        sentry.getShootPos("Main",partial).subtract(sentry.getShootVec("Main",partial).normalize().scale(sentry.laserBackoff.toDouble()))
    fun beamBounds(sentry: SentryHellpodEntity,partial: Float): AABB? {
        if(sentry.phase!=Phase.ACTIVE || sentry.laserState!=LaserClock.State.FIRING || sentry.beamLength<=0)return null
        val start=beamOrigin(sentry,partial)
        val end=start.add(sentry.getShootVec("Main",partial).normalize().scale(sentry.beamLength.toDouble()))
        return AABB(start,end).inflate(.18)
    }
    fun clear() { states.clear() }
}
