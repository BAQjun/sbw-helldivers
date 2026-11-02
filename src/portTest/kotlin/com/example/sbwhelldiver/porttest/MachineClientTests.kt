package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.*
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.survival.*
import com.example.sbwhelldiver.compat.jei.SampleJeiPlugin
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.*
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.*
import net.minecraft.world.item.*
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.client.event.ClientTickEvent

/** Disposable, Creative-only deployment, world-port and JEI fixture. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object MachineClientTests {
    private var started=false
    private var ticks=0
    private val launched=System.currentTimeMillis()
    private val boxes=mutableListOf<java.util.UUID>()
    private val roots=listOf(BlockPos(-5,80,0),BlockPos(4,80,0))
    private var capturedJets=false
    private var lodPod:java.util.UUID?=null
    private var configParent:net.minecraft.client.gui.screens.Screen?=null
    private val openingAudio=mutableListOf<Pair<java.util.UUID,Int>>()
    private var placements=0
    @SubscribeEvent fun sound(event:net.neoforged.neoforge.client.event.sound.PlaySoundEvent) {
        if(!java.lang.Boolean.getBoolean("sbw_helldiver.machineRegression"))return
        val sound=event.sound?:return
        if(sound.location==net.minecraft.sounds.SoundEvents.METAL_PLACE.location)placements++
        if(sound.location.path=="sfx_emplacement_retract_mechanism") {
            val e=Minecraft.getInstance().level!!.entitiesForRendering().filterIsInstance<MachineContainerEntity>().minBy{it.position().distanceToSqr(net.minecraft.world.phys.Vec3(sound.x,sound.y,sound.z))}
            val age=(e.level().gameTime-e.phaseStart).toInt()
            check(age in 8..9){"Container mechanical audio outside door movement start: $age"}
            openingAudio.add(e.uuid to age)
        }
    }
    @SubscribeEvent fun tick(event:ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("sbw_helldiver.machineRegression"))return
        val mc=Minecraft.getInstance()
        if(System.currentTimeMillis()-launched>300000){LOGGER.error("MACHINE_CLIENT_TIMEOUT");mc.stop();return}
        if(!started&&mc.screen is TitleScreen&&mc.overlay==null){
            System.setProperty("sbw_helldiver.beaconSmokeTest","true")
            System.setProperty("sbw_helldiver.clientSmokeTest","true")
            started=true;mc.options.pauseOnLostFocus=false;mc.options.languageCode="zh_cn"
            com.example.sbwhelldiver.config.ClientModelConfig.lowQualityModels.set(false)
            com.example.sbwhelldiver.config.ClientModelConfig.modelLod.set(true)
            com.example.sbwhelldiver.config.ClientModelConfig.modelLodDistance.set(32)
            mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Survival machine verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(1,false,false),{a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()},mc.screen!!)
            return
        }
        val player=mc.player?:return;val server=mc.singleplayerServer?:return;ticks++
        if(ticks in 410..430)mc.options.keyShift.setDown(true)
        if(!capturedJets&&mc.level!!.entitiesForRendering().filterIsInstance<MachineContainerEntity>().any{it.phase==MachineContainerEntity.FALLING&&it.y-it.root.y in 3.0..10.0}) {
            capturedJets=true;capture("machine-retro-jets.png")
        }
        if(ticks==1)server.execute {
            val l=server.overworld();l.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);l.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);l.dayTime=6000
            for(x in -16..16)for(z in -16..12)for(y in 77..79)l.setBlock(BlockPos(x,y,z),if(y==79)Blocks.SMOOTH_STONE.defaultBlockState()else Blocks.STONE.defaultBlockState(),3)
            for(x in listOf(-14,14))for(z in listOf(-14,10))l.setBlock(BlockPos(x,79,z),Blocks.SEA_LANTERN.defaultBlockState(),3)
            l.setDefaultSpawnPos(BlockPos(0,80,-14),0f)
            val p=server.playerList.getPlayer(player.uuid)!!;p.setGameMode(GameType.CREATIVE);p.teleportTo(l,.5,83.0,-14.0,0f,8f);p.abilities.flying=true;p.onUpdateAbilities()
            p.inventory.clearContent();p.inventory.setItem(0,ItemStack(com.atsuishio.superbwarfare.init.ModItems.CROWBAR.get()))
            val pod=ModEntities.HMG_EMPLACEMENT.get().create(l)!!;pod.setPos(-11.0,80.0,6.0)
            val saved=CompoundTag();pod.saveWithoutId(saved);saved.putInt("HellpodPhase",com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase.ACTIVE.ordinal);saved.putBoolean("HellpodCapReleased",true);saved.putDouble("HellpodRestY",80.0);pod.load(saved);l.addFreshEntity(pod);lodPod=pod.uuid
            for((i,m)in MachineDeployment.entries.withIndex()){
                val e=ModEntities.MACHINE_CONTAINER.get().create(l)!!;e.configure(m,roots[i],Direction.NORTH,CompoundTag(),true,l.gameTime+100);l.addFreshEntity(e);boxes.add(e.uuid)
            }
            LOGGER.info("MACHINE_FIXTURE_READY")
        }
        if(ticks==120){
            check(mc.level!!.getBlockEntity(roots[0])==null){"Machine appeared inside closed container"}
            val audio=com.example.sbwhelldiver.client.SoundTimeline
            check(audio.startedCount("sfx_hellpod_entry_layer_03")>=2&&audio.startedCount("sfx_hellpod_retro_start")>=2&&audio.startedCount("sfx_hellpod_impact")>=2){"Delivery flight audio missing: ${audio.debugReport()}"}
            check(com.example.sbwhelldiver.client.HellpodJetParticle.rendered>0){"Container nozzle flames never rendered"}
            check(com.example.sbwhelldiver.client.DeliveryFlightVisuals.containerJetFrames>0){"Attached container nozzle flames never rendered"}
            LOGGER.info("MACHINE_FLIGHT_AUDIO_AND_JETS_VERIFIED")
            for(m in MachineDeployment.entries){
                val model=mc.itemRenderer.getModel(MachineContainerItem.stack(m),mc.level,player,0)
                check(model.isCustomRenderer){"Container item is still a flat sprite: ${m.id}"}
                check(model.particleIcon.contents().name()!=net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()){"Missing container item texture: ${m.id}"}
            }
            capture("machine-closed-containers.png")
        }
        if(ticks==130)server.execute {
            val p=server.playerList.getPlayer(player.uuid)!!
            for(id in boxes)(server.overworld().getEntity(id) as MachineContainerEntity).interact(p,InteractionHand.MAIN_HAND)
        }
        if(ticks==150)capture("machine-shell-opening.png")
        if(ticks==175)server.execute{check(boxes.all{server.overworld().getEntity(it)==null}){"Container opening still takes more than two seconds"}}
        if(ticks==185){check(openingAudio.size==2&&openingAudio.map{it.first}.toSet()==boxes.toSet()){"Missing or duplicate container opening audio: $openingAudio"};LOGGER.info("MACHINE_OPENING_AUDIO_ALIGNED {}",openingAudio)}
        if(ticks==195)server.execute {
            val l=server.overworld()
            for((i,m)in MachineDeployment.entries.withIndex()){
                val be=l.getBlockEntity(roots[i]) as SampleMachineBlockEntity
                val energy=l.getCapability(Capabilities.EnergyStorage.BLOCK,roots[i].offset(MachineLayout.portOffset(m,MachineLayout.Port.ENERGY)),Direction.NORTH)!!
                energy.receiveEnergy(5_000_000,false)
                be.inventory.setStackInSlot(0,ItemStack(if(i==0)Items.ROTTEN_FLESH else Items.RAW_IRON,64))
                val output=roots[i].offset(MachineLayout.portOffset(m,MachineLayout.Port.OUTPUT)).north()
                l.setBlock(output,Blocks.CHEST.defaultBlockState(),3)
            }
            LOGGER.info("MACHINE_WORKING_WITHOUT_GUI")
        }
        if(ticks==245){
            val iris=Class.forName("net.irisshaders.iris.Iris").getMethod("isPackInUseQuick").invoke(null)
            LOGGER.info("MACHINE_IRIS_SHADER_ACTIVE={}",iris)
            capture("machine-working.png")
            val mesh=com.example.sbwhelldiver.client.MachineMesh
            check(mesh.batchCount("bioprocessor")==2&&mesh.batchCount("refinery")<=8){"Machine draw batches were not merged"}
            check(mesh.batchCount("bioprocessor_container")<=12&&mesh.batchCount("refinery_container")<=12){"Container parts still draw separately"}
            check(mesh.glowFaceCount("bioprocessor")>0&&mesh.glowFaceCount("refinery")>0){"Blue emissive geometry missing"}
            check(com.example.sbwhelldiver.client.MachineAudio.activeCount==2){"Working machine loops missing"}
            val lod=com.example.sbwhelldiver.client.ModelLod
            LOGGER.info("LOD_SKELETONS_VERIFIED={}",lod.verifySkeletons())
            check(!lod.reduced(31.0*31)&&lod.reduced(32.0*32))
            check(lod.mineTier(6.0*6)==0&&lod.mineTier(7.0*7)==1&&lod.mineTier(15.0*15)==2)
            com.example.sbwhelldiver.config.ClientModelConfig.modelLodDistance.set(64)
            check(!lod.reduced(40.0*40)&&lod.reduced(64.0*64))
            com.example.sbwhelldiver.config.ClientModelConfig.modelLod.set(false);check(!lod.reduced(256.0*256))
            com.example.sbwhelldiver.config.ClientModelConfig.modelLod.set(true);com.example.sbwhelldiver.config.ClientModelConfig.modelLodDistance.set(32)
            check(mesh.batchCount("bioprocessor_lod")>0&&mesh.batchCount("refinery_lod")>0)
            LOGGER.info("MACHINE_BATCHES bio={} refinery={} containers={}/{} glowFaces={}/{} audio={}",mesh.batchCount("bioprocessor"),mesh.batchCount("refinery"),mesh.batchCount("bioprocessor_container"),mesh.batchCount("refinery_container"),mesh.glowFaceCount("bioprocessor"),mesh.glowFaceCount("refinery"),com.example.sbwhelldiver.client.MachineAudio.activeCount)
        }
        if(ticks==250)server.execute {
            server.overworld().dayTime=18000
            server.playerList.getPlayer(player.uuid)!!.teleportTo(server.overworld(),-5.0,82.0,-4.7,0f,5f)
        }
        if(ticks==265)capture("machine-bio-emissive-night.png")
        if(ticks==270)server.execute{server.playerList.getPlayer(player.uuid)!!.teleportTo(server.overworld(),4.0,83.0,7.0,180f,-3f)}
        if(ticks==285)capture("machine-refinery-emissive-night.png")
        if(ticks==290)server.execute{server.overworld().dayTime=6000;server.playerList.getPlayer(player.uuid)!!.teleportTo(server.overworld(),.5,83.0,-14.0,0f,8f)}
        if(ticks==300){
            server.execute {
                check(boxes.all{server.overworld().getEntity(it)==null}){"Shell entity did not disappear"}
                for(root in roots){val be=server.overworld().getBlockEntity(root) as SampleMachineBlockEntity;check(be.workTicks>=100){"Machine did not complete first batch"}}
                LOGGER.info("MACHINE_PROCESSING_CLIENT_VERIFIED")
            }
            val runtime=checkNotNull(MachineJeiProbe.runtime){"JEI runtime missing"}
            runtime.recipesGui.showTypes(listOf(SampleJeiPlugin.BIO))
        }
        if(ticks==320)capture("machine-jei-bioprocessor.png")
        if(ticks==330)MachineJeiProbe.runtime!!.recipesGui.showTypes(listOf(SampleJeiPlugin.REFINERY))
        if(ticks==350)capture("machine-jei-refinery.png")
        if(ticks==360)MachineJeiProbe.runtime!!.recipesGui.showTypes(listOf(SampleJeiPlugin.DEPLOYMENT))
        if(ticks==380)capture("machine-jei-deployment.png")
        if(ticks==390){mc.setScreen(null);check(mc.screen==null);capture("machine-final.png")}
        if(ticks==395)server.execute {
            // Pause both real machines without removing their reserved batches.
            for(root in roots){val be=server.overworld().getBlockEntity(root) as SampleMachineBlockEntity;val n=be.contents();n.putInt("Energy",0);be.restoreContents(n)}
            server.playerList.getPlayer(player.uuid)!!.teleportTo(server.overworld(),-5.0,81.0,-3.0,0f,10f)
        }
        if(ticks==410){
            check(com.example.sbwhelldiver.client.MachineAudio.activeCount==0){"Unpowered machine audio kept playing"}
            player.isShiftKeyDown=true
            server.execute{server.playerList.getPlayer(player.uuid)!!.isShiftKeyDown=true}
        }
        if(ticks==415){
            player.input.shiftKeyDown=true
            player.isShiftKeyDown=true
            val pos=roots[0].north();val hit=net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos),Direction.NORTH,pos,false)
            check(mc.gameMode!!.useItemOn(player,InteractionHand.MAIN_HAND,hit).consumesAction()){"Client sneaking click was bypassed"}
        }
        if(ticks==430){
            check(mc.level!!.getBlockEntity(roots[0])==null){"Client/server crowbar packing did not remove machine"}
            val drops=mc.level!!.entitiesForRendering().filterIsInstance<net.minecraft.world.entity.item.ItemEntity>()
            check(drops.count{it.item.`is`(com.example.sbwhelldiver.init.ModItems.BIOPROCESSOR_CONTAINER.get())}==1){"Client packing did not produce one container"}
            check(com.example.sbwhelldiver.client.MachineAudio.activeCount==0){"Packed machine audio kept playing"}
            LOGGER.info("MACHINE_CLIENT_PACKING_AND_AUDIO_STOP_VERIFIED starts={} stops={}",com.example.sbwhelldiver.client.MachineAudio.starts,com.example.sbwhelldiver.client.MachineAudio.stops)
            mc.options.keyShift.setDown(false)
        }
        if(ticks==435)server.execute{server.playerList.getPlayer(player.uuid)!!.teleportTo(server.overworld(),.5,87.0,-45.0,0f,7f)}
        if(ticks==450){
            val pod=mc.level!!.entitiesForRendering().first{it.uuid==lodPod} as com.example.sbwhelldiver.entity.AbstractHellpodEntity
            check(com.example.sbwhelldiver.client.ModelLod.entry(pod)!=null){"Distant emplacement did not switch to LOD"}
            capture("machine-and-emplacement-lod.png");LOGGER.info("LOD_DISTANT_RENDER_VERIFIED")
        }
        if(ticks==455){
            configParent=object:net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("QA parent")){}
            mc.setScreen(com.example.sbwhelldiver.client.HelldiversConfigScreen(configParent!!))
        }
        if(ticks==460)capture("machine-lod-config.png")
        if(ticks==465){
            val screen=mc.screen!!;screen.children().filterIsInstance<net.minecraft.client.gui.components.EditBox>().single().value=""
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first{it.message.string==net.minecraft.network.chat.Component.translatable("config.sbw_helldiver.save").string}.onPress()
            check(mc.screen===screen&&com.example.sbwhelldiver.config.ClientModelConfig.modelLodDistance.get()==32){"Invalid LOD distance was saved"}
            screen.children().filterIsInstance<net.minecraft.client.gui.components.EditBox>().single().value="48"
            screen.children().filterIsInstance<net.minecraft.client.gui.components.Button>().first{it.message.string==net.minecraft.network.chat.Component.translatable("config.sbw_helldiver.save").string}.onPress()
            check(mc.screen===configParent&&com.example.sbwhelldiver.config.ClientModelConfig.modelLodDistance.get()==48){"LOD GUI did not apply distance"}
            com.example.sbwhelldiver.config.ClientModelConfig.modelLodDistance.set(32);com.example.sbwhelldiver.config.ClientModelConfig.SPEC.save();mc.setScreen(null)
            LOGGER.info("LOD_CONFIG_GUI_VERIFIED")
        }
        if(ticks==480)server.execute {
            val l=server.overworld();val p=server.playerList.getPlayer(player.uuid)!!
            val packed=MachineDeployment.entries.map{MachineContainerItem.stack(it)}.toMutableList()
            p.inventory.clearContent();p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(com.atsuishio.superbwarfare.init.ModItems.CROWBAR.get()));p.isShiftKeyDown=true
            for(type in listOf(ModEntities.HMG_EMPLACEMENT,ModEntities.ANTI_TANK_EMPLACEMENT,ModEntities.GRENADIER_BATTLEMENT)) {
                val e=type.get().create(l)!!;e.setPos(0.0,80.0,-6.0)
                val n=CompoundTag();e.saveWithoutId(n);n.putInt("HellpodPhase",com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase.ACTIVE.ordinal);n.putInt("HmgAmmo",17);e.load(n);l.addFreshEntity(e)
                check(p.interactOn(e,InteractionHand.MAIN_HAND).consumesAction()&&e.isRemoved)
            }
            val nativeBoxes=(0 until p.inventory.containerSize).map{p.inventory.getItem(it)}.filter{it.`is`(com.atsuishio.superbwarfare.init.ModBlocks.CONTAINER.get().asItem())}
            check(nativeBoxes.size==3){"Real player did not receive all native vehicle containers"}
            packed.addAll(nativeBoxes.map{it.copy()});p.inventory.clearContent()
            for((i,stack)in packed.withIndex()) {
                p.inventory.setItem(i,stack.copy())
                val drop=net.minecraft.world.entity.item.ItemEntity(l,-4.0+i*2,80.2,-7.0,stack.copy());drop.setNoGravity(true);drop.deltaMovement=net.minecraft.world.phys.Vec3.ZERO;drop.setNeverPickUp();l.addFreshEntity(drop)
            }
            p.isShiftKeyDown=false;p.teleportTo(l,0.0,82.0,-12.0,0f,18f)
            LOGGER.info("ALL_THREE_NATIVE_CONTAINER_ITEMS_VERIFIED")
        }
        if(ticks==500)capture("packed-containers-ground.png")
        if(ticks==505)server.execute{server.playerList.getPlayer(player.uuid)!!.teleportTo(server.overworld(),-4.0,80.4,-9.0,0f,15f)}
        if(ticks==520)capture("packed-bioprocessor-item.png")
        if(ticks==525) {
            player.inventory.selected=1
            server.execute{val p=server.playerList.getPlayer(player.uuid)!!;p.inventory.selected=1;p.teleportTo(server.overworld(),-2.0,80.4,-9.0,0f,15f)}
        }
        if(ticks==540)capture("packed-refinery-item.png")
        if(ticks==545)mc.setScreen(net.minecraft.client.gui.screens.inventory.InventoryScreen(player))
        if(ticks==555)capture("packed-containers-inventory.png")
        if(ticks==565){mc.setScreen(null);player.inventory.selected=0;server.execute{val p=server.playerList.getPlayer(player.uuid)!!;p.inventory.selected=0;p.setItemInHand(InteractionHand.MAIN_HAND,MachineContainerItem.stack(MachineDeployment.BIOPROCESSOR));p.teleportTo(server.overworld(),-10.5,80.0,-10.5,0f,20f)}}
        if(ticks==580){
            val pos=BlockPos(-10,79,-10);val hit=net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0.0,.5,0.0),Direction.UP,pos,false)
            check(mc.gameMode!!.useItemOn(player,InteractionHand.MAIN_HAND,hit).consumesAction())
        }
        if(ticks==590){check(placements==1){"Expected one iron block placement sound: $placements"};val pos=BlockPos(-10,79,-10);val hit=net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos).add(0.0,.5,0.0),Direction.UP,pos,false);mc.gameMode!!.useItemOn(player,InteractionHand.MAIN_HAND,hit)}
        if(ticks==600){check(placements==1){"Failed placement also played iron sound: $placements"};LOGGER.info("MACHINE_PLACEMENT_SOUND_VERIFIED");LOGGER.info("MACHINE_CLIENT_COMPLETED");mc.stop()}
    }
    private fun capture(name:String){val mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget){LOGGER.info("MACHINE_SCREENSHOT {} {}",name,it.string)}}
}

@mezz.jei.api.JeiPlugin
class MachineJeiProbe:mezz.jei.api.IModPlugin {
    override fun getPluginUid()=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MOD_ID,"machine_test_probe")
    override fun onRuntimeAvailable(runtime:mezz.jei.api.runtime.IJeiRuntime){Companion.runtime=runtime}
    override fun onRuntimeUnavailable(){runtime=null}
    companion object {var runtime:mezz.jei.api.runtime.IJeiRuntime?=null}
}
