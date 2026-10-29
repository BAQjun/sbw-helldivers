package com.example.sbwhelldiver.porttest

import com.atsuishio.superbwarfare.client.particle.CustomFlareParticle
import com.example.sbwhelldiver.*
import com.example.sbwhelldiver.client.ShaderPackCompat
import com.example.sbwhelldiver.entity.*
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
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent

/** Exercises the actual carrier and fast, short-lived rockets at an ordinary render distance. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object PortRocketTests {
 private val launch=System.currentTimeMillis()
 private var started=false
 private var ticks=0
 private var peak=0
 private var early=0
 private var fired=0
 private val shortPeaks=mutableMapOf<Int,Int>()
 private val tracked=mutableSetOf<Int>()
 private val xf=Particle::class.java.getDeclaredField("x").apply{isAccessible=true}
 private val yf=Particle::class.java.getDeclaredField("y").apply{isAccessible=true}
 private fun particles():List<Particle>{val f=ParticleEngine::class.java.getDeclaredField("particles").apply{isAccessible=true};return(f.get(Minecraft.getInstance().particleEngine) as Map<*,*>).values.flatMap{(it as Collection<*>).filterIsInstance<Particle>()}}
 @SubscribeEvent fun tick(e:ClientTickEvent.Post){
  if(!java.lang.Boolean.getBoolean("sbw_helldiver.rocketRegression"))return
  val mc=Minecraft.getInstance()
  if(System.currentTimeMillis()-launch>300000){LOGGER.error("ROCKET_REGRESSION_TIMEOUT");mc.stop();return}
  if(!started&&mc.screen is TitleScreen&&mc.overlay==null){
   started=true;mc.options.pauseOnLostFocus=false;mc.options.hideGui=true;mc.options.bobView().set(false);mc.options.renderDistance().set(8)
   mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Eagle rocket trail regression",GameType.CREATIVE,false,Difficulty.NORMAL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(6L,false,false),{r->r.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()},mc.screen!!);return
  }
  if(mc.overlay!=null)return
  val player=mc.player?:return;val server=mc.singleplayerServer?:return;ticks++
  if(ticks==1)server.execute{
   val level=server.overworld();level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.dayTime=6000
   for(x in -72..72)for(z in -32..32)level.setBlock(BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
   level.setBlock(BlockPos(60,79,-20),Blocks.SEA_LANTERN.defaultBlockState(),3);level.setDefaultSpawnPos(BlockPos(60,80,-20),161f)
   val p=server.playerList.getPlayer(player.uuid)!!;p.setGameMode(GameType.CREATIVE);p.teleportTo(level,60.0,90.0,-20.0,161f,-17f);p.abilities.flying=true;p.onUpdateAbilities()
   LOGGER.info("ROCKET_FIXTURE_READY")
  }
  player.setPos(60.0,90.0,-20.0);player.yRot=161f;player.yRotO=player.yRot;player.xRot=-17f;player.xRotO=player.xRot;player.abilities.flying=true
  if(ticks==80)server.execute{
   val level=server.overworld();val eagle=ModEntities.EAGLE.get().create(level)!!;eagle.configure(Vec3(0.0,80.0,0.0),Vec3(0.0,0.0,1.0),EagleAttack.ROCKETS,player.uuid,level.gameTime-72);check(level.addFreshEntity(eagle));LOGGER.info("ROCKET_CARRIER_START")
  }
  if(ticks in 90..104){player.yRot=174f;player.yRotO=174f;player.xRot=-25f;player.xRotO=-25f}
  if(ticks in 80..180){
   val count=particles().count{it is CustomFlareParticle&&yf.getDouble(it)>110};peak=maxOf(peak,count);if(ticks in 92..100)early=maxOf(early,count)
   mc.level!!.entitiesForRendering().filterIsInstance<Eagle110mmRocketEntity>().forEach{tracked.add(it.id)}
   if(ticks%5==0)LOGGER.info("ROCKET_TRAILS tick={} elevated={} tracked={}",ticks,count,tracked.size)
   if(ticks in listOf(100,110,120,130))Screenshot.grab(mc.gameDirectory,"rocket-flight-$ticks.png",mc.mainRenderTarget){LOGGER.info("ROCKET_SCREENSHOT {}",it.string)}
   if(ticks==145)server.execute{val eagle=server.overworld().allEntities.filterIsInstance<EagleEntity>().firstOrNull();fired=eagle?.fired?:-1}
  }
  if(ticks in 200..279){
   val stage=(ticks-200)/40;val age=(ticks-200)%40
   player.yRot=90f;player.yRotO=90f;player.xRot=-5f;player.xRotO=-5f
   if(age==0){
    ParticleEngine::class.java.getDeclaredMethod("clearParticles").apply{isAccessible=true}.invoke(mc.particleEngine)
    server.execute{
     val level=server.overworld()
     for(x in -3..0)level.setChunkForced(x,-2,true)
     for(y in 95..103)for(z in -22..-18)level.setBlock(BlockPos(10,y,z),Blocks.WHITE_CONCRETE.defaultBlockState(),3)
     val rocket=if(stage==0)ModEntities.EAGLE_110MM_ROCKET.get().create(level)!! else ModEntities.SENTRY_ROCKET.get().create(level)!!
     rocket.setPos(-40.0,98.0,-20.0);rocket.setCustomGravity(0f);rocket.shoot(1.0,0.0,0.0,35f,0f);rocket.tickCount=10;level.addFreshEntity(rocket)
     LOGGER.info("ROCKET_SHORT_LAUNCH stage={}",stage)
    }
   }
   if(age in 5..15){val count=particles().count{it is CustomFlareParticle&&xf.getDouble(it)<-15&&yf.getDouble(it) in 95.0..104.0};shortPeaks[stage]=maxOf(shortPeaks[stage]?:0,count)}
   if(age==10)server.execute{LOGGER.info("ROCKET_SHORT_STATE stage={} rockets={}",stage,server.overworld().allEntities.filter{it is Eagle110mmRocketEntity||it is SentryRocketEntity}.map{"${it.type}:${it.position()}:tick${it.tickCount}"})}
   if(age==12)Screenshot.grab(mc.gameDirectory,"rocket-short-$stage.png",mc.mainRenderTarget){LOGGER.info("ROCKET_SCREENSHOT {}",it.string)}
  }
  if(ticks==290){
   LOGGER.info("ROCKET_REGRESSION peak={} early={} fired={} tracked={} shaders={}",peak,early,fired,tracked.size,ShaderPackCompat.active())
   check(fired==6){"Incomplete rocket salvo: $fired"}
   if(System.getenv("ROCKET_BASELINE")!="1")check(early>50){"Early rocket trail missing: $early (peak $peak)"}
   LOGGER.info("ROCKET_SHORT_PEAKS {}",shortPeaks)
   check((0..1).all{(shortPeaks[it]?:0)>10}){"Short rocket trail missing: $shortPeaks"}
   LOGGER.info("ROCKET_REGRESSION_COMPLETED");mc.stop()
  }
 }
}
