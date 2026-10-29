package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.*
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.stratagem.*
import com.example.sbwhelldiver.config.ClientModelConfig
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.Difficulty
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent

/** Isolated client fixture. Only compiled into explicitly requested development runs. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object PortClientTests {
    private var started=false
    private var ticks=0
    private var launchMillis=System.currentTimeMillis()
    @SubscribeEvent fun tick(event:ClientTickEvent.Post) {
        if(!java.lang.Boolean.getBoolean("sbw_helldiver.portClientTests") || java.lang.Boolean.getBoolean("sbw_helldiver.hudRegression") || java.lang.Boolean.getBoolean("sbw_helldiver.mineRegression") || java.lang.Boolean.getBoolean("sbw_helldiver.destroyerRegression") || java.lang.Boolean.getBoolean("sbw_helldiver.explosionRegression")) return
        val mc=Minecraft.getInstance()
        if(System.currentTimeMillis()-launchMillis>300000) { LOGGER.error("PORT_CLIENT_TIMEOUT");mc.stop();return }
        if(!started&&mc.screen is TitleScreen&&mc.overlay==null) {
            started=true
            mc.options.pauseOnLostFocus=false
            ClientModelConfig.lowQualityModels.set(false)
            mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("NeoForge port verification",GameType.CREATIVE,false,Difficulty.NORMAL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(1L,false,false),{ access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions() },mc.screen!!)
            return
        }
        val player=mc.player?:return
        val server=mc.singleplayerServer?:return
        ticks++
        if(ticks in 230..510) {
            player.setPos(4.0,82.0,11.0)
            player.yRot=180f;player.yRotO=180f;player.xRot=8f;player.xRotO=8f
        }
        if(ticks==1) server.execute {
            val level=server.overworld()
            level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server)
            level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server)
            level.dayTime=6000
            for(x in -26..26)for(z in -26..26)for(y in 77..79)level.setBlock(BlockPos(x,y,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
            level.setDefaultSpawnPos(BlockPos(0,80,18),180f)
            val p=server.playerList.getPlayer(player.uuid)!!;p.setGameMode(GameType.CREATIVE);p.teleportTo(level,0.5,84.0,19.5,180f,18f)
            val beacon=ItemStack(ModItems.BEACON.get());BeaconData.setItems(beacon,listOf(ModItems.MACHINE_GUN_SENTRY,ModItems.ORBITAL_LASER,ModItems.EAGLE_AIRSTRIKE,ModItems.GAS_MINEFIELD).map{ItemStack(it.get())});BeaconData.ensureIdentity(beacon);p.inventory.setItem(0,beacon)
            val types=listOf(ModEntities.MACHINE_GUN_SENTRY,ModEntities.GATLING_SENTRY,ModEntities.LASER_SENTRY,ModEntities.FLAME_SENTRY,ModEntities.SHIELD_GENERATOR,ModEntities.TESLA_TOWER,ModEntities.ANTI_PERSONNEL_MINEFIELD,ModEntities.GAS_MORTAR_SENTRY)
            for((i,type)in types.withIndex()) {val pod=type.get().create(level)!!;pod.setPos(-12.0+i%4*8,140.0,2.0-i/4*12);pod.scheduleArrival(80.0,level.gameTime+80+i*5);level.addFreshEntity(pod)}
            MortarFieldEntity.create(level,net.minecraft.world.phys.Vec3(-12.0,80.0,-15.0),MortarFieldEntity.Kind.GAS)
            MortarFieldEntity.create(level,net.minecraft.world.phys.Vec3(12.0,80.0,-15.0),MortarFieldEntity.Kind.EMS)
            LOGGER.info("PORT_CLIENT_FIXTURE_READY")
        }
        if(ticks==210) server.execute {
            val level=server.overworld()
            for(x in listOf(4.0,12.0)) {
                val target=net.minecraft.world.entity.EntityType.RAVAGER.create(level)!!
                target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)!!.baseValue=50000.0
                target.health=50000f;target.isNoAi=true;target.setPos(x,80.0,-3.0);level.addFreshEntity(target)
            }
        }
        if(ticks==280) {
            LOGGER.info("PORT_SHADER_ACTIVE={}",com.example.sbwhelldiver.client.ShaderPackCompat.active())
            capture(if(com.example.sbwhelldiver.client.ShaderPackCompat.active())"port-shader-high.png" else "port-vanilla-high.png")
        }
        if(ticks==300) { ClientModelConfig.lowQualityModels.set(true);mc.reloadResourcePacks().thenRun{LOGGER.info("PORT_CLIENT_LOW_MODELS_RELOADED")} }
        if(ticks==500) capture(if(com.example.sbwhelldiver.client.ShaderPackCompat.active())"port-shader-low.png" else "port-vanilla-low.png")
        if(ticks in 502..509) {
            player.setPos(8.0,82.0,6.0)
            player.yRot=135f;player.yRotO=135f;player.xRot=8f;player.xRotO=8f
        }
        if(ticks==508) capture("port-laser-side.png")
        if(ticks==510) server.execute {
            val level=server.overworld()
            for(x in 2..6) for(z in 0..4) for(y in 73..79) level.setBlock(BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3)
        }
        if(ticks in 512..550) {
            player.setPos(4.0,81.0,7.0)
            player.yRot=180f;player.yRotO=180f;player.xRot=58f;player.xRotO=58f
        }
        if(ticks==545) capture("port-dug-hull.png")
        if(ticks==555) mc.setScreen(com.example.sbwhelldiver.client.HelldiversConfigScreen(TitleScreen()))
        if(ticks==560) capture("port-config.png")
        if(ticks==600) { LOGGER.info("PORT_CLIENT_COMPLETED");mc.stop() }
    }
    private fun capture(name:String) {
        val mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget) { message -> LOGGER.info("PORT_SCREENSHOT {}: {}",name,message.string) }
    }
}
