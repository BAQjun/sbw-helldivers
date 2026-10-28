package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.stratagem.BeaconData
import com.example.sbwhelldiver.stratagem.Stratagems
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.math.*
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object EagleClientScene {
 private val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.eagleSmokeTest")
 private val out=Path.of(System.getProperty("sbw_helldiver.eagleCapture","D:/Codex/projects/2026-09-19/new-chat/work/eagle/integration/client_capture"))
 private val recordMotion=System.getProperty("sbw_helldiver.eagleRecordMotion","true").toBoolean()
 private val arrivedHudPasses=mutableSetOf<Int>();private val completedMarkers=mutableMapOf<Int,Double>()
 private var oldRenderDistance=-1
 private var arrivalChecked=false;private val arrivals=mutableListOf<Double>()
 private val motionWriter=java.util.concurrent.ThreadPoolExecutor(1,1,0L,java.util.concurrent.TimeUnit.MILLISECONDS,java.util.concurrent.ArrayBlockingQueue<Runnable>(8),java.util.concurrent.ThreadFactory{r->Thread(r,"EagleCaptureWriter").apply{isDaemon=true}})
 private var motionFrame=0;private var lastMotionClock=Double.NEGATIVE_INFINITY
 private var requested=false;private var prepared=false;private var finished=false
 @Volatile private var stage=0;@Volatile private var age=-1;@Volatile private var entityId:UUID?=null
 private var plane:EagleEntity?=null;private var beacon:StratagemBeacon?=null;private var marker:StratagemMarker?=null
 private var startTick=0L;private var finishedAt=-1L
 private val throwPoint=Vec3(8.5,65.0,-26.5)
 private val seen=mutableSetOf<UUID>();private val counts=mutableListOf<Int>();private var maxPlanes=0;private var frameCount=0
 private val captures=mutableSetOf<String>();private var capture:String?=null
 private fun cleanup(l:ServerLevel){l.allEntities.toList().filter {net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(it.type)?.namespace==MOD_ID||it is Projectile}.forEach{it.discard()}}
 private fun observer(l:ServerLevel,target:Vec3,height:Double=if(stage==0)75.0 else 35.0){
  val p=l.server.playerList.players.first();val at=target.add(40.0,5.0,60.0);val v=target.add(0.0,height,0.0).subtract(at.add(0.0,p.eyeHeight.toDouble(),0.0));p.teleportTo(l,at.x,at.y,at.z,Math.toDegrees(atan2(-v.x,v.z)).toFloat(),-Math.toDegrees(atan2(v.y,v.horizontalDistance())).toFloat())
 }
 private fun setup(l:ServerLevel){
  cleanup(l);l.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.server);l.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,l.server);l.dayTime=6000;l.setWeatherParameters(6000,0,false,false)
  val p=l.server.playerList.players.first();p.setGameMode(GameType.CREATIVE);p.teleportTo(l,throwPoint.x,throwPoint.y,throwPoint.z,0f,0f)
  val id=if(stage==0)"eagle_airstrike" else "eagle_strafing_run";val card=if(stage==0)ModItems.EAGLE_AIRSTRIKE.get() else ModItems.EAGLE_STRAFING_RUN.get();val stack=ItemStack(ModItems.BEACON.get());BeaconData.setItems(stack,listOf(ItemStack(card)));BeaconData.activate(stack,Stratagems.get(id)!!)
  p.inventory.setItem(0,stack);p.inventory.selected=1
  val q=StratagemBeacon(ModEntities.STRATAGEM_BEACON.get(),p,l);q.item=stack.copy();q.shoot(0.0,-.15,1.0,1.2f,0f);l.addFreshEntity(q);beacon=q;plane=null;marker=null;entityId=null;seen.clear();arrivalChecked=false;lastMotionClock=Double.NEGATIVE_INFINITY;startTick=l.gameTime;finishedAt=-1;prepared=true;observer(l,Vec3(8.5,64.0,8.5))
 }
 @SubscribeEvent fun server(e:TickEvent.ServerTickEvent){
  if(!enabled||!prepared||finished||e.phase!=TickEvent.Phase.END)return
  val l=e.server.overworld();check(l.gameTime-startTick<450){"Eagle scene timed out at stage $stage"}
  if(marker==null){marker=l.allEntities.filterIsInstance<StratagemMarker>().firstOrNull();marker?.let {check(it.color==0xFF3434);observer(l,it.position())}}
  if(plane==null){val id=marker?.podUUID;if(id!=null){plane=l.getEntity(id) as? EagleEntity;entityId=id;val p=plane?:return;val expected=EagleFlight.heading(throwPoint,p.target,0f,stage==0);check(expected.dot(p.runDirection)>1-1e-8){"Throwing player's later movement changed flight axis"}}}
  val p=plane?:return;age=p.age.toInt()
  if(p.fired>0&&!arrivalChecked){arrivalChecked=true;val ticks=l.gameTime-marker!!.callStartedAt;check(ticks==EagleFlight.attackStart(stage==0).toLong()){"Call arrival drift: $ticks ticks"};arrivals.add(ticks/20.0)}
  if(age==EagleFlight.attackStart(stage==0)+1)observer(l,p.target,12.0)
  if(age==EagleFlight.attackStart(stage==0)+10)observer(l,p.target)
  if(age>=EagleFlight.attackStart(stage==0)&&!p.strikeComplete)check(marker?.isRemoved==false){"Beacon disappeared before payload completion"}
  if(marker?.isRemoved==true){check(p.strikeComplete){"Beacon disappeared before actual impacts"};completedMarkers.putIfAbsent(stage,(l.gameTime-marker!!.callStartedAt)/20.0)}
  val live=l.allEntities.filterIsInstance<EagleEntity>().count{!it.isRemoved};maxPlanes=max(maxPlanes,live);check(live<=1){"Duplicate planes"}
  l.allEntities.filter{it is EagleBombEntity||it is EagleCannonRoundEntity}.forEach{seen.add(it.uuid)}
  if(p.isRemoved){
   if(finishedAt<0){finishedAt=l.gameTime;counts.add(p.fired);check(p.fired==if(stage==0)6 else 100){"Aircraft removed before salvo: age=${p.age}, reason=${p.removalReason}, pos=${p.position()}, fired=${p.fired}, started=${p.startedAt}, now=${l.gameTime}"};check(seen.size==p.fired){"Missing visible spawned projectiles ${seen.size}/${p.fired}"}}
   if(l.gameTime-finishedAt>30){cleanup(l);if(stage==0){stage=1;setup(l)}else{prepared=false;finished=true;LOGGER.info("EAGLE_SCENE_SERVER_PASSED counts={}",counts)}}
  }
 }
 @SubscribeEvent fun client(e:TickEvent.ClientTickEvent){
  if(!enabled||e.phase!=TickEvent.Phase.END)return
  val mc=Minecraft.getInstance();val server=mc.singleplayerServer?:return;val player=mc.player?:return
  if(mc.screen is net.minecraft.client.gui.screens.PauseScreen){mc.setScreen(null);mc.mouseHandler.grabMouse()}
  mc.options.cameraType=net.minecraft.client.CameraType.FIRST_PERSON;player.inventory.selected=1
  if(!requested){requested=true;oldRenderDistance=mc.options.renderDistance().get();mc.options.renderDistance().set(6);mc.options.broadcastOptions();Files.createDirectories(out.resolve("motion"));server.execute{setup(server.overworld())}}
  val p=mc.level?.entitiesForRendering()?.filterIsInstance<EagleEntity>()?.firstOrNull{it.uuid==entityId}
  if(p!=null){frameCount++;check(EagleRenderer.Model.parts.sumOf{it.size}>40000){"Reviewed Eagle mesh missing"}}
  val t=age-EagleFlight.attackStart(stage==0)
  if(BeaconCallHud.label?.marker?.let{it.uuid==marker?.uuid&&it.hasArrived}==true)arrivedHudPasses.add(stage)
  val name=when{stage==0&&t in 4..7->"07_空袭已抵达";stage==1&&t in 4..7->"08_扫射已抵达";stage==0&&t in 13..15->"01_空袭高速通场";stage==0&&t in 20..22->"02_飞机实际投弹";stage==0&&t in 27..29->"03_空袭飞越信标";stage==1&&t in 9..11->"04_扫射沿投掷方向进入";stage==1&&t in 25..27->"05_连续机炮扫射";stage==1&&t in 39..41->"06_扫射结束拉升";else->null}
  if(name!=null&&!captures.contains(name))capture=name
  if(finished){check(arrivedHudPasses==setOf(0,1)&&completedMarkers.size==2){"Arrival HUD or strike completion missing: hud=$arrivedHudPasses, completed=$completedMarkers"};check(EagleAudio.flybys>=2&&EagleAudio.strafes>=1&&EagleAudio.allocated.containsAll(listOf("sfx_eagle_flyby","sfx_eagle_strafe","sfx_eagle_engine"))){"Eagle sounds did not obtain playback channels: ${EagleAudio.allocated}"};check(frameCount>100&&counts==listOf(6,100)&&maxPlanes==1){"Bad client scene: frames=$frameCount, shots=$counts, planes=$maxPlanes"};Files.writeString(out.resolve("result.json"),"""{"passes":2,"actual_beacon_throws":2,"bombs":${counts[0]},"cannon_rounds":${counts[1]},"max_simultaneous_aircraft":$maxPlanes,"client_frames":$frameCount,"flyby_sounds":${EagleAudio.flybys},"strafe_sounds":${EagleAudio.strafes},"audio_channels_verified":true,"arrival_seconds":${arrivals},"render_distance_chunks":6,"arrived_hud_verified":true,"beacons_wait_for_impacts":true,"beacon_finish_seconds":[${completedMarkers[0]},${completedMarkers[1]}],"scene_cleaned":true}""");LOGGER.info("EAGLE_CLIENT_PASSED captures={} frames={}",captures.size,frameCount);motionWriter.shutdown();motionWriter.awaitTermination(20,java.util.concurrent.TimeUnit.SECONDS);mc.options.renderDistance().set(oldRenderDistance);mc.options.broadcastOptions();mc.stop()}
 }
 @SubscribeEvent fun screenshot(e:net.minecraftforge.client.event.RenderGuiEvent.Post){
  if(!enabled)return
  val mc=Minecraft.getInstance()
  val n=capture;if(n!=null&&captures.add(n))Screenshot.takeScreenshot(mc.mainRenderTarget).use{it.writeToFile(out.resolve("$n.png"))};capture=null
  val p=mc.level?.entitiesForRendering()?.filterIsInstance<EagleEntity>()?.firstOrNull{it.uuid==entityId}?:return
  val clock=p.age+mc.frameTime
  if(recordMotion && clock>=EagleFlight.attackStart(p.airstrike)-16 && clock<=EagleFlight.attackEnd(p.airstrike)+40 && clock-lastMotionClock>=.4 && motionWriter.queue.remainingCapacity()>0){
   lastMotionClock=clock;val file="s${stage}_${motionFrame++.toString().padStart(5,'0')}.png"
   val frame=Screenshot.takeScreenshot(mc.mainRenderTarget);val capturedStage=stage
   motionWriter.execute {frame.use{it.writeToFile(out.resolve("motion/$file"))};Files.writeString(out.resolve("motion/frames.tsv"),"$capturedStage\t$clock\t$file\n",java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.APPEND)}
  }
 }
}
