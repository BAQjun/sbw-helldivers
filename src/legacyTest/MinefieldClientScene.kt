package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.projectile.Arrow
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderGuiEvent
import net.minecraftforge.client.event.sound.PlaySoundEvent
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import kotlin.math.*

/** Opt-in real integrated-world test, dormant during ordinary gameplay. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object MinefieldClientScene {
    private val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.minefieldSmokeTest")
    private val out by lazy { Path.of(requireNotNull(System.getProperty("sbw_helldiver.minefieldCapture"))) }
    private val normal=Vec3(1420.5,257.0,1420.5)
    private val dense=Vec3(1490.5,257.0,1420.5)
    private val box=AABB(1380.0,250.0,1380.0,1520.0,320.0,1460.0)
    private var launched=false
    @Volatile private var prepared=false
    @Volatile private var stage=0
    @Volatile private var stageStart=0L
    @Volatile private var podId: UUID?=null
    @Volatile private var focus=normal.add(0.0,1.0,0.0)
    @Volatile private var result=""
    @Volatile private var complete=false
    private var positions=emptyList<Vec3>()
    private var field=emptyList<AntiPersonnelMineEntity>()
    private var selected=emptyList<Int>()
    private var case=0
    private val results=mutableListOf<Int>()
    private var denseField=emptyList<AntiPersonnelMineEntity>()
    private var interruptedAt=-1L
    private var interruptedCount=0
    private var frames=0
    private var lastFrame=0L
    private var ticks=0
    private val soundInstances=mutableListOf<net.minecraft.client.resources.sounds.SoundInstance>()
    private val played=mutableSetOf<String>()
    private val channels=mutableSetOf<String>()
    private val soundChecks=setOf("sfx_ap_mine_land_1","sfx_ap_mine_explosion_1","sfx_ap_mine_hellpod_impact")
    private var distantExplosions=0
    private var closeExplosions=0
    private var closeMine:AntiPersonnelMineEntity?=null
    private val volumes=mutableMapOf<String,MutableSet<Float>>()
    private val captures=mutableSetOf<String>()
    private val overviewRendered=mutableMapOf<Int,MutableSet<UUID>>()
    private data class Assembly(val panel: Int,val bottom: org.joml.Vector3f,val top: org.joml.Vector3f,val shaftOrigin: org.joml.Vector3f,val length: Float)
    private val assemblies=listOf(
        Assembly(1,org.joml.Vector3f(0.2206036728f,0.8451335911f,-0.3820965067f),org.joml.Vector3f(0.2385131642f,1.2852190841f,-0.4131166451f),org.joml.Vector3f(0.2341663759f,1.1784065323f,-0.4055877894f),0.1071657569f),
        Assembly(2,org.joml.Vector3f(0.4412071779f,0.8451336656f,0.0000001024f),org.joml.Vector3f(0.4770261236f,1.2852192331f,0.0000001420f),org.joml.Vector3f(0.4683325515f,1.1784066089f,0.0000001324f),0.1071658289f),
        Assembly(3,org.joml.Vector3f(0.2206035610f,0.8451336656f,0.3820966929f),org.joml.Vector3f(0.2385130525f,1.2852191586f,0.4131168872f),org.joml.Vector3f(0.2341662640f,1.1784066038f,0.4055880178f),0.1071657609f),
        Assembly(4,org.joml.Vector3f(-0.2206036355f,0.8451336656f,0.3820966184f),org.joml.Vector3f(-0.2385131270f,1.2852191586f,0.4131168872f),org.joml.Vector3f(-0.2341663384f,1.1784065999f,0.4055879994f),0.1071657661f),
        Assembly(5,org.joml.Vector3f(-0.4412072338f,0.8451336656f,0.0000000559f),org.joml.Vector3f(-0.4770261981f,1.2852192331f,0.0000000489f),org.joml.Vector3f(-0.4683326214f,1.1784066077f,0.0000000506f),0.1071658304f),
        Assembly(6,org.joml.Vector3f(-0.2206035145f,0.8451335911f,-0.3820966557f),org.joml.Vector3f(-0.2385130059f,1.2852190841f,-0.4131166637f),org.joml.Vector3f(-0.2341662179f,1.1784065392f,-0.4055878402f),0.1071657478f)
    )
    private var linkageOpeningFrames=0
    private var linkageSpinningFrames=0
    private var maxLinkageError=0f
    private var maxAxisError=0f
    private var minimumOverlap=Float.MAX_VALUE
    private var closeCamera=false
    fun checkLinkage(pod: MineDispenserEntity,instance: com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance) {
        if(!enabled || !prepared || pod.uuid!=podId)return
        if(pod.phase!=AbstractHellpodEntity.Phase.DEPLOYING && pod.phase!=AbstractHellpodEntity.Phase.ACTIVE)return
        fun point(bone: String,p: org.joml.Vector3f)=instance.getGlobalTransform(instance.getIndex(bone)).transformPosition(org.joml.Vector3f(p))
        for(a in assemblies) {
            val base=point("spinner",a.bottom)
            val joint=point("panel_${a.panel}",a.top)
            val axis=org.joml.Vector3f(joint).sub(base).normalize()
            val sleeve=point("panel_${a.panel}_axis_sleeve",a.bottom)
            val sleeveEnd=point("panel_${a.panel}_axis_sleeve",org.joml.Vector3f(a.bottom).add(0f,6.05f/16f,0f))
            val shaftStart=point("panel_${a.panel}_axis_shaft",a.shaftOrigin)
            val shaftEnd=point("panel_${a.panel}_axis_shaft",org.joml.Vector3f(a.shaftOrigin).add(0f,a.length,0f))
            val expectedStart=org.joml.Vector3f(base).fma(5.35f/16f,axis)
            val error=max(sleeve.distance(base),max(shaftEnd.distance(joint),shaftStart.distance(expectedStart)))
            val axisError=max(org.joml.Vector3f(sleeveEnd).sub(base).cross(axis).length(),org.joml.Vector3f(shaftStart).sub(base).cross(axis).length())
            val overlap=org.joml.Vector3f(sleeveEnd).sub(shaftStart).dot(axis)
            maxLinkageError=max(maxLinkageError,error);maxAxisError=max(maxAxisError,axisError);minimumOverlap=min(minimumOverlap,overlap)
            check(error<.0005f && axisError<.0005f && overlap>.043f) { "Piston assembly misaligned: panel=${a.panel} joint=$error axis=$axisError overlap=$overlap phase=${pod.phase} age=${pod.phaseAge} spin=${pod.spreadAge}" }
        }
        if(pod.phase==AbstractHellpodEntity.Phase.DEPLOYING && pod.phaseAge in 28..48)linkageOpeningFrames++
        if(pod.spinning)linkageSpinningFrames++
    }
    fun renderedMine(mine:AntiPersonnelMineEntity) {
        if(!enabled || !prepared || (stage!=2 && stage!=9))return
        overviewRendered.getOrPut(stage) { mutableSetOf() }.add(mine.uuid)
    }
    private fun mines(level:ServerLevel,at:Vec3)=level.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(at,at).inflate(27.0))
    private fun setStage(level:ServerLevel,value:Int) { stage=value;stageStart=level.gameTime;LOGGER.info("MINE_SCENE_STAGE stage={} time={} result={}",stage,stageStart,result) }
    private fun camera(level:ServerLevel,at:Vec3,target:Vec3) {
        focus=target
        val player=level.server.playerList.players.first();val delta=target.subtract(at.add(0.0,player.eyeHeight.toDouble(),0.0))
        player.teleportTo(level,at.x,at.y,at.z,Math.toDegrees(atan2(-delta.x,delta.z)).toFloat(),-Math.toDegrees(atan2(delta.y,delta.horizontalDistance())).toFloat())
    }
    private fun settledMine(level:ServerLevel,at:Vec3):AntiPersonnelMineEntity {
        val m=ModEntities.ANTI_PERSONNEL_MINE.get().create(level)!!;m.setPos(at)
        val t=CompoundTag();m.saveWithoutId(t);t.putInt("MineAge",60);t.putBoolean("MineArmed",true);t.putBoolean("MineSettled",true);m.load(t)
        level.addFreshEntity(m);return m
    }
    private fun shoot(level:ServerLevel,mine:AntiPersonnelMineEntity) {
        val p=mine.position();val a=Arrow(level,p.x-1.0,p.y+.09,p.z);a.shoot(1.0,0.0,0.0,1f,0f);level.addFreshEntity(a)
        LOGGER.info("MINE_SCENE_ARROW case={} at={}",case,p)
    }
    @SubscribeEvent fun sounds(event:PlaySoundEvent) {
        if(!enabled || !prepared)return
        val sound=event.sound ?: return
        val name=sound.location.path
        if(name in soundChecks) {
            soundInstances.add(sound)
            val distance=Minecraft.getInstance().player?.eyePosition?.distanceTo(Vec3(sound.x,sound.y,sound.z)) ?: 0.0
            if(name=="sfx_ap_mine_explosion_1" && distance>20.0)distantExplosions++
            if(name=="sfx_ap_mine_explosion_1" && distance<5.0)closeExplosions++
            LOGGER.info("MINE_SCENE_AUDIO name={} distance={} stage={} nanos={}",name,distance,stage,System.nanoTime())
        }
        if(name.startsWith("sfx_ap_mine_deploy_") || name.startsWith("sfx_ap_mine_spin_")) {
            val pod=Minecraft.getInstance().level?.entitiesForRendering()?.filterIsInstance<MineDispenserEntity>()?.firstOrNull { it.uuid==podId }
            if(name.startsWith("sfx_ap_mine_spin_"))check(pod!=null && pod.spinning && pod.spreadAge>0) { "Rotation sound began before rotation: $name / ${pod?.phase}" }
            else check(pod!=null && pod.phase==AbstractHellpodEntity.Phase.DEPLOYING) { "Opening sound played outside deployment: $name / ${pod?.phase}" }
            if(name=="sfx_ap_mine_deploy_3")check(pod!!.phaseAge in 28..32) { "Blade opening sound missed blade motion: ${pod.phaseAge}" }
            played.add(name);soundInstances.add(sound)
            LOGGER.info("MINE_SCENE_SOUND name={} phase={} age={} angle={} nanos={}",name,pod.phase,pod.spreadAge,pod.interpolatedSpin(0f),System.nanoTime())
        }
    }
    @SubscribeEvent fun client(event:TickEvent.ClientTickEvent) {
        if(!enabled || event.phase!=TickEvent.Phase.END)return
        val mc=Minecraft.getInstance();val player=mc.player ?: return;val server=mc.singleplayerServer ?: return
        mc.options.pauseOnLostFocus=false
        if(mc.overlay!=null || mc.isPaused)return
        if(!launched) {
            launched=true;Files.createDirectories(out.resolve("frames"));Files.createDirectories(mc.gameDirectory.toPath().resolve("screenshots/minefield_0334"))
            Files.writeString(out.resolve("frames.csv"),"frame,nanos,audio_bytes,stage,world_time,phase,spread_age,mines,phase_age\n")
            mc.options.hideGui=false;mc.options.gamma().set(1.0);mc.options.fov().set(65);mc.options.framerateLimit().set(60);mc.options.guiScale().set(2);mc.resizeDisplay()
            mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);mc.options.getSoundSourceOptionInstance(SoundSource.AMBIENT).set(0.0);mc.options.getSoundSourceOptionInstance(SoundSource.WEATHER).set(0.0)
            mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(1.0);mc.options.getSoundSourceOptionInstance(SoundSource.BLOCKS).set(1.0);mc.options.getSoundSourceOptionInstance(SoundSource.NEUTRAL).set(1.0)
            server.execute {
                val level=server.overworld()
                for(x in 86..94)for(z in 86..90) { level.setChunkForced(x,z,true);level.getChunk(x,z) }
                level.getEntitiesOfClass(Entity::class.java,box).filter { it !is net.minecraft.world.entity.player.Player }.forEach { it.discard() }
                level.dayTime=6000;level.setWeatherParameters(12000,0,false,false)
                level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.gameRules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false,server)
                for((center,radius) in listOf(normal to 28,dense to 14))for(x in -radius..radius)for(z in -radius..radius) {
                    val edge=abs(x)==radius || abs(z)==radius
                    val grid=x%5==0 || z%5==0
                    val block=if(edge && (x+z)%2==0)Blocks.YELLOW_CONCRETE else if(edge)Blocks.BLACK_CONCRETE else if(grid)Blocks.GRAY_CONCRETE else Blocks.LIGHT_GRAY_CONCRETE
                    level.setBlock(BlockPos.containing(center.add(x.toDouble(),-1.0,z.toDouble())),block.defaultBlockState(),2)
                }
                val user=server.playerList.getPlayer(player.uuid)!!;user.setGameMode(GameType.SPECTATOR)
                camera(level,normal.add(5.0,2.5,-6.0),normal.add(0.0,1.0,0.0))
                val marker=ModEntities.STRATAGEM_MARKER.get().create(level)!!;marker.moveTo(normal.add(0.0,.01,0.0));marker.configure(ResourceLocation(MOD_ID,"anti_personnel_minefield"),60,user.uuid,Direction.UP,257.0);level.addFreshEntity(marker)
                prepared=true;setStage(level,1);LOGGER.info("MINE_SCENE_READY normal={} dense={} marker={} observer=spectator grid=5m",normal,dense,marker.uuid)
            }
        }
        if(!prepared)return
        ticks++
        val delta=focus.subtract(player.eyePosition)
        player.yRot=Math.toDegrees(atan2(-delta.x,delta.z)).toFloat();player.yRotO=player.yRot;player.xRot=-Math.toDegrees(atan2(delta.y,delta.horizontalDistance())).toFloat();player.xRotO=player.xRot
        soundInstances.filter { mc.soundManager.isActive(it) }.forEach {
            val name=it.location.path;channels.add(name)
            if((name in soundChecks || name in played) && volumes.getOrPut(name) { mutableSetOf() }.add(it.volume)) {
                if(name=="sfx_ap_mine_land_1")check(abs(it.volume-.2f)<.0001f) { "Mine landing gain not reduced: ${it.volume}" }
                if(name=="sfx_ap_mine_explosion_1")check(it.sound.location.path.startsWith("original/ap_mine/blast_mix_")) { "Trigger click substituted for actual blast: ${it.sound.location}" }
                if(name in played)check(it.volume<=.3501f) { "Motor gain not reduced: $name ${it.volume}" }
                if(name=="sfx_ap_mine_hellpod_impact")check(it.volume in .01f.. 1f) { "Invalid restored pod landing gain: ${it.volume}" }
                LOGGER.info("MINE_SCENE_AUDIO_CHANNEL name={} volume={} file={}",name,it.volume,it.sound.location)
            }
        }
        if(complete) {
            check(captures.count { it.startsWith("linkage_") }==6) { "Six close inspection views missing: $captures" }
            check(linkageOpeningFrames>0 && linkageSpinningFrames>0) { "Opening/rotating rods were not checked" }
            LOGGER.info("MINE_SCENE_LINKAGE_PASSED openingFrames={} spinningFrames={} maxEndpointErrorBlocks={} maxAxisErrorBlocks={} minimumOverlapBlocks={}",linkageOpeningFrames,linkageSpinningFrames,maxLinkageError,maxAxisError,minimumOverlap)
            check(channels.containsAll(soundChecks) && distantExplosions>=3 && closeExplosions>=1) { "Landing or distant blast audio failed: channels=$channels distant=$distantExplosions" }
            check(overviewRendered[2]?.size==48 && overviewRendered[9]?.size==48) { "Mines culled in actual overview: $overviewRendered" }
            check(played.size==5 && channels.containsAll(played)) { "Actual audio channels missing: played=$played channels=$channels" }
            LOGGER.info("MINE_SCENE_CLIENT_PASSED frames={} sounds={} channels={} cases={} interrupted={}",frames,played,channels,results,interruptedCount)
            LOGGER.info("MINE_SCENE_AUDIO_PASSED distantBlasts={} closeBlasts={} volumes={}",distantExplosions,closeExplosions,volumes)
            LOGGER.info("MINE_SCENE_RENDER_PASSED overview38m={} distant80m={}",overviewRendered[2]?.size,overviewRendered[9]?.size)
            Files.writeString(out.resolve("client_passed.txt"),"cases=$results\nplayed=$played\nchannels=$channels\nframes=$frames\ndistantExplosions=$distantExplosions\ncloseExplosions=$closeExplosions\nresolvedVolumes=$volumes\nrendered38m=${overviewRendered[2]?.size}\nrendered80m=${overviewRendered[9]?.size}\ninterruptedCount=$interruptedCount\nlinkageOpeningFrames=$linkageOpeningFrames\nlinkageSpinningFrames=$linkageSpinningFrames\nmaxLinkageErrorBlocks=$maxLinkageError\nmaxAxisErrorBlocks=$maxAxisError\nminimumOverlapBlocks=$minimumOverlap\ncloseInspectionViews=${captures.count { it.startsWith("linkage_") }}\n")
            mc.stop()
        }
        if(ticks>1600)error("Minefield client test timed out: stage=$stage result=$result")
    }
    @SubscribeEvent fun server(event:TickEvent.ServerTickEvent) {
        if(!enabled || !prepared || complete || event.phase!=TickEvent.Phase.END)return
        val level=event.server.overworld();val age=level.gameTime-stageStart
        var pod=podId?.let { level.getEntity(it) } as? MineDispenserEntity
        if(stage==1) {
            if(pod==null) { pod=level.getEntitiesOfClass(MineDispenserEntity::class.java,AABB(normal,normal).inflate(30.0)).firstOrNull();podId=pod?.uuid }
            if(pod!=null && pod.phase==AbstractHellpodEntity.Phase.DEPLOYING && pod.phaseAge>=20 && !closeCamera) {
                camera(level,normal.add(1.8,.15,-2.1),normal.add(0.0,1.25,0.0));closeCamera=true
            }
            if(pod!=null && pod.phase==AbstractHellpodEntity.Phase.ACTIVE && pod.spreadAge>=60) {
                val all=mines(level,normal)
                if(all.size==48 && all.all { it.settled && it.armed }) {
                    positions=all.map { it.position() };field=all
                    selected=listOf(positions.indices.minBy { positions[it].distanceTo(normal) },positions.indices.minBy { abs(positions[it].distanceTo(normal)-12.0) },positions.indices.maxBy { positions[it].distanceTo(normal) })
                    result="六组管件近景核对：1 / 6";camera(level,normal.add(1.9,.15,0.0),normal.add(0.0,1.3,0.0));setStage(level,12)
                }
            }
        } else if(stage==12) {
            if(age in 20..100 && age%20==0L) {
                val angle=age.toDouble()/20*PI/3
                result="六组管件近景核对：${age/20+1} / 6";camera(level,normal.add(cos(angle)*1.9,.15,sin(angle)*1.9),normal.add(0.0,1.3,0.0))
            }
            if(age>=120) { result="完整播撒：48 / 48";camera(level,normal.add(0.0,33.0,-19.0),normal);setStage(level,2) }
        } else if(stage==2 && age>=40) {
            result="80 米远景：完整雷区仍然显示";camera(level,normal.add(0.0,80.0,0.0),normal);setStage(level,9)
        } else if(stage==9 && age>=40) {
            camera(level,normal.add(0.0,33.0,-19.0),normal);shoot(level,field[selected[case]]);setStage(level,3)
        } else if(stage==3) {
            if(age==20L) {
                val dead=field.count { it.isRemoved };check(dead in 1..12) { "One normal-field mine cleared $dead/48" };results.add(dead)
                result="单点起爆 ${case+1}：引爆 $dead，剩余 ${48-dead}";LOGGER.info("MINE_SCENE_NORMAL case={} detonated={} remaining={}",case,dead,48-dead)
            }
            if(age>=50) {
                if(case<2) {
                    field.forEach { it.discard() };field=positions.map { settledMine(level,it) };case++;shoot(level,field[selected[case]]);setStage(level,3)
                } else {
                    denseField=listOf(dense,dense.add(2.5,0.0,0.0),dense.add(0.0,0.0,4.5)).map { settledMine(level,it) }
                    camera(level,dense.add(7.0,5.0,-9.0),dense.add(1.0,0.0,1.0));result="近邻与边缘对照：3 枚";setStage(level,4)
                }
            }
        } else if(stage==4 && age>=40) { shoot(level,denseField[0]);setStage(level,5) }
        else if(stage==5) {
            if(age==20L) { check(denseField[0].isRemoved && denseField[1].isRemoved && !denseField[2].isRemoved);result="近处连爆 2 枚，边缘地雷保留";LOGGER.info("MINE_SCENE_LOCAL_CHAIN_PASSED close=2.5m edge=4.5m") }
            if(age>=60) {
                denseField.forEach { it.discard() };closeMine=settledMine(level,dense)
                camera(level,dense.add(1.8,.1,-1.0),dense);result="近距离单枚地雷：真正爆炸声";setStage(level,10)
            }
        } else if(stage==10 && age>=20) { shoot(level,closeMine!!);setStage(level,11) }
        else if(stage==11 && age>=150) {
                val next=ModEntities.ANTI_PERSONNEL_MINEFIELD.get().create(level)!!;next.setPos(dense)
                val t=CompoundTag();next.saveWithoutId(t);t.putInt("HellpodPhase",AbstractHellpodEntity.Phase.DEPLOYING.ordinal);t.putDouble("HellpodRestY",dense.y);t.putBoolean("HellpodCapReleased",false);next.load(t);level.addFreshEntity(next);podId=next.uuid
                camera(level,dense.add(5.0,2.5,-6.0),dense.add(0.0,1.0,0.0));result="播撒途中摧毁机构";setStage(level,6)
        } else if(stage==6 && pod!=null && pod.releasedWaves>=2) {
            interruptedCount=mines(level,dense).size;pod.hurt(level.damageSources().generic(),1000f);check(pod.phase==AbstractHellpodEntity.Phase.SPENT)
            interruptedAt=level.gameTime;setStage(level,7)
        } else if(stage==7 && age>=80) {
            val count=mines(level,dense).size;check(count==interruptedCount && count in 6..18) { "Destroyed dispenser continued releasing: $interruptedCount->$count" }
            result="摧毁后停止播撒：保留已释放的 $count 枚";LOGGER.info("MINE_SCENE_INTERRUPTION_PASSED count={} ticks={}",count,level.gameTime-interruptedAt)
            setStage(level,8)
        } else if(stage==8 && age>=30)complete=true
    }
    @SubscribeEvent fun draw(event:RenderGuiEvent.Post) {
        if(!enabled || !prepared || complete)return
        val mc=Minecraft.getInstance();val level=mc.level ?: return
        val pod=level.entitiesForRendering().filterIsInstance<MineDispenserEntity>().firstOrNull { it.uuid==podId }
        val count=level.entitiesForRendering().filterIsInstance<AntiPersonnelMineEntity>().count { !it.isRemoved }
        val name=when(stage) {1->"空投、升起、展开与旋转";12->"六组伸缩管件：清理残片、连接中心与同轴近景";2->"正常雷区：5 米距离网格";9->"80 米远景显示距离检查";3->"正常雷区：三处单点起爆";4,5->"近邻连爆与边缘保留";10,11->"近距离爆炸声检查";else->"机构中途摧毁"}
        val gui=event.guiGraphics
        gui.fill(8,8,mc.window.guiScaledWidth-8,70,0xC0151A20.toInt())
        gui.drawString(mc.font,"反人员地雷实际游戏测试  0.3.34",16,14,0xFFFFFF,true)
        gui.drawString(mc.font,name,16,29,0xFFE16A,true)
        gui.drawString(mc.font,if(stage==1)"${pod?.phase ?: "等待空投"}  旋转：${if(pod?.spinning==true)"是" else "否"}  地雷：$count" else result,16,44,0xDBE8ED,true)
        gui.drawString(mc.font,"观察者无碰撞；平整无遮挡；没有敌人和其它炮台",16,58,0xB9C8D0,true)
        val nanos=System.nanoTime()
        if(nanos-lastFrame>=50_000_000L) {
            lastFrame=nanos
            val file="frame_%05d.png".format(frames)
            val audio=out.resolve("game_audio.wav").toFile().let { if(it.exists())it.length() else 0L }
            Files.writeString(out.resolve("frames.csv"),"$frames,$nanos,$audio,$stage,${level.gameTime},${pod?.phase},${pod?.spreadAge},$count,${pod?.phaseAge}\n",StandardOpenOption.APPEND)
            Screenshot.grab(out.toFile(),"../frames/$file",mc.mainRenderTarget) {}
            if(stage==12) {
                val age=level.gameTime-stageStart
                val side=(age/20).toInt()
                if(side in 0..5 && age%20>=7 && captures.add("linkage_$side"))Screenshot.grab(mc.gameDirectory,"minefield_0334/linkage_${side+1}.png",mc.mainRenderTarget) {}
            }
            if(captures.add("stage_$stage"))Screenshot.grab(mc.gameDirectory,"minefield_0334/stage_$stage.png",mc.mainRenderTarget) {}
            frames++
        }
    }
}