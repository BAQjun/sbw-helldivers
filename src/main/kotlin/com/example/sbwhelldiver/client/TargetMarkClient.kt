package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.marking.TargetMarks
import com.example.sbwhelldiver.network.*
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.*
import net.minecraftforge.client.settings.KeyConflictContext
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector4f
import org.lwjgl.glfw.GLFW
import kotlin.math.*

/** HUD projection deliberately ignores world depth, so an established mark remains visible through walls. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object TargetMarkClient {
 val KEY=KeyMapping("key.sbw_helldiver.mark",KeyConflictContext.IN_GAME,InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_X,"key.categories.sbw_helldiver")
 private val ICON=ResourceLocation(MOD_ID,"textures/gui/target_mark.png")
 private val LOCATION=ResourceLocation(MOD_ID,"textures/gui/target_mark_location.png")
 private val CORNERS=ResourceLocation(MOD_ID,"textures/gui/target_mark_corners.png")
 private val POINTER=ResourceLocation(MOD_ID,"textures/gui/target_mark_pointer.png")
 private var entries=emptyList<TargetMarks.View>()
 private var receivedAt=0L
 data class Label(val mark:TargetMarks.View,val point:Vec3,val x:Int,val y:Int,val hovered:Boolean,val remaining:Int,val distance:Int)
 var labels=emptyList<Label>();private set
 var rendered=0;private set
 var expanded=0;private set
 var compact=0;private set
 var requests=0;private set
 fun receive(packet:MarkSnapshotPacket){
  val level=Minecraft.getInstance().level ?: return
  if(level.dimension().location()!=packet.dimension)return
  entries=packet.marks;receivedAt=level.gameTime
 }
 @SubscribeEvent fun disconnect(e:ClientPlayerNetworkEvent.LoggingOut){entries=emptyList();labels=emptyList()}
 @SubscribeEvent fun tick(e:TickEvent.ClientTickEvent){
  if(e.phase!=TickEvent.Phase.END)return
  val mc=Minecraft.getInstance();val player=mc.player ?: return
  if(mc.screen!=null){while(KEY.consumeClick()){};return}
  while(KEY.consumeClick()){
   val camera=mc.gameRenderer.mainCamera
   val direction=Vec3.directionFromRotation(camera.xRot,camera.yRot)
   val cancel=labels.any {it.hovered&&it.mark.owner==player.uuid}
   if(java.lang.Boolean.getBoolean("sbw_helldiver.markSmokeTest"))com.example.sbwhelldiver.LOGGER.info("MARK_REQUEST start={} direction={} cancel={}",camera.position,direction,cancel)
   BeaconNetwork.CHANNEL.sendToServer(MarkRequestPacket(camera.position,direction,cancel));requests++
  }
 }
 @SubscribeEvent fun project(e:RenderLevelStageEvent){
  if(e.stage!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return
  labels=emptyList();val mc=Minecraft.getInstance();val level=mc.level ?: return
  if(mc.options.hideGui||mc.screen!=null)return
  val view=Matrix4f().rotationY(PI.toFloat()).rotate(Quaternionf(e.camera.rotation()).conjugate())
  val matrix=Matrix4f(e.projectionMatrix).mul(view);val camera=e.camera.position
  val w=mc.window.guiScaledWidth;val h=mc.window.guiScaledHeight
  val projected=entries.mapNotNull {m->
   val remaining=(m.remaining-(level.gameTime-receivedAt)).toInt();if(remaining<=0)return@mapNotNull null
   val entity=if(m.entityId>=0)level.getEntity(m.entityId)?.takeIf {it.uuid==m.target} else null
   if(entity is net.minecraft.world.entity.LivingEntity&&!entity.isAlive)return@mapNotNull null
   val point=entity?.let {it.getPosition(e.partialTick).add(0.0,it.bbHeight*.5,0.0)} ?: m.point
   val delta=point.subtract(camera);val clip=matrix.transform(Vector4f(delta.x.toFloat(),delta.y.toFloat(),delta.z.toFloat(),1f))
   if(clip.w<=.001f)return@mapNotNull null
   val x=(clip.x/clip.w*.5+.5)*w;val y=(-clip.y/clip.w*.5+.5)*h
   if(x !in 12.0..w-12.0||y !in 14.0..h-24.0)return@mapNotNull null
   Label(m,point,x.roundToInt(),y.roundToInt()-30,abs(x-w*.5)<=15 && h*.5 in y-44.0..y+5.0,remaining,point.distanceTo(mc.player?.eyePosition ?: camera).roundToInt())
  }
  // At most one expanded label, even when two squad members mark the same creature.
  val focus=projected.filter {it.hovered}.minByOrNull {hypot(it.x-w*.5,it.y-h*.5)}
  labels=projected.map {it.copy(hovered=it===focus)}
 }
 @SubscribeEvent fun hud(e:RenderGuiEvent.Post){
  val mc=Minecraft.getInstance();if(mc.options.hideGui||mc.screen!=null)return
  val g=e.guiGraphics
  for(l in labels){
   val x=l.x;val y=l.y;val red=l.mark.hostile
   val color=if(red)0xffff7777.toInt() else 0xfff3f0dc.toInt()
   g.fill(x-11,y-11,x+12,y+12,0x58272727)
   g.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc()
   if(l.mark.icon.isNotEmpty())RenderSystem.setShaderColor(1f,1f,1f,1f) else if(red)RenderSystem.setShaderColor(1f,.42f,.42f,1f) else RenderSystem.setShaderColor(1f,.98f,.91f,1f)
   if(l.mark.icon.isEmpty())g.blit(if(l.mark.target==null)LOCATION else ICON,x-9,y-9,0f,0f,19,19,19,19)
   else g.blit(ResourceLocation(MOD_ID,l.mark.icon.replace("textures/item/","textures/gui/target_marks/")),x-9,y-9,19,19,0f,0f,64,64,64,64)
   RenderSystem.setShaderColor(1f,1f,1f,1f);RenderSystem.disableBlend()
   // Pixel-aligned corner mask follows the native four-quadrant lifetime display.
   val fraction=l.remaining.toDouble()/TargetMarks.LIFETIME
   g.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc()
   for(i in 0..3){
    val part=(fraction*4-i).coerceIn(0.0,1.0).toFloat();if(part<=0f)continue
    val sx=if(i==0||i==3)0 else 12;val sy=if(i<2)0 else 12
    RenderSystem.setShaderColor(1f,if(red).42f else .98f,if(red).42f else .91f,part)
    g.blit(CORNERS,x-11+sx,y-11+sy,sx.toFloat(),sy.toFloat(),11,11,23,23)
   }
   RenderSystem.setShaderColor(1f,1f,1f,1f);RenderSystem.disableBlend()
   val distance=Component.translatable("hud.sbw_helldiver.mark_distance",l.distance)
   g.drawString(mc.font,distance,x-mc.font.width(distance)/2,y+13,0xfff4f1df.toInt(),true)
   g.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShaderColor(if(red)1f else .96f,if(red).42f else .95f,if(red).42f else .88f,1f)
   g.blit(POINTER,x-5,y+24,0f,0f,11,7,11,7)
   RenderSystem.setShaderColor(1f,1f,1f,1f);RenderSystem.disableBlend()
   if(l.hovered){
    val textX=x+17;val name=mc.font.substrByWidth(l.mark.name,(mc.window.guiScaledWidth-textX-5).coerceAtLeast(16))
    g.drawString(mc.font,net.minecraft.locale.Language.getInstance().getVisualOrder(name),textX,y-9,color,true)
    if(l.mark.owner==mc.player?.uuid){
     val key=KEY.translatedKeyMessage;val keyW=max(9,mc.font.width(key)+4)
     g.fill(textX,y+2,textX+keyW,y+12,0xffe5e5db.toInt());g.drawString(mc.font,key,textX+2,y+3,0xff262626.toInt(),false)
     g.drawString(mc.font,Component.translatable("hud.sbw_helldiver.cancel_mark"),textX+keyW+4,y+3,0xfff4f1df.toInt(),true)
    }
    expanded++
   }else compact++
   rendered++
  }
 }
 @Mod.EventBusSubscriber(modid=MOD_ID,bus=Mod.EventBusSubscriber.Bus.MOD,value=[Dist.CLIENT])
 object Bindings{@SubscribeEvent fun register(e:RegisterKeyMappingsEvent){e.register(KEY)}}
}
