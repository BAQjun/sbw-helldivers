package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.LaserClock
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.resources.ResourceLocation
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
        if(alarm) {
            val p=sentry.getShootPos("Main",1f).subtract(sentry.getShootVec("Main",1f).scale(.45))
            repeat(if(state==LaserClock.State.OVERLOADED)5 else 2) {
                val rng=sentry.level().random
                sentry.level().addParticle(ParticleTypes.ELECTRIC_SPARK,p.x+(rng.nextDouble()-.5)*.4,p.y+(rng.nextDouble()-.5)*.4,p.z+(rng.nextDouble()-.5)*.4,(rng.nextDouble()-.5)*.12,rng.nextDouble()*.08,(rng.nextDouble()-.5)*.12)
            }
        }
        if(state==LaserClock.State.FIRING && sentry.beamLength<199f && sentry.tickCount%2==0) {
            val p=beamOrigin(sentry,1f).add(sentry.getShootVec("Main",1f).normalize().scale(sentry.beamLength.toDouble()))
            sentry.level().addParticle(ParticleTypes.END_ROD,p.x,p.y,p.z,0.0,.01,0.0)
        }
    }
    private fun beamOrigin(sentry: SentryHellpodEntity,partial: Float): Vec3 =
        sentry.getShootPos("Main",partial).subtract(sentry.getShootVec("Main",partial).normalize().scale(sentry.laserBackoff.toDouble()))
    fun beamBounds(sentry: SentryHellpodEntity,partial: Float): AABB? {
        if(sentry.phase!=Phase.ACTIVE || sentry.laserState!=LaserClock.State.FIRING || sentry.beamLength<=0)return null
        val start=beamOrigin(sentry,partial)
        val end=start.add(sentry.getShootVec("Main",partial).normalize().scale(sentry.beamLength.toDouble()))
        return AABB(start,end).inflate(.18)
    }
    private val beamTexture=ResourceLocation("sbw_helldiver","textures/effect/laser_beam.png")
    fun renderBeam(sentry: SentryHellpodEntity,partial: Float,pose: PoseStack,buffers: MultiBufferSource) {
        if(sentry.phase!=Phase.ACTIVE || sentry.laserState!=LaserClock.State.FIRING || sentry.beamLength<=0)return
        val start=beamOrigin(sentry,partial).subtract(sentry.getPosition(partial))
        val axis=sentry.getShootVec("Main",partial).normalize()
        val end=start.add(axis.scale(sentry.beamLength.toDouble()))
        val view=Minecraft.getInstance().gameRenderer.mainCamera.position.subtract(sentry.getPosition(partial)).subtract(start)
        var side=axis.cross(view).normalize()
        if(side.lengthSqr()<.01)side=axis.cross(if(kotlin.math.abs(axis.y)<.95)Vec3(0.0,1.0,0.0) else Vec3(1.0,0.0,0.0)).normalize()
        // Emissive entity pass stays visible in daylight and renders both sides.
        // The lightning pass culls this ribbon and uses the weather render target.
        val vc=buffers.getBuffer(RenderType.entityTranslucentEmissive(beamTexture))
        val matrix=pose.last().pose();val normalMatrix=pose.last().normal()
        val normal=side.cross(axis).normalize()
        val pulse=1.0+.06*kotlin.math.sin((sentry.tickCount+partial)*1.7)
        fun ribbon(width: Double,r: Int,g: Int,b: Int,a: Int) {
            val offset=side.scale(width)
            val corners=listOf(start.subtract(offset),start.add(offset),end.add(offset),end.subtract(offset))
            val uv=listOf(0f to 0f,1f to 0f,1f to 1f,0f to 1f)
            corners.forEachIndexed { i,p ->
                vc.vertex(matrix,p.x.toFloat(),p.y.toFloat(),p.z.toFloat()).color(r,g,b,a)
                    .uv(uv[i].first,uv[i].second).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880)
                    .normal(normalMatrix,normal.x.toFloat(),normal.y.toFloat(),normal.z.toFloat()).endVertex()
            }
        }
        ribbon(.15*pulse,255,180,12,85)
        ribbon(.055*pulse,255,227,32,230)
        ribbon(.018*pulse,255,255,205,255)
    }
    fun clear() { states.clear() }
}
