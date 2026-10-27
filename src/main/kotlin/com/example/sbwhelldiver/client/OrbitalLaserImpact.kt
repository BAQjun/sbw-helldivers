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
 private data class Spark(var at:Vec3,var previous:Vec3,var velocity:Vec3,var age:Int,val life:Int,val seed:Double,val jet:Boolean)
 private class State {val sparks=ArrayList<Spark>();var tick=Long.MIN_VALUE}
 private val states=WeakHashMap<OrbitalLaserEntity,State>()
 fun tick(e:OrbitalLaserEntity){
  val state=states.getOrPut(e){State()};val now=e.level().gameTime;if(state.tick==now)return
  if(state.tick!=Long.MIN_VALUE&&now-state.tick>1)state.sparks.clear()
  state.tick=now
  val iterator=state.sparks.iterator()
  while(iterator.hasNext()){
   val s=iterator.next();s.age++;if(s.age>=s.life){iterator.remove();continue}
   s.previous=s.at;s.velocity=s.velocity.scale(.97).add(0.0,if(s.jet)-.025 else -.055,0.0)
   val next=s.at.add(s.velocity)
   val hit=e.level().clip(ClipContext(s.at,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,e))
   if(hit.type==net.minecraft.world.phys.HitResult.Type.BLOCK){iterator.remove();continue};s.at=next
  }
  val rng=e.level().random;val origin=e.position()
  fun emit(jet:Boolean){
   val angle=rng.nextDouble()*PI*2;val radial=if(jet).25+rng.nextDouble()*.55 else .24+rng.nextDouble()*.34
   val velocity=Vec3(cos(angle)*radial,if(jet)1.25+rng.nextDouble()*1.0 else .55+rng.nextDouble()*.55,sin(angle)*radial)
   val at=origin.add(cos(angle)*.14,.07,sin(angle)*.14)
   state.sparks.add(Spark(at,at,velocity,0,if(jet)2+rng.nextInt(2) else 7+rng.nextInt(6),rng.nextDouble(),jet))
  }
  repeat(5){emit(false)};repeat(6){emit(true)}
  if(now%5L==0L)repeat(5){emit(false)}
  if(state.sparks.size>128)state.sparks.subList(0,state.sparks.size-128).clear()
 }
 /** Independent 80–120 ms needle bursts: fast expansion, sharp tips and immediate extinction. */
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
   // Small continuous contact core, rather than a slow billowing column.
   volume(at.add(0.0,.18,0.0),Vec3(0.0,1.0,0.0),.32,.52,seed,1.25)
   for(i in 0 until 8){
    val phase=time*(9.0+i*.47)+i*.618+seed*11.0
    val cycle=phase-floor(phase)
    val burst=floor(phase)
    val variation=.5+.5*sin(burst*12.9898+i*7.31)
    val angle=i*PI*2/8+seed*6.0+sin(burst*2.17+i)*.30
    val reach=(cycle/.23).coerceIn(0.0,1.0)
    val height=(2.4+variation*2.0)*reach
    val power=(1.0-((cycle-.48)/.52).coerceIn(0.0,1.0))
    if(height<.08||power<.02)continue
    val axis=Vec3(cos(angle)*(.23+variation*.35),1.0,sin(angle)*(.23+variation*.35)).normalize()
    volume(at.add(axis.scale(height*.5)),axis,height*.5,.14+variation*.12,seed+i*.137,power*1.55)
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
   val length=if(s.jet)1.5+s.seed*1.7 else .40+s.velocity.length()*(.8+s.seed*1.1)
   val tail=at.subtract(s.velocity.normalize().scale(length))
   val width=if(s.jet).035+s.seed*.04 else .018+s.seed*.022
   streak(at,tail,width*2.2,.18f*fade,.065f*fade,.003f)
   streak(at,tail,width,fade,fade*(if(s.jet).97f else .86f),fade*(if(s.jet).84f else .48f))
  }
 }
}
