package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.entity.OrbitalLaserEntity
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.Vec3
import java.util.WeakHashMap
import kotlin.math.*

/** White-hot impact jets and ballistic molten streaks, based on the supplied native preview.
 * Closed tapered geometry has no dark sprite border; no smoke/dust is emitted. */
object OrbitalLaserImpact {
 private data class Spark(var at:Vec3,var previous:Vec3,var velocity:Vec3,var age:Int,val life:Int,val seed:Double)
 private class State {val sparks=ArrayList<Spark>();var tick=Long.MIN_VALUE}
 private val states=WeakHashMap<OrbitalLaserEntity,State>()
 fun tick(e:OrbitalLaserEntity){
  val state=states.getOrPut(e){State()};val now=e.level().gameTime;if(state.tick==now)return
  if(state.tick!=Long.MIN_VALUE&&now-state.tick>1)state.sparks.clear()
  state.tick=now
  val iterator=state.sparks.iterator()
  while(iterator.hasNext()){
   val s=iterator.next();s.age++;if(s.age>=s.life){iterator.remove();continue}
   s.previous=s.at;s.velocity=s.velocity.scale(.97).add(0.0,-.055,0.0)
   val next=s.at.add(s.velocity)
   val hit=e.level().clip(ClipContext(s.at,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e))
   if(hit.type==net.minecraft.world.phys.HitResult.Type.BLOCK){iterator.remove();continue};s.at=next
  }
  val rng=e.level().random;val origin=e.position()
  fun emit(){
   val angle=rng.nextDouble()*PI*2;val radial=.28+rng.nextDouble()*.65
   val velocity=Vec3(cos(angle)*radial,.45+rng.nextDouble()*.85,sin(angle)*radial)
   val at=origin.add(cos(angle)*.14,.07,sin(angle)*.14)
   state.sparks.add(Spark(at,at,velocity,0,4+rng.nextInt(5),rng.nextDouble()))
  }
  repeat(6){emit()}
  if(now%5L==0L)repeat(3){emit()}
  if(state.sparks.size>72)state.sparks.subList(0,state.sparks.size-72).clear()
 }
 /** Independent 100–145 ms needle bursts: fast expansion, sharp tips and immediate extinction. */
 fun volumeInstances(partial:Float,camera:Vec3,frustum:net.minecraft.client.renderer.culling.Frustum):FloatArray {
  val world=net.minecraft.client.Minecraft.getInstance().level?:return FloatArray(0)
  val result=ArrayList<Float>()
  for(e in world.entitiesForRendering().filterIsInstance<OrbitalLaserEntity>()){
   if(!e.active||e.distanceToSqr(camera)>96.0*96.0)continue
   val at=e.getPosition(partial)
   if(!frustum.isVisible(net.minecraft.world.phys.AABB(at,at).inflate(5.0)))continue
   val time=(world.gameTime+partial)/20.0
   val fade=minOf(1.0,(world.gameTime-e.startedAt+partial)/3.0,(e.endsAt-world.gameTime-partial)/5.0).coerceIn(0.0,1.0)
   fun volume(center:Vec3,axis:Vec3,length:Double,radius:Double,seed:Double,power:Double){
    val p=center.subtract(camera)
    result.addAll(listOf(p.x.toFloat(),p.y.toFloat(),p.z.toFloat(),axis.x.toFloat(),axis.y.toFloat(),axis.z.toFloat(),length.toFloat(),radius.toFloat(),0f,(power*fade).toFloat(),4f,seed.toFloat()))
   }
   val seed=(e.id%997)/997.0
   // Compact flash roots the spray at the surface; it does not form a tall
   // soft plume or a circular billboard around the incoming beam.
   volume(at.add(0.0,.10,0.0),Vec3(0.0,1.0,0.0),.20,.44,seed,1.15)
   for(i in 0 until 10){
    val phase=time*(7.0+i*.31)+i*.618+seed*11.0
    val cycle=phase-floor(phase)
    val burst=floor(phase)
    val variation=.5+.5*sin(burst*12.9898+i*7.31)
    val spread=.5+.5*sin(burst*4.71+i*2.37)
    val angle=i*PI*2/10+seed*6.0+sin(burst*2.17+i)*.38
    // Tall lances mixed with flatter, radial splashes. Each grows in a few
    // milliseconds and dies independently, rather than pulsing as a star.
    val slope=if(i%3==0)1.25+spread*.75 else .28+spread*.65
    val axis=Vec3(cos(angle)*slope,1.0,sin(angle)*slope).normalize()
    val reach=(cycle/.16).coerceIn(0.0,1.0)
    val length=(2.2+variation*2.9)*reach
    val power=1.0-((cycle-.30)/.70).coerceIn(0.0,1.0)
    if(length<.08||power<.02)continue
    val root=at.add(cos(angle)*.10,.04,sin(angle)*.10)
    volume(root.add(axis.scale(length*.5)),axis,length*.5,.055+variation*.075,seed+i*.137,power*1.35)
   }
  }
  return result.toFloatArray()
 }
 fun render(e:OrbitalLaserEntity,partial:Float,p:PoseStack,b:MultiBufferSource){
  val state=states[e]?:return
  if(e.level().gameTime-state.tick>1||e.distanceToSqr(net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera.position)>96.0*96.0)return
  val offset=e.getPosition(partial);val pose=p.last();val vc=b.getBuffer(DestroyerRenderTypes.GLOW)
  fun vertex(v:Vec3,gain:Float,green:Float,blue:Float){vc.vertex(pose.pose(),v.x.toFloat(),v.y.toFloat(),v.z.toFloat()).color(gain,green,blue,1f).uv(0f,0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.normal(),0f,1f,0f).endVertex()}
  fun streak(head:Vec3,tail:Vec3,radius:Double,r:Float,g:Float,blue:Float){
   val axis=head.subtract(tail).normalize();var right=axis.cross(Vec3(0.0,1.0,0.0)).normalize()
   if(right.lengthSqr()<.01)right=Vec3(1.0,0.0,0.0);val up=axis.cross(right).normalize()
   for(i in 0 until 6){
    fun ring(n:Int,scale:Double):Vec3{val angle=n*PI/3;return right.scale(cos(angle)*radius*scale).add(up.scale(sin(angle)*radius*scale))}
    // A tapered rear and a closed bright nose keep individual streaks sharp in silhouette.
    val a=ring(i,1.0);val c=ring(i+1,1.0)
    vertex(head.add(a),r,g,blue);vertex(head.add(c),r,g,blue)
    vertex(tail.add(ring(i+1,.06)),0f,0f,0f);vertex(tail.add(ring(i,.06)),0f,0f,0f)
    val nose=head.add(axis.scale(radius*3.5))
    vertex(nose,r,g,blue);vertex(head.add(c),r,g,blue);vertex(head.add(a),r,g,blue);vertex(nose,r,g,blue)
   }
  }
  for(s in state.sparks){
   val t=(s.age-1.0+partial).coerceAtLeast(0.0)/s.life
   val fade=((1.0-t)*3.0).coerceIn(0.0,1.0).toFloat()
   val at=s.previous.lerp(s.at,partial.toDouble()).subtract(offset)
   val length=.12+s.velocity.length()*(.12+s.seed*.25)
   val tail=at.subtract(s.velocity.normalize().scale(length))
   val width=.008+s.seed*.009
   streak(at,tail,width*1.8,.12f*fade,.045f*fade,.003f)
   streak(at,tail,width,fade,fade*.87f,fade*.53f)
  }
 }
}
