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
    private val mechanicalSounds=mutableListOf<String>()
    private val flashRuns=mutableMapOf<Long,Int>()
    private var flashWasRed=false
    private var flashAt=-1L
    private var flashCaptureAt=-1
    private var flashCaptureName=""
    @SubscribeEvent fun sound(event:net.neoforged.neoforge.client.event.sound.PlaySoundEvent) {
        if(java.lang.Boolean.getBoolean("sbw_helldiver.equipmentRegression"))event.sound?.location?.path?.let {
            if(it.startsWith("sfx_equipment_beacon_")) {
                mechanicalSounds.add(it)
                val e=Minecraft.getInstance().level!!.entitiesForRendering().filterIsInstance<EquipmentDeploymentBeaconEntity>().first{b->b.uuid==device}
                val age=(e.level().gameTime-e.phaseStart).toInt()
                val expected=mapOf("sfx_equipment_beacon_lift" to 9,"sfx_equipment_beacon_unfold" to 22,"sfx_equipment_beacon_fold" to 1,"sfx_equipment_beacon_lower" to 24,"sfx_equipment_beacon_iris" to 50).getValue(it)
                check(kotlin.math.abs(age-expected)<=1){"Mechanical cue out of sync: $it at $age, expected $expected"}
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
            val e=ModEntities.EQUIPMENT_BEACON.get().create(l)!!;val landing=net.minecraft.world.phys.Vec3(.5,80.0,.5)
            e.moveTo(.5,80.0,.5,EquipmentBeaconGeometry.deploymentYaw(p.position(),landing,p.yRot),0f);e.configure(80.0,l.gameTime+35,p.uuid);l.addFreshEntity(e);device=e.uuid
            LOGGER.info("EQUIPMENT_FIXTURE_READY")
        }
        if(ticks==95)server.execute {val p=server.playerList.getPlayer(player.uuid)!!;p.teleportTo(server.overworld(),.5,83.0,-15.0,0f,0f);p.abilities.flying=true;p.onUpdateAbilities()}
        if(ticks==105){
            check(com.example.sbwhelldiver.client.DeliveryFlightVisuals.beaconJetFrames>0){"Attached beacon jets never rendered"}
            check(com.example.sbwhelldiver.client.BeaconCallHud.label==null){"Deployed beacon still shows an arrived world HUD"}
            capture("equipment-deployed.png")
        }
        if(ticks==110)server.execute {
            val e=server.overworld().getEntity(device) as EquipmentDeploymentBeaconEntity
            val p=server.playerList.getPlayer(player.uuid)!!;val eye=e.screenCenter().add(e.screenNormal().scale(1.1));val d=e.screenCenter().subtract(eye)
            p.abilities.flying=true;p.onUpdateAbilities()
            p.teleportTo(server.overworld(),eye.x,eye.y-p.eyeHeight,eye.z,Math.toDegrees(kotlin.math.atan2(-d.x,d.z)).toFloat(),(-Math.toDegrees(kotlin.math.atan2(d.y,kotlin.math.sqrt(d.x*d.x+d.z*d.z)))).toFloat())
        }
        if(ticks==120) {
            val e=mc.level!!.entitiesForRendering().filterIsInstance<EquipmentDeploymentBeaconEntity>().first()
            LOGGER.info("EQUIPMENT_CLIENT_SCREEN_HIT={}",e.screenHit(player))
            mc.gameMode!!.interactAt(player,e,net.minecraft.world.phys.EntityHitResult(e,e.screenCenter()),InteractionHand.MAIN_HAND)
        }
        if(ticks==135){
            check(mc.screen is EquipmentTerminalScreen){"Terminal did not open"}
            val e=(mc.screen as EquipmentTerminalScreen).beacon()!!;val camera=mc.gameRenderer.mainCamera
            check(net.minecraft.world.phys.Vec3(camera.lookVector).dot(e.screenCenter().subtract(camera.position).normalize())>.999){"Camera is not aimed at the screen centre"}
            capture("equipment-menu.png")
            server.execute{server.playerList.getPlayer(player.uuid)!!.setGameMode(GameType.SURVIVAL)}
        }
        fun key(d:Int){(mc.screen as? EquipmentTerminalScreen)?.keyPressed(listOf(GLFW.GLFW_KEY_UP,GLFW.GLFW_KEY_RIGHT,GLFW.GLFW_KEY_DOWN,GLFW.GLFW_KEY_LEFT)[d],0,0)?:error("No terminal at $ticks")}
        if(ticks==138)key(1) // Invalid selection; subsequent valid arrows remain usable during the feedback.
        if(ticks==167)key(0) // Invalid confirmation command on the material page.
        val observed=mc.level!!.entitiesForRendering().filterIsInstance<EquipmentDeploymentBeaconEntity>().firstOrNull{it.uuid==device}
        if(observed!=null&&observed.inputErrorAt>=0) {
            if(flashAt!=observed.inputErrorAt){flashAt=observed.inputErrorAt;flashWasRed=false}
            val red=com.example.sbwhelldiver.client.EquipmentTerminalFeedback.flashing(observed)
            if(red&&!flashWasRed){flashRuns.merge(flashAt,1,Int::plus);flashCaptureAt=ticks+2;flashCaptureName="equipment-input-error-${flashRuns.size}-${flashRuns[flashAt]}.png"}
            if(ticks==flashCaptureAt)capture(flashCaptureName)
            flashWasRed=red
        }
        if(ticks==200){check(flashRuns.size==2&&flashRuns.values.all{it==1}){"Incorrect input did not produce one red flash on both menus: $flashRuns"};LOGGER.info("EQUIPMENT_ALL_PAGES_ERROR_FLASH_VERIFIED {}",flashRuns)}
        val bio=MachineDeployment.BIOPROCESSOR.code
        if(ticks in 140..156&&(ticks-140)%4==0)key(bio[(ticks-140)/4])
        if(ticks==165)capture("equipment-material.png")
        if(ticks in 170..190&&(ticks-170)%4==0)key(1)
        if(ticks==200)capture("equipment-insufficient.png")
        if(ticks==205)key(3)
        if(ticks==209)server.execute {server.overworld().setBlock(BlockPos(2,80,2),Blocks.STONE.defaultBlockState(),3)}
        if(ticks in 214..230&&(ticks-214)%4==0)key(MachineDeployment.REFINERY.code[(ticks-214)/4])
        if(ticks==240)capture("equipment-no-space.png")
        if(ticks==245)server.execute {
            server.overworld().setBlock(BlockPos(2,80,2),Blocks.AIR.defaultBlockState(),3)
            val inventory=server.playerList.getPlayer(player.uuid)!!.inventory
            MachineDeployment.BIOPROCESSOR.materials.forEachIndexed{i,m->inventory.setItem(i+1,ItemStack(m.item,m.count))}
            server.playerList.getPlayer(player.uuid)!!.containerMenu.broadcastChanges()
        }
        if(ticks in 250..266&&(ticks-250)%4==0)key(bio[(ticks-250)/4])
        if(ticks in 275..295&&(ticks-275)%4==0)key(1)
        if(ticks==310){check(mc.screen !is EquipmentTerminalScreen&&mc.cameraEntity===player){"Camera did not return"};server.execute{val p=server.playerList.getPlayer(player.uuid)!!;p.setGameMode(GameType.CREATIVE);p.teleportTo(server.overworld(),.5,83.0,-15.0,0f,0f);p.abilities.flying=true;p.onUpdateAbilities()};capture("equipment-waiting.png")}
        if(ticks==395){
            val label=com.example.sbwhelldiver.client.BeaconCallHud.label
            LOGGER.info("EQUIPMENT_DELIVERY_HUD: label={} player={} camera={} entities={}",label,player.position(),mc.gameRenderer.mainCamera.position,mc.level!!.entitiesForRendering().filter{it is MachineContainerEntity||it is EquipmentDeploymentBeaconEntity}.map{e->"${e.type}:${e.position()}:"+when(e){is EquipmentDeploymentBeaconEntity->"${e.phase}:${e.selectedMachine}";is MachineContainerEntity->"${e.phase}:${e.root}";else->""}})
            check(label==null){"Container landing world HUD was not removed"}
            check(com.example.sbwhelldiver.client.StratagemCooldownClient.displayName(Stratagems.equipmentDeployment).string==net.minecraft.network.chat.Component.translatable(MachineDeployment.BIOPROCESSOR.translation).string){"Arrival HUD still names the beacon"}
            capture("equipment-countdown.png")
        }
        if(ticks==450)capture("equipment-retract.png")
        if(ticks==505) {
            for(effect in listOf("lift","unfold","fold","lower","iris"))check(mechanicalSounds.count{it=="sfx_equipment_beacon_$effect"}==1){"Missing/duplicate mechanical stage: $mechanicalSounds"}
        }
        if(ticks==515)server.execute {
            val l=server.overworld()
            check(l.getEntity(device)==null){"Beacon/pod remained after machine delivery"}
            val boxes=l.getEntitiesOfClass(MachineContainerEntity::class.java,net.minecraft.world.phys.AABB(-4.0,79.0,-4.0,5.0,85.0,5.0))
            check(boxes.size==1&&boxes.single().phase==MachineContainerEntity.CLOSED){"Paid deployment did not deliver one closed container"}
            check(l.getBlockEntity(BlockPos(0,80,0))==null){"Machine generated before opening the delivered container"}
            server.playerList.getPlayer(player.uuid)!!.teleportTo(l,5.0,80.0,7.0,145f,-10f)
            LOGGER.info("EQUIPMENT_REAL_CONTAINER_DELIVERED")
        }
        if(ticks==525)capture("equipment-container-arrived.png")
        if(ticks==530)server.execute {
            val l=server.overworld();val p=server.playerList.getPlayer(player.uuid)!!
            p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(com.atsuishio.superbwarfare.init.ModItems.CROWBAR.get()))
            l.getEntitiesOfClass(MachineContainerEntity::class.java,net.minecraft.world.phys.AABB(-4.0,79.0,-4.0,5.0,85.0,5.0)).single().interact(p,InteractionHand.MAIN_HAND)
        }
        if(ticks==585){
            server.execute {check(server.overworld().getBlockEntity(BlockPos(0,80,0)) is SampleMachineBlockEntity);LOGGER.info("EQUIPMENT_MACHINE_OPENED")}
            capture("equipment-machine-deployed.png")
        }
        if(ticks==600){LOGGER.info("EQUIPMENT_CLIENT_COMPLETED");mc.stop()}
    }
    private fun capture(name:String){val mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget){LOGGER.info("EQUIPMENT_SCREENSHOT {} {}",name,it.string)}}
}
