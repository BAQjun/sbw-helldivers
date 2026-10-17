package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.example.sbwhelldiver.entity.TeslaClock
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import java.util.WeakHashMap
import kotlin.math.*

/** Procedural branching arcs and shader-generated plasma; visuals never apply damage. */
object TeslaTowerClient {
    private val cosmeticRandom = net.minecraft.util.RandomSource.create()
    private class State {
        var active=false
        var charging=false
        var serial=0
        var born=-100
        var attack=false
        var points=emptyList<Vec3>()
        var geometrySerial=Int.MIN_VALUE
        var strands=emptyList<TeslaLightning.Strand>()
        var impacts=emptyList<List<TeslaLightning.Strand>>()
        var coronaFrame=Long.MIN_VALUE
        var corona=emptyList<TeslaLightning.Strand>()
        var columnFrame=Long.MIN_VALUE
        var column=emptyList<TeslaLightning.Strand>()
    }
    private val states=WeakHashMap<SentryHellpodEntity,State>()
    fun tick(tower: SentryHellpodEntity) {
        val s=states.getOrPut(tower){State()}
        if(tower.phase!=Phase.ACTIVE || tower.isWreck) {
            SoundTimeline.cancel(tower,"tesla_charge")
            if(s.charging)SoundTimeline.stopMatching(tower){it.startsWith("sfx_tesla_charge")}
            if(tower.isWreck || tower.phase==Phase.SPENT || tower.phase==Phase.RETRACTING)
                SoundTimeline.stopMatching(tower){it.startsWith("sfx_tesla_activate_")}
            s.active=false;s.charging=false;s.points=emptyList();return
        }
        if(!s.active) {
            // Restore native active loops when joining a tower already deployed.
            // SoundTimeline suppresses duplicates that started during deployment.
            SoundTimeline.schedule(tower,"animation.tesla.activate")
            s.active=true
        }
        val charge=tower.teslaState==TeslaClock.State.CHARGING
        if(charge!=s.charging) {
            if(charge)SoundTimeline.schedule(tower,"animation.tesla.charge",tower.teslaChargeAge,group="tesla_charge")
            else {
                SoundTimeline.cancel(tower,"tesla_charge")
                SoundTimeline.schedule(tower,"animation.tesla.charge_stop")
            }
            s.charging=charge
        }
        val tag=tower.teslaArcData;val serial=tag.getInt("Serial")
        if(serial!=s.serial) {
            s.serial=serial
            if(abs(tower.level().gameTime-tag.getLong("Time"))<=10) {
                s.born=tower.tickCount;s.attack=tag.getBoolean("Attack")
                s.points=(0 until tag.getInt("Count").coerceIn(0,4)).map { Vec3(tag.getDouble("X$it"),tag.getDouble("Y$it"),tag.getDouble("Z$it")) }
                SoundTimeline.schedule(tower,if(s.attack) "animation.tesla.discharge" else "animation.tesla.idle_arc")
                s.points.drop(1).forEach { p -> repeat(if(s.attack)20 else 2) {
                    tower.level().addParticle(ParticleTypes.ELECTRIC_SPARK,p.x,p.y,p.z,
                        (cosmeticRandom.nextDouble()-.5)*.22,cosmeticRandom.nextDouble()*.16,(cosmeticRandom.nextDouble()-.5)*.22)
                } }
            }
        }
        if(charge && tower.tickCount%2==0) {
            val p=tower.teslaOrigin();repeat(3) {
                val a=cosmeticRandom.nextDouble()*PI*2
                tower.level().addParticle(ParticleTypes.ELECTRIC_SPARK,p.x+cos(a)*.7,p.y+(cosmeticRandom.nextDouble()-.5),p.z+sin(a)*.7,
                    (cosmeticRandom.nextDouble()-.5)*.14,(cosmeticRandom.nextDouble()-.5)*.14,(cosmeticRandom.nextDouble()-.5)*.14)
            }
        }
    }
    fun effectBounds(tower: SentryHellpodEntity): AABB? {
        if(tower.phase!=Phase.ACTIVE || tower.isWreck)return null
        var bounds=AABB(tower.position().add(0.0,.8,0.0),tower.teslaOrigin()).inflate(3.2)
        val s=states[tower]
        if(s!=null && tower.tickCount-s.born<9)for(p in s.points)bounds=bounds.minmax(AABB(p,p).inflate(3.5))
        return bounds
    }
    fun render(tower: SentryHellpodEntity,partial: Float,pose: PoseStack,buffers: MultiBufferSource) {
        if(tower.phase!=Phase.ACTIVE || tower.isWreck)return
        // Never queue a render type with a missing shader during load/reload.
        if(!TeslaRenderTypes.isReady())return
        val s=states[tower] ?: return
        val offset=tower.getPosition(partial)
        val camera=Minecraft.getInstance().gameRenderer.mainCamera.position.subtract(offset)
        val matrix=pose.last().pose()
        val time=tower.tickCount+partial.toDouble()
        val flashes=mutableListOf<Triple<Vec3,Double,Double>>()
        val center=tower.teslaOrigin(partial).subtract(offset)
        val charging=tower.teslaState==TeslaClock.State.CHARGING
        val progress=if(charging)((tower.teslaChargeAge+partial)/20).coerceIn(0f,1f).toDouble() else 0.0
        val age=time-s.born;val lifetime=if(s.attack)9.0 else 5.5
        val discharging=age>=0 && age<lifetime && s.points.size>=2
        // A live terminal always has a small plasma ball, including idle and cooldown.
        flashes.add(Triple(center,if(charging).75+2.1*progress else .60+.035*sin(time*.29),
            if(charging).65+.30*progress else .66+.06*sin(time*.41)))
        // All arc geometry is submitted before plasma. Never reuse a consumer
        // after MultiBufferSource switches its shared fallback buffer.
        val vc=buffers.getBuffer(TeslaRenderTypes.ARC)
        fun ribbon(strand:TeslaLightning.Strand,origin:Vec3,width:Double,alpha:Double,seed:Long) {
            val source=strand.points
            val pts=source.mapIndexed { i,p ->
                val w=if(i==0 || i==source.lastIndex)0.0 else .018
                Vec3(p.x+sin(i*2.71+time*2.8+seed%97)*w,
                    p.y+cos(i*1.37-time*3.1+seed%71)*w,p.z+sin(i*1.91+time*2.4)*w).add(origin)
            }
            if(pts.size<2)return
            var distance=0.0
            val lengths=DoubleArray(pts.size)
            for(i in 1 until pts.size){distance+=pts[i].distanceTo(pts[i-1]);lengths[i]=distance}
            val opacity=(alpha*strand.strength).coerceIn(0.0,1.0).toFloat()
            for(i in 0 until pts.lastIndex) {
                val axis=pts[i+1].subtract(pts[i]);val length=axis.length()
                if(length<1e-6)continue
                val tangent=axis.scale(1/length)
                var side=tangent.cross(camera.subtract(pts[i])).normalize()
                if(side.lengthSqr()<.01)side=tangent.cross(Vec3(0.0,1.0,0.0)).normalize()
                if(side.lengthSqr()<.01)side=Vec3(1.0,0.0,0.0)
                val t=(i+.5)/pts.lastIndex
                val taper=if(strand.tapered)(1-t).pow(.65).coerceAtLeast(.025) else 1.0
                val wanted=width*strand.width*taper*(.92+.08*sin(i*1.51+seed%53))
                // Analytic round-ended capsules avoid twisted ribbon joins at
                // acute bends. R carries their exact, quantized aspect ratio.
                // Logarithmic packing preserves wide halos even on short segments.
                val packed=((log2(length/wanted)+5)/12*255).roundToInt().coerceIn(0,255)
                val aspect=2.0.pow(packed/255.0*12-5);val halfWidth=length/aspect
                side=side.scale(halfWidth)
                val a=pts[i].subtract(tangent.scale(halfWidth));val b=pts[i+1].add(tangent.scale(halfWidth))
                fun vertex(p:Vec3,u:Double,v:Float) {
                    vc.vertex(matrix,p.x.toFloat(),p.y.toFloat(),p.z.toFloat())
                        .color(packed,((lengths[i]%32)/32*255).toInt(),255,(opacity*255).toInt())
                        .uv(u.toFloat(),v).endVertex()
                }
                vertex(a.subtract(side),0.0,0f);vertex(a.add(side),0.0,1f)
                vertex(b.add(side),aspect+2,1f);vertex(b.subtract(side),aspect+2,0f)
            }
        }
        if(charging) {
            val frame=(tower.tickCount/2).toLong()
            if(s.coronaFrame!=frame) {
                s.coronaFrame=frame;s.corona=TeslaLightning.corona(frame*109+tower.id*817L,progress)
            }
            s.corona.forEachIndexed { i,arc ->ribbon(arc,center,.20+.12*progress,.72+progress*.28,frame+i) }
        }
        if(charging || discharging) {
            val frame=(tower.tickCount/2).toLong()
            if(s.columnFrame!=frame) {
                s.columnFrame=frame
                s.column=TeslaLightning.columnArcs(frame*131+tower.id*817L,
                    (tower.profile.aimHeight-1.05).coerceIn(.5,4.0))
            }
            val strength=if(charging).55+.40*progress else (1-age/lifetime).pow(.35)
            s.column.forEachIndexed { i,arc ->ribbon(arc,center,if(charging).20+.08*progress else .28,strength,frame+i) }
        }
        if(discharging) {
            if(s.geometrySerial!=s.serial) {
                s.geometrySerial=s.serial
                s.strands=s.points.zipWithNext().flatMapIndexed { i,(a,b) ->
                    TeslaLightning.bolt(TeslaLightning.Point(a.x,a.y,a.z),TeslaLightning.Point(b.x,b.y,b.z),
                        s.serial*9187L+tower.id*211L+i*137,s.attack,if(s.attack)1.0 else .72)
                }
                s.impacts=if(s.attack)s.points.drop(1).mapIndexed { i,_ ->
                    TeslaLightning.impactBurst(s.serial*3719L+tower.id*211L+i*137)
                } else emptyList()
            }
            val restrike=if(s.attack).38*exp(-((age-2.4)/.55).pow(2)) else 0.0
            val envelope=((exp(-age*.31)+restrike)*(1-age/lifetime).pow(.35)).coerceIn(0.0,1.0)
            s.strands.forEachIndexed { i,arc ->ribbon(arc,offset.scale(-1.0),if(s.attack).68 else .34,envelope,s.serial*53L+i) }
            if(s.attack) {
                flashes.add(Triple(center,1.0+age*.12,envelope*.8))
                s.points.drop(1).forEachIndexed { target,p ->
                    val hit=p.subtract(offset)
                    val burst=envelope*exp(-age*.12)
                    s.impacts.getOrNull(target)?.forEachIndexed { i,arc ->
                        ribbon(arc,hit,.38,burst,s.serial*73L+target*19+i)
                    }
                    flashes.add(Triple(hit,1.3+age*.12,envelope*.85))
                }
            }
        }
        // Procedural plasma: no sprites or sampled textures. The fragment shader
        // integrates a noisy volume and computes its soft halo analytically.
        if(flashes.isNotEmpty()) {
            val plasma=buffers.getBuffer(TeslaRenderTypes.PLASMA)
            for((center,radius,strength) in flashes) {
                val toward=camera.subtract(center).normalize()
                var right=toward.cross(Vec3(0.0,1.0,0.0)).normalize()
                if(right.lengthSqr()<.01)right=Vec3(1.0,0.0,0.0)
                val up=right.cross(toward).normalize();val x=right.scale(radius);val y=up.scale(radius)
                val ps=listOf(center.subtract(x).subtract(y),center.add(x).subtract(y),center.add(x).add(y),center.subtract(x).add(y))
                val uv=listOf(0f to 0f,1f to 0f,1f to 1f,0f to 1f)
                ps.forEachIndexed { i,p ->plasma.vertex(matrix,p.x.toFloat(),p.y.toFloat(),p.z.toFloat())
                    .color(1f,1f,1f,strength.toFloat()).uv(uv[i].first,uv[i].second).endVertex() }
            }
        }
        if(TeslaClientSmokeTest.enabled) TeslaClientSmokeTest.rendered(
            charging,
            discharging,
            s.attack)
    }

    fun clear(){states.clear()}
}

