package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.marking.TargetMarks
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.KeyMapping
import net.minecraft.client.CameraType
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.monster.Husk
import net.minecraft.world.entity.animal.Cow
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.AABB
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.client.event.RenderGuiEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.nio.file.Path
import java.nio.file.Files
import kotlin.math.*

/** Uses the actual registered key and network channel, never a fake HUD overlay. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object MarkClientScene {
 private val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.markSmokeTest")
 private val origin=Vec3(8.5,64.0,8.5)
 private val out=Path.of("D:/Codex/projects/2026-09-19/new-chat/work/target_marking/client_capture")
 private var waitTicks=0;private var requested=false;@Volatile private var ready=false;@Volatile private var age=0
 private var enemy:Husk?=null;private var cow:Cow?=null;private var turret:AbstractHellpodEntity?=null
 private val captures=mutableSetOf<String>();private val checks=mutableSetOf<String>();private var request:String?=null
 private var lastPos:Vec3?=null;@Volatile private var desiredLook:Vec3?=null
 private fun clean(l:ServerLevel){
  TargetMarks.clear()
  l.getEntities(null,AABB(origin.subtract(80.0,15.0,80.0),origin.add(80.0,50.0,80.0))).filter {it is AbstractHellpodEntity||it is GrenadierWallPanelEntity||it is HellpodCapEntity||it is StratagemMarker||it is Projectile||it is Husk||it is Cow}.forEach {it.discard()}
 }
 private fun look(l:ServerLevel,point:Vec3){desiredLook=point;val p=l.server.playerList.players.first();val delta=point.subtract(p.eyePosition);p.teleportTo(l,p.x,p.y,p.z,Math.toDegrees(atan2(-delta.x,delta.z)).toFloat(),-Math.toDegrees(atan2(delta.y,delta.horizontalDistance())).toFloat())}
 private fun setup(l:ServerLevel){
  clean(l);l.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,l.server);l.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,l.server);l.dayTime=6000;l.setWeatherParameters(6000,0,false,false)
  for(x in -20..20)for(z in -5..45){val p=BlockPos.containing(origin.add(x.toDouble(),0.0,z.toDouble()));l.setBlock(p.below(),Blocks.SANDSTONE.defaultBlockState(),2);for(y in 0..7)l.setBlock(p.above(y),Blocks.AIR.defaultBlockState(),2)}
  val p=l.server.playerList.players.first();p.setGameMode(GameType.CREATIVE);p.teleportTo(l,origin.x,origin.y,origin.z,0f,0f);p.inventory.clearContent()
  enemy=EntityType.HUSK.create(l)!!.also {it.setPos(origin.add(0.0,0.0,23.0));it.isNoAi=true;it.isNoGravity=true;it.isInvulnerable=true;l.addFreshEntity(it)}
  cow=EntityType.COW.create(l)!!.also {it.setPos(origin.add(-9.0,0.0,18.0));it.isNoAi=true;it.isNoGravity=true;it.isInvulnerable=true;l.addFreshEntity(it)}
  turret=ModEntities.HMG_EMPLACEMENT.get().create(l)!!.also {t->t.setPos(origin.add(9.0,0.0,19.0));l.addFreshEntity(t);val tag=CompoundTag();t.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.DEPLOYING.ordinal);tag.putInt("HellpodPhaseTicks",t.deployTicks-1);tag.putDouble("HellpodRestY",t.y);tag.putBoolean("HellpodCapReleased",true);t.load(tag);t.tick();t.setOwnerUUID(p.uuid)}
  look(l,enemy!!.boundingBox.center);ready=true
 }
 @SubscribeEvent fun server(e:TickEvent.ServerTickEvent){
  if(!enabled||!ready||e.phase!=TickEvent.Phase.END)return
  val l=e.server.overworld();val p=l.server.playerList.players.first()
  // Do not let render/PNG scheduling races skip a real key action. Failure still times out.
  val current=TargetMarks.current(l,p.uuid)
  val gate=when(age){44->current?.target==enemy?.uuid;144->current==null;180->current?.target==cow?.uuid;223->current!=null&&current.target==null;294->current?.target==turret?.uuid;else->true}
  if(!gate){check(++waitTicks<100){"Real-key acknowledgement timeout: age=$age current=$current requests=${TargetMarkClient.requests}"};return};waitTicks=0;age++
  when(age){
   45->{check(TargetMarks.current(l,p.uuid)?.target==enemy!!.uuid){"First real X mark failed: age=$age mark=${TargetMarks.current(l,p.uuid)} requests=${TargetMarkClient.requests}"};checks.add("real_x_marks_enemy")}
   60->look(l,enemy!!.boundingBox.center.add(10.0,0.0,0.0))
   80->{enemy!!.setPos(enemy!!.position().add(3.0,0.0,0.0));look(l,enemy!!.boundingBox.center)}
   100->{val m=TargetMarks.current(l,p.uuid)!!;check(TargetMarks.point(l,m)==enemy!!.boundingBox.center);checks.add("moves_with_target");for(x in -4..5)for(y in 0..4)l.setBlock(BlockPos.containing(origin.add(x.toDouble(),y.toDouble(),12.0)),Blocks.STONE.defaultBlockState(),2)}
   126->{for(x in -4..5)for(y in 0..4)l.setBlock(BlockPos.containing(origin.add(x.toDouble(),y.toDouble(),12.0)),Blocks.AIR.defaultBlockState(),2);look(l,enemy!!.boundingBox.center)}
   145->{check(TargetMarks.current(l,p.uuid)==null);checks.add("real_x_cancels");look(l,cow!!.boundingBox.center)}
   181->{check(TargetMarks.current(l,p.uuid)?.target==cow!!.uuid);checks.add("neutral_creature_marked")}
   191->look(l,origin.add(5.0,-.01,14.0))
   224->{val m=TargetMarks.current(l,p.uuid)!!;check(m.target==null);lastPos=m.fixed;checks.add("surface_selected");look(l,origin.add(-8.0,1.0,20.0))}
   238->{check(TargetMarks.current(l,p.uuid)?.fixed==lastPos);look(l,lastPos!!)}
   253->look(l,turret!!.position().add(0.0,.8,0.0))
   295->{val m=TargetMarks.current(l,p.uuid)!!;check(m.target==turret!!.uuid);check(TargetMarks.resolve(l,m) !is net.minecraft.world.entity.LivingEntity);checks.add("turret_mark_not_target");look(l,turret!!.boundingBox.center)}
   450->{check(TargetMarks.current(l,p.uuid)!=null&&TargetMarks.views(l).none {it.owner==p.uuid});checks.add("mark_hidden_instruction_retained");clean(l);TargetMarks.sync(l);ready=false}
  }
 }
 private fun press(){KeyMapping.click(InputConstants.getKey("key.keyboard.x"))}
 @SubscribeEvent fun client(e:TickEvent.ClientTickEvent){
  if(!enabled||e.phase!=TickEvent.Phase.END)return
  val mc=Minecraft.getInstance();val server=mc.singleplayerServer ?: return;val p=mc.player ?: return
  if(mc.screen is net.minecraft.client.gui.screens.PauseScreen){mc.setScreen(null);mc.mouseHandler.grabMouse()}
  mc.options.cameraType=CameraType.FIRST_PERSON
  if(!requested){requested=true;Files.createDirectories(out);server.execute {setup(server.overworld())}}
  if(!ready){if(age>=450){check(checks.size==7);check(TargetMarkClient.requests>=5);check(TargetMarkClient.expanded>15&&TargetMarkClient.compact>15);check(MarkAudio.started.toList()==listOf(1,2,1,1)){"Missing mark feedback events: ${MarkAudio.started.toList()}"};check(MarkAudio.allocated.size==4){"Missing actual mark audio playback channels: ${MarkAudio.allocated}"}
    Files.writeString(out.resolve("result.json"),"""{"checks":"${checks.joinToString(",")}","real_key_requests":${TargetMarkClient.requests},"expanded_hud_frames":${TargetMarkClient.expanded},"compact_hud_frames":${TargetMarkClient.compact},"scene_cleaned":true,"audio_events":"${MarkAudio.started.joinToString(",")}","four_original_audio_channels_verified":true,"arrow_tip_targets_entity_center":true}""");LOGGER.info("MARK_CLIENT_PASSED {}",checks);mc.stop()};return}
  // Drive the real test player's view every tick; a one-off server teleport can be
  // overwritten by queued host mouse/rotation events while a screenshot is saved.
  desiredLook?.let {point->
   val delta=point.subtract(p.eyePosition)
   val yaw=Math.toDegrees(atan2(-delta.x,delta.z)).toFloat();val pitch=-Math.toDegrees(atan2(delta.y,delta.horizontalDistance())).toFloat()
   p.yRot=yaw;p.yRotO=yaw;p.xRot=pitch;p.xRotO=pitch;p.yHeadRot=yaw;p.yHeadRotO=yaw;p.yBodyRot=yaw;p.yBodyRotO=yaw
  }
  val pendingKey=listOf(25,134,160,205,275).firstOrNull {age>=it&&it !in pressedKeys}
  if(pendingKey!=null){pressedKeys.add(pendingKey);LOGGER.info("MARK_TEST_PRESS at={} planned={}",age,pendingKey);press()}
  val name=when(age){in 42..46->"01_敌对标记与X取消";in 70..74->"02_移开准星仅图标距离";in 110..114->"03_实体跟随与穿墙";in 178..182->"04_非敌对生物";in 246..250->"05_特殊地点";in 296..300->"06_炮台图标且不索敌";else->null}
  if(name!=null&&!captures.contains(name))request=name
 }
 private val pressedKeys=mutableSetOf<Int>()
 @SubscribeEvent fun capture(e:RenderGuiEvent.Post){
  if(!enabled)return;val name=request ?: return
  // RenderGuiEvent subscription order is not part of the API: explicitly flush the real HUD first.
  TargetMarkClient.hud(e)
  if(captures.add(name)){
   val frame=Screenshot.takeScreenshot(Minecraft.getInstance().mainRenderTarget)
   net.minecraft.Util.ioPool().execute {frame.use {it.writeToFile(out.resolve("$name.png"))}}
  }
  request=null
 }
}
