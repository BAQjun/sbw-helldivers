package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.StratagemMarker
import com.example.sbwhelldiver.init.ModSounds
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.sounds.SoundSource
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import java.util.WeakHashMap
import kotlin.math.*

/** Far flight is visual only; all impacts belong to real server projectiles. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object OrbitalBarrageClient {
 private val buffers=MultiBufferSource.immediate(ByteBufferBuilder(4096))
 private val heard=WeakHashMap<StratagemMarker,Int>()
 private var level:net.minecraft.client.multiplayer.ClientLevel?=null
 @SubscribeEvent fun tick(e:net.neoforged.neoforge.client.event.ClientTickEvent.Post){
  val mc=Minecraft.getInstance()
  if(mc.level!==level){heard.clear();level=mc.level}
  val world=mc.level?:return
  for(marker in world.entitiesForRendering().filterIsInstance<StratagemMarker>()){
   if(!marker.isOrbitalBarrage||marker.orbitalFiredAt<0)continue
   val s=marker.barrageSettings;val age=(world.gameTime-marker.orbitalFiredAt).toDouble()
   val previous=heard[marker]?:-1
   val due=com.example.sbwhelldiver.stratagem.OrbitalBarragePattern.due(age,s)
   val targets=marker.barrageTargets()
   for(index in previous+1 until due){
    if(index>=targets.size)break
    val at=targets[index]
    if(age-s.shotTime(index)<s.flightTicks&&at.distanceToSqr(mc.gameRenderer.mainCamera.position)<=128*128){
     world.playLocalSound(at.x,at.y,at.z,ModSounds.EVENTS.getValue("sfx_orbital_incoming").get(),SoundSource.HOSTILE,.9f,1f,false)
    }
   }
   heard[marker]=due-1
  }
  heard.keys.removeAll{it.isRemoved}
 }
 @SubscribeEvent fun render(e:RenderLevelStageEvent){
  if(e.stage!=RenderLevelStageEvent.Stage.AFTER_PARTICLES||ShaderPackCompat.shadowPass())return
  val world=Minecraft.getInstance().level?:return
  val eye=e.camera.position;val pose=e.poseStack.last()
  fun tracer(head:Vec3,tail:Vec3,width:Double){
   val direction=head.subtract(tail).normalize()
   var right=direction.cross(head.subtract(eye)).normalize().scale(width)
   if(right.lengthSqr()<1e-8)right=Vec3(width,0.0,0.0)
   val factor=min(1.0,192.0/head.distanceTo(eye).coerceAtLeast(1.0))
   val vc=buffers.getBuffer(DestroyerRenderTypes.GLOW)
   for(v in listOf(head.add(right),tail.add(right),tail.subtract(right),head.subtract(right))){
    val p=v.subtract(eye).scale(factor)
    vc.vertex(pose.pose(),p.x.toFloat(),p.y.toFloat(),p.z.toFloat()).color(1f,.8f,.4f,1f).uv(0f,0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.normal(),0f,1f,0f).endVertex()
   }
  }
  for(marker in world.entitiesForRendering().filterIsInstance<StratagemMarker>()){
   if(!marker.isOrbitalBarrage||marker.orbitalFiredAt<0)continue
   val source=marker.orbitalOrigin?:continue;val s=marker.barrageSettings
   val targets=marker.barrageTargets()
   val age=world.gameTime-marker.orbitalFiredAt+e.partialTick.getGameTimeDeltaPartialTick(false).toDouble()
   for(i in 0 until min(s.total,targets.size)){
    val t=(age-s.shotTime(i))/s.flightTicks
    if(t !in 0.0..1.0)continue
    val target=targets[i]
    tracer(source.lerp(target,t),source.lerp(target,(t-.8/s.flightTicks).coerceAtLeast(0.0)),if(s.kind=="orbital_120mm_he_barrage").19 else .24)
   }
  }
  buffers.endBatch()
 }
}
