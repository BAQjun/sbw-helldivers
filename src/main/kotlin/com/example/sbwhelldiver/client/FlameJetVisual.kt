package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.*
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.*
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.util.WeakHashMap
import kotlin.math.*

/** World-space combustion parcels. Once emitted, no parcel depends on the gun's transform. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object FlameJetVisual {
    private const val CORE=0
    private const val BODY=1
    private const val OUTER=2
    private const val SOOT=3
    private data class Parcel(var p:Vec3,var previous:Vec3,var velocity:Vec3,var age:Double,var life:Double,val kind:Int,val seed:Double,val limit:Double,var travelled:Double=0.0,var smoked:Boolean=false)
    private data class Emitter(var position:Vec3,var direction:Vec3,var ignition:Int=0,var spraying:Int=0)
    // Flame sheets occupy different world-space planes, not one camera-facing surface.
    private data class FlameSheet(val center:Vec3,val along:Vec3,val across:Vec3,val length:Double,val width:Double,val age:Double,val seed:Double,val kind:Int,val opacity:Double)
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
                val spread=when(kind){CORE->.6;BODY->4.0;else->9.0}
                val speed=when(kind){CORE->3.0;BODY->3.6+rng.nextDouble()*.6;else->2.3+rng.nextDouble()*.5}
                val life=when(kind){CORE->2.0+rng.nextDouble()*.5;BODY->11.5+rng.nextDouble()*2.5;else->15.0+rng.nextDouble()*4.0}
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
    // Emissions are drawn independently of the turret's model and its culling box.
    fun bounds(s:SentryHellpodEntity):AABB?=null
    @SubscribeEvent fun advance(event:TickEvent.ClientTickEvent) {
        if(event.phase!=TickEvent.Phase.END)return
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
            val hit=level.clip(ClipContext(p.p,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,null))
            if(hit.type==HitResult.Type.MISS)p.p=next else {
                p.p=hit.location.add(Vec3.atLowerCornerOf(hit.direction.normal).scale(.035))
                val surfaceNormal=Vec3.atLowerCornerOf(hit.direction.normal)
                val tangent=p.velocity.subtract(surfaceNormal.scale(p.velocity.dot(surfaceNormal)))
                p.velocity=tangent.scale(.10).add(surfaceNormal.scale(.16)).add(0.0,.04,0.0)
                // Retire an impacting flame immediately instead of sliding a large,
                // half-buried billboard along the floor with a perfectly flat bottom.
                if(p.kind!=SOOT)p.life=min(p.life,p.age+.65)
            }
            p.travelled+=p.p.distanceTo(p.previous)
            if(p.kind!=SOOT && p.travelled>p.limit)p.life=min(p.life,p.age+2.0)
            if(p.kind==BODY && !p.smoked && (progress>.66 || p.life-p.age<3.0)) {
                p.smoked=true
                if(p.seed<.105)born.add(Parcel(p.p,p.p,p.velocity.scale(.24).add(0.0,.035,0.0),0.0,15.0+p.seed*15.0,SOOT,(p.seed*7.13)%1.0,100.0))
            }
        }
        parcels.addAll(born)
    }
    @SubscribeEvent fun renderWorld(event:RenderLevelStageEvent) {
        if(event.stage!=RenderLevelStageEvent.Stage.AFTER_PARTICLES || parcels.isEmpty() || !AreaEffectRenderTypes.isReady())return
        val mc=Minecraft.getInstance();if(mc.level!==world)return
        val camera=event.camera.position;val rotation=mc.entityRenderDispatcher.cameraOrientation()
        val rr=org.joml.Vector3f(1f,0f,0f).rotate(rotation);val uu=org.joml.Vector3f(0f,1f,0f).rotate(rotation)
        val right=Vec3(rr.x.toDouble(),rr.y.toDouble(),rr.z.toDouble());val up=Vec3(uu.x.toDouble(),uu.y.toDouble(),uu.z.toDouble());val normal=right.cross(up)
        val buffers=mc.renderBuffers().bufferSource();buffers.endBatch()
        RenderSystem.enableDepthTest()
        val vc=buffers.getBuffer(AreaEffectRenderTypes.JET);val matrix=event.poseStack.last().pose()
        val partial=event.partialTick.toDouble()
        val sheets=ArrayList<FlameSheet>(parcels.size*4)
        for(p in parcels) {
            val age=(p.age-1.0+partial).coerceAtLeast(0.0);val t=(age/p.life).coerceIn(0.0,1.0)
            val fade=smooth(t/(if(p.kind==CORE).10 else .16))*(1.0-smooth((t-.58)/.42))
            if(fade<.002)continue
            val center=p.previous.lerp(p.p,partial)
            val radius=when(p.kind){CORE->.09+.13*smooth(t/.7);BODY->.12+.78*smooth(t/.8);OUTER->.16+1.08*smooth(t/.85);else->.30+1.35*smooth(t/.9)}
            // Use the actual eye-to-parcel direction: an off-axis camera must not see
            // a globally aligned billboard edge-on.
            val eyeNormal=camera.subtract(center).normalize()
            val speed=p.velocity.length();var along=p.velocity.subtract(eyeNormal.scale(p.velocity.dot(eyeNormal)))
            val projection=(along.length()/max(.001,speed)).coerceIn(0.0,1.0)
            along=if(along.lengthSqr()>.0001)along.normalize()else right
            val across=eyeNormal.cross(along).normalize()
            val stretched=when(p.kind){CORE->.42+speed*.19;BODY->radius*(4.2-2.0*t)+speed*.12;OUTER->radius*(2.6-1.0*t);else->radius*(1.75-.30*t)+speed*.08}
            val halfLength=radius+(stretched-radius)*projection
            if(!event.frustum.isVisible(AABB(center,center).inflate(max(stretched,radius)*1.25)))continue
            val opacity=fade*when(p.kind){CORE->.43;BODY->.40;OUTER->.40;else->.46}
            if(p.kind==BODY || p.kind==OUTER) {
                // A soft central layer fills end-on views. Three twisted world-space
                // layers supply parallax and a real cross-section around the flow axis.
                // Optical weights avoid multiplying opacity into solid opaque lumps.
                fun weighted(weight:Double)=1.0-(1.0-opacity).pow(weight)
                sheets.add(FlameSheet(center,along,across,halfLength,radius,t,p.seed,p.kind,weighted(.55)))
                val axis=if(speed>.001)p.velocity.scale(1.0/speed)else Vec3(0.0,1.0,0.0)
                val reference=if(abs(axis.y)<.92)Vec3(0.0,1.0,0.0)else Vec3(1.0,0.0,0.0)
                val side=axis.cross(reference).normalize();val vertical=side.cross(axis).normalize()
                for(layer in 0 until 3) {
                    val angle=p.seed*PI*2+layer*PI/3+age*.035
                    val cross=side.scale(cos(angle)).add(vertical.scale(sin(angle)))
                    val depth=axis.cross(cross).normalize()
                    // Suppress grazing sheets before they collapse into bright wires.
                    val facing=abs(depth.dot(eyeNormal))
                    val visibility=smooth((facing-.12)/.40)
                    if(visibility<.002)continue
                    val offset=depth.scale(radius*.20*sin(p.seed*17+layer*2.1))
                        .add(axis.scale(radius*.16*sin(p.seed*11+layer)))
                    sheets.add(FlameSheet(center.add(offset),axis,cross,stretched*.96,radius*.90,t,
                        (p.seed+layer*.271+.13)%1.0,p.kind,weighted(.34)*visibility))
                }
            }else sheets.add(FlameSheet(center,along,across,halfLength,radius,t,p.seed,p.kind,opacity))
        }
        // Sort every layer, including soot, together so smoke cannot become a front overlay.
        for(sheet in sheets.sortedBy{it.center.dot(normal)}) {
            for(v in arrayOf(floatArrayOf(-1f,-1f,0f,0f),floatArrayOf(1f,-1f,1f,0f),floatArrayOf(1f,1f,1f,1f),floatArrayOf(-1f,1f,0f,1f))) {
                val point=sheet.center.add(sheet.along.scale(v[0]*sheet.length)).add(sheet.across.scale(v[1]*sheet.width)).subtract(camera)
                vc.vertex(matrix,point.x.toFloat(),point.y.toFloat(),point.z.toFloat()).color(sheet.age.toFloat(),sheet.seed.toFloat(),sheet.kind/3f,sheet.opacity.toFloat()).uv(v[2],v[3]).endVertex()
            }
        }
        buffers.endBatch(AreaEffectRenderTypes.JET)
    }
}
