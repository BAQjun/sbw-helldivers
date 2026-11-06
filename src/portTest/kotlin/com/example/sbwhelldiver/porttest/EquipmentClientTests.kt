package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.*
import com.example.sbwhelldiver.client.EquipmentTerminalScreen
import com.example.sbwhelldiver.survival.*
import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.stratagem.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.*
import net.minecraft.world.item.*
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import org.lwjgl.glfw.GLFW

/** One device, a prepared Creative platform and an automatically removed disposable world. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object EquipmentClientTests {
    private var started=false;private var ticks=0;private val launched=System.currentTimeMillis()
    private var device=java.util.UUID(0,0)
    private val arrivalSounds=mutableListOf<String>()
    private val mechanicalSounds=mutableListOf<String>()
    private val flashRuns=mutableMapOf<Long,Int>()
    private var hiddenPlayerFrames=0
    @net.neoforged.bus.api.SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST,receiveCanceled=true)
    fun renderedPlayer(e:net.neoforged.neoforge.client.event.RenderPlayerEvent.Pre) {
        val mc=Minecraft.getInstance()
        if(mc.screen is EquipmentTerminalScreen && e.entity===mc.player && e.isCanceled)hiddenPlayerFrames++
    }
    private var flashWasRed=false
    private var flashAt=-1L
    private var flashCaptureAt=-1
    private var flashCaptureName=""
    @SubscribeEvent fun sound(event:net.neoforged.neoforge.client.event.sound.PlaySoundEvent) {
        if(java.lang.Boolean.getBoolean("sbw_helldiver.equipmentRegression"))event.sound?.location?.path?.let {
            val e=Minecraft.getInstance().level?.entitiesForRendering()?.filterIsInstance<EquipmentDeploymentBeaconEntity>()?.firstOrNull{b->b.uuid==device}
            if(e!=null&&e.phase in listOf(EquipmentDeploymentBeaconEntity.PACKED,EquipmentDeploymentBeaconEntity.IMPACT,EquipmentDeploymentBeaconEntity.DEPLOYING)&&it.startsWith("sfx_hellpod_")){
                arrivalSounds.add(it)
                if(it=="sfx_hellpod_impact"&&!java.lang.Boolean.getBoolean("sbw_helldiver.equipmentCollisionOnly"))check(kotlin.math.abs(e.level().gameTime-e.flightArrival)<=2){"Landing sound missed contact: age=${e.level().gameTime-e.flightArrival}, phase=${e.phase}"}
                if(it=="sfx_hellpod_cap_release"&&!java.lang.Boolean.getBoolean("sbw_helldiver.equipmentCollisionOnly"))check(e.level().gameTime-e.flightArrival>=10){"Cap sound precedes the impact pause"}
                check(!it.startsWith("sfx_hellpod_retro")&&!it.startsWith("sfx_hellpod_thruster")){"Equipment entry layers extra player retro audio"}
                LOGGER.info("EQUIPMENT_SHARED_POD_AUDIO {} phase={} arrivalAge={}",it,e.phase,e.level().gameTime-e.flightArrival)
            }
            if(it.startsWith("sfx_equipment_beacon_")) {
                mechanicalSounds.add(it)
                check(e!=null)
                val age=(e.level().gameTime-e.phaseStart).toInt()
                val expected=mapOf("sfx_equipment_beacon_unfold" to 22,"sfx_equipment_beacon_fold" to 1).getValue(it)
                if(!java.lang.Boolean.getBoolean("sbw_helldiver.equipmentCollisionOnly"))check(kotlin.math.abs(age-expected)<=1){"Mechanical cue out of sync: $it at $age, expected $expected"}
                LOGGER.info("EQUIPMENT_ALIGNED_AUDIO {} age={}",it,age)
            }
        }
    }
    @SubscribeEvent fun tick(event:ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("sbw_helldiver.equipmentRegression"))return
        val mc=Minecraft.getInstance()
        if(System.currentTimeMillis()-launched>240000){LOGGER.error("EQUIPMENT_CLIENT_TIMEOUT");mc.stop();return}
        if(!started&&mc.screen is TitleScreen&&mc.overlay==null){
            started=true;mc.options.pauseOnLostFocus=false
            mc.options.languageCode="zh_cn"
            mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Equipment beacon verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(1,false,false),{a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()},mc.screen!!)
            return
        }
        val player=mc.player?:return;val server=mc.singleplayerServer?:return;ticks++
        if(ticks==1)server.execute {
            val l=server.overworld();l.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);l.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);l.dayTime=6000
            for(x in -10..10)for(z in -20..12)for(y in 73..79)l.setBlock(BlockPos(x,y,z),if(y==79)Blocks.SMOOTH_STONE.defaultBlockState()else Blocks.STONE.defaultBlockState(),3)
            for(x in listOf(-8,8))for(z in listOf(-8,8))l.setBlock(BlockPos(x,79,z),Blocks.SEA_LANTERN.defaultBlockState(),3)
            l.setDefaultSpawnPos(BlockPos(5,80,7),145f)
            val p=server.playerList.getPlayer(player.uuid)!!;p.setGameMode(GameType.CREATIVE);p.teleportTo(l,5.0,80.0,7.0,145f,-10f)
            p.inventory.clearContent();val ball=ItemStack(ModItems.BEACON.get());BeaconData.ensureIdentity(ball);BeaconEnergy.set(ball,10000);p.inventory.setItem(0,ball)
        }
        if(ticks==60)server.execute {
            val l=server.overworld();val p=server.playerList.getPlayer(player.uuid)!!
            val e=ModEntities.EQUIPMENT_BEACON.get().create(l)!!;val landing=net.minecraft.world.phys.Vec3(.5,80.0,.5)
            e.moveTo(.5,80.0,.5,EquipmentBeaconGeometry.deploymentYaw(p.position(),landing,p.yRot),0f);e.configure(80.0,l.gameTime+35,p.uuid);l.addFreshEntity(e);device=e.uuid
            checkTickOptimizer(e,"server/high-altitude")
            LOGGER.info("EQUIPMENT_FIXTURE_READY")
        }
        val collisionOnly=java.lang.Boolean.getBoolean("sbw_helldiver.equipmentCollisionOnly")
        if(ticks==154&&collisionOnly)mc.entityRenderDispatcher.setRenderHitBoxes(true)
        if(ticks==155)server.execute {val p=server.playerList.getPlayer(player.uuid)!!;p.teleportTo(server.overworld(),.5,87.0,-4.5,0f,40f);p.abilities.flying=true;p.onUpdateAbilities()}
        if(ticks==166)server.execute {val p=server.playerList.getPlayer(player.uuid)!!;p.teleportTo(server.overworld(),4.0,80.0,5.0,142f,-4f)}
        if(ticks==169)capture("equipment-pedestal-hidden.png")
        if(ticks==160)server.execute {
            val e=server.overworld().getEntity(device) as EquipmentDeploymentBeaconEntity
            check(e.phase==EquipmentDeploymentBeaconEntity.ACTIVE){"Beacon deployment froze: phase=${e.phase}, y=${e.y}"}
            checkTickOptimizer(e,"server/deployed")
        }
        if(ticks==165){
            check(com.example.sbwhelldiver.client.DeliveryFlightVisuals.beaconJetFrames>0){"Attached beacon jets never rendered"}
            check(com.example.sbwhelldiver.client.BeaconCallHud.label==null){"Deployed beacon still shows an arrived world HUD"}
            if(!collisionOnly)for(effect in listOf("sfx_hellpod_bolts_layer_a","sfx_hellpod_bolts_layer_b","sfx_hellpod_entry_layer_03","sfx_hellpod_entry_layer_01","sfx_hellpod_entry_layer_02","sfx_hellpod_impact","sfx_hellpod_cap_release"))check(arrivalSounds.count{it==effect}==1){"Missing or repeated pod sound $effect: $arrivalSounds"}
            val e=mc.level!!.entitiesForRendering().filterIsInstance<EquipmentDeploymentBeaconEntity>().single()
            checkTickOptimizer(e,"client/deployed")
            // The renderer interpolates multipart previous coordinates; drifting values render ghost boxes.
            check(e.parts.all{it.xOld==e.xOld&&it.yOld==e.yOld&&it.zOld==e.zOld}){"Beacon multipart debug positions drifted from the parent"}
            val shapes=mc.level!!.getEntityCollisions(player,net.minecraft.world.phys.AABB(e.x-.1,e.y+.4,e.z-.1,e.x+.1,e.y+1,e.z+.1))
            check(shapes.isNotEmpty()){"Client did not register physical beacon collision"}
            val old=player.position();val flying=player.abilities.flying
            player.abilities.flying=false;player.setPos(e.x-2,e.y+.3,e.z)
            player.move(net.minecraft.world.entity.MoverType.SELF,net.minecraft.world.phys.Vec3(4.0,0.0,0.0))
            check(player.x<e.x-.6){"Client movement passed through the beacon"}
            // Press into a side while sliding along it: a wall should not push the player back or snag.
            check(e.parts.size==2){"Beacon collision should consist of a base and body"}
            player.setPos(e.x-1.2,e.y+.4,e.z-.4)
            player.move(net.minecraft.world.entity.MoverType.SELF,net.minecraft.world.phys.Vec3(.6,0.0,0.0))
            val wallX=player.x;val startZ=player.z
            repeat(8){player.move(net.minecraft.world.entity.MoverType.SELF,net.minecraft.world.phys.Vec3(.08,0.0,.06))}
            check(kotlin.math.abs(player.x-wallX)<.001 && player.z-startZ>.45){"Beacon wall pushed back or snagged the player: x=${player.x-wallX}, z=${player.z-startZ}"}
            player.setPos(old);player.abilities.flying=flying
            LOGGER.info("EQUIPMENT_CLIENT_COLLISION_AND_SHARED_AUDIO_VERIFIED")
            capture("equipment-deployed.png")
        }
        if(ticks==170)server.execute {
            val e=server.overworld().getEntity(device) as EquipmentDeploymentBeaconEntity
            val p=server.playerList.getPlayer(player.uuid)!!;val eye=e.screenCenter().add(e.screenNormal().scale(.48));val d=e.screenCenter().subtract(eye)
            p.abilities.flying=true;p.onUpdateAbilities()
            p.teleportTo(server.overworld(),eye.x,eye.y-p.eyeHeight,eye.z,Math.toDegrees(kotlin.math.atan2(-d.x,d.z)).toFloat(),(-Math.toDegrees(kotlin.math.atan2(d.y,kotlin.math.sqrt(d.x*d.x+d.z*d.z)))).toFloat())
        }
        if(ticks==180) {
            val e=mc.level!!.entitiesForRendering().filterIsInstance<EquipmentDeploymentBeaconEntity>().first()
            LOGGER.info("EQUIPMENT_CLIENT_SCREEN_HIT={}",e.screenHit(player))
            mc.gameMode!!.interactAt(player,e,net.minecraft.world.phys.EntityHitResult(e,e.screenCenter()),InteractionHand.MAIN_HAND)
        }
        if(ticks==195){
            check(mc.screen is EquipmentTerminalScreen){"Terminal did not open"}
            val e=(mc.screen as EquipmentTerminalScreen).beacon()!!;val camera=mc.gameRenderer.mainCamera
            check(net.minecraft.world.phys.Vec3(camera.lookVector).dot(e.screenCenter().subtract(camera.position).normalize())>.999){"Camera is not aimed at the screen centre"}
            check(hiddenPlayerFrames>0){"Local player was not hidden during close-up terminal view"}
            LOGGER.info("EQUIPMENT_CLOSE_VIEW_PLAYER_HIDDEN frames={}",hiddenPlayerFrames)
            capture("equipment-menu.png")
            server.execute{server.playerList.getPlayer(player.uuid)!!.setGameMode(GameType.SURVIVAL)}
        }
        if(ticks==200&&collisionOnly){LOGGER.info("EQUIPMENT_COLLISION_DEBUG_AND_TERMINAL_VERIFIED");mc.stop();return}
        fun key(d:Int){(mc.screen as? EquipmentTerminalScreen)?.keyPressed(listOf(GLFW.GLFW_KEY_UP,GLFW.GLFW_KEY_RIGHT,GLFW.GLFW_KEY_DOWN,GLFW.GLFW_KEY_LEFT)[d],0,0)?:error("No terminal at $ticks")}
        if(ticks==198)key(1) // Invalid selection; subsequent valid arrows remain usable during the feedback.
        if(ticks==227)key(0) // Invalid confirmation command on the material page.
        val observed=mc.level!!.entitiesForRendering().filterIsInstance<EquipmentDeploymentBeaconEntity>().firstOrNull{it.uuid==device}
        if(observed!=null&&observed.inputErrorAt>=0) {
            if(flashAt!=observed.inputErrorAt){flashAt=observed.inputErrorAt;flashWasRed=false}
            val red=com.example.sbwhelldiver.client.EquipmentTerminalFeedback.flashing(observed)
            if(red&&!flashWasRed){flashRuns.merge(flashAt,1,Int::plus);flashCaptureAt=ticks+2;flashCaptureName="equipment-input-error-${flashRuns.size}-${flashRuns[flashAt]}.png"}
            if(ticks==flashCaptureAt)capture(flashCaptureName)
            flashWasRed=red
        }
        if(ticks==260){check(flashRuns.size==2&&flashRuns.values.all{it==1}){"Incorrect input did not produce one red flash on both menus: $flashRuns"};LOGGER.info("EQUIPMENT_ALL_PAGES_ERROR_FLASH_VERIFIED {}",flashRuns)}
        val bio=MachineDeployment.BIOPROCESSOR.code
        if(ticks in 200..216&&(ticks-200)%4==0)key(bio[(ticks-200)/4])
        if(ticks==225)capture("equipment-material.png")
        if(ticks in 230..250&&(ticks-230)%4==0)key(1)
        if(ticks==260)capture("equipment-insufficient.png")
        if(ticks==265)key(3)
        if(ticks==269)server.execute {server.overworld().setBlock(BlockPos(2,80,2),Blocks.STONE.defaultBlockState(),3)}
        if(ticks in 274..290&&(ticks-274)%4==0)key(MachineDeployment.REFINERY.code[(ticks-274)/4])
        if(ticks==300)capture("equipment-no-space.png")
        if(ticks==305)server.execute {
            server.overworld().setBlock(BlockPos(2,80,2),Blocks.AIR.defaultBlockState(),3)
            val inventory=server.playerList.getPlayer(player.uuid)!!.inventory
            MachineDeployment.BIOPROCESSOR.materials.forEachIndexed{i,m->inventory.setItem(i+1,ItemStack(m.item,m.count))}
            server.playerList.getPlayer(player.uuid)!!.containerMenu.broadcastChanges()
        }
        if(ticks in 310..326&&(ticks-310)%4==0)key(bio[(ticks-310)/4])
        if(ticks in 335..355&&(ticks-335)%4==0)key(1)
        if(ticks==370){check(mc.screen !is EquipmentTerminalScreen&&mc.cameraEntity===player){"Camera did not return"};server.execute{val p=server.playerList.getPlayer(player.uuid)!!;p.setGameMode(GameType.CREATIVE);p.teleportTo(server.overworld(),.5,83.0,-15.0,0f,0f);p.abilities.flying=true;p.onUpdateAbilities()};capture("equipment-waiting.png")}
        if(ticks==455){
            val label=com.example.sbwhelldiver.client.BeaconCallHud.label
            LOGGER.info("EQUIPMENT_DELIVERY_HUD: label={} player={} camera={} entities={}",label,player.position(),mc.gameRenderer.mainCamera.position,mc.level!!.entitiesForRendering().filter{it is MachineContainerEntity||it is EquipmentDeploymentBeaconEntity}.map{e->"${e.type}:${e.position()}:"+when(e){is EquipmentDeploymentBeaconEntity->"${e.phase}:${e.selectedMachine}";is MachineContainerEntity->"${e.phase}:${e.root}";else->""}})
            check(label==null){"Container landing world HUD was not removed"}
            check(com.example.sbwhelldiver.client.StratagemCooldownClient.displayName(Stratagems.equipmentDeployment).string==net.minecraft.network.chat.Component.translatable(MachineDeployment.BIOPROCESSOR.translation).string){"Arrival HUD still names the beacon"}
            capture("equipment-countdown.png")
        }
        if(ticks==510)capture("equipment-retract.png")
        if(ticks==565) {
            for(effect in listOf("unfold","fold"))check(mechanicalSounds.count{it=="sfx_equipment_beacon_$effect"}==1){"Missing/duplicate mechanical stage: $mechanicalSounds"}
        }
        if(ticks==575)server.execute {
            val l=server.overworld()
            check(l.getEntity(device)==null){"Beacon/pod remained after machine delivery"}
            val boxes=l.getEntitiesOfClass(MachineContainerEntity::class.java,net.minecraft.world.phys.AABB(-4.0,79.0,-4.0,5.0,85.0,5.0))
            check(boxes.size==1&&boxes.single().phase==MachineContainerEntity.CLOSED){"Paid deployment did not deliver one closed container"}
            checkTickOptimizer(boxes.single(),"server/container-arrived")
            check(l.getBlockEntity(BlockPos(0,80,0))==null){"Machine generated before opening the delivered container"}
            server.playerList.getPlayer(player.uuid)!!.teleportTo(l,5.0,80.0,7.0,145f,-10f)
            LOGGER.info("EQUIPMENT_REAL_CONTAINER_DELIVERED")
        }
        if(ticks==585){
            checkTickOptimizer(mc.level!!.entitiesForRendering().filterIsInstance<MachineContainerEntity>().single(),"client/container-arrived")
            capture("equipment-container-arrived.png")
        }
        if(ticks==590)server.execute {
            val l=server.overworld();val p=server.playerList.getPlayer(player.uuid)!!
            p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(com.atsuishio.superbwarfare.init.ModItems.CROWBAR.get()))
            l.getEntitiesOfClass(MachineContainerEntity::class.java,net.minecraft.world.phys.AABB(-4.0,79.0,-4.0,5.0,85.0,5.0)).single().interact(p,InteractionHand.MAIN_HAND)
        }
        if(ticks==645){
            server.execute {check(server.overworld().getBlockEntity(BlockPos(0,80,0)) is SampleMachineBlockEntity);LOGGER.info("EQUIPMENT_MACHINE_OPENED")}
            capture("equipment-machine-deployed.png")
        }
        if(ticks==660){LOGGER.info("EQUIPMENT_CLIENT_COMPLETED");mc.stop()}
    }
    private fun checkTickOptimizer(entity:net.minecraft.world.entity.Entity,stage:String) {
        val contract=runCatching{Class.forName("me.kall.doespotatotick.ext.Tickable")}.getOrNull()?:return
        for(method in listOf("dpt\$alwaysTick","dpt\$tickable"))check(contract.getMethod(method).invoke(entity)==true){"$stage ${entity.type}: $method=false"}
        LOGGER.info("DPT_COMPAT_VERIFIED {} type={} y={} phase={}",stage,entity.type,entity.y,when(entity){is EquipmentDeploymentBeaconEntity->entity.phase;is MachineContainerEntity->entity.phase;else->-1})
    }
    private fun capture(name:String){val mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget){LOGGER.info("EQUIPMENT_SCREENSHOT {} {}",name,it.string)}}
}
