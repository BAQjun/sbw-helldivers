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
    @SubscribeEvent fun tick(event:ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("sbw_helldiver.machineRegression"))return
        val mc=Minecraft.getInstance()
        if(System.currentTimeMillis()-launched>300000){LOGGER.error("MACHINE_CLIENT_TIMEOUT");mc.stop();return}
        if(!started&&mc.screen is TitleScreen&&mc.overlay==null){
            System.setProperty("sbw_helldiver.beaconSmokeTest","true")
            System.setProperty("sbw_helldiver.clientSmokeTest","true")
            started=true;mc.options.pauseOnLostFocus=false;mc.options.languageCode="zh_cn"
            mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Survival machine verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(1,false,false),{a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()},mc.screen!!)
            return
        }
        val player=mc.player?:return;val server=mc.singleplayerServer?:return;ticks++
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
                check(model.particleIcon.contents().name()!=net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()){"Missing container item texture: ${m.id}"}
            }
            capture("machine-closed-containers.png")
        }
        if(ticks==130)server.execute {
            val p=server.playerList.getPlayer(player.uuid)!!
            for(id in boxes)(server.overworld().getEntity(id) as MachineContainerEntity).interact(p,InteractionHand.MAIN_HAND)
        }
        if(ticks==150)capture("machine-shell-opening.png")
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
        }
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
        if(ticks==405){LOGGER.info("MACHINE_CLIENT_COMPLETED");mc.stop()}
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
