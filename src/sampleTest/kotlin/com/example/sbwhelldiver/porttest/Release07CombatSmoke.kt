package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.item.GuardDogBackpack
import net.minecraft.client.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack

/** Actual rendering and live-target firing check for the newly backported close-up renderers. */
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
object Release07CombatSmoke {
 var started=false
 private var age=0;private var stage=-1;private var sprayingTicks=0;private var previousDogPosition:net.minecraft.world.phys.Vec3?=null;private var maxFlightStep=0.0;private var rendered=false;private var effectCaptured=false
 @Volatile private var failure:Throwable?=null
 @Volatile private var ready=false
 private var poseDog:GuardDogEntity?=null
 private var poseWorld:net.minecraft.world.phys.Vec3?=null
 private var poseTick=0f
 private var posePhase:GuardDogEntity.Flight?=null
 private var poseSamples=0
 private var maxPoseStep=0.0
 private var victim:net.minecraft.world.entity.monster.Husk?=null
 @Volatile private var gasFirstSpray = -1L
 @Volatile private var gasStopped = -1L
 @Volatile private var lingeringVerified = false
 private var lingerVictim:net.minecraft.world.entity.animal.Pig?=null
 private val dogs by lazy {listOf(ModItems.GUARD_DOG_BACKPACK,ModItems.LASER_GUARD_DOG_BACKPACK,ModItems.STUN_GUARD_DOG_BACKPACK,ModItems.FLAME_GUARD_DOG_BACKPACK,ModItems.GAS_GUARD_DOG_BACKPACK)}
 private val turrets by lazy {listOf(ModEntities.MACHINE_GUN_SENTRY,ModEntities.FLAME_SENTRY,ModEntities.LASER_SENTRY,ModEntities.MORTAR_SENTRY)}
 fun tick(mc:Minecraft){
  failure?.let{throw it};val player=mc.player?:return;val server=mc.singleplayerServer?:return
  val level=server.overworld();mc.options.cameraType=CameraType.THIRD_PERSON_BACK
  if(!ready){
   if(age!=0)return
   age=1;stage++;rendered=false;effectCaptured=false;sprayingTicks=0;previousDogPosition=null
   if(stage>=dogs.size+turrets.size){check(poseSamples>=dogs.size){"Missing visible handoffs: $poseSamples"};LOGGER.info("RELEASE07_VISIBLE_CONTINUITY maxStep={} handoffs={}",maxPoseStep,poseSamples);LOGGER.info("RELEASE07_COMBAT_PASSED: all five Guard Dogs and MG/flame/laser/mortar sentries had deployed entities and affected live targets; inspect screenshots for visual verification");mc.stop();return}
   server.execute{try{
    level.getAllEntities().toList().filter{it !is net.minecraft.world.entity.player.Player}.forEach{it.discard()}
    server.setDifficulty(net.minecraft.world.Difficulty.NORMAL,true)
    for(x in -12..12)for(z in -6..32)level.setBlock(net.minecraft.core.BlockPos(x,79,z),net.minecraft.world.level.block.Blocks.SMOOTH_STONE.defaultBlockState(),3)
    val p=server.playerList.getPlayer(player.uuid)!!;p.setItemSlot(EquipmentSlot.CHEST,ItemStack.EMPTY)
    p.teleportTo(level,.5,80.0,-2.1,0f,0f)
    val target=EntityType.HUSK.create(level)!!;target.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=1024.0;target.health=1024f;target.isNoAi=true
    val distance=if(stage==8)26.0 else 6.0
    target.moveTo(.5,80.0,distance,180f,0f);level.addFreshEntity(target);victim=target
    if(stage<dogs.size){p.setItemSlot(EquipmentSlot.CHEST,ItemStack(dogs[stage].get()));GuardDogBackpack.toggle(p)}else{
     val pod=turrets[stage-dogs.size].get().create(level)!!;pod.moveTo(.5,80.0,.5,180f,0f);pod.setOwnerUUID(p.uuid)
     val tag=CompoundTag();pod.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.DEPLOYING.ordinal);tag.putInt("HellpodPhaseTicks",pod.deployTicks-1);tag.putBoolean("HellpodCapReleased",true);tag.putDouble("HellpodRestY",80.0);pod.load(tag);level.addFreshEntity(pod)
    }
    ready=true;LOGGER.info("RELEASE07_COMBAT_BEGIN stage={}",stage)
   }catch(t:Throwable){failure=t}};return
  }
  com.example.sbwhelldiver.client.GuardDogVisual.poseObserver={dog,matrix->
   if(dog!=null&&stage<dogs.size&&!com.example.sbwhelldiver.client.ShaderPackCompat.shadowPass()){
    val camera=mc.gameRenderer.mainCamera.position
    val point=com.mojang.blaze3d.systems.RenderSystem.getInverseViewRotationMatrix().transform(matrix.getTranslation(org.joml.Vector3f()))
    val world=camera.add(point.x.toDouble(),point.y.toDouble(),point.z.toDouble())
    val now=dog.tickCount.toFloat()
    val previous=poseWorld
    if(poseDog===dog&&previous!=null&&now-poseTick in 0f..2f){
     val step=world.distanceTo(previous);maxPoseStep=maxOf(maxPoseStep,step)
     if(step>=.85)failure=IllegalStateException("Visible drone jumped $step blocks at ${dog.flight}/${dog.phaseAge}, prior=$posePhase")
     if(posePhase==GuardDogEntity.Flight.LAUNCH&&dog.flight!=posePhase){
      poseSamples++;LOGGER.info("RELEASE07_VISIBLE_HANDOFF stage={} step={} world={} entity={}",stage,step,world,dog.position())
     }
    }
    poseDog=dog;poseWorld=world;poseTick=now;posePhase=dog.flight
   }
  }
  age++
  player.yRot=0f;player.yRotO=0f;player.xRot=8f;player.xRotO=8f
  if(stage==0&&age in 30..190){val step=age;server.execute{server.playerList.getPlayer(player.uuid)?.teleportTo(level,.5+2*kotlin.math.sin((step-30)*.035),80.0,-2.1,0f,0f)}}
  val entities=mc.level!!.entitiesForRendering().toList()
  if(stage<dogs.size){if(entities.any{it is GuardDogEntity&&it.flight!=GuardDogEntity.Flight.LAUNCH})rendered=true}
  else if(entities.any{it is SentryHellpodEntity&&it.phase==AbstractHellpodEntity.Phase.ACTIVE})rendered=true
  val flying=entities.filterIsInstance<GuardDogEntity>().firstOrNull()
  if(stage<dogs.size&&age in listOf(20,40,60,68))LOGGER.info("RELEASE07_LAUNCH_CLOCK age={} phase={} phaseAge={} position={}",age,flying?.flight,flying?.phaseAge,flying?.position())
  if(stage==0&&age in listOf(40,60)&&flying!=null&&flying.usesPackPose){
   val owner=flying.clientOwner()!!
   check(flying.position().distanceTo(flying.transitionPosition(owner,flying.phaseAge.toDouble()))<.1){"Off-camera launch tick was skipped"}
  }
  if(stage==0&&age==100&&flying!=null){
   val probe=GuardDogEntity(ModEntities.GUARD_DOG.get(),mc.level!!)
   probe.entityData.assignValues(flying.entityData.nonDefaultValues!!)
   probe.setPos(1.0,81.0,1.0)
   probe.lerpTo(4.0,83.0,4.0,0f,0f,3,false)
   mc.level!!.putNonPlayerEntity(-70700,probe)
   mc.connection!!.handleMoveEntity(net.minecraft.network.protocol.game.ClientboundMoveEntityPacket.Rot(probe.id,64.toByte(),0.toByte(),false))
   check(probe.lerpTargetX()==4.0&&probe.lerpTargetY()==83.0&&probe.lerpTargetZ()==4.0){"Rotation packet overwrote pending destination"}
   probe.discard()
   LOGGER.info("RELEASE07_ROTATION_PACKET_TARGET_PASSED")
  }
  if(stage==0&&flying!=null){
   val previous=previousDogPosition
   if(previous!=null&&flying.flight==GuardDogEntity.Flight.PATROL){val step=flying.position().distanceTo(previous);maxFlightStep=maxOf(maxFlightStep,step);check(step<.85){"Drone jumped $step blocks in free flight at phase ${flying.phaseAge}"}}
   previousDogPosition=flying.position()
  }
  if(stage in listOf(3,4)&&age in listOf(100,150))server.execute{
   val p=server.playerList.getPlayer(player.uuid)!!
   GuardDogBackpack.equipped(p)?.let{pack->repeat(3){check(GuardDogBackpack.loadSpare(pack))}}
  }
  if(stage in listOf(3,4)&&age in listOf(90,130,180))Screenshot.grab(mc.gameDirectory,"release072-gauge-$stage-$age.png",mc.mainRenderTarget){}
  if(stage==4)server.execute{try{
   val dog=level.getAllEntities().filterIsInstance<GuardDogEntity>().firstOrNull()?:return@execute
   val now=level.gameTime
   if(dog.gasSpraying&&gasFirstSpray<0)gasFirstSpray=now
   if(victim!!.hasEffect(ModEffects.GAS.get())&&gasStopped<0){
    val exposure=now-gasFirstSpray
    check(exposure in 19L..23L){"Gas exposure took $exposure ticks instead of one second"}
    LOGGER.info("RELEASE072_GAS_EXPOSURE_PASSED ticks={}",exposure)
    gasStopped=now
   }
   if(gasStopped>=0&&now-gasStopped>=15&&lingerVictim==null){
    check(!dog.gasSpraying){"Gas dog kept spraying poisoned target"}
    val pig=EntityType.PIG.create(level)!!;pig.isNoAi=true
    val at=victim!!.position();pig.moveTo(at.x+.6,at.y,at.z,0f,0f)
    check(level.addFreshEntity(pig));lingerVictim=pig
   }
   if(gasStopped>=0&&now-gasStopped>=38&&!lingeringVerified){
    check(!dog.gasSpraying)
    check(lingerVictim?.hasEffect(ModEffects.GAS.get())==true){"Late entrant was not poisoned by lingering mist after spray stopped"}
    lingeringVerified=true;LOGGER.info("RELEASE072_LINGERING_GAS_PASSED stoppedTicks={}",now-gasStopped)
   }
  }catch(t:Throwable){failure=t}}
  if(stage==4&&flying?.gasSpraying==true){sprayingTicks++;if(sprayingTicks%8==0)LOGGER.info("RELEASE07_GAS_ACTIVE ticks={} {}",sprayingTicks,com.example.sbwhelldiver.client.FieldVolumeRenderer.diagnostics())}
  if(!effectCaptured&&age>90&&stage in listOf(2,4)){
   val dog=entities.filterIsInstance<GuardDogEntity>().firstOrNull()
   if(dog!=null&&(stage==2&&dog.arcData.getInt("Count")>0&&dog.tickCount-dog.arcReceivedTick in 1..3 || stage==4&&dog.gasSpraying&&sprayingTicks>=16)){
    effectCaptured=true
    Screenshot.grab(mc.gameDirectory,"release07-active-$stage.png",mc.mainRenderTarget){LOGGER.info("RELEASE07_ACTIVE_EFFECT {}",stage)}
   }
  }
  if(age==200)LOGGER.info("RELEASE07_RENDER_DIAGNOSTICS stage={} {}",stage,com.example.sbwhelldiver.client.FieldVolumeRenderer.diagnostics())
  if(age==200)Screenshot.grab(mc.gameDirectory,"release07-combat-$stage.png",mc.mainRenderTarget){LOGGER.info("RELEASE07_COMBAT_SCREENSHOT {}",stage)}
  if(age==260){check(rendered){"No deployed client entity stage=$stage"};if(stage==0)LOGGER.info("RELEASE07_MOVING_LAUNCH_CONTINUITY maxStep={}",maxFlightStep);server.execute{try{
   if(stage==4)check(lingeringVerified){"Lingering gas check did not finish"};val v=victim!!;check(v.health<1024f||v.activeEffects.isNotEmpty()){ "No actual damage/effect stage=$stage health=${v.health}" }
   LOGGER.info("RELEASE07_COMBAT_VERIFIED stage={} health={} effects={}",stage,v.health,v.activeEffects.size);ready=false;age=0
  }catch(t:Throwable){failure=t}}}
 }
}
