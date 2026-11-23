package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.client.BeaconCallHud
import com.example.sbwhelldiver.client.MachineMesh
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.HellpodCapEntity
import com.example.sbwhelldiver.entity.ResupplyHellpodEntity
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.item.StratagemBackpacks
import com.example.sbwhelldiver.item.SupplyBackpack
import com.example.sbwhelldiver.stratagem.CallPhase
import com.example.sbwhelldiver.stratagem.StratagemCooldowns
import com.example.sbwhelldiver.stratagem.Stratagems
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.CameraType
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.client.gui.screens.inventory.InventoryScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemDisplayContext
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
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import org.lwjgl.glfw.GLFW
import top.theillusivec4.curios.api.CuriosApi
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
 private var vanillaPickupsAtDeployment=0
 @SubscribeEvent fun sound(event:PlaySoundSourceEvent) {
  if(!enabled)return
  if(event.sound.location.path=="sfx_supply_pickup") { check(event.sound.sound.location.path.startsWith("original/resupply/pickup_")); check(event.sound.pitch==1f); pickupSounds++; LOGGER.info("RESUPPLY_NATIVE_SOUND {} pitch={}",pickupSounds,event.sound.pitch) }
  if(event.sound.location.path=="entity.item.pickup")vanillaPickups++
 }
 @Volatile private var testPlayer:UUID?=null
 private var standUnderPodAt=Long.MAX_VALUE
 private var packBeforeImpact:ItemStack?=null
 @SubscribeEvent fun standUnderPod(event:PlayerTickEvent.Pre) {
  val victim=event.entity as? ServerPlayer ?:return
  val level=victim.serverLevel()
  if(!enabled || victim.uuid!=testPlayer || level.gameTime<standUnderPodAt)return
  standUnderPodAt=Long.MAX_VALUE
  packBeforeImpact=StratagemBackpacks.equipped(victim)?.copy()
  victim.setGameMode(GameType.SURVIVAL)
  victim.teleportTo(level,.5,80.0,.5,0f,0f)
 }
 @Volatile private var crushed=false
 @Volatile private var respawned=false
 @Volatile private var verified=false
 @SubscribeEvent fun death(event:LivingDeathEvent) {
  if(enabled && event.entity is ServerPlayer && event.entity.uuid==testPlayer)
   crushed=event.source.directEntity is ResupplyHellpodEntity
 }
 @SubscribeEvent fun respawn(event:PlayerEvent.PlayerRespawnEvent) {
  val player=event.entity as? ServerPlayer ?:return
  if(!enabled || player.uuid!=testPlayer)return
  try {
   check(crushed){"Test player was not killed by the resupply pod"}
   val status=StratagemCooldowns.state(player).entry(Stratagems.resupply.id)
   check(status.remaining==1){"Respawn changed the remaining resupply count"}
   check(status.phase==CallPhase.COOLDOWN || status.phase==CallPhase.READY ||
       status.phase==CallPhase.ARRIVING && status.end<=StratagemCooldowns.now(player.server))
   // Restore the test fixture for the independent rack pickup assertions.
   packBeforeImpact?.let {
    CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("back").orElseThrow().stacks.setStackInSlot(0,it)
   }
   player.setGameMode(GameType.CREATIVE)
   player.teleportTo(player.serverLevel(),.5,80.0,-2.1,0f,0f)
   respawned=true
  }catch(e:Throwable){failure=e}
 }
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
    level.gameRules.getRule(GameRules.RULE_DO_IMMEDIATE_RESPAWN).set(true,server)
    level.gameRules.getRule(GameRules.RULE_KEEPINVENTORY).set(true,server)
    val serverPlayer=server.playerList.getPlayer(player.uuid)!!
    serverPlayer.teleportTo(level,.5,80.0,-2.1,0f,0f)
    testPlayer=serverPlayer.uuid
    val back=CuriosApi.getCuriosInventory(serverPlayer).orElseThrow().getStacksHandler("back").orElseThrow().stacks
    check(back.insertItem(0,ItemStack(ModItems.SUPPLY_BACKPACK.get()),false).isEmpty){"Supply pack could not be equipped in Curios"}
    serverPlayer.inventory.setItem(0, ItemStack(ModItems.SUPPLY_BACKPACK.get()))
    serverPlayer.inventory.setItem(1, ItemStack(ModItems.GUARD_DOG_BACKPACK.get()))
    serverPlayer.inventory.setItem(2, ItemStack(ModItems.SUPPLY_BACKPACK.get()))
    serverPlayer.inventory.setItem(3, ItemStack(ModItems.SUPPLY_BACKPACK_STRATAGEM.get()))
    serverPlayer.containerMenu.broadcastChanges()
    val marker=ModEntities.STRATAGEM_MARKER.get().create(level)!!
    marker.moveTo(.5,80.0,.5,180f,0f)
    val definition=Stratagems.resupply
    val request=UUID.randomUUID()
    marker.configure(definition.dropId,197,player.uuid,groundY=80.0)
    marker.managedRequest=request
    standUnderPodAt=marker.arrivalDeadline-10
    check(StratagemCooldowns.change(server,player.uuid){state,now->
     check(state.purchaseResupply() && state.purchaseResupply())
     check(state.reserve(definition.id,request,now))
     state.beginArrival(definition.id,request,now,marker.arrivalDeadline)
    })
    check(level.addFreshEntity(marker))

   }catch(e:Throwable){failure=e}}
   val pack = StratagemBackpacks.equipped(player)
   if (ticks == 25) {
    check(pack?.item is SupplyBackpack && SupplyBackpack.cases(pack) == 4)
    check(MachineMesh.batchCount("supply_backpack") == 5)
    val model = mc.itemRenderer.getModel(pack, client, player, 0)
    check(model.applyTransform(ItemDisplayContext.GUI, PoseStack(), false).isCustomRenderer)
    for (context in listOf(
     ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
     ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, ItemDisplayContext.THIRD_PERSON_LEFT_HAND,
    )) {
     val pose = PoseStack()
     check(model.applyTransform(context, pose, context.name.endsWith("LEFT_HAND")).isCustomRenderer) {
      "Supply backpack uses a flat icon in $context"
     }
     check(!PoseStack().last().pose().equals(pose.last().pose())) { "Missing hand transform for $context" }
    }
    check(!model.applyTransform(ItemDisplayContext.GROUND, PoseStack(), false).isCustomRenderer)
    check(player.mainHandItem.item is SupplyBackpack)
    capture(mc, "supply-backpack-in-hand.png")
    server.execute {
     server.playerList.getPlayer(player.uuid)?.inventory?.setItem(0, ItemStack.EMPTY)
    }
   }
   if (ticks == 27) {
    mc.options.cameraType = CameraType.THIRD_PERSON_BACK
   }
   if (ticks == 28) mc.setScreen(InventoryScreen(player))
   if (ticks == 30) capture(mc,"supply-backpack-item-and-stratagem.png")
   if (ticks == 32) mc.setScreen(null)
   if (ticks == 35) capture(mc,"supply-backpack-four-cases.png")
   if (ticks == 40) mc.keyboardHandler.keyPress(mc.window.window, GLFW.GLFW_KEY_G, 0, GLFW.GLFW_PRESS, 0)
   if (ticks in 45..55 step 5) mc.keyboardHandler.keyPress(mc.window.window, GLFW.GLFW_KEY_G, 0, GLFW.GLFW_REPEAT, 0)
   if (ticks == 60) mc.keyboardHandler.keyPress(mc.window.window, GLFW.GLFW_KEY_G, 0, GLFW.GLFW_RELEASE, 0)
   if (ticks == 65) {
    check(mc.screen == null) {"The backpack key also opened Curios"}
    check(pack?.item is SupplyBackpack && SupplyBackpack.cases(pack) == 3) {"Back slot did not synchronize after pressing G"}
    capture(mc,"supply-backpack-three-cases.png")
   }
   if (ticks == 75) mc.options.cameraType = CameraType.FIRST_PERSON
   if(verified){
    val loose=client.entitiesForRendering().filterIsInstance<com.example.sbwhelldiver.entity.SupplyCaseEntity>()
    if(!looseRequested&&loose.size==2){
     check(pickupSounds==3&&vanillaPickups==vanillaPickupsAtDeployment){"Wrong rack audio: native=$pickupSounds vanilla=${vanillaPickups-vanillaPickupsAtDeployment}"}
     capture(mc,"resupply-dropped-cases.png");looseRequested=true
     check(mc.gameMode!!.interact(player,loose.first(),InteractionHand.MAIN_HAND).consumesAction())
    }
    if(looseRequested&&loose.size==1&&pickupSounds==4){
     check(vanillaPickups==vanillaPickupsAtDeployment);LOGGER.info("RESUPPLY_CLIENT_PASSED: rack and loose case native event heard exactly once per pickup; four-case lifecycle verified");mc.stop()
    }
    return
   }
   if(ticks<200){player.yRot=0f;player.yRotO=0f;player.xRot=0f;player.xRotO=0f}
   val label=BeaconCallHud.label
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
    if(active==30){
     check(hudSeen){"Resupply HUD never displayed"}
     check(crushed && respawned && player.isAlive){"The resupply crush and respawn did not complete"}
     check(label?.iconKey!=Stratagems.resupply.id){"Resupply HUD lingered after respawn"}
     val stale=ModEntities.STRATAGEM_MARKER.get().create(client)!!
     stale.configure(Stratagems.resupply.dropId,197,player.uuid,groundY=80.0)
     check(stale.hasBeaconBeam)
     stale.load(stale.saveWithoutId(CompoundTag()).apply{
      putLong("StartedAt",client.gameTime-stale.duration-stale.landingGraceTicks-60)
     })
     check(!stale.isRemoved && stale.hasArrived && !stale.hasBeaconBeam){"Late tracked marker can still show a finished call"}
     LOGGER.info("RESUPPLY_RESPAWN_PASSED")
     // Only rack and loose-case interactions should be counted after respawn.
     vanillaPickupsAtDeployment=vanillaPickups
     check(braking&&cap)
     check((0..3).all(pod::hasCase))
     check(MachineMesh.batchCount("resupply_rack")==7)
     capture(mc,"resupply-four-cases.png")
    }
    if(active==50||active==100){check(mc.gameMode!!.interactAt(player,pod,EntityHitResult(pod),InteractionHand.MAIN_HAND).consumesAction());picked++}
    if(active==70){
     check(!pod.hasCase(0)&&pod.hasCase(2))
     check(pack?.item is SupplyBackpack && SupplyBackpack.cases(pack) == 4) {"Rack pickup did not restock the Curios slot"}
     LOGGER.info("SUPPLY_BACKPACK_CLIENT_PASSED: G consumed a case in Curios and the rack restored it")
     capture(mc,"resupply-one-consumed.png")
    }
    if(active==120){check(!pod.hasCase(0)&&!pod.hasCase(2)&&pod.hasCase(1)&&pod.hasCase(3));capture(mc,"resupply-front-empty.png")
     server.execute{try{val actual=level.getEntity(podId!!) as com.example.sbwhelldiver.entity.ResupplyHellpodEntity;actual.destroy();val cases=level.getEntitiesOfClass(com.example.sbwhelldiver.entity.SupplyCaseEntity::class.java,actual.boundingBox.inflate(5.0));check(cases.size==2);verified=true}catch(e:Throwable){failure=e}}
    }
   }
  }catch(e:Throwable){LOGGER.error("RESUPPLY_CLIENT_FAILED at tick {}",ticks,e);mc.stop()}
 }
 private fun capture(mc:Minecraft,name:String)=Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget){LOGGER.info("RESUPPLY_SCREENSHOT {}",name)}
}
