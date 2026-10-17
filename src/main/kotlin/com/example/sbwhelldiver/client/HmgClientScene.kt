package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
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
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.client.event.RenderGuiEvent
import net.minecraftforge.client.event.sound.PlaySoundEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import kotlin.math.*
/** Explicitly enabled isolated acceptance scene. Never runs in ordinary games. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object HmgClientScene {
    val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.hmgSmokeTest")
    private val out by lazy {Path.of("D:/Codex/projects/2026-09-19/new-chat/work/hmg_emplacement/integration/client_capture")}
    private val origin=Vec3(1730.5,257.0,1730.5)
    @Volatile private var stage=-1
    @Volatile private var started=0L
    @Volatile private var age=0L
    @Volatile private var id:UUID?=null
    @Volatile private var spawned:HmgEmplacementEntity?=null
    @Volatile private var complete=false
    var frames=0;private set
    var muzzleError=0.0;private set
    private val recoilPeaks=DoubleArray(2)
    private var rearCameraFrames=0
    private var projectedFrames=0
    @Volatile private var nearPlaced=false
    @Volatile private var nearAt=0L
    private var thirdFarFrames=0
    private var thirdNearFrames=0
    private var lagFrames=0
    private var steadyFrames=0
    private var projectionError=0.0
    private var launched=false
    private var clientAge=0
    private val shots=mutableSetOf<Int>()
    private val captures=mutableSetOf<String>()
    private val sounds=mutableMapOf<String,Int>()
    fun aimOverride():Boolean?=if(enabled&&stage in 2..3) true else if(enabled&&stage==1)true else null
    fun fireOverride():Boolean?=if(enabled&&stage in 2..3)true else if(enabled&&stage==1)false else null
    @SubscribeEvent fun client(event:TickEvent.ClientTickEvent) {
        if(!enabled||event.phase!=TickEvent.Phase.END)return
        val mc=Minecraft.getInstance();if(mc.level==null||mc.player==null||mc.singleplayerServer==null)return
        if(!launched){launched=true;Files.createDirectories(out.resolve("frames"));mc.singleplayerServer!!.execute {prepare(mc.singleplayerServer!!.overworld())}}
        mc.options.cameraType=if(stage<=0||stage>=4)CameraType.FIRST_PERSON else if(stage==1)CameraType.THIRD_PERSON_BACK else CameraType.FIRST_PERSON
        if(stage==0 || stage==4) {
            val delta=origin.add(0.0,1.65,0.0).subtract(mc.player!!.eyePosition)
            mc.player!!.yRot=Math.toDegrees(atan2(-delta.x,delta.z)).toFloat()
            mc.player!!.xRot=-Math.toDegrees(atan2(delta.y,delta.horizontalDistance())).toFloat()
        }
        if(stage in 1..3) {
            if(age%20L==0L)LOGGER.info("HMG_SCENE_INPUT vehicle={} screen={} ads={}",mc.player!!.vehicle?.javaClass?.simpleName,mc.screen?.javaClass?.simpleName,HmgClient.aiming())
            mc.player!!.yRot=if(stage==1 || (stage==2 && age>70))0f else (sin(age/14.0)*22).toFloat()
            mc.player!!.xRot=if(stage==1 || (stage==2 && age>70))0f else (sin(age/20.0)*13).toFloat()
        }
        if(complete && ++clientAge>=25) {mc.stop()}
        if(++clientAge>2400&&!complete)error("HMG client scene timed out: stage=$stage")
    }
    private fun camera(level:ServerLevel,p:Vec3,target:Vec3) {
        val player=level.server.playerList.players.first();val delta=target.subtract(p.add(0.0,player.eyeHeight.toDouble(),0.0))
        player.teleportTo(level,p.x,p.y,p.z,Math.toDegrees(atan2(-delta.x,delta.z)).toFloat(),-Math.toDegrees(atan2(delta.y,delta.horizontalDistance())).toFloat())
    }
    private fun wall(level:ServerLevel,z:Int,present:Boolean) {
        for(x in -3..3)for(y in 1..5)level.setBlock(BlockPos.containing(origin.add(x.toDouble(),y.toDouble(),z.toDouble())),
            (if(present)Blocks.SMOOTH_STONE else Blocks.AIR).defaultBlockState(),2)
    }
    private fun prepare(level:ServerLevel) {
        level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,level.server)
        level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,level.server);level.dayTime=6000;level.setWeatherParameters(6000,0,false,false)
        level.getEntities(null,AABB(origin,origin).inflate(60.0)).filter{it !is net.minecraft.world.entity.player.Player}.forEach{it.discard()}
        for(x in -15..15)for(z in -15..15){val p=BlockPos.containing(origin.add(x.toDouble(),0.0,z.toDouble()));level.setBlock(p.below(),Blocks.SMOOTH_STONE.defaultBlockState(),2);for(y in 0..5)level.setBlock(p.above(y),Blocks.AIR.defaultBlockState(),2)}
        wall(level,30,true)
        val player=level.server.playerList.players.first();player.setGameMode(GameType.CREATIVE)
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY)
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,net.minecraft.world.item.ItemStack.EMPTY)
        camera(level,origin.add(3.3,.2,-3.6),origin.add(0.0,1.65,0.0))
        val pod=ModEntities.HMG_EMPLACEMENT.get().create(level)!!;pod.setPos(origin)
        val tag=CompoundTag();pod.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.DEPLOYING.ordinal);tag.putInt("HellpodPhaseTicks",0);tag.putDouble("HellpodRestY",origin.y);tag.putBoolean("HellpodCapReleased",true);pod.load(tag)
        level.setChunkForced(net.minecraft.core.SectionPos.blockToSectionCoord(origin.x.toInt()),net.minecraft.core.SectionPos.blockToSectionCoord(origin.z.toInt()),true)
        pod.isInvulnerable=true
        check(level.addFreshEntity(pod)){"Could not spawn HMG acceptance fixture"};spawned=pod;id=pod.uuid;stage=0;started=level.gameTime
        LOGGER.info("HMG_SCENE_PREPARE phase={} ammo={} health={} uuid={}",pod.phase,pod.ammoRemaining,pod.health,pod.uuid)
    }
    @SubscribeEvent fun server(event:TickEvent.ServerTickEvent) {
        if(!enabled||stage<0||complete||event.phase!=TickEvent.Phase.END)return
        val level=event.server.overworld();val pod=(id?.let{level.getEntity(it)} as? HmgEmplacementEntity) ?: spawned?.takeIf{!it.isRemoved} ?: error("HMG scene lost turret: removed=${spawned?.isRemoved}, reason=${spawned?.removalReason}, phase=${spawned?.phase}, health=${spawned?.health}")
        if(stage==0)level.getEntities(null,AABB(origin,origin).inflate(60.0)).filter{it !== pod && it !is net.minecraft.world.entity.player.Player}.forEach{it.discard()}
        age=level.gameTime-started
        fun next(s:Int){stage=s;started=level.gameTime;age=0;LOGGER.info("HMG_SCENE_STAGE {} ammo={} serial={}",s,pod.ammoRemaining,pod.shotSerial)}
        if(stage==0&&pod.phase==AbstractHellpodEntity.Phase.ACTIVE&&age>75) {
            val p=level.server.playerList.players.first();p.yRot=0f;p.xRot=0f;check(p.startRiding(pod,true));next(1)
        } else if(stage==1&&age>=60&&!nearPlaced&&thirdFarFrames>10){wall(level,8,true);nearPlaced=true;nearAt=age}
        else if(stage==1&&nearPlaced&&age>=nearAt+60&&thirdNearFrames>10){check(pod.shotSerial==0);wall(level,8,false);next(2)}
        else if(stage==1&&age>600)error("Third-person convergence timed out: far=$thirdFarFrames near=$thirdNearFrames")
        else if(stage==2&&age>=100) {
            check(pod.shotSerial in 45..50){"Wrong real-network shooting rate: ${pod.shotSerial}; operator=${pod.operator?.name}; playerVehicle=${level.server.playerList.players.first().vehicle?.javaClass?.simpleName}"}
            val t=CompoundTag();pod.saveWithoutId(t);t.putInt("HmgAmmo",2);pod.load(t);next(3)
        } else if(stage==3&&pod.phase==AbstractHellpodEntity.Phase.RETRACTING) {
            check(pod.ammoRemaining==0&&pod.operator==null);camera(level,origin.add(3.3,.2,-3.6),origin.add(0.0,1.65,0.0));next(4)
        } else if(stage==4&&age>=80) {
            check(pod.phase==AbstractHellpodEntity.Phase.DONE||pod.isRemoved)
            check(thirdFarFrames>0&&thirdNearFrames>0&&lagFrames>0){"Camera-to-bore convergence missing: far=$thirdFarFrames near=$thirdNearFrames lag=$lagFrames"}
            check(rearCameraFrames>10){"Rear camera was not validated"}
            check(projectedFrames>10&&steadyFrames>0&&projectionError<.002){"Reticle projection failed: frames=$projectedFrames steady=$steadyFrames error=$projectionError"}
            check(recoilPeaks.all{it>.08&&it<.139}){"Wrong real barrel recoil: ${recoilPeaks.toList()}"}
            check(frames>10&&muzzleError<.015){"Muzzle mismatch: $muzzleError, frames=$frames"}
            Files.writeString(out.resolve("result.txt"),"PASS: actual client/server packets, Ergo follow, alternating 600 RPM, ammo ejection/retraction; frames=$frames; maxMuzzleError=$muzzleError; observedShots=${shots.size}; recoilPeaks=${recoilPeaks.toList()}; rearCameraFrames=$rearCameraFrames; thirdFarFrames=$thirdFarFrames; thirdNearFrames=$thirdNearFrames; lagFrames=$lagFrames; projectionFrames=$projectedFrames; steadyFrames=$steadyFrames; projectionError=$projectionError; sounds=$sounds")
            LOGGER.info("HMG_CLIENT_PASSED frames={} muzzle={} sounds={}",frames,muzzleError,sounds);complete=true;clientAge=0
        }
    }
    fun rendered(pod:HmgEmplacementEntity,instance:VehicleModelInstance,partial:Float,entityYaw:Float) {
        if(!enabled||pod.uuid!=id)return
        frames++
        if(pod.phase!=AbstractHellpodEntity.Phase.ACTIVE)return
        for(i in 0..1) {
            val local=pod.profile.muzzleOffsets[i]
            val p=org.joml.Vector3f(-local.x.toFloat(),(local.y+28.10731/16).toFloat(),-(local.z+.06009/16).toFloat())
            val recoilTip=org.joml.Vector3f(p)
            instance.getGlobalTransform(instance.getIndex(if(i==0)"l_barrel_01" else "r_barrel_01")).transformPosition(recoilTip)
            instance.getGlobalTransform(instance.getIndex("barrel")).transformPosition(p)
            recoilPeaks[i]=max(recoilPeaks[i],recoilTip.distance(p).toDouble())
            p.rotateY((180f-entityYaw)*(Math.PI/180).toFloat())
            val actual=pod.getPosition(partial).add(p.x.toDouble(),p.y.toDouble(),p.z.toDouble())
            val error=actual.distanceTo(pod.muzzlePosition(i,partial));muzzleError=max(muzzleError,error)
            check(error<.015){"Rendered HMG muzzle misses actual projectile origin by $error; actual=$actual expected=${pod.muzzlePosition(i,partial)} yaw=${pod.interpolatedYaw(partial)} pitch=${pod.interpolatedPitch(partial)}"}
        }
        shots.add(pod.shotSerial)
    }
    fun projected(event:net.minecraftforge.client.event.RenderLevelStageEvent,delta:Vec3,clip:org.joml.Vector4f,dx:Double,dy:Double) {
        if(!enabled || stage !in 1..2)return
        val camera=event.camera
        val depth=delta.dot(Vec3(camera.lookVector))
        if(depth<=.001)return
        val expectedX=-delta.dot(Vec3(camera.leftVector))/depth*event.projectionMatrix.m00()
        val expectedY=delta.dot(Vec3(camera.upVector))/depth*event.projectionMatrix.m11()
        val error=max(abs(clip.x/clip.w-expectedX),abs(clip.y/clip.w-expectedY))
        projectionError=max(projectionError,error);projectedFrames++
        check(error<.002){"Reticle disagrees with camera basis: $error"}
        val far=stage==1&&!nearPlaced&&age>=40
        val near=stage==1&&nearPlaced&&age>=nearAt+40
        if(((far||near)&&hypot(dx,dy)<2)||(stage==2&&age>95)) {
            check(hypot(dx,dy)<2){"Bore failed to converge on screen centre: stage=$stage age=$age error=$dx,$dy"}
            if(far)thirdFarFrames++ else if(near)thirdNearFrames++ else steadyFrames++
        }
        if(stage==2&&age in 15..60&&hypot(dx,dy)>2)lagFrames++
    }
    @SubscribeEvent fun cameraCheck(event:net.minecraftforge.client.event.RenderLevelStageEvent) {
        if(!enabled || stage!=1 || age<10 || event.stage!=net.minecraftforge.client.event.RenderLevelStageEvent.Stage.AFTER_LEVEL)return
        val mc=Minecraft.getInstance();val pod=mc.player?.vehicle as? HmgEmplacementEntity ?: return
        val partial=event.partialTick
        val eye=pod.operatorPosition(partial).add(0.0,mc.player!!.eyeHeight.toDouble(),0.0)
        val offset=event.camera.position.subtract(eye)
        val yaw=Math.toRadians(mc.player!!.getViewYRot(partial).toDouble())
        val sideways=offset.x*cos(yaw)+offset.z*sin(yaw)
        val behind=offset.x*sin(yaw)-offset.z*cos(yaw)
        check(abs(sideways)<.01 && abs(offset.y-1.10)<.01 && abs(behind-1.75)<.01){"Wrong centred rear upper camera: $offset sideways=$sideways behind=$behind"}
        rearCameraFrames++
    }
    @SubscribeEvent fun sound(event:PlaySoundEvent){if(!enabled)return;event.sound?.location?.path?.takeIf{it.startsWith("sfx_hmg_")}?.let{sounds.merge(it,1,Int::plus)}}
    @SubscribeEvent fun capture(event:RenderGuiEvent.Post) {
        if(!enabled||stage<0)return
        val label=when {
            stage==0&&age in 23..27 -> "deploy_belt"
            stage==1&&nearPlaced&&age>nearAt+40 -> "third_near_aligned"
            stage==1&&!nearPlaced&&age>40 -> "third_far_aligned"
            stage==1&&age>20 -> "standing_operator"
            stage==2&&age>85 -> "aim_aligned"
            stage==2&&age>40 -> "aim_and_fire"
            stage==4&&age in 5..10 -> "retract_belt"
            else -> return
        }
        if(captures.add(label))Screenshot.grab(out.toFile(),"../frames/$label.png",Minecraft.getInstance().mainRenderTarget){}
    }
}
