package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.client.*
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.marking.*
import net.minecraft.client.Minecraft
import net.minecraft.client.CameraType
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderGuiEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.nio.file.Files
import java.nio.file.Path

/** Real commands and passenger replication, including first-use and repeated camera transitions. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object PlayerDropClientScene {
 private val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.playerDropSmokeTest")
 private val out=Path.of("D:/Project/sbw_helldiver/.codex/playerdrop0375/client")
 private var requested=false
 @Volatile private var age=0
 @Volatile private var done=false
 @Volatile private var pass=0
 private var pod:PlayerHellpodEntity?=null
 private var frames=0
 private var centeredFrames=0
 private var standingFrames=0
 private var steeringMoved=false
 private var stoppedAtImpact=false
 private var firstLaunchZ=0.0
 private var clientSteerIssued=false
 private var exitChecks=0
 private var markDisabled=false
 private var markEnabled=false
 private val captures=mutableSetOf<String>()
 private val originals=mutableMapOf<BlockPos,net.minecraft.world.level.block.state.BlockState>()
 private var oldMark=true
 private var oldMode=net.minecraft.world.level.GameType.CREATIVE
 private var oldPos=Vec3.ZERO
 private var oldDay=6000L
 private val origin=Vec3(8.5,96.0,8.5)
 private fun clean(l:ServerLevel){
  l.allEntities.filter{it is AbstractHellpodEntity||it is HellpodCapEntity||it is StratagemMarker||it is GrenadierWallPanelEntity||it is EagleEntity||it is Projectile}.toList().forEach{it.discard()}
 }
 private fun command(l:ServerLevel,s:String):Int {val p=l.server.playerList.players.first();return l.server.commands.performPrefixedCommand(p.createCommandSourceStack().withPermission(4),s)}
 private fun setup(l:ServerLevel){
  clean(l);val p=l.server.playerList.players.first();oldMode=p.gameMode.gameModeForPlayer;oldPos=p.position();oldDay=l.dayTime;oldMark=MarkSettings.get(l.server).enabled
  Files.createDirectories(out)
  for(x in -18..18)for(z in -18..18){val b=BlockPos.containing(origin).offset(x,-1,z);originals[b]=l.getBlockState(b);l.setBlock(b,if(x%5==0||z%5==0)Blocks.CUT_SANDSTONE.defaultBlockState() else Blocks.SANDSTONE.defaultBlockState(),2)}
  p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.teleportTo(l,origin.x,origin.y,origin.z,0f,0f);l.dayTime=6000
  command(l,"helldivers marking false");age=1
 }
 private fun launch(l:ServerLevel){
  check(command(l,"helldivers drop @s 8.5 96 8.5")==1)
  check(PlayerDrops.pending(l.server.playerList.players.first().uuid)!=null)
  check(l.allEntities.filterIsInstance<PlayerHellpodEntity>().none{!it.isRemoved})
 }
 private fun finish(l:ServerLevel){
  clean(l);for((b,s) in originals)l.setBlock(b,s,2);originals.clear()
  val p=l.server.playerList.players.first();p.noPhysics=false;p.fallDistance=0f;p.setGameMode(oldMode);p.teleportTo(l,oldPos.x,oldPos.y,oldPos.z,0f,0f);l.dayTime=oldDay;MarkSettings.set(l.server,oldMark);TargetMarks.clear();done=true
 }
 @SubscribeEvent fun server(e:TickEvent.ServerTickEvent){
  if(!enabled||!requested||age==0||done||e.phase!=TickEvent.Phase.END)return
  val l=e.server.overworld();age++
  if(l.server.playerList.players.first().vehicle is PlayerHellpodEntity){
   pod=l.server.playerList.players.first().vehicle as PlayerHellpodEntity
   if(pass==0&&pod!!.phase==AbstractHellpodEntity.Phase.IMPACT){steeringMoved=pod!!.z>origin.z+2;check(steeringMoved){"Actual movement keys did not adjust landing: z=${pod!!.z}"}}
  }
  when(age){
   15->{check(!MarkSettings.get(l.server).enabled);command(l,"helldivers marking true")}
   30->launch(l)
   280->{val p=l.server.playerList.players.first();check(p.vehicle==null&&!p.noPhysics&&p.y<100&&p.fallDistance==0f);exitChecks++;clean(l);pass=1}
   330->launch(l)
   580->{val p=l.server.playerList.players.first();check(p.vehicle==null&&!p.noPhysics&&p.y<100&&p.fallDistance==0f);exitChecks++;clean(l)}
   610->finish(l)
  }
 }
 @SubscribeEvent fun client(e:TickEvent.ClientTickEvent){
  if(!enabled||e.phase!=TickEvent.Phase.END)return
  val mc=Minecraft.getInstance();val server=mc.singleplayerServer?:return;val p=mc.player?:return
  if(mc.screen is net.minecraft.client.gui.screens.PauseScreen){mc.setScreen(null);mc.mouseHandler.grabMouse()}
  if(!requested){requested=true;mc.options.cameraType=CameraType.FIRST_PERSON;server.execute{setup(server.overworld())};return}
  if(age in 24..29)mc.options.cameraType=CameraType.FIRST_PERSON
  if(age in 5..12){markDisabled=markDisabled||!TargetMarkClient.enabled;check(TargetMarkClient.labels.isEmpty())}
  if(age in 20..28)markEnabled=markEnabled||TargetMarkClient.enabled
  if(age in 285..325)mc.options.cameraType=CameraType.THIRD_PERSON_FRONT
  if(age in 275..279){check(mc.options.cameraType==CameraType.FIRST_PERSON){"First view restored as ${mc.options.cameraType}"}}
  if(age in 585..605)check(mc.options.cameraType==CameraType.FIRST_PERSON)
  val d=p.vehicle as? PlayerHellpodEntity
  if(d!=null&&d.phase==AbstractHellpodEntity.Phase.DEPLOYING&&d.phaseAge>3){
   check(SoundTimeline.activeEffects(d).none{it.startsWith("sfx_hellpod_retro")||it.startsWith("sfx_hellpod_thruster")||it.startsWith("sfx_hellpod_player_")}){"Thruster audio continued after landing: ${SoundTimeline.activeMix(d)}"};stoppedAtImpact=true
  }
  if(d!=null){frames++;p.yRot+=37f;p.xRot=25f
    if(d.phase==AbstractHellpodEntity.Phase.FALLING && d.braking && d.brakeAge>=14 && d.flightSampleTime<d.impactDeadline-2){
     val mix=SoundTimeline.activeMix(d)
     check((mix["sfx_hellpod_retro_layer_03"]?:-1f)>.99f && (mix["sfx_hellpod_retro_layer_01"]?:-1f)==0f){"Incorrect onboard near/far mix: $mix"}
     check((mix["sfx_hellpod_player_entry_03"]?:-1f)==0f){"High wind was not stopped on ignition: $mix"}
     check(SoundTimeline.startedCount("sfx_hellpod_entry_layer_01")==0){"Player heard payload incoming event"}
    }
   if(pass==0&&d.phase==AbstractHellpodEntity.Phase.FALLING&&d.phaseAge in 20..45)mc.options.keyUp.isDown=true else mc.options.keyUp.isDown=false
   val v=PlayerDropClient.view(d,1f);check(v.eye.x.isFinite()&&v.eye.y.isFinite()&&v.eye.distanceTo(v.look)>1)
  }
  if(d==null)mc.options.keyUp.isDown=false
  if(done){
   check(SoundTimeline.startedWithPrefix("sfx_hellpod_player_thruster_")==10){"Incomplete occupant startup event"}
   check(stoppedAtImpact&&SoundTimeline.startedCount("sfx_hellpod_retro_start")==2&&SoundTimeline.startedCount("sfx_hellpod_retro_layer_01")==2)
   check(SoundTimeline.allocatedChannel("sfx_hellpod_retro_start")&&SoundTimeline.allocatedChannel("sfx_hellpod_retro_layer_03")){"Native thruster sound did not acquire playback channels"}
   check(steeringMoved&&frames>150&&standingFrames>8&&centeredFrames>60&&exitChecks==2&&markDisabled&&markEnabled)
   Files.writeString(out.resolve("result.json"),"""{"first_and_repeated_drop":true,"drop_commands":2,"camera_frames":$frames,"centered_flight_frames":$centeredFrames,"standing_exit_frames":$standingFrames,"smooth_first_person_exit":true,"marking_setting_synced":true,"safe_exit":true,"movement_keys_adjust_landing":true,"thruster_start_and_loop_channels":true,"thrusters_stopped_at_impact":true,"scene_cleaned":true,"captures":${captures.size}}""")
   LOGGER.info("PLAYER_DROP_CLIENT_PASSED frames={} captures={}",frames,captures.size);mc.stop()
  }
 }
 @SubscribeEvent fun standing(e:net.minecraftforge.client.event.RenderPlayerEvent.Post){
  if(!enabled)return
  val d=e.entity.vehicle as? PlayerHellpodEntity?:return
  if(d.phase!=AbstractHellpodEntity.Phase.DEPLOYING||d.phaseAge<30)return
  check(!e.renderer.model.riding&&kotlin.math.abs(e.renderer.model.leftLeg.xRot)<.4&&kotlin.math.abs(e.renderer.model.rightLeg.xRot)<.4){"Exit player still rendered in sitting pose"}
  standingFrames++
 }
 @SubscribeEvent fun projected(e:net.minecraftforge.client.event.RenderLevelStageEvent){
  if(!enabled||e.stage!=net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL)return
  val d=Minecraft.getInstance().player?.vehicle as? PlayerHellpodEntity?:return
  if(d.phase!=AbstractHellpodEntity.Phase.FALLING||d.phaseAge<3)return
  val v=PlayerDropClient.view(d,e.partialTick)
  val actual=Vec3.directionFromRotation(e.camera.xRot,e.camera.yRot)
  val desired=v.look.subtract(e.camera.position).normalize()
  check(e.camera.xRot>89.9&&actual.dot(desired)>.995){"Final camera no longer faces pod: dir=$actual wanted=$desired"}
  check(e.camera.position.distanceTo(v.eye)<.1){"Final camera displaced from shared flight position"}
  centeredFrames++
 }
 @SubscribeEvent fun capture(e:RenderGuiEvent.Post){
  if(!enabled)return
  val mc=Minecraft.getInstance();val d=mc.player?.vehicle as? PlayerHellpodEntity?:return
  val name=when{
   d.phase==AbstractHellpodEntity.Phase.FALLING&&d.phaseAge in 5..12->"${pass}_01_high_follow"
   d.phase==AbstractHellpodEntity.Phase.FALLING&&d.phaseAge in 119..124->"${pass}_02_top_approach"
   d.phase==AbstractHellpodEntity.Phase.IMPACT&&d.phaseAge in 2..7->"${pass}_03_impact"
   d.phase==AbstractHellpodEntity.Phase.DEPLOYING&&d.phaseAge in 12..16->"${pass}_04_rising"
   d.phase==AbstractHellpodEntity.Phase.DEPLOYING&&d.phaseAge in 36..40->"${pass}_05_exit_camera"
   else->null
  }?:return
  if(captures.add(name))Screenshot.takeScreenshot(mc.mainRenderTarget).use{it.writeToFile(out.resolve("$name.png"))}
 }
 @SubscribeEvent fun stopping(e:net.minecraftforge.event.server.ServerStoppingEvent){if(enabled&&requested&&!done)finish(e.server.overworld())}
}
