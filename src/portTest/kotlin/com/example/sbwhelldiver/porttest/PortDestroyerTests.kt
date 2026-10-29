package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.*
import com.example.sbwhelldiver.client.*
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.stratagem.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.world.Difficulty
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.core.registries.Registries
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.*
import org.lwjgl.opengl.GL33C.*
import org.lwjgl.BufferUtils

/** Calls and relocates a real synchronized ship beyond the terrain render distance. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object PortDestroyerTests {
 private val launch=System.currentTimeMillis()
 private var started=false
 private var ticks=0
 private var frame=0
 private var before:java.nio.ByteBuffer?=null
 private val writes=mutableMapOf<Int,Int>()
 private val samples=mutableMapOf<Int,Int>()
 private var query=0
 private fun active()=java.lang.Boolean.getBoolean("sbw_helldiver.destroyerRegression")
 private fun view()=((ticks-5)/120).coerceIn(0,3)
 private fun sample()=ticks in 85..480 && (ticks-5)%120 in 80..115
 @SubscribeEvent fun tick(e:ClientTickEvent.Post){
  if(!active())return
  val mc=Minecraft.getInstance()
  if(System.currentTimeMillis()-launch>300000){LOGGER.error("DESTROYER_REGRESSION_TIMEOUT");mc.stop();return}
  if(!started&&mc.screen is TitleScreen&&mc.overlay==null){
   started=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.renderDistance().set(8)
   mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Destroyer sky regression",GameType.CREATIVE,false,Difficulty.NORMAL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(4L,false,false),{r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()},mc.screen!!);return
  }
  if(mc.overlay!=null)return
  val player=mc.player?:return;val server=mc.singleplayerServer?:return;ticks++
  if(ticks==1)server.execute{
   val level=server.overworld();level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.dayTime=6000
   for(x in -12..12)for(z in -12..12)level.setBlock(BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
   level.setBlock(BlockPos(0,79,0),Blocks.SEA_LANTERN.defaultBlockState(),3);level.setDefaultSpawnPos(BlockPos(0,80,0),180f)
   val p=server.playerList.getPlayer(player.uuid)!!;p.setGameMode(GameType.CREATIVE);p.teleportTo(level,0.0,80.0,0.0,180f,-52f);p.abilities.flying=true;p.onUpdateAbilities()
   val beacon=ItemStack(ModItems.BEACON.get());BeaconData.setItems(beacon,listOf(ItemStack(ModItems.ORBITAL_PRECISION_STRIKE.get())));p.inventory.setItem(0,beacon)
   LOGGER.info("DESTROYER_FIXTURE_READY")
  }
  if(ticks in 5..480){
   val view=view();player.setPos(0.0,80.0,0.0);player.yRot=180f+view*90f;player.yRotO=player.yRot;player.xRot=-52f;player.xRotO=-52f;player.abilities.flying=true
   if((ticks-5)%120==0)server.execute{val p=server.playerList.getPlayer(player.uuid)!!;check(SuperDestroyers.move(p,view)){"Destroyer call failed"};LOGGER.info("DESTROYER_CALL direction={}",view)}
   if((ticks-5)%120==100){check(DestroyerClient.available()){"Destroyer snapshot unavailable"};Screenshot.grab(mc.gameDirectory,"destroyer-heading-$view.png",mc.mainRenderTarget){LOGGER.info("DESTROYER_SCREENSHOT {}",it.string)}}
  }
  if(ticks==485)server.execute{check(SuperDestroyers.depart(server.playerList.getPlayer(player.uuid)!!));LOGGER.info("DESTROYER_DEPART")}
  if(ticks==560){
   check(!DestroyerClient.available()){"Departure was not synchronized"}
   LOGGER.info("DESTROYER_REGRESSION_WRITES {} shaders={}",writes,ShaderPackCompat.active())
   LOGGER.info("DESTROYER_REGRESSION_SAMPLES {}",samples)
   if(System.getenv("DESTROYER_BASELINE")!="1")check((0..3).all{((if(ShaderPackCompat.active())samples else writes)[it]?:0)>100}){"Sky ship invisible: writes=$writes samples=$samples"}
   LOGGER.info("DESTROYER_REGRESSION_COMPLETED");mc.stop()
  }
 }
 private fun read():java.nio.ByteBuffer{
  val target=Minecraft.getInstance().mainRenderTarget;val old=glGetInteger(GL_READ_FRAMEBUFFER_BINDING);val draw=glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING)
  val result=BufferUtils.createByteBuffer(target.width*target.height*4)
  glBindFramebuffer(GL_READ_FRAMEBUFFER,draw);glReadPixels(0,0,target.width,target.height,GL_RGBA,GL_UNSIGNED_BYTE,result);glBindFramebuffer(GL_READ_FRAMEBUFFER,old);return result
 }
 @SubscribeEvent(priority=EventPriority.HIGHEST) fun before(e:RenderLevelStageEvent){
  if(active()&&e.stage==RenderLevelStageEvent.Stage.AFTER_SKY&&sample()&&!ShaderPackCompat.shadowPass()&&frame++%10==0){
   before=read()
   // Iris writes its G-buffer attachments rather than the vanilla color attachment.
   // Count depth-passing fragments as well, then inspect the composed screenshots.
   if(glGetQueryi(GL_SAMPLES_PASSED,GL_CURRENT_QUERY)==0){query=glGenQueries();glBeginQuery(GL_SAMPLES_PASSED,query)}
  }
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) fun after(e:RenderLevelStageEvent){
  if(!active()||e.stage!=RenderLevelStageEvent.Stage.AFTER_SKY||ShaderPackCompat.shadowPass())return
  val first=before?:return;before=null
  if(query!=0){glEndQuery(GL_SAMPLES_PASSED);val count=glGetQueryObjectui(query,GL_QUERY_RESULT);glDeleteQueries(query);query=0;samples[view()]=maxOf(samples[view()]?:0,count)}
  val last=read();var changed=0
  for(i in 0 until first.capacity() step 4){var delta=0;for(k in 0..2)delta+=kotlin.math.abs((last[i+k].toInt()and 255)-(first[i+k].toInt()and 255));if(delta>30)changed++}
  val view=view();writes[view]=maxOf(writes[view]?:0,changed)
  LOGGER.info("DESTROYER_SKY_FRAME heading={} changedPixels={}",view,changed)
 }
}
