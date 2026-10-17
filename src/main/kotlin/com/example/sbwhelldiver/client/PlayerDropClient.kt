package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.PlayerHellpodEntity
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.*
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.EventPriority
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import kotlin.math.*

/** Vertical descent camera, then a level view and a smooth push into first person. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object PlayerDropClient {
 private var original:CameraType?=null
 private var waiting:com.example.sbwhelldiver.network.DropPreparePacket?=null
 private var waitingTicks=0
 private var sent=false
 private var restoreTicks=0
 private var completed=false
 fun prepare(packet:com.example.sbwhelldiver.network.DropPreparePacket){original=Minecraft.getInstance().options.cameraType;if(java.lang.Boolean.getBoolean("sbw_helldiver.playerDropSmokeTest"))com.example.sbwhelldiver.LOGGER.info("DROP_CAMERA_PREPARE original={}",original);waiting=packet;waitingTicks=0;sent=false;restoreTicks=0;completed=false}
 fun cancel(token:java.util.UUID){if(waiting?.token==token){waiting=null;restore()}}
 private fun loading()=waiting!=null
 private val position by lazy {net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(net.minecraft.client.Camera::class.java,"m_90581_",Vec3::class.java)}
 fun mounted()=Minecraft.getInstance().player?.vehicle as? PlayerHellpodEntity
 // The entity can tick before its passenger packet. Keep the first entry sound
 // on the occupant route using the already acknowledged, reserved landing point.
 fun belongsToLocalPlayer(pod:PlayerHellpodEntity):Boolean {
  if(mounted()===pod)return true
  val order=waiting?:return false
  return pod.level().dimension().location()==order.dimension && abs(pod.x-order.point.x)<.05 && abs(pod.z-order.point.z)<.05
 }
 private fun restore(){original?.let{Minecraft.getInstance().options.cameraType=it};original=null}
 @SubscribeEvent fun tick(e:TickEvent.ClientTickEvent){
  if(e.phase!=TickEvent.Phase.END)return
  val mc=Minecraft.getInstance();val pod=mounted()
  if(pod==null){
   val w=waiting
   if(w!=null){
    waitingTicks++
    val l=mc.level
    if(waitingTicks>300){waiting=null;restore();return}
    if((!sent||waitingTicks%10==0)&&waitingTicks>=20&&l!=null&&l.dimension().location()==w.dimension){
     val center=net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(w.point))
     val loaded=(-2..2).all{x->(-2..2).all{z->l.hasChunk(center.x+x,center.z+z)}}
     if(loaded&&mc.levelRenderer.isChunkCompiled(net.minecraft.core.BlockPos.containing(w.point).below())){com.example.sbwhelldiver.network.BeaconNetwork.CHANNEL.sendToServer(com.example.sbwhelldiver.network.DropReadyPacket(w.token));sent=true}
    }
   }else if(original!=null){if(java.lang.Boolean.getBoolean("sbw_helldiver.playerDropSmokeTest"))com.example.sbwhelldiver.LOGGER.info("DROP_CAMERA_RESTORE saved={} before={} tick={}",original,mc.options.cameraType,restoreTicks);mc.options.cameraType=if(completed)CameraType.FIRST_PERSON else original!!;if(++restoreTicks>=3){original=null;restoreTicks=0}}
   return
  }
  waiting=null;restoreTicks=0;completed=true
  if(original==null)original=mc.options.cameraType
  mc.options.cameraType=CameraType.THIRD_PERSON_BACK
  mc.player?.let{it.yRot=pod.yRot;it.yRotO=pod.yRot;it.xRot=0f;it.xRotO=0f;it.yBodyRot=pod.yRot;it.yHeadRot=pod.yRot}
 }
 @SubscribeEvent fun logout(e:ClientPlayerNetworkEvent.LoggingOut){waiting=null;restore()}
 @SubscribeEvent fun input(e:MovementInputUpdateEvent){if(mounted()==null&&!loading())return
  mounted()?.let{pod->com.example.sbwhelldiver.network.BeaconNetwork.CHANNEL.sendToServer(com.example.sbwhelldiver.network.DropSteerPacket(pod.id,e.input.forwardImpulse,e.input.leftImpulse))}
  e.input.forwardImpulse=0f;e.input.leftImpulse=0f;e.input.jumping=false;e.input.shiftKeyDown=false
 }
 @SubscribeEvent fun interaction(e:InputEvent.InteractionKeyMappingTriggered){if(mounted()!=null||loading()){e.isCanceled=true;e.setSwingHand(false)}}
 @SubscribeEvent fun hide(e:RenderPlayerEvent.Pre){val pod=e.entity.vehicle as? PlayerHellpodEntity?:return
  if(pod.phase==Phase.FALLING||pod.phase==Phase.IMPACT||(pod.phase==Phase.DEPLOYING&&(pod.phaseAge<10||(e.entity===Minecraft.getInstance().player&&pod.visualPhaseAge(1f)>=44))))e.isCanceled=true
 }
 data class View(val eye:Vec3,val look:Vec3)
 private fun smooth(t:Double)=t.coerceIn(0.0,1.0).let{it*it*(3-2*it)}
 fun view(pod:PlayerHellpodEntity,partial:Float):View {
  val base=pod.visualOrigin(partial)
  val forward=Vec3.directionFromRotation(0f,pod.yRot)
  val top=base.add(0.0,22.0,0.0)
  if(pod.phase==Phase.FALLING||pod.phase==Phase.IMPACT)return View(top,base.add(0.0,-1.0,0.0))
  val age=if(pod.phase==Phase.DEPLOYING)pod.visualPhaseAge(partial) else 48.0
  val level=smooth((age-10)/16.0)
  val push=smooth((age-30)/18.0)
  val rider=pod.riderPosition(partial)
  val eyeHeight=Minecraft.getInstance().player?.eyeHeight?.toDouble()?:1.62
  val first=rider.add(0.0,eyeHeight,0.0)
  val rear=first.add(forward.scale(-4.2))
  val eye=top.lerp(rear,level).lerp(first,push)
  val angle=(1-level)*PI/2
  return View(eye,eye.add(forward.scale(cos(angle)*10)).add(0.0,-sin(angle)*10,0.0))
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) fun camera(e:ViewportEvent.ComputeCameraAngles){
  val pod=mounted()?:return;val v=view(pod,e.partialTick.toFloat())
  // The reference uses an external camera; keep the final camera outside nearby terrain.
  val from=if(pod.phase==Phase.FALLING)v.look else Vec3(pod.x,maxOf(pod.landingY.toDouble()+2.0,v.look.y),pod.z)
  val hit=pod.level().clip(net.minecraft.world.level.ClipContext(from,v.eye,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,pod))
  val eye=if(hit.type==net.minecraft.world.phys.HitResult.Type.MISS)v.eye else hit.location.add(from.subtract(v.eye).normalize().scale(.2))
  position.invoke(e.camera,eye)
  val delta=v.look.subtract(eye)
  e.yaw=pod.yRot;e.pitch=-Math.toDegrees(atan2(delta.y,delta.horizontalDistance())).toFloat()
  e.roll=0f
 }
 @SubscribeEvent fun hand(e:RenderHandEvent){if(mounted()!=null||loading())e.isCanceled=true}
 @SubscribeEvent fun overlay(e:RenderGuiOverlayEvent.Pre){if(mounted()!=null&&(e.overlay.id.namespace in setOf("superbwarfare","tacz")||e.overlay.id.path in setOf("crosshair","hotbar","experience_bar","jump_bar")))e.isCanceled=true}
}
