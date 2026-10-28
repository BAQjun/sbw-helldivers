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
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** Local contact effects; the long shaft is depth-composited by OrbitalBeamGeometry. */
class OrbitalLaserRenderer(context:EntityRendererProvider.Context):EntityRenderer<OrbitalLaserEntity>(context) {
 override fun getTextureLocation(e:OrbitalLaserEntity)=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID,"textures/mesh/white.png")
 override fun shouldRender(e:OrbitalLaserEntity,f:Frustum,x:Double,y:Double,z:Double):Boolean = e.active && e.shouldRenderAtSqrDistance(e.distanceToSqr(x,y,z))
 override fun render(e:OrbitalLaserEntity,yaw:Float,partial:Float,p:PoseStack,b:MultiBufferSource,light:Int){
  if(!e.active)return
  OrbitalLaserImpact.render(e,partial,p,b)
 }
}

@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object OrbitalLaserClient {
 private val active=WeakHashMap<OrbitalLaserEntity,LaserSound>()
 private var level:net.minecraft.client.multiplayer.ClientLevel?=null
 @SubscribeEvent fun tick(event:net.neoforged.neoforge.client.event.ClientTickEvent.Post){
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
