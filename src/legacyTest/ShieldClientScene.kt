package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModItems
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.CameraType
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraft.resources.ResourceLocation
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.math.*

/** Isolated acceptance scene, inert without the dedicated launch property. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object ShieldClientScene {
 private val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.shieldSmokeTest")
 private val origin=Vec3(8.5,64.0,8.5)
 private val out=Path.of("D:/Codex/projects/2026-09-19/new-chat/work/shield_generator/integration/client_capture")
 private var requested=false;private var ready=false;private var done=false
 private var marker:StratagemMarker?=null;private var generator:ShieldGeneratorEntity?=null
 @Volatile private var caseIndex=0
 @Volatile private var currentPhase=AbstractHellpodEntity.Phase.FALLING
 @Volatile private var currentAge=0
 @Volatile private var currentId:UUID?=null
 @Volatile private var finish=false
 private var activeStarted=-1L;private var fortySecondsTicks=-1L;private var brokeAt=-1L
 private var incoming=0;private var outgoing=0;private var maximumBodies=0
 private val flights=mutableMapOf<Projectile,Pair<Long,Boolean>>()
 private var guiRequest:String?=null
 private val captures=mutableSetOf<String>()
 private fun cleanup(level:ServerLevel){
  val bounds=AABB(origin.subtract(80.0,15.0,80.0),origin.add(80.0,45.0,80.0))
  level.getEntities(null,bounds).filter {it is AbstractHellpodEntity||it is GrenadierWallPanelEntity||it is HellpodCapEntity||it is StratagemMarker||it is Projectile}.forEach {it.discard()}
  flights.keys.toList().forEach {it.discard()};flights.clear()
 }
 private fun observer(level:ServerLevel,inside:Boolean=false){
  val p=level.server.playerList.players.first();val at=origin.add(if(inside)3.0 else 16.0,if(inside).2 else 5.0,if(inside)4.0 else 20.0)
  val target=origin.add(0.0,if(inside)2.0 else 4.5,0.0);val delta=target.subtract(at.add(0.0,p.eyeHeight.toDouble(),0.0))
  p.teleportTo(level,at.x,at.y,at.z,Math.toDegrees(atan2(-delta.x,delta.z)).toFloat(),-Math.toDegrees(atan2(delta.y,delta.horizontalDistance())).toFloat())
 }
 private fun prepare(level:ServerLevel){
  cleanup(level);level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.server);level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,level.server);level.dayTime=6000;level.setWeatherParameters(6000,0,false,false)
  for(x in -25..25)for(z in -25..30){val b=BlockPos.containing(origin.add(x.toDouble(),0.0,z.toDouble()));level.setBlock(b.below(),Blocks.SANDSTONE.defaultBlockState(),2);for(y in 0..14)level.setBlock(b.above(y),Blocks.AIR.defaultBlockState(),2)}
  val player=level.server.playerList.players.first();player.setGameMode(GameType.CREATIVE);player.inventory.setItem(0,net.minecraft.world.item.ItemStack(ModItems.SHIELD_GENERATOR.get()));player.inventory.setItem(1,net.minecraft.world.item.ItemStack.EMPTY);player.inventory.selected=1;observer(level)
  val m=ModEntities.STRATAGEM_MARKER.get().create(level)!!;m.setPos(origin);m.configure(ResourceLocation(MOD_ID,"shield_generator"),80,player.uuid,groundY=origin.y);level.addFreshEntity(m);marker=m;generator=null;currentId=null;activeStarted=-1;brokeAt=-1;ready=true
 }
 @SubscribeEvent fun server(e:TickEvent.ServerTickEvent){
  if(!enabled||!ready||done||e.phase!=TickEvent.Phase.END)return
  val level=e.server.overworld();var g=generator
  if(g==null){val id=marker?.podUUID ?: return;g=level.getEntity(id) as? ShieldGeneratorEntity ?: return;generator=g;currentId=id}
  currentPhase=g.phase;currentAge=g.phaseAge
  val bodies=level.getEntitiesOfClass(AbstractHellpodEntity::class.java,AABB(origin.subtract(10.0,10.0,10.0),origin.add(10.0,900.0,10.0))).size;maximumBodies=max(maximumBodies,bodies);check(bodies==1){"Duplicate bodies in the acceptance scene: $bodies"}
  for((p,v) in flights.toMap()){
   if(p.isRemoved){flights.remove(p);continue}
   if(!v.second&&p.position().distanceTo(g.shieldCenter())>ShieldGeneratorEntity.RADIUS+1){outgoing++;flights[p]=v.first to true}
   if(level.gameTime-v.first>20){p.discard();flights.remove(p)}
  }
  if(g.phase==AbstractHellpodEntity.Phase.ACTIVE){
   if(activeStarted<0)activeStarted=level.gameTime
   if(caseIndex==0){
    check(!g.shieldBroken){"Demonstration broke shield before timed retraction"}
    if(g.phaseAge>20&&g.phaseAge%35==0){val b=ModEntities.SENTRY_BULLET.get().create(level)!!;b.setPos(g.shieldCenter().add(ShieldGeneratorEntity.RADIUS+4,.7,3.0));b.setDamage(20f);b.setCustomGravity(0f);b.shoot(-1.0,0.0,0.0,7f,0f);level.addFreshEntity(b);incoming++}
    if(g.phaseAge>30&&g.phaseAge%80==0){val b=ModEntities.SENTRY_BULLET.get().create(level)!!;b.setPos(g.shieldCenter().add(0.0,1.0,0.0));b.setDamage(5f);b.setCustomGravity(0f);b.shoot(0.0,1.0,0.0,3f,0f);level.addFreshEntity(b);flights[b]=level.gameTime to false}
    if(g.phaseAge==180)observer(level,true)
    if(g.phaseAge==260)observer(level)
   }else {
    if(g.phaseAge==60){g.absorb(640f,g.shieldCenter().add(12.0,0.0,9.0));brokeAt=level.gameTime}
    if(brokeAt>=0){check(g.shieldHealth==0f&&!g.shieldOnline){"Broken shield restarted"};if(level.gameTime-brokeAt==130L)g.hurt(level.damageSources().playerAttack(level.server.playerList.players.first()),200f)}
   }
  }
  if(caseIndex==0&&g.phase==AbstractHellpodEntity.Phase.RETRACTING&&fortySecondsTicks<0){fortySecondsTicks=level.gameTime-activeStarted;check(fortySecondsTicks==800L){"Lifetime is $fortySecondsTicks ticks"};observer(level)}
  if(caseIndex==0&&g.phase==AbstractHellpodEntity.Phase.DONE){cleanup(level);caseIndex=1;prepare(level)}
  if(caseIndex==1&&brokeAt>=0&&level.gameTime-brokeAt>=175){check(g.shieldBroken){"Body destruction resurrected shield"};cleanup(level);ready=false;done=true;finish=true;LOGGER.info("SHIELD_SERVER_CLIENT_SCENE_DONE incoming={} outgoing={} lifetime={}",incoming,outgoing,fortySecondsTicks)}
 }
 @SubscribeEvent fun client(e:TickEvent.ClientTickEvent){
  if(!enabled||e.phase!=TickEvent.Phase.END)return
  val mc=Minecraft.getInstance();val server=mc.singleplayerServer ?: return;val player=mc.player ?: return
  if(mc.screen is net.minecraft.client.gui.screens.PauseScreen){mc.setScreen(null);mc.mouseHandler.grabMouse()}
  mc.options.cameraType=CameraType.FIRST_PERSON
  player.inventory.selected=1
  if(!requested){requested=true;Files.createDirectories(out);server.execute {prepare(server.overworld())}}
  val g=mc.level?.entitiesForRendering()?.filterIsInstance<ShieldGeneratorEntity>()?.firstOrNull {it.uuid==currentId}
  val name=when{
   caseIndex==0&&currentPhase==AbstractHellpodEntity.Phase.FALLING&&g!=null&&g.braking&&g.y>origin.y+4&&g.y<origin.y+18->"01_空投下降"
   caseIndex==0&&currentPhase==AbstractHellpodEntity.Phase.DEPLOYING&&currentAge in 14..18->"02_同步抬升"
   caseIndex==0&&currentPhase==AbstractHellpodEntity.Phase.DEPLOYING&&currentAge in 32..36->"03_生成器激活"
   caseIndex==0&&currentPhase==AbstractHellpodEntity.Phase.ACTIVE&&currentAge in 5..8->"04_护盾生成"
   caseIndex==0&&currentPhase==AbstractHellpodEntity.Phase.ACTIVE&&currentAge in 39..43->"05_球面蜂窝与命中波纹"
   caseIndex==0&&currentPhase==AbstractHellpodEntity.Phase.ACTIVE&&currentAge in 202..210->"06_透明护盾内侧"
   caseIndex==0&&currentPhase==AbstractHellpodEntity.Phase.RETRACTING&&currentAge in 5..9->"07_40秒结束关闭"
   caseIndex==0&&currentPhase==AbstractHellpodEntity.Phase.RETRACTING&&currentAge in 33..38->"08_生成器回收"
   caseIndex==1&&currentPhase==AbstractHellpodEntity.Phase.ACTIVE&&currentAge in 64..69->"09_护盾破裂"
   caseIndex==1&&currentPhase==AbstractHellpodEntity.Phase.ACTIVE&&currentAge in 166..172->"10_破盾后不再恢复"
   caseIndex==1&&currentPhase==AbstractHellpodEntity.Phase.DESTROYED->"11_本体可被玩家摧毁"
   else->null}
  if(name!=null&&!captures.contains(name))guiRequest=name
  if(finish){
   finish=false;check(ShieldGeneratorClient.renderedFrames>150&&ShieldGeneratorClient.renderedImpactFrames>8){"Shield or ripple not rendered"};check(outgoing>5&&incoming>15&&maximumBodies==1)
   val deploy=SoundTimeline.startedCount("sfx_shield_unfold");val retract=SoundTimeline.startedCount("sfx_shield_retract");val breaking=SoundTimeline.startedCount("sfx_shield_break");check(deploy>=2&&retract>=1&&breaking>=1){"Missing original audio: $deploy/$retract/$breaking"}
   val channelEffects=listOf("sfx_shield_unfold","sfx_shield_retract","sfx_shield_break","sfx_shield_maintain")
   check(channelEffects.all {SoundTimeline.allocatedChannel(it)}){"Original audio did not obtain playback channels: ${SoundTimeline.debugReport()}"}
   Files.writeString(out.resolve("result.json"),"""{"render_frames":${ShieldGeneratorClient.renderedFrames},"ripple_frames":${ShieldGeneratorClient.renderedImpactFrames},"incoming_projectiles":$incoming,"outgoing_crossings":$outgoing,"lifetime_ticks":$fortySecondsTicks,"radius_metres":${ShieldGeneratorEntity.RADIUS},"max_simultaneous_bodies":$maximumBodies,"deploy_sounds":$deploy,"retract_sounds":$retract,"break_sounds":$breaking,"broken_never_regenerated":true,"scene_cleaned":true,"audio_playback_channels_verified":true}""")
   LOGGER.info("SHIELD_CLIENT_PASSED {}",SoundTimeline.debugReport());mc.stop()
  }
 }
 @SubscribeEvent fun screenshot(e:net.minecraftforge.client.event.RenderGuiEvent.Post){
  if(!enabled)return;val name=guiRequest ?: return;if(captures.add(name))Screenshot.takeScreenshot(Minecraft.getInstance().mainRenderTarget).use {it.writeToFile(out.resolve("$name.png"))};guiRequest=null
 }
}
