package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.Difficulty
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent

@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object AttachedLightClientTest {
    private val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.attachedLightTests")
    private var start=0L
    private var prepared=false
    @Volatile private var ready=false
    @Volatile private var failure:Throwable?=null
    @SubscribeEvent fun tick(e:ClientTickEvent.Post) {
        if(!enabled)return
        val mc=Minecraft.getInstance()
        try {
            failure?.let{throw it}
            if(start==0L&&mc.screen is TitleScreen&&mc.overlay==null){
                start=System.currentTimeMillis();mc.options.pauseOnLostFocus=false
                mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Attached light render verification",GameType.CREATIVE,false,Difficulty.NORMAL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(21L,false,false),{it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()},mc.screen!!)
            }
            check(start==0L||System.currentTimeMillis()-start<150000){"Light scene timed out"}
            val player=mc.player?:return;val server=mc.singleplayerServer?:return
            if(!prepared){prepared=true;server.execute {try {
                val world=server.overworld()
                world.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server)
                world.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);world.dayTime=6000
                for(x in -20..20)for(z in -20..50)world.setBlock(BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
                world.setDefaultSpawnPos(BlockPos(0,80,-4),0f)
                server.playerList.getPlayer(player.uuid)!!.setGameMode(GameType.CREATIVE);ready=true
            }catch(t:Throwable){failure=t}}}
            if(ready&&AttachedLightClientCheck.tick(mc))mc.stop()
        }catch(t:Throwable){LOGGER.error("ATTACHED_LIGHT_FAILED",t);mc.stop()}
    }
}
