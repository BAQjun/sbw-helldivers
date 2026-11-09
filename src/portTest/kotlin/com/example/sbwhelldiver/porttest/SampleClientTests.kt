package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.*
import com.example.sbwhelldiver.client.*
import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.menu.SampleContainerMenu
import com.example.sbwhelldiver.stratagem.*
import com.example.sbwhelldiver.survival.*
import net.minecraft.client.*
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.*
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.*
import net.minecraft.world.item.*
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.*
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.client.event.ClientTickEvent
import java.math.BigInteger

/** Disposable creative fixture with a real online owner and both client/server menu protocols. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object SampleClientTests {
    private var started=false;private var ticks=0;private val launched=System.currentTimeMillis()
    private val terminal=BlockPos(0,80,0);private val machine=BlockPos(5,80,1)
    @SubscribeEvent fun tick(e:ClientTickEvent.Post){
        if(!java.lang.Boolean.getBoolean("sbw_helldiver.sampleRegression"))return
        val mc=Minecraft.getInstance()
        try{run(mc)}catch(t:Throwable){LOGGER.error("SAMPLE_CLIENT_FAILED",t);mc.stop()}
    }
    private fun run(mc:Minecraft){
        if(System.currentTimeMillis()-launched>600000){LOGGER.error("SAMPLE_CLIENT_TIMEOUT");mc.stop();return}
        if(!started&&mc.screen is TitleScreen&&mc.overlay==null){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.languageCode="zh_cn"
            mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Sample container verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(1,false,false),{a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()},mc.screen!!);return
        }
        val p=mc.player?:return;val server=mc.singleplayerServer?:return;ticks++
        fun onServer(action:(net.minecraft.server.level.ServerPlayer)->Unit){server.execute{try{action(server.playerList.getPlayer(p.uuid)!!)}catch(t:Throwable){LOGGER.error("SAMPLE_SERVER_FAILED",t);mc.execute{mc.stop()}}}}
        if(ticks==1)onServer{player->
            val l=server.overworld();l.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);l.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);l.dayTime=6000
            for(x in -8..10)for(z in -8..8)l.setBlock(BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
            l.setDefaultSpawnPos(BlockPos(1,80,-4),0f);player.setGameMode(GameType.CREATIVE);player.teleportTo(l,1.0,80.0,-3.0,0f,8f);player.inventory.clearContent()
            val bag=ItemStack(ModItems.SAMPLE_CONTAINER.get());SampleStorage.add(bag,0,BigInteger.valueOf(1000));SampleStorage.add(bag,1,BigInteger.valueOf(300));SampleStorage.add(bag,2,BigInteger.valueOf(10));player.inventory.setItem(0,bag)
            val state=ModBlocks.TACTICAL_TERMINAL.get().defaultBlockState().setValue(TacticalTerminalBlock.FACING,Direction.NORTH)
            for(i in 0..3)l.setBlock(TacticalTerminalBlock.position(terminal,Direction.NORTH,i),state.setValue(TacticalTerminalBlock.PART,i),3)
            val be=MachineStructure.place(l,machine,MachineDeployment.BIOPROCESSOR,Direction.NORTH,CompoundTag().also{it.putUUID("MachineOwner",player.uuid)},null)!!;be.readyAt=0
            player.isShiftKeyDown=true
            (bag.item as SampleContainerItem).useOn(UseOnContext(player,InteractionHand.MAIN_HAND,BlockHitResult(Vec3.atCenterOf(machine),Direction.NORTH,machine,false)))
            player.isShiftKeyDown=false
            check(be.boundContainer==SampleStorage.identity(bag))
            be.inventory.setStackInSlot(0,ItemStack(Items.DRAGON_EGG))
            l.getCapability(Capabilities.EnergyStorage.BLOCK,machine.offset(MachineLayout.portOffset(MachineDeployment.BIOPROCESSOR,MachineLayout.Port.ENERGY)),Direction.NORTH)!!.receiveEnergy(150000,false)
            LOGGER.info("SAMPLE_FIXTURE_READY")
        }
        if(ticks==50)capture("sample-container-held.png")
        if(ticks==60)mc.gameMode!!.useItem(p,InteractionHand.MAIN_HAND)
        if(ticks==85){check(mc.screen is SampleContainerScreen);capture("sample-container-menu.png")}
        if(ticks==90){val menu=p.containerMenu as SampleContainerMenu;mc.gameMode!!.handleInventoryButtonClick(menu.containerId,1)}
        if(ticks==105){check(SampleStorage.count((p.containerMenu as SampleContainerMenu).container,0)==BigInteger.valueOf(999));check(p.inventory.items.any{it.`is`(ModItems.COMMON_SAMPLE.get())});LOGGER.info("SAMPLE_MENU_WITHDRAW_SYNC_VERIFIED")}
        if(ticks==110){val menu=p.containerMenu as SampleContainerMenu;mc.gameMode!!.handleInventoryButtonClick(menu.containerId,0)}
        if(ticks==125){check(SampleStorage.count((p.containerMenu as SampleContainerMenu).container,0)==BigInteger.valueOf(1000));check(SampleStorage.count((p.containerMenu as SampleContainerMenu).container,2)==BigInteger.valueOf(60));capture("sample-container-remote-output.png");LOGGER.info("SAMPLE_REMOTE_OUTPUT_VERIFIED")}
        if(ticks==130)p.closeContainer()
        if(ticks==140)mc.gameMode!!.useItemOn(p,InteractionHand.MAIN_HAND,BlockHitResult(Vec3(1.0,80.95,0.0),Direction.NORTH,terminal,false))
        if(ticks==165){check(mc.screen is TacticalTerminalScreen);capture("sample-terminal-duration-options.png")}
        if(ticks==170)click(100.0,97.0)
        if(ticks==210){check(DestroyerClient.calls(40)==1);capture("sample-terminal-purchased.png");LOGGER.info("SAMPLE_CONTAINER_TERMINAL_PURCHASE_VERIFIED")}
        if(ticks==215){mc.screen?.onClose();onServer{player->val beacon=ItemStack(ModItems.BEACON.get());BeaconData.ensureIdentity(beacon);player.inventory.setItem(1,beacon);player.inventory.selected=1}}
        if(ticks in 230..305)StratagemClient.ALT.isDown=true
        if(ticks==245)onServer{player->val id=BeaconData.identity(player.mainHandItem)!!;for(arrow in Stratagems.destroyerCall.code)BeaconInput.accept(player,InteractionHand.MAIN_HAND,id,arrow)}
        if(ticks==260){check(StratagemClient.destroyerStage==3);capture("sample-destroyer-duration-menu.png")}
        if(ticks==265)StratagemClient.input(Stratagems.RIGHT)
        if(ticks==275){check(StratagemClient.destroyerStage==1);capture("sample-destroyer-bearing-menu.png")}
        if(ticks==280)StratagemClient.input(Stratagems.UP)
        if(ticks==300){check(StratagemCooldownClient.visual(Stratagems.destroyerCall.id).remaining<0);capture("sample-destroyer-warp.png")}
        if(ticks==310)StratagemClient.ALT.isDown=false
        if(ticks==365){check(DestroyerClient.calls(40)==0);check(DestroyerClient.remainingResidence(0f)!! in 2390..2400);capture("sample-destroyer-residence.png");LOGGER.info("SAMPLE_CLIENT_COMPLETED")
            if(!java.lang.Boolean.getBoolean("sbw_helldiver.samplePonderTests")){mc.stop();return}}
        if(ticks==380){mc.setScreen(net.createmod.ponder.foundation.ui.PonderUI.of(ItemStack(ModItems.SAMPLE_CONTAINER.get())).also{it.setComfyReadingEnabled(false)});LOGGER.info("SAMPLE_PONDER_OPENED")}
        if(ticks in listOf(430,530,640,750,865)){
            val ui=mc.screen as net.createmod.ponder.foundation.ui.PonderUI
            check(ui.activeScene.title.isNotBlank());capture("sample-ponder-${ticks}.png")
            LOGGER.info("SAMPLE_PONDER_FRAME {} {}",ticks,ui.activeScene.currentTime)
        }
        if(ticks==990){check((mc.screen as net.createmod.ponder.foundation.ui.PonderUI).activeScene.currentTime>=540);LOGGER.info("SAMPLE_PONDER_COMPLETED");mc.stop()}
    }
    private fun click(x:Double,y:Double){val mc=Minecraft.getInstance();val be=mc.level!!.getBlockEntity(terminal) as TacticalTerminalBlockEntity;val point=TacticalTerminalDisplay.screenPoint(be,x,y)!!;(mc.screen as TacticalTerminalScreen).mouseClicked(point.first,point.second,0)}
    private fun capture(name:String){val mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget){LOGGER.info("SAMPLE_SCREENSHOT {}",it.string)}}
}
