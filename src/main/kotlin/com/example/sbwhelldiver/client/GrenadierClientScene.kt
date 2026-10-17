package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModItems
import com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance
import net.minecraft.client.Minecraft
import net.minecraft.client.CameraType
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.sound.PlaySoundEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.math.*

/** Explicit isolated acceptance scene; inert in normal clients. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object GrenadierClientScene {
    val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.grenadierSmokeTest")
    private val out=Path.of("D:/Codex/projects/2026-09-19/new-chat/work/grenadier_battlement/integration/client_capture")
    private val origin=Vec3(1850.5,257.0,1850.5)
    @Volatile private var stage=0
    @Volatile private var age=0L
    @Volatile private var pod:GrenadierBattlementEntity?=null
    @Volatile private var identity:UUID?=null
    private val headings=floatArrayOf(37f,-120f,179f,0f)
    @Volatile private var caseIndex=0
    private val firstViews=IntArray(headings.size)
    private val returnedViews=IntArray(headings.size)
    private var captureRequest:String?=null
    private var launched=false
    private var frames=0
    private var muzzleError=0.0
    private var sightAngleError=0.0
    private var cameraPositionError=0.0
    private var thirdPersonFrames=0
    private val captures=mutableSetOf<String>()
    private val sounds=mutableMapOf<String,Int>()
    @Volatile private var clientReadyCase=-1
    private var rightHeld=false
    private var nativeZoomFrames=0
    private var returnedSightFrames=0
    fun aimOverride():Boolean?=null
    fun fireOverride():Boolean?=if(enabled&&stage==1)age>=26L else null
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOW)
    fun displacedCamera(e:net.minecraftforge.client.event.ViewportEvent.ComputeCameraAngles) {
        if(!enabled||stage!=1||!Minecraft.getInstance().options.cameraType.isFirstPerson)return
        // Simulate another camera layer attempting to move the eye away from the lens.
        val move=net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(net.minecraft.client.Camera::class.java,"m_90581_",Vec3::class.java)
        move.invoke(e.camera,e.camera.position.add(.20,.07,-.10))
        e.yaw+=15f;e.pitch+=5f
    }
    @SubscribeEvent fun sound(e:PlaySoundEvent) {
        if(!enabled)return
        val name=e.sound?.location?.path ?: return
        if(name.startsWith("sfx_grenadier"))sounds[name]=(sounds[name] ?: 0)+1
    }
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST) fun client(e:TickEvent.ClientTickEvent) {
        if(!enabled||e.phase!=TickEvent.Phase.END)return
        val mc=Minecraft.getInstance();val player=mc.player ?: return;val server=mc.singleplayerServer ?: return
        if(!launched) {launched=true;Files.createDirectories(out);server.execute {prepare(server.overworld())};return}
        if(mc.screen is net.minecraft.client.gui.screens.PauseScreen){mc.setScreen(null);mc.mouseHandler.grabMouse()}
        clientReadyCase=if(stage==1&&mc.screen==null&&player.vehicle?.uuid==identity)caseIndex else -1
        mc.options.cameraType=if(stage==1&&age in 60L..84L)CameraType.THIRD_PERSON_BACK else CameraType.FIRST_PERSON
        mc.entityRenderDispatcher.setRenderHitBoxes(stage==0&&age>=65)
        if(stage==1){
            when {
                age<25 -> {player.yRot=headings[caseIndex];player.xRot=-8f}
                age<42 -> player.turn(40.0,-10.0)
                age<60 -> player.turn(-100.0,25.0)
                age<85 -> {player.yRot=headings[caseIndex]-25f;player.xRot=-8f}
                else -> {player.yRot=headings[caseIndex];player.xRot=-8f}
            }
            // Exercise the native additive camera layer that previously displaced the optic.
            com.atsuishio.superbwarfare.event.ClientMouseHandler.freeCameraYaw=12.0
            com.atsuishio.superbwarfare.event.ClientMouseHandler.freeCameraPitch=6.0
        }
        val wantRight=stage==1&&age>=20
        if(wantRight!=rightHeld || (wantRight&&org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.window.window,org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT)!=org.lwjgl.glfw.GLFW.GLFW_PRESS)){
            org.lwjgl.glfw.GLFW.glfwFocusWindow(mc.window.window)
            val hwnd=com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer(org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(mc.window.window)))
            com.sun.jna.platform.win32.User32.INSTANCE.PostMessage(hwnd,if(wantRight)0x0204 else 0x0205,com.sun.jna.platform.win32.WinDef.WPARAM(if(wantRight)2L else 0L),com.sun.jna.platform.win32.WinDef.LPARAM(0))
            rightHeld=wantRight
        }
        val capture=when {
            stage==0&&age in 12L..16L -> "抬升"
            stage==0&&age in 37L..41L -> "展开"
            stage==0&&age in 68L..74L -> "部署完成"
            stage==1&&age in 23L..30L -> "首次上车开镜"
            stage==1&&age in 35L..39L -> "第一人称右转镜位"
            stage==1&&age in 52L..56L -> "第一人称左转镜位"
            stage==1&&age in 75L..80L -> "第三人称瞄准"
            stage==1&&age in 100L..106L -> "切回第一人称瞄准"
            stage==1&&age in 115L..120L -> "弹药耗尽仍在操作位"
            stage==2&&age in 12L..18L -> "下车后墙体保留"
            else -> null
        }
        if(capture!=null)captureRequest="${headings[caseIndex].toInt()}度_$capture"
        if(stage==2&&age>=40) {
            check(firstViews[caseIndex]>10&&returnedViews[caseIndex]>10){"Actual RMB did not exercise both first-person entries: first=${firstViews[caseIndex]} returned=${returnedViews[caseIndex]}"}
            if(caseIndex<headings.lastIndex) {
                identity=null
                stage=3
                server.execute {cleanup(server.overworld());caseIndex++;stage=0;age=0;prepare(server.overworld())}
                return
            }
            check(GrenadierOpticClient.nativeSightFrames>10){"Native weapon sight renderer was not called"}
            check(thirdPersonFrames>10&&sightAngleError<2.0&&cameraPositionError<.001){"ADS camera mode or sight lock failed: thirdPerson=$thirdPersonFrames, angle=$sightAngleError"}
            check(frames>20&&muzzleError<.025){"Grenadier muzzle alignment failed: $frames frames, $muzzleError"}
            check((sounds["sfx_grenadier_unfold"] ?: 0)==headings.size&&(sounds["sfx_grenadier_fire_shot"] ?: 0)>=3*headings.size){"Native sound cues missing or repeated: $sounds"}
            Files.writeString(out.resolve("result.json"),"""{"render_frames":$frames,"max_muzzle_error":$muzzleError,"unfold_sounds":${sounds["sfx_grenadier_unfold"]},"shot_sounds":${sounds["sfx_grenadier_fire_shot"]},"empty_wall_retained":true,"native_sight_frames":${GrenadierOpticClient.nativeSightFrames},"third_person_frames":$thirdPersonFrames,"max_sight_angle_degrees":$sightAngleError,"max_camera_position_error":$cameraPositionError,"native_zoom_frames":$nativeZoomFrames,"returned_sight_frames":$returnedSightFrames,"tested_body_headings":[${headings.joinToString()}],"first_entry_frames":[${firstViews.joinToString()}],"returned_entry_frames":[${returnedViews.joinToString()}]}""")
            server.execute {cleanup(server.overworld())}
            LOGGER.info("GRENADIER_CLIENT_PASSED frames={} muzzleError={} sounds={}",frames,muzzleError,sounds);mc.stop()
        }
    }
    @SubscribeEvent fun screenshot(e:net.minecraftforge.client.event.RenderGuiEvent.Post) {
        if(!enabled)return
        val name=captureRequest ?: return
        if(stage==1&&!HmgClient.aiming())return
        if(captures.add(name))Screenshot.takeScreenshot(Minecraft.getInstance().mainRenderTarget).use {it.writeToFile(out.resolve("$name.png"))}
        captureRequest=null
    }
    private fun observer(level:ServerLevel) {
        val p=level.server.playerList.players.first();val position=origin.add(-5.0,1.2,-7.0)
        val delta=origin.add(0.0,1.0,0.0).subtract(position.add(0.0,p.eyeHeight.toDouble(),0.0))
        p.teleportTo(level,position.x,position.y,position.z,Math.toDegrees(atan2(-delta.x,delta.z)).toFloat(),-Math.toDegrees(atan2(delta.y,delta.horizontalDistance())).toFloat())
    }
    private fun cleanup(level:ServerLevel) {
        val box=net.minecraft.world.phys.AABB(origin.subtract(20.0,10.0,20.0),origin.add(20.0,10.0,40.0))
        level.allEntities.filter {it is GrenadierGrenadeEntity}.forEach {it.discard()}
        level.getEntities(null,box).filter {it is AbstractHellpodEntity || it is GrenadierWallPanelEntity || it is GrenadierGrenadeEntity}.forEach {it.discard()}
    }
    private fun prepare(level:ServerLevel) {
        captureRequest=null
        level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.server)
        level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,level.server);level.dayTime=6000;level.setWeatherParameters(6000,0,false,false)
        for(x in -15..15)for(z in -15..35) {
            val pos=BlockPos.containing(origin.add(x.toDouble(),0.0,z.toDouble()));level.setBlock(pos.below(),Blocks.SANDSTONE.defaultBlockState(),2)
            for(y in 0..5)level.setBlock(pos.above(y),Blocks.AIR.defaultBlockState(),2)
        }
        for(x in -8..8)for(y in 0..6)level.setBlock(BlockPos.containing(origin.add(x.toDouble(),y.toDouble(),30.0)),Blocks.STONE.defaultBlockState(),2)
        cleanup(level)
        val player=level.server.playerList.players.first();player.setGameMode(GameType.CREATIVE);player.inventory.setItem(0,net.minecraft.world.item.ItemStack(ModItems.GRENADIER_BATTLEMENT.get()))
        observer(level)
        val d=ModEntities.GRENADIER_BATTLEMENT.get().create(level)!!;d.moveTo(origin.x,origin.y,origin.z,headings[caseIndex],0f)
        val t=CompoundTag();d.saveWithoutId(t);t.putInt("HellpodPhase",AbstractHellpodEntity.Phase.DEPLOYING.ordinal);t.putInt("HellpodPhaseTicks",0);t.putDouble("HellpodRestY",origin.y);t.putBoolean("HellpodCapReleased",true);t.putInt("HmgAmmo",3);d.load(t);d.isInvulnerable=true
        level.addFreshEntity(d);pod=d;identity=d.uuid;age=0
        check(level.getEntitiesOfClass(AbstractHellpodEntity::class.java,net.minecraft.world.phys.AABB(origin.subtract(10.0,5.0,10.0),origin.add(10.0,5.0,10.0))).size==1){"Acceptance scene contains duplicate turrets"}
    }
    @SubscribeEvent fun server(e:TickEvent.ServerTickEvent) {
        if(!enabled||e.phase!=TickEvent.Phase.END)return
        val d=pod ?: return
        if(stage==1&&clientReadyCase!=caseIndex)return
        if(stage==1&&age==20L&&firstViews[caseIndex]<3)return
        age++
        check(stage!=1||age<240){"Sight acceptance firing timeout: ammo=${d.ammoRemaining}, rider=${d.passengers.size}, frames=${GrenadierOpticClient.nativeSightFrames}"}
        val player=(d.level() as ServerLevel).server.playerList.players.firstOrNull() ?: return
        if(stage==0&&age>=80) {check(d.phase==AbstractHellpodEntity.Phase.ACTIVE);player.yRot=headings[caseIndex];player.xRot=-8f;d.interact(player,net.minecraft.world.InteractionHand.MAIN_HAND);stage=1;age=0}
        if(stage==1&&age>=130&&d.ammoRemaining==0) {
            check(d.ammoRemaining==0&&player.vehicle===d&&d.phase==AbstractHellpodEntity.Phase.ACTIVE){"Empty wall ejected rider or retracted"}
            player.stopRiding();observer(d.level() as ServerLevel);stage=2;age=0
        }
        if(stage==2)check(d.phase==AbstractHellpodEntity.Phase.ACTIVE&&d.ammoRemaining==0){"Empty unoccupied wall retracted"}
    }
    fun rendered(d:HmgEmplacementEntity,instance:VehicleModelInstance,partial:Float,entityYaw:Float) {
        if(!enabled||d.uuid!=identity||d.phase!=AbstractHellpodEntity.Phase.ACTIVE)return
        val wall=d as? GrenadierBattlementEntity ?: return
        frames++
        check(abs(net.minecraft.util.Mth.wrapDegrees(d.yRot-headings[caseIndex]))<.0001f){"Client lost full-precision deployment yaw: ${d.yRot} vs ${headings[caseIndex]}"}
        if(stage==1) {
            val mc=Minecraft.getInstance()
            if(age in 63L..82L) {
                check(mc.options.cameraType==CameraType.THIRD_PERSON_BACK&&!wall.throughSight()){"Third person ADS switched into the weapon sight"}
                thirdPersonFrames++
            } else if(age>=20&&wall.throughSight()) {
                val lens=org.joml.Vector3f(.005071f/16f,26.079528f/16f,-.879322f/16f)
                instance.getGlobalTransform(instance.getIndex("optic_lens")).transformPosition(lens)
                lens.rotateY((180f-entityYaw)*(Math.PI/180).toFloat())
                if(age>=87){returnedSightFrames++;returnedViews[caseIndex]++} else if(age in 20L..58L)firstViews[caseIndex]++
                if(com.atsuishio.superbwarfare.event.ClientEventHandler.zoomVehicle)nativeZoomFrames++
                val camera=mc.gameRenderer.mainCamera
                cameraPositionError=max(cameraPositionError,camera.position.distanceTo(wall.opticalCameraPosition(partial)))
                val direction=d.getPosition(partial).add(lens.x.toDouble(),lens.y.toDouble(),lens.z.toDouble()).subtract(camera.position).normalize()
                val look=camera.lookVector
                val dot=direction.dot(Vec3(look.x.toDouble(),look.y.toDouble(),look.z.toDouble())).coerceIn(-1.0,1.0)
                val error=Math.toDegrees(acos(dot));sightAngleError=max(sightAngleError,error)
                if(error>2)LOGGER.info("OPTIC_ROOT_DIAG age={} nativeZoom={} bodyYaw={} oldBodyYaw={} pitch={} roll={} fakePitch={} fakeRoll={} visualYaw={} camera={} error={}",age,com.atsuishio.superbwarfare.event.ClientEventHandler.zoomVehicle,d.yRot,d.yRotO,d.xRot,d.roll,d.fakePitch,d.fakeRoll,d.interpolatedYaw(partial),camera.position,error)
            }
        }
        val local=d.profile.muzzleOffsets.first().add(d.weaponPivot)
        val p=org.joml.Vector3f(-local.x.toFloat(),local.y.toFloat(),-local.z.toFloat())
        instance.getGlobalTransform(instance.getIndex("barrel")).transformPosition(p)
        p.rotateY((180f-entityYaw)*(Math.PI/180).toFloat())
        val actual=d.getPosition(partial).add(p.x.toDouble(),p.y.toDouble(),p.z.toDouble())
        muzzleError=max(muzzleError,actual.distanceTo(d.muzzlePosition(0,partial)))
        if(actual.distanceTo(d.muzzlePosition(0,partial))>.025) LOGGER.info("GRENADIER_MUZZLE_DIAG actual={} expected={} yaw={} pitch={} entityYaw={} partial={} phaseAge={}",actual,d.muzzlePosition(0,partial),d.interpolatedYaw(partial),d.interpolatedPitch(partial),entityYaw,partial,d.phaseAge)
    }
}

