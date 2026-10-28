package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.*
import com.example.sbwhelldiver.client.*
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.marking.TargetMarks
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.Difficulty
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.ModContainer
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.*
import net.neoforged.neoforge.client.gui.ModListScreen
import org.lwjgl.glfw.GLFW
import java.util.UUID
import kotlin.math.*

/** Exercise actual screen filtering, synchronized HUD marks and operated turret aiming. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object PortHudTests {
    private val launchMillis=System.currentTimeMillis()
    private val queries=listOf("SBW","Helldivers","绝地潜兵","sbw_helldiver","Minecraft","no-such-mod-987","地狱潜兵", "")
    private var menuTicks=0
    private var searchIndex=-1
    private var worldStarted=false
    private var ticks=0
    private var hmgId=-1
    private var cannonId=-1
    private var deploySoundSeen=false
    private var markedFrames=0
    private var beaconFrames=0
    private var aimFrames=0
    private val passedViews=mutableSetOf<Int>()
    private var mouseProcess:Process?=null
    private var released=false
    private fun active()=java.lang.Boolean.getBoolean("sbw_helldiver.hudRegression")
    private fun field(screen:ModListScreen,name:String)=ModListScreen::class.java.getDeclaredField(name).apply{isAccessible=true}.get(screen)
    private fun requireThat(ok:Boolean,message:String) { check(ok){"HUD_REGRESSION_FAILED: $message"} }

    @SubscribeEvent fun tick(event:ClientTickEvent.Post) {
        if(!active())return
        val mc=Minecraft.getInstance()
        if(System.currentTimeMillis()-launchMillis>300000) { releaseMouse();LOGGER.error("HUD_REGRESSION_TIMEOUT");mc.stop();return }
        if(!worldStarted) {
            if(mc.overlay!=null)return
            if(mc.screen is TitleScreen) { mc.setScreen(ModListScreen(mc.screen!!));return }
            val screen=mc.screen as? ModListScreen?:return
            menuTicks++
            if(menuTicks%4!=0)return
            if(searchIndex>=0) {
                val ids=(field(screen,"mods") as List<*>).map{(it as ModContainer).modInfo.modId}
                val q=queries[searchIndex]
                requireThat(when(q){"Minecraft"->"minecraft" in ids;"no-such-mod-987"->ids.isEmpty();else->MOD_ID in ids},"Search '$q': $ids")
                LOGGER.info("HUD_SEARCH_PASS query={} results={}",q,ids)
            }
            searchIndex++
            if(searchIndex<queries.size) {(field(screen,"search") as EditBox).value=queries[searchIndex];return}
            worldStarted=true
            mc.options.pauseOnLostFocus=false;mc.options.hideGui=false;mc.options.bobView().set(false)
            mc.options.renderDistance().set(8);mc.options.simulationDistance().set(6)
            mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("HUD regression",GameType.CREATIVE,false,Difficulty.NORMAL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(2L,false,false),{ access->access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()},screen)
            return
        }
        if(mc.overlay!=null)return
        val player=mc.player?:return
        val server=mc.singleplayerServer?:return
        ticks++
        if(ticks==1)server.execute {
            val level=server.overworld()
            level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server)
            level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.dayTime=6000
            for(x in -22..22)for(z in -22..22)for(y in 77..79)level.setBlock(BlockPos(x,y,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
            level.setDefaultSpawnPos(BlockPos(0,80,0),0f)
            val p=server.playerList.getPlayer(player.uuid)!!;p.setGameMode(GameType.CREATIVE);p.teleportTo(level,0.0,82.0,0.0,0f,0f)
            p.abilities.flying=true;p.onUpdateAbilities()
            for((i,at)in listOf(Vec3(0.0,80.0,12.0),Vec3(-12.0,80.0,0.0),Vec3(0.0,80.0,-12.0),Vec3(12.0,80.0,0.0)).withIndex()) {
                val marker=ModEntities.STRATAGEM_MARKER.get().create(level)!!;marker.setPos(at)
                marker.configure(ResourceLocation.fromNamespaceAndPath(MOD_ID,"machine_gun_sentry"),1200,p.uuid,groundY=80.0)
                level.addFreshEntity(marker)
                TargetMarks.put(level,UUID(0L,i+1L),null,at.add(0.0,2.0,0.0))
            }
            val hmg=ModEntities.HMG_EMPLACEMENT.get().create(level)!!;hmg.setPos(0.0,140.0,-6.0);hmg.scheduleArrival(80.0,level.gameTime+65);level.addFreshEntity(hmg);hmgId=hmg.id
            LOGGER.info("HUD_FIXTURE_READY")
        }
        if(ticks==2 && System.getenv("SBW_AUTOCANNON_ONLY")=="1")ticks=219
        if(ticks in 20..147) {
            val view=((ticks-20)/32).coerceIn(0,3)
            player.setPos(0.0,82.0,0.0);player.abilities.flying=true
            player.yRot=view*90f;player.yRotO=player.yRot;player.xRot=4f;player.xRotO=4f
            if((ticks-20)%32==28)capture("hud-direction-$view.png")
            if((ticks-20)%32==30)requireThat(view in passedViews,"No projected mark / beacon at cardinal view $view")
        }
        if(ticks==150)server.execute {
            val p=server.playerList.getPlayer(player.uuid)!!
            val pod=server.overworld().getEntity(hmgId) as HmgEmplacementEntity
            requireThat(pod.phase==AbstractHellpodEntity.Phase.ACTIVE,"Emplacement not deployed")
            p.yRot=0f;p.xRot=0f;p.startRiding(pod,true)
        }
        if(ticks==165) {
            GLFW.glfwFocusWindow(mc.window.window);mc.mouseHandler.grabMouse()
            val handle=org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(mc.window.window)
            val script="""Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public class HudTestMouse { [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h,uint m,IntPtr w,IntPtr l); }'; [HudTestMouse]::PostMessage([IntPtr]$handle,516,[IntPtr]2,[IntPtr]::Zero); Start-Sleep -Milliseconds 4000; [HudTestMouse]::PostMessage([IntPtr]$handle,517,[IntPtr]::Zero,[IntPtr]::Zero)"""
            mouseProcess=ProcessBuilder("powershell", "-NoProfile", "-WindowStyle", "Hidden", "-EncodedCommand",java.util.Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))).redirectErrorStream(true).start()
        }
        if(ticks==210)capture("hud-operated-reticle.png")
        if(ticks==220) {
            releaseMouse()
            if(System.getenv("SBW_AUTOCANNON_ONLY")!="1") {
            requireThat(markedFrames>0&&beaconFrames>0&&passedViews.size==4,"HUD labels were not visible in every direction")
            requireThat(aimFrames>0,"Operated turret reticle was never validated")
            LOGGER.info("HUD_REGRESSION_PASS views={} markFrames={} beaconFrames={} aimFrames={} shaders={}",passedViews,markedFrames,beaconFrames,aimFrames,ShaderPackCompat.active())
            }
            server.execute {
                val level=server.overworld()
                level.allEntities.filter{it !is net.minecraft.world.entity.player.Player}.toList().forEach{it.discard()}
                val p=server.playerList.getPlayer(player.uuid)!!;p.stopRiding();p.teleportTo(level,2.8,80.5,2.8,135f,3.6f);p.abilities.flying=true;p.onUpdateAbilities()
                val cannon=ModEntities.AUTOCANNON_SENTRY.get().create(level)!!;cannon.setPos(0.0,140.0,0.0);cannon.scheduleArrival(80.0,level.gameTime+20);level.addFreshEntity(cannon);cannonId=cannon.id
            }
        }
        if(ticks in 240..700) {
            player.setPos(2.8,80.5,2.8);player.yRot=135f;player.yRotO=135f;player.xRot=3.6f;player.xRotO=3.6f;player.abilities.flying=true
            val cannon=mc.level?.getEntity(cannonId) as? SentryHellpodEntity
            if(cannon!=null && cannon.phase==AbstractHellpodEntity.Phase.DEPLOYING && cannon.phaseAge in 17..20 && "sfx_native_autocannon_sentry_deploy_633699812_0" in SoundTimeline.activeEffects(cannon)) {
                if(!deploySoundSeen)LOGGER.info("AUTOCANNON_DEPLOY_SOUND_PASS phaseAge={}",cannon.phaseAge)
                deploySoundSeen=true
            }
        }
        if(ticks==305)capture("autocannon-high.png")
        if(ticks==320) {com.example.sbwhelldiver.config.ClientModelConfig.lowQualityModels.set(true);mc.reloadResourcePacks().thenRun{LOGGER.info("AUTOCANNON_LOW_RELOADED")} }
        if(ticks==410)capture("autocannon-low.png")
        if(ticks==420)server.execute {
            val level=server.overworld();val target=net.minecraft.world.entity.EntityType.RAVAGER.create(level)!!
            target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)!!.baseValue=50000.0;target.health=50000f;target.isNoAi=true;target.setNoGravity(true);target.setPos(6.0,80.0,14.0);level.addFreshEntity(target)
        }
        if(ticks==675)capture("autocannon-aim-fire.png")
        if(ticks==700) {
            val cannon=mc.level?.getEntity(cannonId) as? SentryHellpodEntity
            requireThat(deploySoundSeen,"Autocannon deployment audio did not begin with the unfold")
            requireThat(cannon!=null && cannon.shotSerial>0,"Autocannon did not traverse and fire")
            LOGGER.info("AUTOCANNON_CLIENT_PASS shotSerial={} shaders={}",cannon?.shotSerial,ShaderPackCompat.active());mc.stop()
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) fun hud(event:RenderGuiEvent.Post) {
        if(!active())return
        val mc=Minecraft.getInstance();if(mc.screen!=null||mc.level==null)return
        if(ticks in 20..147) {
            val view=((ticks-20)/32).coerceIn(0,3)
            val labels=TargetMarkClient.labels
            val beacon=BeaconCallHud.label
            if(labels.isNotEmpty())markedFrames++
            if(beacon!=null)beaconFrames++
            if(labels.isNotEmpty()&&beacon!=null)passedViews.add(view)
        }
        if(ticks in 195..215 && HmgClient.aiming()) {
            val type=HmgClient::class.java
            fun value(name:String)=type.getDeclaredField(name).apply{isAccessible=true}.get(null)
            if(value("projectedId")!=hmgId)return
            val dx=value("projectedX") as Double;val dy=value("projectedY") as Double
            requireThat(dx.isFinite()&&dy.isFinite()&&hypot(dx,dy)<12.0,"Reticle away from settled forward bore: $dx,$dy")
            aimFrames++
            if(aimFrames==1)LOGGER.info("HUD_RETICLE_PASS dx={} dy={} size={}x{}",dx,dy,mc.window.guiScaledWidth,mc.window.guiScaledHeight)
        }
    }
    private fun releaseMouse(){if(!released){mouseProcess?.waitFor(5,java.util.concurrent.TimeUnit.SECONDS);LOGGER.info("HUD_INPUT {}",mouseProcess?.inputStream?.bufferedReader()?.readText());released=true}}
    private fun capture(name:String){val mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget){LOGGER.info("HUD_SCREENSHOT {}: {}",name,it.string)}}
}
