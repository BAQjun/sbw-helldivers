package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.*
import com.example.sbwhelldiver.client.ShaderPackCompat
import com.example.sbwhelldiver.config.*
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.client.*
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Difficulty
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Husk
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod.EventBusSubscriber
import net.minecraftforge.event.TickEvent

import java.nio.file.Files
import kotlin.math.*

/** Fixed close-up, full-detail fixtures with live targets, frame-time samples and disposable saves. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object TurretPerformanceClientTest {
    private val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.turretPerformanceTests")
    private val rocketOnly=java.lang.Boolean.getBoolean("sbw_helldiver.perfRocketOnly")
    private val laserOnly=true
    private var started=0L
    private var prepared=false
    @Volatile private var ready=false
    @Volatile private var failure:Throwable?=null
    @Volatile private var scenario=if(rocketOnly)4 else -1
    @Volatile private var age=0
    @Volatile private var done=false
    private var playerId:java.util.UUID?=null
    private var renderScenario=-1
    private var lastFrame=0L
    private var captured=-1
    private var digWait=0
    private var diggingPodId=-1
    private val frames=mutableMapOf<Int,MutableList<Double>>()
    private val firing=mutableMapOf<Int,Int>()
    private val targets=mutableListOf<Husk>()
    private val pods=mutableListOf<SentryHellpodEntity>()
    private val types by lazy { listOf(ModEntities.MACHINE_GUN_SENTRY,ModEntities.GATLING_SENTRY,
        ModEntities.AUTOCANNON_SENTRY,ModEntities.ROCKET_SENTRY,ModEntities.MORTAR_SENTRY,
        ModEntities.GAS_MORTAR_SENTRY,ModEntities.EMS_MORTAR_SENTRY,ModEntities.FLAME_SENTRY,
        ModEntities.LASER_SENTRY,ModEntities.TESLA_TOWER) }
    private val names=listOf("empty","mixed-idle","machine-gun","gatling","autocannon","rocket",
        "mortar","gas-mortar","ems-mortar","flame","laser","tesla","mixed-active")

    private val laserNames=listOf("empty","laser-idle","laser-active","four-laser-idle","four-laser-active","eight-laser-idle","eight-laser-active")
    private fun label(i:Int)=if(laserOnly)laserNames[i] else names[i]

    @SubscribeEvent fun serverTick(e:TickEvent.ServerTickEvent) {
        if(e.phase!=TickEvent.Phase.END)return
        val server=Minecraft.getInstance().singleplayerServer?:return
        if(!enabled||!ready||done||failure!=null)return
        try {
            val level=server.overworld();age++
            for(target in targets)if(target.isAlive){target.health=target.maxHealth;target.clearFire()}
            if(scenario>=2) firing[scenario]=pods.sumOf{it.shotSerial+it.teslaArcData.getInt("Serial")}
            if(age>=240){
                if(scenario==(if(laserOnly)6 else if(rocketOnly)5 else names.lastIndex)){done=true;return}
                level.getAllEntities().toList().filter{it !is net.minecraft.world.entity.player.Player}.forEach{it.discard()}
                targets.clear();pods.clear();scenario++;age=0
                val indices=if(laserOnly)when(scenario){0->emptyList();1,2->listOf(8);3,4->List(4){8};else->List(8){8}}else when(scenario){0->emptyList();1,12->types.indices.toList();else->listOf(scenario-2)}
                for((slot,index) in indices.withIndex()){
                    val x=if(indices.size==1)0.5 else if(laserOnly)(slot%4)*3.0-4.0 else (slot%5-2)*4.0+.5
                    val z=if(indices.size==1)0.5 else if(laserOnly)slot/4*3.0+.5 else slot/5*4.0+.5
                    val pod=types[index].get().create(level)!!
                    pod.moveTo(x,80.0,z,180f,0f);pod.setOwnerUUID(playerId!!)
                    val data=CompoundTag();pod.saveWithoutId(data)
                    data.putInt("HellpodPhase",AbstractHellpodEntity.Phase.DEPLOYING.ordinal)
                    data.putInt("HellpodPhaseTicks",pod.deployTicks-1);data.putBoolean("HellpodCapReleased",true)
                    data.putDouble("HellpodRestY",80.0);pod.load(data)
                    check(level.addFreshEntity(pod));pods.add(pod)
                    if(if(laserOnly)scenario==2||scenario==4||scenario==6 else scenario>=2){
                        val target=EntityType.HUSK.create(level)!!
                        target.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=1024.0;target.health=target.maxHealth
                        target.isNoAi=true;target.setPersistenceRequired()
                        // Minimum mortar distance and short-range flame/Tesla require separate target lanes.
                        target.moveTo(x,80.0,z+if(index in 4..6)26.0 else if(index==3)18.0 else 9.0,180f,0f)
                        check(level.addFreshEntity(target));targets.add(target)
                    }
                }
                LOGGER.info("TURRET_PERF_BEGIN {} fullDetail=true lod=false",label(scenario))
            }
        }catch(t:Throwable){failure=t}
    }
    @SubscribeEvent fun frame(e:TickEvent.RenderTickEvent){
        if(e.phase!=TickEvent.Phase.END)return
        if(!enabled||!ready||done||scenario<0)return
        val now=System.nanoTime()
        if(renderScenario==scenario && age in 80..230 && Minecraft.getInstance().screen==null && lastFrame!=0L)
            frames.getOrPut(scenario){mutableListOf()}.add((now-lastFrame)/1e6)
        renderScenario=scenario;lastFrame=now
    }
    @SubscribeEvent fun tick(e:TickEvent.ClientTickEvent){
        if(e.phase!=TickEvent.Phase.END)return
        if(!enabled)return
        val mc=Minecraft.getInstance()
        try{
            failure?.let{throw it}
            if(started==0L && mc.screen is TitleScreen && mc.overlay==null){
                started=System.currentTimeMillis();mc.options.pauseOnLostFocus=false
                mc.options.enableVsync().set(false);mc.options.framerateLimit().set(260)
                mc.options.cameraType=CameraType.FIRST_PERSON;mc.options.fov().set(70)
                ClientModelConfig.lowQualityModels.set(false);ClientModelConfig.modelLod.set(false)
                mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Close-up sentry performance",GameType.CREATIVE,false,Difficulty.NORMAL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(761L,false,false),{it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()})
            }
            check(started==0L || System.currentTimeMillis()-started<600_000){"Turret fixture timed out at $scenario/$age"}
            val player=mc.player?:return;val server=mc.singleplayerServer?:return
            if(!prepared){prepared=true
                val id=net.minecraft.resources.ResourceLocation(MOD_ID,"models/bedrock/vehicle/laser_hellpod_v1.geo.json")
                val resource=mc.resourceManager.getResource(id).orElseThrow()
                val sha=resource.open().use{java.security.MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString(""){b->"%02x".format(b)}}
                LOGGER.info("TURRET_PERF_MODEL_RESOURCE source={} sha256={}",resource.sourcePackId(),sha)
                server.execute{
                try{
                    HelldiversConfig.autoRetract.set(false);HelldiversConfig.infiniteTurretAmmo.set(true)
                    val level=server.overworld();val p=server.playerList.getPlayer(player.uuid)!!;playerId=p.uuid
                    level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server)
                    level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.dayTime=6000
                    for(x in -23..23)for(z in -18..46)for(y in 76..79)level.setBlock(BlockPos(x,y,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
                    level.setDefaultSpawnPos(BlockPos(5,80,-8),30f);p.setGameMode(GameType.CREATIVE)
                    p.teleportTo(level,5.5,80.0,-7.5,30f,-6f);ready=true;age=235
                }catch(t:Throwable){failure=t}
            }}
            if(!ready)return
            // Approximately nine blocks from the primary model; never switch to reduced models.
            player.setPos(5.5,80.0,-7.5);player.yRot=30f;player.yRotO=30f;player.xRot=-6f;player.xRotO=-6f
            if(scenario!=captured && age in 170..210 && scenario>=0){
                captured=scenario;Screenshot.grab(mc.gameDirectory,"turret-perf-${label(scenario)}.png",mc.mainRenderTarget){LOGGER.info("TURRET_PERF_SCREENSHOT {}",label(scenario))}
            }
            if(done){
                if(digWait==0){
                    val pod=mc.level!!.entitiesForRendering().filterIsInstance<SentryHellpodEntity>().first()
                    check(com.example.sbwhelldiver.client.TerrainMeshOcclusion.volume(pod)!=null){"No buried hull volume before digging"}
                    diggingPodId=pod.id;val at=pod.blockPosition()
                    server.execute{for(y in at.y-7 until at.y)server.overworld().setBlock(BlockPos(at.x,y,at.z),Blocks.AIR.defaultBlockState(),3)}
                    digWait=1;return
                }
                if(digWait++<12)return
                val pod=mc.level!!.getEntity(diggingPodId) as SentryHellpodEntity
                check(com.example.sbwhelldiver.client.TerrainMeshOcclusion.volume(pod)==null){"Digging did not expose retained hull"}
                LOGGER.info("TURRET_PERF_DIG_EXPOSURE_PASSED")
                val lines=mutableListOf("scenario,frames,median_ms,p95_ms,shots_or_arcs")
                for(i in if(laserOnly)(0..6).toList()else if(rocketOnly)listOf(5)else names.indices.toList()){
                    if(if(laserOnly)i==2||i==4||i==6 else i>=2)check((firing[i]?:0)>0){"No actual shots from ${names[i]}"}
                    val values=frames[i].orEmpty().sorted();check(values.size>=30){"Insufficient frames ${names[i]}: ${values.size}"}
                    val label=label(i)
                    val row="$label,${values.size},${values[values.size/2]},${values[(values.size*.95).toInt().coerceAtMost(values.lastIndex)]},${firing[i]?:0}"
                    lines.add(row);LOGGER.info("TURRET_PERF_RESULT {}",row)
                }
                val path=mc.gameDirectory.toPath().resolve("turret-performance.csv");Files.write(path,lines)
                LOGGER.info("NEAR_MESH_COMPILED triangles={} submittedQuads={} enabled={}",com.example.sbwhelldiver.client.PlanarMeshQuads.inputTriangles,com.example.sbwhelldiver.client.PlanarMeshQuads.outputQuads,com.example.sbwhelldiver.client.PlanarMeshQuads.ENABLED)
                for(name in listOf("guard_dog","laser_guard_dog","stun_guard_dog","flame_guard_dog","gas_guard_dog").flatMap{listOf(it,it+"_backpack")})
                    LOGGER.info("NEAR_MESH_MODEL {} faces={}",name,com.example.sbwhelldiver.client.MachineMesh.faceCounts(name))
                LOGGER.info("TURRET_PERF_PASSED shader={} fullDetail=true lod=false",ShaderPackCompat.active());mc.stop()
            }
        }catch(t:Throwable){LOGGER.error("TURRET_PERF_FAILED",t);done=true;mc.stop()}
    }
}
