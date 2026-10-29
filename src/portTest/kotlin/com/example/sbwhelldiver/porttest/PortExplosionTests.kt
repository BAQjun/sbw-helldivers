package com.example.sbwhelldiver.porttest

import com.atsuishio.superbwarfare.client.particle.CustomFlareOption
import com.atsuishio.superbwarfare.client.particle.CustomFlareParticle
import com.example.sbwhelldiver.*
import com.example.sbwhelldiver.client.ShaderPackCompat
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.particle.ParticleEngine
import net.minecraft.client.particle.Particle
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.Difficulty
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.*
import org.lwjgl.opengl.GL33C.*
import org.lwjgl.BufferUtils
import kotlin.math.*

/** Measures smoke against a fixed backdrop, then exercises real server explosion packets. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object PortExplosionTests {
 private val launch=System.currentTimeMillis()
 private var started=false
 private var ticks=0
 private var reference:java.nio.ByteBuffer?=null
 private val visible=mutableMapOf<Int,Int>()
 private val actual=mutableSetOf<Int>()
 private val probes=mutableListOf<Particle>()
 private fun active()=java.lang.Boolean.getBoolean("sbw_helldiver.explosionRegression")
 private fun clear(){ParticleEngine::class.java.getDeclaredMethod("clearParticles").apply{isAccessible=true}.invoke(Minecraft.getInstance().particleEngine);probes.clear()}
 private fun allParticles():List<Particle>{val f=ParticleEngine::class.java.getDeclaredField("particles").apply{isAccessible=true};return (f.get(Minecraft.getInstance().particleEngine) as Map<*,*>).values.flatMap{(it as Collection<*>).filterIsInstance<Particle>()}}
 private fun capture(name:String){val mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget){LOGGER.info("EXPLOSION_SCREENSHOT {}",it.string)}}
 @SubscribeEvent fun tick(e:ClientTickEvent.Post){
  if(!active())return
  val mc=Minecraft.getInstance()
  if(System.currentTimeMillis()-launch>300000){LOGGER.error("EXPLOSION_REGRESSION_TIMEOUT");mc.stop();return}
  if(!started&&mc.screen is TitleScreen&&mc.overlay==null){
   started=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.renderDistance().set(8)
   mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Explosion smoke regression",GameType.CREATIVE,false,Difficulty.NORMAL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(5L,false,false),{r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()},mc.screen!!);return
  }
  if(mc.overlay!=null)return
  val player=mc.player?:return;val server=mc.singleplayerServer?:return;ticks++
  if(ticks==1)server.execute{
   val level=server.overworld();level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.dayTime=6000
   for(x in -24..24)for(z in -24..24)level.setBlock(BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
   for(a in -16..16)for(y in 80..94)for(pos in listOf(BlockPos(a,y,16),BlockPos(a,y,-16),BlockPos(16,y,a),BlockPos(-16,y,a)))level.setBlock(pos,Blocks.WHITE_CONCRETE.defaultBlockState(),3)
   level.setBlock(BlockPos(0,79,0),Blocks.SEA_LANTERN.defaultBlockState(),3);level.setDefaultSpawnPos(BlockPos(0,80,0),0f)
   val p=server.playerList.getPlayer(player.uuid)!!;p.setGameMode(GameType.CREATIVE);p.teleportTo(level,0.0,84.0,0.0,0f,0f);p.abilities.flying=true;p.onUpdateAbilities()
   LOGGER.info("EXPLOSION_FIXTURE_READY")
  }
  if(ticks in 20..499){
   val view=((ticks-20)/120).coerceIn(0,3);val age=(ticks-20)%120
   player.setPos(0.0,84.0,0.0);player.yRot=view*90f;player.yRotO=player.yRot;player.xRot=0f;player.xRotO=0f;player.abilities.flying=true
   if(age==0){clear();reference=null}
   if(age==50){val a=Math.toRadians(view*90.0);val x=-sin(a)*12;val z=cos(a)*12
    mc.particleEngine.createParticle(CustomFlareOption(.30f,.20f,.10f,120,.99f,8,0f,size=2f),x,85.6,z,0.0,0.0,0.0)?.let{probes.add(it)}
   }
   if(age==90)capture("explosion-probe-$view.png")
  }
  if(ticks in 500..859){
   val stage=(ticks-500)/120;val age=(ticks-500)%120
   player.setPos(0.0,84.0,0.0);player.yRot=0f;player.yRotO=0f;player.xRot=8f;player.xRotO=8f;player.abilities.flying=true
   if(age==0)clear()
   if(age==10)server.execute{
    val level=server.overworld();val type=listOf(ModEntities.ORBITAL_120_SHELL,ModEntities.ORBITAL_SHELL,ModEntities.EAGLE_500KG_BOMB)[stage]
    val shell=type.get().create(level) as com.example.sbwhelldiver.entity.SentryCannonShellEntity;shell.setPos(0.0,80.1,12.0);shell.causeExplode(shell.position());shell.discard();LOGGER.info("EXPLOSION_REAL_PACKET stage={}",stage)
   }
   if(age in 15..60 && allParticles().any{it is CustomFlareParticle})actual.add(stage)
   if(age in listOf(18,30,50))capture("explosion-real-$stage-$age.png")
  }
  if(ticks==865){
   LOGGER.info("EXPLOSION_REGRESSION_VISIBLE {} shaders={} actual={}",visible,ShaderPackCompat.active(),actual)
   check(actual.size==3){"Explosion particle packets missing: $actual"}
   if(System.getenv("EXPLOSION_BASELINE")!="1")check((0..3).all{(visible[it]?:0)>300}){"Smoke invisible: $visible"}
   LOGGER.info("EXPLOSION_REGRESSION_COMPLETED");mc.stop()
  }
 }
 @SubscribeEvent(priority=EventPriority.LOWEST) fun frame(e:RenderLevelStageEvent){
  if(!active()||e.stage!=RenderLevelStageEvent.Stage.AFTER_LEVEL||ShaderPackCompat.shadowPass()||ticks !in 20..499)return
  val view=((ticks-20)/120).coerceIn(0,3);val age=(ticks-20)%120
  if(age !in 30..40 && age !in 75..100)return
  val mc=Minecraft.getInstance();val target=mc.mainRenderTarget;val old=glGetInteger(GL_READ_FRAMEBUFFER_BINDING)
  val w=target.width;val h=target.height;val last=BufferUtils.createByteBuffer(w*h*4)
  glBindFramebuffer(GL_READ_FRAMEBUFFER,target.frameBufferId);glReadPixels(0,0,w,h,GL_RGBA,GL_UNSIGNED_BYTE,last);glBindFramebuffer(GL_READ_FRAMEBUFFER,old)
  if(age in 30..40){reference=last;return}
  val first=reference?:return;var dark=0
  for(y in h/3 until h*2/3)for(x in w/3 until w*2/3){val i=(y*w+x)*4;var delta=0;for(k in 0..2)delta+=(first[i+k].toInt()and 255)-(last[i+k].toInt()and 255);if(delta>60)dark++}
  visible[view]=maxOf(visible[view]?:0,dark)
 }
}
