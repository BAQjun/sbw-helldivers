package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.*
import com.example.sbwhelldiver.client.TacticalTerminalScreen
import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.survival.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.*
import net.minecraft.core.registries.Registries
import net.minecraft.world.*
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.event.TickEvent
import net.minecraftforge.common.capabilities.ForgeCapabilities

@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object TacticalClientTests {
    private var started=false;private var ticks=0;private val launched=System.currentTimeMillis()
    private val root=BlockPos(0,80,0)
    private var cancelledSound:net.minecraft.client.resources.sounds.SoundInstance?=null
    @SubscribeEvent fun tick(e:TickEvent.ClientTickEvent){
        if(e.phase!=TickEvent.Phase.END)return
        if(!java.lang.Boolean.getBoolean("sbw_helldiver.tacticalRegression"))return
        val mc=Minecraft.getInstance()
        if(System.currentTimeMillis()-launched>240000){LOGGER.error("TACTICAL_TIMEOUT");mc.stop();return}
        if(!started&&mc.screen is TitleScreen&&mc.overlay==null){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.languageCode="zh_cn"
            mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Tactical terminal verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(1,false,false),{a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()});return
        }
        val p=mc.player?:return;val server=mc.singleplayerServer?:return;ticks++
        if(ticks==1)server.execute{
            val l=server.overworld();l.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);l.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);l.dayTime=6000
            for(x in -7..7)for(z in -7..7)l.setBlock(BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
            l.setDefaultSpawnPos(BlockPos(1,80,-4),0f)
            val player=server.playerList.getPlayer(p.uuid)!!;player.setGameMode(GameType.CREATIVE);player.teleportTo(l,1.0,80.0,-4.0,0f,0f);player.inventory.clearContent()
            val state=ModBlocks.TACTICAL_TERMINAL.get().defaultBlockState().setValue(TacticalTerminalBlock.FACING,Direction.NORTH)
            for(i in 0..3)l.setBlock(TacticalTerminalBlock.position(root,Direction.NORTH,i),state.setValue(TacticalTerminalBlock.PART,i),3)
            l.getBlockEntity(root)!!.getCapability(ForgeCapabilities.ENERGY,Direction.DOWN).resolve().orElseThrow().receiveEnergy(1000000,false)
            player.inventory.setItem(8,ItemStack(ModItems.TACTICAL_TERMINAL.get()));player.inventory.setItem(0,ItemStack(ModItems.COMMON_SAMPLE.get(),50));player.inventory.setItem(1,ItemStack(ModItems.RARE_SAMPLE.get(),5))
            LOGGER.info("TACTICAL_FIXTURE_READY")
        }
        if(ticks==80)capture("tactical-powered.png")
        if(ticks==90){mc.gameMode!!.useItemOn(p,InteractionHand.MAIN_HAND,net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3(1.0,80.95,0.0),Direction.NORTH,root,false))}
        if(ticks==120){check(mc.screen is TacticalTerminalScreen);capture("tactical-menu.png")}
        if(ticks==140)click(100.0,65.0)
        if(ticks==150)capture("tactical-orbital.png")
        if(ticks==160)click(100.0,50.0)
        if(ticks==164){
            val f=TacticalTerminalScreen::class.java.getDeclaredField("holdSound").apply{isAccessible=true}
            cancelledSound=f.get(mc.screen) as net.minecraft.client.resources.sounds.SoundInstance
            check(mc.soundManager.isActive(cancelledSound!!)){"Purchase hold sound did not start"}
        }
        if(ticks==165)(mc.screen as TacticalTerminalScreen).mouseReleased(0.0,0.0,0)
        if(ticks==169){
            server.execute{val player=server.playerList.getPlayer(p.uuid)!!;check(player.inventory.getItem(0).count==50);check(player.inventory.getItem(1).count==5);LOGGER.info("TACTICAL_CANCEL_AUDIO_VERIFIED")}
        }
        // SoundEngine retains stopped channels for at least 20 ticks; check after that grace period,
        // before this 1.83 s sample could have finished naturally.
        if(ticks==190)check(!mc.soundManager.isActive(cancelledSound!!)){"Released purchase sound kept playing"}
        if(ticks==195)click(100.0,50.0)
        if(ticks==210)capture("tactical-hold.png")
        if(ticks==235){
            (mc.screen as TacticalTerminalScreen).mouseReleased(0.0,0.0,0)
            server.execute{val player=server.playerList.getPlayer(p.uuid)!!;check((0 until player.inventory.containerSize).any{player.inventory.getItem(it).`is`(ModItems.ORBITAL_PRECISION_STRIKE.get())});LOGGER.info("TACTICAL_CLIENT_PURCHASE_VERIFIED")}
        }
        if(ticks==240)capture("tactical-success.png")
        if(ticks==250){mc.screen?.onClose();LOGGER.info("TACTICAL_CLIENT_COMPLETED");mc.stop()}
    }
    private fun click(x:Double,y:Double){
        val mc=Minecraft.getInstance();val s=mc.screen as TacticalTerminalScreen
        val scale=(s.height*1.269/(2*.70*kotlin.math.tan(Math.toRadians(mc.options.fov().get()/2.0)))/340).coerceIn(.35,2.5)
        s.mouseClicked((s.width/scale-340)/2*scale+x*scale,(s.height/scale-156)/2*scale+y*scale,0)
    }
    private fun capture(name:String){val mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget){LOGGER.info("TACTICAL_SCREENSHOT {}",it.string)}}
}
