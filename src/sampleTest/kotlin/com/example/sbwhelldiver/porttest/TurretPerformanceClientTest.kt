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
    private val autocannonOnly=java.lang.Boolean.getBoolean("sbw_helldiver.autocannonAlignmentTests")
    private val rocketOnly=java.lang.Boolean.getBoolean("sbw_helldiver.perfRocketOnly")
    private val panelsOnly=java.lang.Boolean.getBoolean("sbw_helldiver.perfPanelsOnly")
    private val laserOnly=java.lang.Boolean.getBoolean("sbw_helldiver.perfLaserOnly")
    private var started=0L
    private var prepared=false
    @Volatile private var ready=false
    @Volatile private var failure:Throwable?=null
    @Volatile private var scenario=if(panelsOnly)5 else if(autocannonOnly)3 else if(rocketOnly)4 else -1
    @Volatile private var age=0
    @Volatile private var done=false
    private var playerId:java.util.UUID?=null
    private var renderScenario=-1
    private var lastFrame=0L
    private var captured=-1
    private var reverseCaptured=-1
    private var digWait=0
    private var qualityStage=0
    private var qualityFrames=0
    private var qualityReload:java.util.concurrent.CompletableFuture<Void>?=null
    private var diggingPodId=-1
    private var axisChecks=0
    private var traverseSamples=0
    private var maximumAxisGap=0f
    private var gunAxis=org.joml.Vector3f()
    private val alignmentCaptures=mutableSetOf<Int>()
    private val frames=mutableMapOf<Int,MutableList<Double>>()
    private val firing=mutableMapOf<Int,Int>()
    private val targets=mutableListOf<Husk>()
    private val pods=mutableListOf<AbstractHellpodEntity>()
    private val types by lazy { listOf(ModEntities.MACHINE_GUN_SENTRY,ModEntities.GATLING_SENTRY,
        ModEntities.AUTOCANNON_SENTRY,ModEntities.ROCKET_SENTRY,ModEntities.MORTAR_SENTRY,
        ModEntities.GAS_MORTAR_SENTRY,ModEntities.EMS_MORTAR_SENTRY,ModEntities.FLAME_SENTRY,
        ModEntities.LASER_SENTRY,ModEntities.TESLA_TOWER,ModEntities.HMG_EMPLACEMENT,
        ModEntities.ANTI_TANK_EMPLACEMENT,ModEntities.GRENADIER_BATTLEMENT,ModEntities.SHIELD_GENERATOR) }
    private val names=listOf("empty","mixed-idle","machine-gun","gatling","autocannon","rocket",
        "mortar","gas-mortar","ems-mortar","flame","laser","tesla",
        "hmg-emplacement","anti-tank-emplacement","grenadier-battlement","shield-generator","mixed-active")

    private val laserNames=listOf("empty","laser-idle","laser-active","four-laser-idle","four-laser-active","eight-laser-idle","eight-laser-active")
    private fun label(i:Int)=if(laserOnly)laserNames[i] else names[i]

    @SubscribeEvent fun serverTick(e:TickEvent.LevelTickEvent) {
        if(e.phase!=TickEvent.Phase.START || e.level.isClientSide || e.level.dimension()!=Level.OVERWORLD)return
        val server=e.level.server?:return
        if(!enabled||!ready||done||failure!=null)return
        try {
            val level=server.overworld();age++
            for(target in targets)if(target.isAlive){target.health=target.maxHealth;target.clearFire()}
            if(scenario>=2) firing[scenario]=pods.sumOf{it.shotSerial+(if(it is SentryHellpodEntity)it.teslaArcData.getInt("Serial")else 0)}
            if(autocannonOnly && scenario==4) {
                if(age in listOf(90,160,230)) {
                    val height=when(age){90->6.0;160->0.0;else->3.0}
                    targets.forEach{it.setPos(if(age==160)4.5 else .5,80.0+height,9.5)}
                }
                if(age==300) pods.forEach{pod->
                    val data=CompoundTag();pod.saveWithoutId(data)
                    data.putInt("HellpodPhase",AbstractHellpodEntity.Phase.RETRACTING.ordinal)
                    data.putInt("HellpodPhaseTicks",0);pod.load(data)
                }
            }
            if(!autocannonOnly && !laserOnly && scenario in 12..15) {
                val p=server.playerList.getPlayer(playerId!!)!!
                val emplacement=pods.firstOrNull() as? HmgEmplacementEntity
                if(emplacement!=null && emplacement.phase==AbstractHellpodEntity.Phase.ACTIVE && age in 120..270) {
                    if(scenario==12 && age>=151) {
                        p.stopRiding()
                        if(age in listOf(151,165,220)) {
                            val pose=CompoundTag();emplacement.saveWithoutId(pose)
                            pose.putFloat("HmgPitch",when{age<165->0f;age<220->-65f;else->47f})
                            pose.putBoolean("HmgHasBeenMounted",true);pose.putInt("HmgFirstMountTicksRemaining",0)
                            emplacement.load(pose)
                        }
                    } else if(!p.isPassenger)p.startRiding(emplacement,true)
                    if(scenario==12)targets.first().setPos(.5,80.0+when{age<165->0.0;age<220->24.0;else->-3.0},if(age<220)9.5 else 4.5)
                    if(scenario==12 && age in listOf(155,200,250))LOGGER.info("HMG_FEED_ANGLE age={} pitch={}",age,emplacement.weaponPitch)
                    if(scenario==13)targets.first().setPos(.5+sin(age*.055)*6.0,80.0,9.5)
                    val target=targets.first().position().add(0.0,1.0,0.0)
                    val aim=emplacement.aimAnglesAt(target)
                    emplacement.acceptInput(p,aim.x,aim.y,true,target)
                }
                if(age==290) {
                    p.stopRiding()
                    if(emplacement!=null)check(emplacement.shotSerial>0){"Manned fixture never fired: ${label(scenario)}"}
                    LOGGER.info("MANNED_PERF_OPERATED {} shots={}",label(scenario),emplacement?.shotSerial?:0)
                }
                if(age==310)pods.forEach{pod->val data=CompoundTag();pod.saveWithoutId(data);data.putInt("HellpodPhase",AbstractHellpodEntity.Phase.RETRACTING.ordinal);data.putInt("HellpodPhaseTicks",0);pod.load(data)}
            }
            if(age>=if((autocannonOnly && scenario==4)||(!laserOnly && scenario in 12..15))400 else 240){
                if(scenario==(if(panelsOnly)10 else if(autocannonOnly)4 else if(laserOnly)6 else if(rocketOnly)5 else names.lastIndex)){done=true;return}
                level.getAllEntities().toList().filter{it !is net.minecraft.world.entity.player.Player}.forEach{it.discard()}
                targets.clear();pods.clear();scenario++;age=0
                val indices=if(laserOnly)when(scenario){0->emptyList();1,2->listOf(8);3,4->List(4){8};else->List(8){8}}else when(scenario){0->emptyList();1,16->types.indices.toList();else->listOf(scenario-2)}
                for((slot,index) in indices.withIndex()){
                    val x=if(indices.size==1)0.5 else if(laserOnly)(slot%4)*3.0-4.0 else (slot%5-2)*4.0+.5
                    val z=if(indices.size==1)0.5 else if(laserOnly)slot/4*3.0+.5 else slot/5*4.0+.5
                    val pod=types[index].get().create(level)!!
                    pod.moveTo(x,80.0,z,180f,0f);pod.setOwnerUUID(playerId!!)
                    val data=CompoundTag();pod.saveWithoutId(data)
                    data.putInt("HellpodPhase",AbstractHellpodEntity.Phase.DEPLOYING.ordinal)
                    data.putInt("HellpodPhaseTicks",if(autocannonOnly || index>=10)0 else pod.deployTicks-1);data.putBoolean("HellpodCapReleased",true)
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
        if(autocannonOnly && scenario==4 && age in 65..295)try {
            val mc=Minecraft.getInstance()
            val pod=mc.level?.entitiesForRendering()?.filterIsInstance<SentryHellpodEntity>()?.firstOrNull()
            if(pod!=null && pod.phase==AbstractHellpodEntity.Phase.ACTIVE){
                val renderer=mc.entityRenderDispatcher.getRenderer(pod) as com.example.sbwhelldiver.client.HellpodRenderer
                val instance=renderer.getCurrentModelEntry(com.mojang.blaze3d.vertex.PoseStack(),pod)!!.instance
                val moving=instance.getGlobalTransform(instance.getIndex("pitch_mount")).transformPosition(org.joml.Vector3f(gunAxis))
                val fixed=instance.getGlobalTransform(instance.getIndex("cradle")).transformPosition(org.joml.Vector3f(5.33f/16f,26.91787f/16f,2.11158f/16f))
                val gap=moving.distance(fixed);maximumAxisGap=maxOf(maximumAxisGap,gap)
                check(gap<.001f){"Autocannon trunnion separated from fixed axle: $gap blocks"};axisChecks++
            }
        }catch(t:Throwable){failure=t}
        if(scenario==13 && age in 140..260)try {
            val pod=Minecraft.getInstance().level?.entitiesForRendering()?.filterIsInstance<HmgEmplacementEntity>()?.firstOrNull()
            if(pod!=null){
                val start=pod.interpolatedYaw(0f);val end=pod.interpolatedYaw(1f)
                if(abs(end-start)>.01f){
                    check(abs(pod.interpolatedYaw(.5f)-(start+end)*.5f)<.001f){"Traverse interpolation discontinuity"}
                    traverseSamples++
                }
            }
        }catch(t:Throwable){failure=t}
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
                if(autocannonOnly) {
                    val modelId=net.minecraft.resources.ResourceLocation(MOD_ID,"models/bedrock/vehicle/autocannon_hellpod_v1.geo.json")
                    val doc=mc.resourceManager.getResource(modelId).orElseThrow().openAsReader().use{com.google.gson.JsonParser.parseReader(it).asJsonObject}
                    val bones=doc["minecraft:geometry"].asJsonArray[0].asJsonObject["bones"].asJsonArray
                    val mount=bones.first{it.asJsonObject["name"].asString=="pitch_mount"}.asJsonObject
                    val shaft=mount["poly_mesh"].asJsonObject["positions"].asJsonArray.map{it.asJsonArray.map{v->v.asFloat}}
                        .filter{minOf(abs(abs(it[0])-2.67f),abs(abs(it[0])-5.33f))<.001f}
                    gunAxis=org.joml.Vector3f(5.33f/16f,(shaft.minOf{it[1]}+shaft.maxOf{it[1]})/32f,(shaft.minOf{it[2]}+shaft.maxOf{it[2]})/32f)
                }
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
            // Keep single turrets close enough to inspect their visible surfaces; retain the overview for groups.
            val close = !laserOnly && scenario in 2..15
            mc.options.cameraType=CameraType.FIRST_PERSON
            player.setPos(if(close)2.5 else 5.5,80.0,if(close)-3.5 else -7.5);player.yRot=30f;player.yRotO=30f;player.xRot=-6f;player.xRotO=-6f
            if(close && age>=if(scenario in 12..15)295 else 214) {
                player.setPos(-2.5,80.0,4.5);player.yRot=210f;player.yRotO=210f
                if(reverseCaptured!=scenario && age>=if(scenario in 12..15)305 else 225) {
                    reverseCaptured=scenario
                    Screenshot.grab(mc.gameDirectory,"turret-perf-${label(scenario)}-reverse.png",mc.mainRenderTarget){}
                }
            }
            if(close && age in 85..105) {
                val y=when(scenario){11->80.15;10->81.1;12->81.0;else->80.55}
                player.setPos(2.2,y,1.8)
                val dx=.5-player.x;val dz=.5-player.z;val dy=(if(scenario==11)81.6 else 82.1)-player.eyeY
                player.yRot=Math.toDegrees(atan2(-dx,dz)).toFloat();player.yRotO=player.yRot
                player.xRot=(-Math.toDegrees(atan2(dy,sqrt(dx*dx+dz*dz)))).toFloat();player.xRotO=player.xRot
                if(age==100)Screenshot.grab(mc.gameDirectory,"detail-${label(scenario)}.png",mc.mainRenderTarget){}
            }
            if(panelsOnly || scenario in 6..10) {
                mc.options.fov().set(60)
                val near=age<140 || age>=200
                val back=age>=200
                val cx=if(back)-2.0 else if(near)2.0 else 3.5
                val cz=if(back)1.7 else if(near)-.7 else -2.0
                player.setPos(cx,80.0,cz)
                player.yRot=Math.toDegrees(atan2(cx-.5,.5-cz)).toFloat();player.yRotO=player.yRot
                player.xRot=4f;player.xRotO=4f
                if(age in listOf(110,175,225))Screenshot.grab(mc.gameDirectory,"panel-${label(scenario)}-$age.png",mc.mainRenderTarget){}
            }
            if(scenario!=captured && age in 170..210 && scenario>=0){
                captured=scenario;Screenshot.grab(mc.gameDirectory,"turret-perf-${label(scenario)}.png",mc.mainRenderTarget){LOGGER.info("TURRET_PERF_SCREENSHOT {}",label(scenario))}
            }
            if(!autocannonOnly && !laserOnly && scenario in 12..15 && age in listOf(35,105,155,200,250,335))
                Screenshot.grab(mc.gameDirectory,"manned-${label(scenario)}-$age.png",mc.mainRenderTarget){}
            if(autocannonOnly && scenario==4) {
                val section=when{age<80->0;age<140->1;age<210->2;age<300->3;else->4}
                val camera=when(section){0,1,4->doubleArrayOf(2.8,79.9,-.6);2->doubleArrayOf(-2.1,80.1,2.1);else->doubleArrayOf(1.3,82.4,-2.0)}
                player.setPos(camera[0],camera[1],camera[2]);val dx=.5-player.x;val dz=.5-player.z;val dy=81.75-player.eyeY
                player.yRot=Math.toDegrees(atan2(-dx,dz)).toFloat();player.yRotO=player.yRot
                player.xRot=(-Math.toDegrees(atan2(dy,sqrt(dx*dx+dz*dz)))).toFloat();player.xRotO=player.xRot
                if(age in listOf(35,110,180,260,325) && alignmentCaptures.add(age))
                    Screenshot.grab(mc.gameDirectory,"autocannon-alignment-$age.png",mc.mainRenderTarget){}
                if(done){
                    check(axisChecks>100){"Insufficient animated axis checks: $axisChecks"}
                    check((firing[4]?:0)>0){"Autocannon never fired"}
                    LOGGER.info("AUTOCANNON_ALIGNMENT_PASSED checks={} maximumGap={} shots={} captures={}",axisChecks,maximumAxisGap,firing[4],alignmentCaptures.size)
                    mc.stop()
                }
                return
            }
            if(done){
                if(panelsOnly){LOGGER.info("PANEL_VIEWS_PASSED");mc.stop();return}
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
                if(qualityStage==0) {
                    LOGGER.info("TURRET_PERF_DIG_EXPOSURE_PASSED")
                    ClientModelConfig.lowQualityModels.set(true)
                    for(visible in mc.level!!.entitiesForRendering().filterIsInstance<SentryHellpodEntity>()) {
                        val distant=com.example.sbwhelldiver.client.ModelLod.entry(visible)!!
                        check(distant.texture.path.contains("/lod/")){"Distant mesh retained repacked near UV atlas"}
                        check(com.example.sbwhelldiver.client.BinaryEntityTextures.contains(distant.texture)){"Distant texture lost binary-alpha fast path"}
                    }
                    Screenshot.grab(mc.gameDirectory,"turret-perf-distant-atlases.png",mc.mainRenderTarget){}
                    qualityStage=1;qualityReload=mc.reloadResourcePacks();return
                }
                if(qualityStage==1) {
                    if(qualityReload?.isDone!=true || mc.overlay!=null || qualityFrames++<30)return
                    qualityReload!!.join()
                    val texture=net.minecraft.resources.ResourceLocation(MOD_ID,"textures/bedrock/vehicle/laser_hellpod_v1.png")
                    check(com.example.sbwhelldiver.client.BinaryEntityTextures.contains(texture)){"Low-quality overlay lost compatible texture hash"}
                    check(com.example.sbwhelldiver.client.ModelLod.verifySkeletons()>0)
                    Screenshot.grab(mc.gameDirectory,"turret-perf-low-quality-atlases.png",mc.mainRenderTarget){}
                    LOGGER.info("TURRET_PERF_QUALITY_ATLASES_PASSED")
                    qualityStage=2
                }
                val lines=mutableListOf("scenario,frames,median_ms,p95_ms,shots_or_arcs")
                for(i in if(laserOnly)(0..6).toList()else if(rocketOnly)listOf(5)else names.indices.toList()){
                    if(if(laserOnly)i==2||i==4||i==6 else i>=2 && i!=15)check((firing[i]?:0)>0){"No actual shots from ${names[i]}"}
                    val values=frames[i].orEmpty().sorted();check(values.size>=30){"Insufficient frames ${names[i]}: ${values.size}"}
                    val label=label(i)
                    val row="$label,${values.size},${values[values.size/2]},${values[(values.size*.95).toInt().coerceAtMost(values.lastIndex)]},${firing[i]?:0}"
                    lines.add(row);LOGGER.info("TURRET_PERF_RESULT {}",row)
                }
                val path=mc.gameDirectory.toPath().resolve("turret-performance.csv");Files.write(path,lines)
                LOGGER.info("NEAR_MESH_COMPILED triangles={} submittedQuads={} enabled={}",com.example.sbwhelldiver.client.PlanarMeshQuads.inputTriangles,com.example.sbwhelldiver.client.PlanarMeshQuads.outputQuads,com.example.sbwhelldiver.client.PlanarMeshQuads.ENABLED)
                for(name in listOf("guard_dog","laser_guard_dog","stun_guard_dog","flame_guard_dog","gas_guard_dog").flatMap{listOf(it,it+"_backpack")})
                    LOGGER.info("NEAR_MESH_MODEL {} faces={}",name,com.example.sbwhelldiver.client.MachineMesh.faceCounts(name))
                check(traverseSamples>100){"Insufficient smooth moving-target traverse frames: $traverseSamples"}
                LOGGER.info("TRAVERSE_INTERPOLATION_PASSED samples={}",traverseSamples)
                LOGGER.info("TURRET_PERF_PASSED shader={} fullDetail=true lod=false",ShaderPackCompat.active());mc.stop()
            }
        }catch(t:Throwable){LOGGER.error("TURRET_PERF_FAILED",t);done=true;mc.stop()}
    }
}
