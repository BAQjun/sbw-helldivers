package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.StratagemMarker
import com.example.sbwhelldiver.init.ModSounds
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.sounds.SoundSource
import net.minecraft.util.RandomSource
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.lang.ref.WeakReference
import java.util.WeakHashMap

/** One camera-facing, additive beam; no textured transparency or geometry proportional to ship distance. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object OrbitalRailcannonClient {
 private val heard=WeakHashMap<StratagemMarker,Boolean>()
 private val active=mutableListOf<LockSound>()
 private var currentLevel:net.minecraft.client.multiplayer.ClientLevel?=null
 @SubscribeEvent fun tick(e:TickEvent.ClientTickEvent) {
  if(e.phase!=TickEvent.Phase.END)return
  val mc=Minecraft.getInstance()
  if(currentLevel!==mc.level){active.forEach{it.finish();mc.soundManager.stop(it)};active.clear();heard.clear();currentLevel=mc.level}
  val level=mc.level?:return
  for(m in level.entitiesForRendering().filterIsInstance<StratagemMarker>()) {
   if(!m.isOrbitalRailcannon || m.railLockStartedAt<0 || m.orbitalFiredAt>=0 || heard.containsKey(m))continue
   // Do not replay the start chirp to a listener who only loaded the entity halfway through locking.
   if(level.gameTime-m.railLockStartedAt>4)continue
   heard[m]=true
   if(m.orbitalTarget().distanceToSqr(mc.gameRenderer.mainCamera.position)>128.0*128.0)continue
   val sound=LockSound(m);active.add(sound);mc.soundManager.play(sound)
  }
  active.removeAll{it.isStopped};heard.keys.removeAll{it.isRemoved}
 }
 private class LockSound(marker:StratagemMarker):AbstractTickableSoundInstance(
  ModSounds.EVENTS.getValue("sfx_orbital_railcannon_lock").get(),SoundSource.AMBIENT,RandomSource.create()) {
  private val markerRef=WeakReference(marker)
  private val level=marker.level()
  init {relative=false;looping=false;volume=1f;pitch=1f;place(marker)}
  private fun place(m:StratagemMarker){val at=m.orbitalTarget();x=at.x;y=at.y;z=at.z}
  fun finish(){volume=0f;stop()}
  override fun tick(){val m=markerRef.get();if(m==null || m.isRemoved || m.orbitalFiredAt>=0 || Minecraft.getInstance().level!==level){finish();return};place(m)}
 }
 @SubscribeEvent fun render(e:RenderLevelStageEvent) {
  if(e.stage!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return
  val mc=Minecraft.getInstance();val level=mc.level?:return
  val camera=e.camera.position;val pose=e.poseStack.last();val buffers=mc.renderBuffers().bufferSource()
  var drew=false
  for(m in level.entitiesForRendering().filterIsInstance<StratagemMarker>()) {
   if(!m.railBeamActive(e.partialTick))continue
   val origin=m.orbitalOrigin?:continue
   val target=m.orbitalTarget(e.partialTick)
   // Keep real depth near the target; do not project a large distant quad through the foreground.
   val end=target.add(origin.subtract(target).normalize().scale(minOf(384.0,origin.distanceTo(target))))
   val direction=end.subtract(target).normalize()
   var right=direction.cross(target.subtract(camera)).normalize()
   if(right.lengthSqr()<1e-8)right=Vec3(1.0,0.0,0.0)
   val vc=buffers.getBuffer(DestroyerRenderTypes.GLOW)
   for((width,gain) in listOf(.12 to .08f,.045 to .28f,.014 to 1f)) {
    val side=right.scale(width)
    for(v in listOf(target.add(side),end.add(side),end.subtract(side),target.subtract(side))) {
     val q=v.subtract(camera)
     vc.vertex(pose.pose(),q.x.toFloat(),q.y.toFloat(),q.z.toFloat()).color(gain,.015f*gain,.008f*gain,1f)
      .uv(0f,0f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.normal(),0f,1f,0f).endVertex()
    }
   }
   drew=true
  }
  if(drew)buffers.endBatch(DestroyerRenderTypes.GLOW)
 }
}
