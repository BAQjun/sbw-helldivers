package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.*
import net.minecraft.client.Minecraft
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.*
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import java.util.WeakHashMap
import kotlin.math.*

/** World-space combustion parcels. Once emitted, no parcel depends on the gun's transform. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object FlameJetVisual {
    private const val CORE=0
    private const val BODY=1
    private const val OUTER=2
    private const val SOOT=3
    private data class Parcel(var p:Vec3,var previous:Vec3,var velocity:Vec3,var age:Double,var life:Double,val kind:Int,val seed:Double,val limit:Double,var travelled:Double=0.0,var smoked:Boolean=false)
    private data class Emitter(var position:Vec3,var direction:Vec3,var ignition:Int=0,var spraying:Int=0)
    private val emitters=WeakHashMap<SentryHellpodEntity,Emitter>()
    private val parcels=ArrayList<Parcel>()
    private var world:Level?=null
    private fun world(level:Level?){if(world!==level){parcels.clear();emitters.clear();world=level}}
    private fun smooth(x:Double)=x.coerceIn(0.0,1.0).let{it*it*(3-2*it)}
    private fun scatter(axis:Vec3,degrees:Double,rng:net.minecraft.util.RandomSource):Vec3 {
        var side=axis.cross(Vec3(0.0,1.0,0.0)).normalize();if(side.lengthSqr()<.01)side=axis.cross(Vec3(1.0,0.0,0.0)).normalize()
        val up=side.cross(axis).normalize();val a=rng.nextDouble()*PI*2;val radius=tan(Math.toRadians(degrees))*sqrt(rng.nextDouble())
        return axis.add(side.scale(cos(a)*radius)).add(up.scale(sin(a)*radius)).normalize()
    }
    fun tick(s:SentryHellpodEntity) {
        world(s.level())
        val state=if(s.phase==AbstractHellpodEntity.Phase.ACTIVE)s.flameState else FlameClock.State.OFF
        val muzzle=s.getShootPos("Main",1f);val direction=s.getShootVec("Main",1f).normalize()
        val emitter=emitters.getOrPut(s){Emitter(muzzle,direction)};val rng=s.level().random
        emitter.ignition=if(state==FlameClock.State.IGNITING)emitter.ignition+1 else 0
        emitter.spraying=if(state==FlameClock.State.SPRAYING)emitter.spraying+1 else 0
        fun emit(kind:Int,count:Int,short:Boolean=false) {
            for(i in 0 until count) {
                // Sub-tick emission fills the angular gap during a 140 degree/s traverse.
                val fraction=(i+.5)/count
                val at=emitter.position.lerp(muzzle,fraction)
                val axis=emitter.direction.lerp(direction,fraction).normalize()
                // Outer combustion travels with the main fuel stream. Slower,
                // widely scattered parcels left detached yellow clouds above it.
                val spread=when(kind){CORE->.6;BODY->4.0;else->3.0}
                val speed=if(kind==CORE)3.0 else 3.6+rng.nextDouble()*.6
                val life=if(kind==CORE)2.0+rng.nextDouble()*.5 else 11.5+rng.nextDouble()*2.5
                val v=scatter(axis,spread,rng).scale(if(short).28 else speed)
                parcels.add(Parcel(at,at,v,-fraction,if(short)4.0 else life,kind,rng.nextDouble(),if(short)1.8 else FlameClock.RANGE))
            }
        }
        if(state==FlameClock.State.SPRAYING) {
            emit(CORE,12)
            emit(BODY,if(emitter.spraying<3)12 else 24)
            if(s.tickCount%2==0)emit(OUTER,2)
        }else if(state==FlameClock.State.IGNITING && emitter.ignition>=14)emit(CORE,2,true)
        emitter.position=muzzle;emitter.direction=direction
        if(parcels.size>1600)parcels.subList(0,parcels.size-1600).clear()
    }
    /** A tiny white-hot contact core supplies surface lighting, without smoke or flame puffs. */
    fun orbitalContactLight(level:Level,at:Vec3) {
        world(level)
        parcels.add(Parcel(at,at,Vec3(0.0,.01,0.0),0.0,2.0,CORE,0.0,1.0))
        if(parcels.size>1600)parcels.subList(0,parcels.size-1600).clear()
    }
    // Emissions are drawn independently of the turret's model and its culling box.
    fun bounds(s:SentryHellpodEntity):AABB?=null
    @SubscribeEvent fun advance(event:net.neoforged.neoforge.client.event.ClientTickEvent.Post) {
        val mc=Minecraft.getInstance();world(mc.level)
        val level=world?:return;if(mc.isPaused)return
        val born=ArrayList<Parcel>();val it=parcels.iterator()
        while(it.hasNext()) {
            val p=it.next();val before=p.age;p.age+=1.0
            if(p.age>=p.life){it.remove();continue}
            val dt=(1.0-max(0.0,-before)).coerceIn(0.0,1.0)
            p.previous=p.p;val progress=(p.age/p.life).coerceIn(0.0,1.0)
            if(p.kind!=CORE) {
                val phase=p.seed*PI*2;val frequency=p.age*.23
                val mix=progress*progress
                val side=.006+mix*.016
                val buoyancy=when(p.kind){BODY->.008+mix*.020;OUTER->.013+mix*.027;else->.012+mix*.009}
                p.velocity=p.velocity.scale(if(p.kind==SOOT).973 else .994).add(sin(frequency+phase)*side,buoyancy,cos(frequency*.81+phase)*side)
            }
            val next=p.p.add(p.velocity.scale(dt))
            val hit=level.clip(ClipContext(p.p,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,net.minecraft.world.phys.shapes.CollisionContext.empty()))
            if(hit.type==HitResult.Type.MISS)p.p=next else {
                p.p=hit.location.add(Vec3.atLowerCornerOf(hit.direction.normal).scale(.035))
                val surfaceNormal=Vec3.atLowerCornerOf(hit.direction.normal)
                val tangent=p.velocity.subtract(surfaceNormal.scale(p.velocity.dot(surfaceNormal)))
                p.velocity=tangent.scale(.10).add(surfaceNormal.scale(.16)).add(0.0,.04,0.0)
                // Retire an impacting flame immediately instead of sliding a large,
                // half-buried billboard along the floor with a perfectly flat bottom.
                if(p.kind!=SOOT)p.life=min(p.life,p.age+.65)
            }
            val travelled=p.p.distanceTo(p.previous)
            if(p.kind!=SOOT && p.travelled+travelled>p.limit) {
                p.p=p.previous.lerp(p.p,((p.limit-p.travelled)/max(.0001,travelled)).coerceIn(0.0,1.0))
                p.velocity=Vec3.ZERO;p.life=min(p.life,p.age+.65)
            }
            p.travelled+=p.p.distanceTo(p.previous)
            if(p.kind==BODY && !p.smoked && (progress>.60 || p.life-p.age<3.0)) {
                p.smoked=true
                val smokeSeed=(p.seed*7.13)%1.0
                if(p.seed<.16)born.add(Parcel(p.p,p.p,p.velocity.scale(.035).add(0.0,.19,0.0),0.0,18.0+smokeSeed*12.0,SOOT,smokeSeed,100.0))
            }
        }
        parcels.addAll(born)
    }
    /** Ellipsoidal combustion volumes, sampled by the post-world ray integrator.
     * Geometry is only a conservative bound; no flame-bearing plane is drawn. */
    fun volumeInstances(partial:Float,camera:Vec3,frustum:net.minecraft.client.renderer.culling.Frustum):FloatArray {
        if(Minecraft.getInstance().level!==world)return FloatArray(0)
        val instances=ArrayList<Pair<Double,FloatArray>>()
        for(p in parcels) {
            // Keep enough overlapping combustion throughout the plume. Stable
            // sampling avoids flicker without hollowing out its older half.
            if((p.kind==CORE && p.seed>.55)||(p.kind==BODY && p.seed>.72))continue
            val age=(p.age-1.0+partial).coerceAtLeast(0.0)
            val t=(age/p.life).coerceIn(0.0,1.0)
            val fade=if(p.kind==SOOT)smooth(t/.12)*(1.0-smooth((t-.35)/.65)) else smooth(t/(if(p.kind==CORE).10 else .12))*(1.0-smooth((t-.72)/.28))
            if(fade<.003)continue
            val center=p.previous.lerp(p.p,partial.toDouble())
            if(center.distanceToSqr(camera)>128.0*128.0)continue
            val radius=when(p.kind){CORE->.075+.12*smooth(t/.7);BODY->.15+1.02*smooth(t/.8);OUTER->.18+1.12*smooth(t/.85);else->.26+1.05*smooth(t/.8)}
            val speed=p.velocity.length()
            val length=when(p.kind){CORE->.40+speed*.17;BODY->.55+radius*1.7+speed*.10;OUTER->radius*(2.4-.8*t);else->radius*1.25}
            // Offscreen fire can still illuminate a visible nearby surface.
            val margin=if(p.kind==CORE||p.kind==BODY)5.8 else 0.0
            if(!frustum.isVisible(AABB(center,center).inflate(max(radius,length)*1.05+margin)))continue
            val axis=if(speed>.001)p.velocity.scale(1.0/speed)else Vec3(0.0,1.0,0.0)
            val relative=center.subtract(camera)
            val opacity=fade*when(p.kind){CORE->.65;BODY->.52;OUTER->.26;else->.20}
            instances.add(center.distanceToSqr(camera) to floatArrayOf(relative.x.toFloat(),relative.y.toFloat(),relative.z.toFloat(),axis.x.toFloat(),axis.y.toFloat(),axis.z.toFloat(),length.toFloat(),radius.toFloat(),t.toFloat(),opacity.toFloat(),p.kind.toFloat(),p.seed.toFloat()))
        }
        val data=FloatArray(instances.size*12);var index=0
        for((_,row)in instances.sortedByDescending{it.first})for(v in row)data[index++]=v
        return data
    }
}
