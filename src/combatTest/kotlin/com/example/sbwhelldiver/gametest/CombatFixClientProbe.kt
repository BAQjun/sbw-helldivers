package com.example.sbwhelldiver.gametest

import com.atsuishio.superbwarfare.client.lighting.LightPositionRegistry
import com.atsuishio.superbwarfare.config.client.DisplayConfig
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.client.TargetMarkClient
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.mojang.authlib.GameProfile
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/** Only present in opt-in client tests; uses live server firing and the native light engine. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object CombatFixClientProbe {
 private val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.markSmokeTest")
 @Volatile private var age=0
 @Volatile private var started=false
 private var finished=false
 private var observedCompact=false
 private var observedExpanded=false
 private var operator:net.minecraftforge.common.util.FakePlayer?=null
 private var oldLight=true
 private var lightConfigured=false
 private val fixtures=mutableListOf<Entity>()
 private val ids=mutableSetOf<UUID>()
 private val registryPeak=mutableMapOf<String,Int>()
 private val blockPeak=mutableMapOf<String,Int>()
 private val lastPoints=mutableListOf<BlockPos>()
 private val originals=mutableMapOf<BlockPos,net.minecraft.world.level.block.state.BlockState>()
 @SubscribeEvent fun server(e:TickEvent.ServerTickEvent){
  if(!enabled||finished||e.phase!=TickEvent.Phase.END||TargetMarkClient.requests<1)return
  val l=e.server.overworld()
  if(!started){
   started=true;val origin=Vec3(8.5,64.0,8.5)
   val target=EntityType.HUSK.create(l)!!.also{it.setPos(origin.add(-38.0,0.0,28.0));it.isNoAi=true;it.isNoGravity=true;it.isInvulnerable=true;l.addFreshEntity(it);fixtures.add(it)}
   val types=listOf(ModEntities.MACHINE_GUN_SENTRY.get(),ModEntities.GATLING_SENTRY.get(),ModEntities.FLAME_SENTRY.get(),ModEntities.HMG_EMPLACEMENT.get())
   for((i,type) in types.withIndex()){
    val at=origin.add(-46.0+i*5,0.0,8.0)
    for(dx in -2..2)for(dz in -2..2){val p=BlockPos.containing(at).offset(dx,-1,dz);originals[p]=l.getBlockState(p);l.setBlock(p,Blocks.QUARTZ_BLOCK.defaultBlockState(),2)}
    val turret=type.create(l) as AbstractHellpodEntity;turret.setPos(at)
    val n=CompoundTag();turret.saveWithoutId(n);n.putInt("HellpodPhase",AbstractHellpodEntity.Phase.DEPLOYING.ordinal);n.putInt("HellpodPhaseTicks",turret.deployTicks-1);n.putDouble("HellpodRestY",at.y);n.putBoolean("HmgHasBeenMounted",true);n.putBoolean("HellpodCapReleased",true)
    turret.load(n);l.addFreshEntity(turret);turret.tick();fixtures.add(turret);ids.add(turret.uuid)
    if(turret is HmgEmplacementEntity){
     operator=FakePlayerFactory.get(l,GameProfile(UUID.randomUUID(),"FlashProbe"));operator!!.setPos(at);check(operator!!.startRiding(turret,true))
    }
   }
   l.dayTime=18000
  }
  age++
  val gun=fixtures.filterIsInstance<HmgEmplacementEntity>().firstOrNull()
  if(age<165&&gun!=null){val target=fixtures.first().boundingBox.center;val a=gun.aimAnglesAt(target);gun.acceptInput(operator!!,a.x,a.y,true,target)}
  if(age==170){LOGGER.info("COMBAT_FIXTURE_SHOTS {}",fixtures.filterIsInstance<AbstractHellpodEntity>().map{it.profile.id to it.shotSerial});clean(l)}
 }
 private fun clean(l:net.minecraft.server.level.ServerLevel){operator?.stopRiding();operator?.discard();fixtures.forEach{it.discard()};for((p,state) in originals)l.setBlock(p,state,2);originals.clear();l.dayTime=6000}
 @SubscribeEvent fun stopping(e:net.minecraftforge.event.server.ServerStoppingEvent){if(enabled&&started)clean(e.server.overworld())}
 @SubscribeEvent fun client(e:TickEvent.ClientTickEvent){
  if(!enabled||finished||e.phase!=TickEvent.Phase.END)return
  val mc=Minecraft.getInstance();val l=mc.level?:return
  if(!started){if(!lightConfigured){oldLight=DisplayConfig.ENABLE_FIRE_FLASH_LIGHT.get();DisplayConfig.ENABLE_FIRE_FLASH_LIGHT.set(true);lightConfigured=true};return}
  observedCompact=observedCompact||TargetMarkClient.labels.any{!it.hovered}
  observedExpanded=observedExpanded||TargetMarkClient.labels.any{it.hovered}
  for(t in l.entitiesForRendering().filterIsInstance<AbstractHellpodEntity>().filter{it.uuid in ids}){
   val points=when(t){
    is HmgEmplacementEntity -> listOf(t.muzzlePosition(0),t.muzzlePosition(1))
    is SentryHellpodEntity -> listOf(t.getShootPos("Main",1f))
    else -> emptyList()
   }
   for(tip in points)for(dx in -1..1)for(dy in -1..1)for(dz in -1..1){
    val p=BlockPos.containing(tip).offset(dx,dy,dz)
    registryPeak[t.profile.id]=maxOf(registryPeak[t.profile.id]?:0,LightPositionRegistry.getLevel(p.asLong()))
    blockPeak[t.profile.id]=maxOf(blockPeak[t.profile.id]?:0,l.getBrightness(LightLayer.BLOCK,p))
    if(age in 140..165)lastPoints.add(p)
   }
  }
  if(age>=210){
   val expected=setOf("machine_gun_sentry","gatling_sentry","flame_sentry","hmg_emplacement")
   check(expected.all{(registryPeak[it]?:0)>=12}){"Missing native firing light: $registryPeak"}
   check(expected.all{(blockPeak[it]?:0)>=10}){"Missing actual block illumination: $blockPeak"}
   check(lastPoints.all{LightPositionRegistry.getLevel(it.asLong())<0}){"Firing light remained after the sources stopped"}
   check(observedCompact&&observedExpanded&&TargetMarkClient.COMPACT_SCALE<.8f)
   DisplayConfig.ENABLE_FIRE_FLASH_LIGHT.set(oldLight)
   val out=Path.of("D:/Project/sbw_helldiver/.codex/combat0374/lighting-client.json")
   Files.writeString(out,"""{"registry_peak":"$registryPeak","block_light_peak":"$blockPeak","lights_expired":true,"compact_scale":${TargetMarkClient.COMPACT_SCALE},"both_hud_states_seen":true,"fixtures_removed":true}""")
   finished=true;LOGGER.info("COMBAT_LIGHTING_CLIENT_PASSED registry={} blocks={}",registryPeak,blockPeak)
  }
 }
}
