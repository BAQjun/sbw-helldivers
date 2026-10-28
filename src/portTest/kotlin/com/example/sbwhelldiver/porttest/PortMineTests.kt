package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.*
import com.example.sbwhelldiver.client.*
import com.example.sbwhelldiver.config.ClientModelConfig
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.Difficulty
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.*
import org.lwjgl.opengl.GL33C.*
import org.lwjgl.BufferUtils
import kotlin.math.*

/** Isolated night scene: verify actual emissive framebuffer writes in four headings. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object PortMineTests {
 private val launch=System.currentTimeMillis()
 private var started=false
 private var ticks=0
 private var frame=0
 private val writes=mutableMapOf<Int,Int>()
 private var before:java.nio.ByteBuffer?=null
 private fun sample()=ticks in 65..375 || ticks in 435..745
 private fun view()=if(ticks>=430)4+((ticks-430)/80).coerceIn(0,3) else ((ticks-60)/80).coerceIn(0,3)
 private fun active()=java.lang.Boolean.getBoolean("sbw_helldiver.mineRegression")
 @SubscribeEvent fun tick(event:ClientTickEvent.Post){
  if(!active())return
  val mc=Minecraft.getInstance()
  if(System.currentTimeMillis()-launch>300000){LOGGER.error("MINE_REGRESSION_TIMEOUT");mc.stop();return}
  if(!started&&mc.screen is TitleScreen&&mc.overlay==null){
   started=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.bobView().set(false)
   ClientModelConfig.lowQualityModels.set(false)
   mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Mine emission regression",GameType.CREATIVE,false,Difficulty.NORMAL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(3L,false,false),{r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()},mc.screen!!);return
  }
  if(mc.overlay!=null)return
  val player=mc.player?:return;val server=mc.singleplayerServer?:return;ticks++
  if(ticks==1)server.execute{
   val level=server.overworld();level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.dayTime=18000
   for(x in -24..24)for(z in -24..24)level.setBlock(BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
   // The raised, lit creative observation pad does not illuminate the lamp samples.
   level.setBlock(BlockPos(0,81,0),Blocks.SEA_LANTERN.defaultBlockState(),3)
   level.setDefaultSpawnPos(BlockPos(0,82,0),0f)
   val p=server.playerList.getPlayer(player.uuid)!!;p.setGameMode(GameType.CREATIVE);p.teleportTo(level,0.0,82.0,0.0,0f,18f);p.abilities.flying=true;p.onUpdateAbilities()
   val types=listOf(ModEntities.ANTI_PERSONNEL_MINE,ModEntities.INCENDIARY_MINE,ModEntities.GAS_MINE,ModEntities.ANTI_TANK_MINE)
   for(side in 0..3)for((i,type)in types.withIndex())for(distance in listOf(5.0,10.0,18.0)){
    val a=Math.toRadians(side*90.0);val across=(i-1.5)*1.8;val x=across*cos(a)-distance*sin(a);val z=across*sin(a)+distance*cos(a)
    val mine=type.get().create(level)!!;mine.launch(net.minecraft.world.phys.Vec3(x,80.05,z),net.minecraft.world.phys.Vec3.ZERO,p.uuid,null);mine.yRot=0f;level.addFreshEntity(mine)
   }
   LOGGER.info("MINE_FIXTURE_READY")
  }
  if(ticks in 60..380 || ticks in 430..750){
   val base=if(ticks>=430)430 else 60
   val view=((ticks-base)/80).coerceIn(0,3)
   player.setPos(0.0,82.0,0.0);player.yRot=view*90f;player.yRotO=player.yRot;player.xRot=18f;player.xRotO=18f;player.abilities.flying=true
   if((ticks-base)%80==65)Screenshot.grab(mc.gameDirectory,"mine-${if(base==430)"low" else "high"}-heading-$view.png",mc.mainRenderTarget){LOGGER.info("MINE_SCREENSHOT {}",it.string)}
  }
  if(ticks==400){ClientModelConfig.lowQualityModels.set(true);mc.reloadResourcePacks().thenRun{LOGGER.info("MINE_LOW_MODELS_RELOADED")}}
  if(ticks==770){
   for((key,mesh)in AntiPersonnelMineRenderer.Model.meshes)check(mesh.emissiveVertexCount()>0){"No baked emission for $key"}
   LOGGER.info("MINE_REGRESSION_WRITES {} shaders={} {}",writes,ShaderPackCompat.active(),FieldVolumeRenderer.diagnostics())
   if(System.getenv("MINE_BASELINE")!="1")check((0..7).all{(writes[it]?:0)>5}){"Mine emission missing in a heading: $writes"}
   LOGGER.info("MINE_REGRESSION_COMPLETED");mc.stop()
  }
 }
 private fun read():java.nio.ByteBuffer{
  val target=Minecraft.getInstance().mainRenderTarget;val old=glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
  val result=BufferUtils.createByteBuffer(target.width*target.height*4)
  glBindFramebuffer(GL_READ_FRAMEBUFFER,target.frameBufferId);glReadPixels(0,0,target.width,target.height,GL_RGBA,GL_UNSIGNED_BYTE,result);glBindFramebuffer(GL_READ_FRAMEBUFFER,old);return result
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) fun before(e:RenderLevelStageEvent){
  if(active()&&e.stage==RenderLevelStageEvent.Stage.AFTER_LEVEL&&sample()&&!ShaderPackCompat.shadowPass()&&frame++%20==0)before=read()
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) fun after(e:RenderLevelStageEvent){
  if(!active()||e.stage!=RenderLevelStageEvent.Stage.AFTER_LEVEL||ShaderPackCompat.shadowPass())return
  val first=before?:return;before=null;val last=read();var changed=0
  for(i in 0 until first.capacity() step 4){var increase=0;for(k in 0..2)increase+=maxOf(0,(last[i+k].toInt()and 255)-(first[i+k].toInt()and 255));if(increase>30)changed++}
  val view=view();writes[view]=maxOf(writes[view]?:0,changed)
  LOGGER.info("MINE_EMISSION_FRAME heading={} vertices={} litPixels={}",view,MineGlowGeometry.vertexCount(),changed)
 }
}
