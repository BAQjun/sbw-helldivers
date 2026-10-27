package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.OrbitalLaserEntity
import com.example.sbwhelldiver.init.ModSounds
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.culling.Frustum
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.*

/** Thick additive emissive shaft and a soft radial impact glow; the depth test remains enabled. */
class OrbitalLaserRenderer(context:EntityRendererProvider.Context):EntityRenderer<OrbitalLaserEntity>(context) {
 override fun getTextureLocation(e:OrbitalLaserEntity)=net.minecraft.resources.ResourceLocation(MOD_ID,"textures/mesh/white.png")
 override fun shouldRender(e:OrbitalLaserEntity,f:Frustum,x:Double,y:Double,z:Double):Boolean = e.active && e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z))
 override fun render(e:OrbitalLaserEntity,yaw:Float,partial:Float,p:PoseStack,b:MultiBufferSource,light:Int){
  if(!e.active)return
  val direction=e.origin.subtract(e.getPosition(partial)).normalize()
  val end=e.origin.subtract(e.getPosition(partial))
  var right=direction.cross(Vec3(0.0,1.0,0.0)).normalize();if(right.lengthSqr()<1e-8)right=Vec3(1.0,0.0,0.0)
  val up=direction.cross(right).normalize();val pose=p.last();val vc=b.getBuffer(DestroyerRenderTypes.GLOW)
  val strength=minOf(1.0,(e.level().gameTime-e.startedAt+partial)/3.0,(e.endsAt-e.level().gameTime-partial)/5.0).coerceIn(0.0,1.0).toFloat()
  fun vertex(v:Vec3,r:Float,g:Float,blue:Float){vc.vertex(pose.pose(),v.x.toFloat(),v.y.toFloat(),v.z.toFloat()).color(r*strength,g*strength,blue*strength,1f).uv(0f,0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.normal(),0f,1f,0f).endVertex()}
  for((radius,color) in listOf(1.1 to floatArrayOf(.09f,.025f,.001f),.65 to floatArrayOf(.35f,.15f,.01f),.34 to floatArrayOf(1f,.85f,.42f))){
   for(i in 0 until 12){
    fun ring(n:Int):Vec3 {val a=n*PI/6;return right.scale(cos(a)*radius).add(up.scale(sin(a)*radius))}
    val a=ring(i);val c=ring(i+1)
    for(v in listOf(a,end.add(a),end.add(c),c))vertex(v,color[0],color[1],color[2])
   }
  }
  OrbitalLaserImpact.render(e,partial,p,b)
  // Continuous radial falloff avoids a hard rectangular impact sprite under shader packs.
  val camera=Minecraft.getInstance().gameRenderer.mainCamera
  val rotation=camera.rotation();val cr=org.joml.Vector3f(1f,0f,0f).rotate(rotation);val cu=org.joml.Vector3f(0f,1f,0f).rotate(rotation)
  val a=Vec3(cr.x.toDouble(),cr.y.toDouble(),cr.z.toDouble());val c=Vec3(cu.x.toDouble(),cu.y.toDouble(),cu.z.toDouble())
  for(j in 0 until 6){val inner=j*.5;val outer=(j+1)*.5;val gain=(1.0-j/6.0).pow(3).toFloat()*.8f;val outerGain=(1.0-(j+1)/6.0).pow(3).toFloat()*.8f
   for(i in 0 until 24){val x=i*PI/12;val y=(i+1)*PI/12
    fun q(t:Double,r:Double)=a.scale(cos(t)*r).add(c.scale(sin(t)*r)).add(0.0,.18,0.0)
    vertex(q(x,inner),gain,gain*.5f,gain*.08f);vertex(q(y,inner),gain,gain*.5f,gain*.08f)
    vertex(q(y,outer),outerGain,outerGain*.5f,outerGain*.08f);vertex(q(x,outer),outerGain,outerGain*.5f,outerGain*.08f)
   }
  }
 }
}

@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object OrbitalLaserClient {
 private val active=WeakHashMap<OrbitalLaserEntity,LaserSound>()
 private var level:net.minecraft.client.multiplayer.ClientLevel?=null
 @SubscribeEvent fun tick(event:TickEvent.ClientTickEvent){
  if(event.phase!=TickEvent.Phase.END)return
  val mc=Minecraft.getInstance();val world=mc.level
  if(world!==level){active.values.forEach{it.finish();mc.soundManager.stop(it)};active.clear();level=world}
  if(world==null)return
  for(e in world.entitiesForRendering().filterIsInstance<OrbitalLaserEntity>()){
   if(e.active && e.distanceToSqr(mc.gameRenderer.mainCamera.position)<=96.0*96.0 && !mc.isPaused){
    FlameJetVisual.orbitalContactLight(world,e.position())
    OrbitalLaserImpact.tick(e)
   }
   if(!e.active || active.containsKey(e) || e.distanceToSqr(mc.gameRenderer.mainCamera.position)>128.0*128.0)continue
   val sound=LaserSound(e);active[e]=sound;mc.soundManager.play(sound)
   // Allow a brief loading stall without losing the initial cue; late observers only hear sustain.
   if(world.gameTime-e.startedAt<20)world.playLocalSound(e.x,e.y,e.z,ModSounds.EVENTS.getValue("sfx_orbital_laser_start").get(),SoundSource.AMBIENT,1f,1f,false)
  }
  val iterator=active.entries.iterator()
  while(iterator.hasNext()) {val item=iterator.next();if(!item.key.active){
   item.value.finish();world.playLocalSound(item.key.x,item.key.y,item.key.z,ModSounds.EVENTS.getValue("sfx_orbital_laser_stop").get(),SoundSource.AMBIENT,1f,1f,false);iterator.remove()
  }}
 }
 private class LaserSound(e:OrbitalLaserEntity):AbstractTickableSoundInstance(ModSounds.EVENTS.getValue("sfx_orbital_laser_loop").get(),SoundSource.AMBIENT,RandomSource.create()) {
  private val ref=WeakReference(e);private var fade=0f
  init {relative=false;looping=true;delay=0;volume=.01f;pitch=1f;x=e.x;y=e.y;z=e.z}
  fun finish(){stop()}
  override fun tick(){val e=ref.get();if(e==null || !e.active || Minecraft.getInstance().level!==e.level()){finish();return}
   x=e.x;y=e.y;z=e.z;fade=minOf(1f,fade+.2f);volume=fade*((e.endsAt-e.level().gameTime)/10f).coerceIn(0f,1f)
  }
 }
}
