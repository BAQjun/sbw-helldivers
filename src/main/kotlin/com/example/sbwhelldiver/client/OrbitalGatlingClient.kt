package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.StratagemMarker
import com.example.sbwhelldiver.init.ModSounds
import com.example.sbwhelldiver.stratagem.OrbitalGatlingPattern
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import kotlin.math.*

@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object OrbitalGatlingClient {
 private val buffers=MultiBufferSource.immediate(ByteBufferBuilder(32768))
 private val heard=WeakHashMap<StratagemMarker,Boolean>()
 private var level:net.minecraft.client.multiplayer.ClientLevel?=null
 private val sounds=mutableListOf<BarrageSound>()
 @SubscribeEvent fun tick(e:net.neoforged.neoforge.client.event.ClientTickEvent.Post){
  val mc=Minecraft.getInstance()
  if(mc.level!==level){sounds.forEach{it.finish();mc.soundManager.stop(it)};sounds.clear();heard.clear();level=mc.level}
  val world=mc.level?:return
  val eye=mc.gameRenderer.mainCamera.position
  for(marker in world.entitiesForRendering().filterIsInstance<StratagemMarker>()){
   if(!marker.isOrbitalGatling||marker.orbitalFiredAt<0||heard.containsKey(marker))continue
   val age=world.gameTime-marker.orbitalFiredAt
   if(age>=marker.orbitalFlightTicks-20&&age<marker.gatlingSettings.lastShotTime+marker.orbitalFlightTicks&&marker.distanceToSqr(eye)<=128*128){
    heard[marker]=true;val sound=BarrageSound(marker);sounds.add(sound);mc.soundManager.play(sound)
   }
  }
  sounds.removeAll{it.isStopped};heard.keys.removeAll{it.isRemoved}
 }
 private class BarrageSound(marker:StratagemMarker):AbstractTickableSoundInstance(ModSounds.EVENTS.getValue("sfx_orbital_gatling").get(),SoundSource.HOSTILE,RandomSource.create()){
  private val reference=WeakReference(marker)
  private val world=marker.level()
  init{looping=true;delay=0;relative=false;volume=.8f;pitch=1f;x=marker.x;y=marker.y+5;z=marker.z}
  fun finish(){volume=0f;stop()}
  override fun tick(){
   val m=reference.get();if(m==null||m.isRemoved||Minecraft.getInstance().level!==world){finish();return}
   val left=m.gatlingSettings.lastShotTime+m.orbitalFlightTicks-(world.gameTime-m.orbitalFiredAt)
   if(left<0){finish();return};volume=(.8*min(1.0,left/5)).toFloat()
  }
 }
 @SubscribeEvent fun render(e:RenderLevelStageEvent){
  if(e.stage!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||ShaderPackCompat.shadowPass())return
  val world=Minecraft.getInstance().level?:return
  val eye=e.camera.position;val pose=e.poseStack.last()
  for(marker in world.entitiesForRendering().filterIsInstance<StratagemMarker>()){
   if(!marker.isOrbitalGatling||marker.orbitalFiredAt<0)continue
   val source=marker.orbitalOrigin?:continue
   val s=marker.gatlingSettings
   val age=world.gameTime-marker.orbitalFiredAt+e.partialTick.getGameTimeDeltaPartialTick(false).toDouble()
   val first=OrbitalGatlingPattern.due(age-s.flightTicks,s)
   val end=OrbitalGatlingPattern.due(age,s)
   if(first>=end)continue
   val targets=marker.gatlingTargets()
   for(i in first until end){
    val progress=((age-s.shotTime(i))/s.flightTicks).coerceIn(0.0,1.0)
    val target=targets[i];val head=source.lerp(target,progress)
    val tail=source.lerp(target,(progress-.65/s.flightTicks).coerceAtLeast(0.0))
    val direction=target.subtract(source).normalize()
    val width=if(OrbitalGatlingPattern.highExplosive(i)).13 else .075
    var right=direction.cross(head.subtract(eye)).normalize().scale(width)
    if(right.lengthSqr()<1e-8)right=Vec3(width,0.0,0.0)
    val factor=min(1.0,192.0/head.distanceTo(eye).coerceAtLeast(1.0))
    val vc=buffers.getBuffer(DestroyerRenderTypes.GLOW)
    for(v in listOf(head.add(right),tail.add(right),tail.subtract(right),head.subtract(right))){
     val p=v.subtract(eye).scale(factor)
     vc.vertex(pose.pose(),p.x.toFloat(),p.y.toFloat(),p.z.toFloat()).color(1f,.78f,.38f,1f).uv(0f,0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.normal(),0f,1f,0f).endVertex()
    }
   }
  };buffers.endBatch()
 }
}
