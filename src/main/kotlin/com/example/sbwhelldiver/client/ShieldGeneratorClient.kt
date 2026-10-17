package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.ShieldGeneratorEntity
import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.atsuishio.superbwarfare.client.animation.AnimationPlayType
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.util.WeakHashMap
import kotlin.math.*

@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object ShieldGeneratorClient {
    private class State{var loop=false;var broken=false}
    private val states=WeakHashMap<ShieldGeneratorEntity,State>()
    var renderedFrames=0;private set
    var renderedImpactFrames=0;private set
    fun update(g:ShieldGeneratorEntity){
        val s=states.getOrPut(g){State()}
        if(g.shieldOnline && !s.loop){SoundTimeline.schedule(g,"animation.shield.maintain");s.loop=true}
        if(!g.shieldOnline && s.loop){SoundTimeline.stopMatching(g){it=="sfx_shield_maintain"};s.loop=false}
        if(g.shieldBroken && !s.broken){
            if(g.brokenAt>=0 && g.level().gameTime-g.brokenAt<20)SoundTimeline.schedule(g,"animation.shield.break")
            g.getAnimationInstance()?.context?.let {ctx->ctx.stopAnimation("animation.helldiver.deployed",0);ctx.playAnimation("animation.shield.broken",AnimationPlayType.PLAY_ONCE_HOLD,0)}
            s.broken=true
        }
        if(g.phase==Phase.RETRACTING)g.getAnimationInstance()?.context?.stopAnimation("animation.shield.broken",0)
    }
    @SubscribeEvent fun render(event:RenderLevelStageEvent){
        if(event.stage!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return
        val mc=Minecraft.getInstance();val level=mc.level ?: return;val shader=ShieldRenderTypes.shader() ?: return
        val camera=event.camera.position;val buffers=mc.renderBuffers().bufferSource();val matrix=event.poseStack.last().pose()
        for(g in level.entitiesForRendering().filterIsInstance<ShieldGeneratorEntity>()){
            val now=level.gameTime+event.partialTick.toDouble();val brokenAge=if(g.brokenAt>=0)now-g.brokenAt else 1000.0
            val formation=if(g.phase==Phase.ACTIVE)(g.phaseAge+event.partialTick)/12.0 else 1.0
            val fading=when{g.shieldBroken -> (1-brokenAge/12).coerceIn(0.0,1.0);g.phase==Phase.RETRACTING -> (1-(g.phaseAge+event.partialTick)/12.0).coerceIn(0.0,1.0);g.phase==Phase.ACTIVE&&!g.isWreck -> 1.0;else -> 0.0}
            if(fading<=0 || g.distanceToSqr(camera.x,camera.y,camera.z)>192.0*192)continue
            val f=formation.coerceIn(0.0,1.0);val radius=ShieldGeneratorEntity.RADIUS*(f*f*(3-2*f)).coerceAtLeast(.015)*(if(g.phase==Phase.RETRACTING)max(.4,fading) else 1.0)
            val center=g.shieldCenter(event.partialTick).subtract(camera);val view=center.scale(-1.0)
            shader.getUniform("ShieldCenter")?.set(center.x.toFloat(),center.y.toFloat(),center.z.toFloat())
            shader.getUniform("ShieldRadius")?.set(radius.toFloat());shader.getUniform("Strength")?.set(fading.toFloat())
            shader.getUniform("EffectTime")?.set((now/20%1000).toFloat());shader.getUniform("BreakFlash")?.set(if(g.shieldBroken)exp(-brokenAge*.35).toFloat() else 0f)
            shader.getUniform("ViewVector")?.set(view.x.toFloat(),view.y.toFloat(),view.z.toFloat())
            val hits=g.shieldHits.getList("hits",10);var liveHits=0
            repeat(8){i->
                val hit=hits.getOrNull(i) as? net.minecraft.nbt.CompoundTag;val age=if(hit==null)100.0 else (now-hit.getLong("time"))/20
                val n=hit?.let {Vec3(it.getDouble("x"),it.getDouble("y"),it.getDouble("z")).normalize()} ?: Vec3.ZERO
                if(age in 0.0..1.2)liveHits++
                shader.getUniform("Hit$i")?.set(n.x.toFloat(),n.y.toFloat(),n.z.toFloat(),age.toFloat())
            }
            shader.getUniform("EffectMode")?.set(0f)
            val out=buffers.getBuffer(ShieldRenderTypes.ENERGY)
            val lat=32;val lon=96
            fun point(y:Int,x:Int):Vec3{val a=PI*y/lat;val b=2*PI*x/lon;return center.add(sin(a)*cos(b)*radius,cos(a)*radius,sin(a)*sin(b)*radius)}
            for(y in 0 until lat)for(x in 0 until lon){
                // UVs carry world-space spherical coordinates. Position already includes the camera
                // pose matrix, so deriving a surface normal from Position would rotate hit locations.
                val corners=arrayOf(y to x,y+1 to x,y+1 to x+1,y to x+1)
                for((cy,cx) in corners){val p=point(cy,cx);out.vertex(matrix,p.x.toFloat(),p.y.toFloat(),p.z.toFloat()).color(1f,1f,1f,1f).uv(cx.toFloat()/lon,cy.toFloat()/lat).endVertex()}
            }
            buffers.endBatch(ShieldRenderTypes.ENERGY)
            if(g.shieldOnline){
                shader.getUniform("EffectMode")?.set(1f);val beam=buffers.getBuffer(ShieldRenderTypes.ENERGY)
                val start=g.emitter(event.partialTick).subtract(camera);val end=center.add(0.0,radius,0.0)
                // One camera-facing core and halo, rather than bright seams on a polygon tube.
                var right=Vec3(view.z,0.0,-view.x).normalize();if(right.lengthSqr()<.1)right=Vec3(1.0,0.0,0.0)
                fun beamLayer(base:Double,top:Double,opacity:Float){
                    val p=arrayOf(start.subtract(right.scale(base)),end.subtract(right.scale(top)),end.add(right.scale(top)),start.add(right.scale(base)))
                    for(j in 0..3)beam.vertex(matrix,p[j].x.toFloat(),p[j].y.toFloat(),p[j].z.toFloat()).color(1f,1f,1f,opacity).uv(if(j<2)0f else 1f,if(j==1||j==2)1f else 0f).endVertex()
                }
                beamLayer(.16,.38,.25f);beamLayer(.04,.065,.95f);buffers.endBatch(ShieldRenderTypes.ENERGY)
            }
            renderedFrames++;if(liveHits>0)renderedImpactFrames++
        }
    }
}
