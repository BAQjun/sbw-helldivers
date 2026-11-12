package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.client.ItemDeliveryRackRenderer
import com.example.sbwhelldiver.client.MachineMesh
import com.example.sbwhelldiver.client.SoundTimeline
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.HellpodCapEntity
import com.example.sbwhelldiver.entity.ItemDeliveryHellpodEntity
import com.example.sbwhelldiver.entity.ItemDeliveryHellpodEntity.Side
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.LevelSettings
import net.minecraft.world.level.WorldDataConfiguration
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.sound.PlaySoundSourceEvent
import java.util.UUID
import kotlin.math.atan2

@EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object ResupplyClientTest {
 private val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.resupplyTests")
 private val launch=System.currentTimeMillis()
 private var started=false;private var ticks=0;private var active=0;private var picked=0;private var braking=false;private var cap=false
 @Volatile private var podId:UUID?=null
 @Volatile private var failure:Throwable?=null
 @Volatile private var pickupSounds=0; @Volatile private var vanillaPickups=0; private var looseRequested=false; private var hudSeen=false
 @SubscribeEvent fun sound(event:PlaySoundSourceEvent) {
  if(!enabled)return
  if(event.sound.location.path=="sfx_supply_pickup") { check(event.sound.sound.location.path.startsWith("original/resupply/pickup_")); check(event.sound.pitch==1f); pickupSounds++; LOGGER.info("RESUPPLY_NATIVE_SOUND {} pitch={}",pickupSounds,event.sound.pitch) }
  if(event.sound.location.path=="entity.item.pickup")vanillaPickups++
 }
 @Volatile private var verified=false
 @SubscribeEvent fun tick(event:ClientTickEvent.Post) {
  if(!enabled)return
  val mc=Minecraft.getInstance()
  try {
   check(System.currentTimeMillis()-launch<360000){"Resupply client timed out"};failure?.let{throw it}
   if(!started&&mc.screen is TitleScreen&&mc.overlay==null){
    started=true;mc.options.pauseOnLostFocus=false
    mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Resupply test",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(5L,false,false),{a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()},mc.screen!!)
   }
   val player=mc.player?:return;val client=mc.level?:return;val server=mc.singleplayerServer?:return;val level=server.overworld();ticks++
   if(java.lang.Boolean.getBoolean("sbw_helldiver.visualOnly")&&ticks>40){Release07CombatSmoke.started=true;Release07CombatSmoke.tick(mc);return}
   if(ticks==1)server.execute{try{
    level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.dayTime=6000
    for(x in -12..12)for(z in -12..12)for(y in 76..79)level.setBlock(BlockPos(x,y,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
    level.setDefaultSpawnPos(BlockPos(0,80,-4),0f)
    server.playerList.getPlayer(player.uuid)!!.teleportTo(level,.5,80.0,-2.1,0f,0f)
    val marker=ModEntities.STRATAGEM_MARKER.get().create(level)!!
    marker.moveTo(.5,80.0,.5,180f,0f)
    marker.configure(com.example.sbwhelldiver.stratagem.Stratagems.resupply.dropId,197,player.uuid,groundY=80.0)
    check(level.addFreshEntity(marker))

   }catch(e:Throwable){failure=e}}
   if(verified){
    val loose=client.entitiesForRendering().filterIsInstance<com.example.sbwhelldiver.entity.SupplyCaseEntity>()
    if(!looseRequested&&loose.size==2){
     check(pickupSounds==2&&vanillaPickups==0){"Wrong rack audio: native=$pickupSounds vanilla=$vanillaPickups"}
     capture(mc,"resupply-dropped-cases.png");looseRequested=true
     check(mc.gameMode!!.interact(player,loose.first(),InteractionHand.MAIN_HAND).consumesAction())
    }
    if(looseRequested&&loose.size==1&&pickupSounds==3){
     check(vanillaPickups==0);LOGGER.info("RESUPPLY_CLIENT_PASSED: rack and loose case native event heard exactly once per pickup; four-case lifecycle verified");mc.stop()
    }
    return
   }
   if(ticks<200){player.yRot=0f;player.yRotO=0f;player.xRot=0f;player.xRotO=0f}
   val label=com.example.sbwhelldiver.client.BeaconCallHud.label
   if(!hudSeen&&label?.iconKey=="resupply"){
    check(com.example.sbwhelldiver.client.BeaconCallHud.monochromeIconCount==com.example.sbwhelldiver.stratagem.Stratagems.definitions.size+2)
    hudSeen=true;capture(mc,"resupply-beacon-hud.png");LOGGER.info("RELEASE072_RESUPPLY_HUD_PASSED")
   }
   val pod=client.entitiesForRendering().filterIsInstance<com.example.sbwhelldiver.entity.ResupplyHellpodEntity>().firstOrNull()?:return
   podId=pod.uuid
   if(pod.braking)braking=true
   if(client.entitiesForRendering().any{it is HellpodCapEntity})cap=true
   if(pod.phase==Phase.ACTIVE){
    active++
    val index=if(active<75)0 else 2
    val target=pod.toWorld(com.example.sbwhelldiver.entity.ResupplyHellpodEntity.caseBounds(index).center)
    val d=target.subtract(player.eyePosition);player.yRot=Math.toDegrees(atan2(-d.x,d.z)).toFloat();player.xRot=Math.toDegrees(atan2(-d.y,d.horizontalDistance())).toFloat();player.yRotO=player.yRot;player.xRotO=player.xRot
    if(active==30){check(hudSeen){"Resupply HUD never displayed"};check(braking&&cap);check((0..3).all(pod::hasCase));check(MachineMesh.batchCount("resupply_rack")==7);capture(mc,"resupply-four-cases.png")}
    if(active==50||active==100){check(mc.gameMode!!.interactAt(player,pod,EntityHitResult(pod),InteractionHand.MAIN_HAND).consumesAction());picked++}
    if(active==70){check(!pod.hasCase(0)&&pod.hasCase(2));capture(mc,"resupply-one-consumed.png")}
    if(active==120){check(!pod.hasCase(0)&&!pod.hasCase(2)&&pod.hasCase(1)&&pod.hasCase(3));capture(mc,"resupply-front-empty.png")
     server.execute{try{val actual=level.getEntity(podId!!) as com.example.sbwhelldiver.entity.ResupplyHellpodEntity;actual.destroy();val cases=level.getEntitiesOfClass(com.example.sbwhelldiver.entity.SupplyCaseEntity::class.java,actual.boundingBox.inflate(5.0));check(cases.size==2);verified=true}catch(e:Throwable){failure=e}}
    }
   }
  }catch(e:Throwable){LOGGER.error("RESUPPLY_CLIENT_FAILED at tick {}",ticks,e);mc.stop()}
 }
 private fun capture(mc:Minecraft,name:String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget){LOGGER.info("RESUPPLY_SCREENSHOT {}",name)}
}
