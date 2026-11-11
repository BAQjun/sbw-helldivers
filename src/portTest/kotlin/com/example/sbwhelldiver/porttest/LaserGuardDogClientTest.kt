package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.client.GuardDogVisual
import com.example.sbwhelldiver.client.MachineMesh
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.item.GuardDogBackpack
import com.example.sbwhelldiver.stratagem.Stratagems
import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.*
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.*
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.monster.Husk
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.*
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

/** Real-client regression; only compiled into explicit isolated development runs. */
@EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
object LaserGuardDogClientTest {
    private val enabled=java.lang.Boolean.getBoolean("sbw_helldiver.laserGuardDogTests")
    private var started=0L
    private var prepared=false
    @Volatile private var ready=false
    @Volatile private var failure:Throwable?=null
    @Volatile private var stage=0
    @Volatile private var pressG=false
    private var picked=false
    private var used=false
    private var equipAt=Long.MAX_VALUE
    private var firingFrom=0L
    private var beams=0
    private var finalBeams=0
    private var pulses=0
    private var sawDock=false
    private var finished=false
    private var phaseStart=Vec3.ZERO
    private var target:Husk?=null
    private var detached:GuardDogEntity?=null
    private var removeAt=0L
    private val rendered=mutableMapOf<GuardDogEntity.Flight,Int>()
    private val screenshots=mutableSetOf<String>()
    private var previousPhase:GuardDogEntity.Flight?=null
    private var previousPose:Vec3?=null
    private var previousTime=0.0
    private var handoffs=0
    private var maxStep=0.0
    private var lastStep=Long.MIN_VALUE
    private var playerId:java.util.UUID?=null
    private val engineAxes=mutableMapOf<String,org.joml.Vector3f>()
    private val engineTravel=mutableMapOf<String,Float>()

    @SubscribeEvent fun serverTick(e:net.neoforged.neoforge.event.tick.ServerTickEvent.Post) {
        if(!enabled||!ready||finished||failure!=null)return
        val player=playerId?.let(e.server.playerList::getPlayer)?:return
        try {step(e.server.overworld(),player)}catch(t:Throwable){failure=t}
    }

    @SubscribeEvent fun damage(event:net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent) {
        if(!enabled || event.entity!==target || event.source.msgId!="sbw_helldiver.laser_beam")return
        try {
            check(abs(event.amount-LaserClock.DIRECT_DAMAGE*.6f)<.001f) { "Wrong direct damage: ${event.amount}" }
            pulses++
        }catch(e:Throwable){failure=e}
    }
    private val heldViews=mutableSetOf<net.minecraft.world.item.ItemDisplayContext>()
    private fun capture(mc:Minecraft,name:String) {
        if(screenshots.add(name))Screenshot.grab(mc.gameDirectory,"laser-dog-$name.png",mc.mainRenderTarget){LOGGER.info("LASER_DOG_SCREENSHOT {}",name)}
    }
    private fun observe(drone:GuardDogEntity?,pose:org.joml.Matrix4f) {
        val mc=Minecraft.getInstance();val level=mc.level?:return
        if(mc.screen!=null||com.example.sbwhelldiver.client.ShaderPackCompat.shadowPass())return
        if(drone!=null)rendered[drone.flight]=(rendered[drone.flight]?:0)+1
        val v=pose.getTranslation(org.joml.Vector3f());val p=Vec3(v.x.toDouble(),v.y.toDouble(),v.z.toDouble()).add(mc.gameRenderer.mainCamera.position)
        val time=level.gameTime+mc.timer.getGameTimeDeltaPartialTick(false).toDouble()
        if(time>previousTime && time-previousTime<=2.0 && previousPhase!=drone?.flight &&
            (previousPhase in listOf(GuardDogEntity.Flight.LAUNCH,GuardDogEntity.Flight.DOCK)||drone?.usesPackPose==true)) {
            val step=p.distanceTo(previousPose?:p);maxStep=maxOf(maxStep,step);handoffs++
            if(step>.45*(time-previousTime)+.05)failure=IllegalStateException("Visible handoff snap $previousPhase -> ${drone?.flight}: $step")
        }
        previousTime=time;previousPose=p;previousPhase=drone?.flight
    }
    @SubscribeEvent fun tick(e:ClientTickEvent.Post) {
        if(!enabled)return
        val mc=Minecraft.getInstance()
        try {
            failure?.let {throw it}
            if(started==0L&&mc.screen is TitleScreen&&mc.overlay==null) {
                started=System.currentTimeMillis();mc.options.pauseOnLostFocus=false
                mc.options.cameraType=CameraType.THIRD_PERSON_BACK;mc.options.fov().set(65)
                GuardDogVisual.heldItemObserver={context,reduced->
                    if(context.firstPerson() || context==net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
                        check(reduced){"Handheld backpack used full mesh: $context"};heldViews.add(context)
                    }
                }
                GuardDogVisual.poseObserver=::observe
                MachineMesh.guardDogEngineObserver={name,matrix->
                    val axis=matrix.transformDirection(org.joml.Vector3f(0f,-1f,0f)).normalize()
                    val first=engineAxes.getOrPut(name){org.joml.Vector3f(axis)}
                    engineTravel[name]=maxOf(engineTravel[name]?:0f,first.angle(axis))
                }
                mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Laser Guard Dog verification",GameType.CREATIVE,false,Difficulty.NORMAL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(19L,false,false),{it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()},mc.screen!!)
            }
            check(started==0L||System.currentTimeMillis()-started<180_000){"Timeout at stage $stage"}
            val player=mc.player?:return;val level=mc.level?:return;val server=mc.singleplayerServer?:return
            if(!prepared){prepared=true;server.execute {
                try {
                    val world=server.overworld();val p=server.playerList.getPlayer(player.uuid)!!;playerId=p.uuid
                    world.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);world.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);world.dayTime=6000
                    for(x in -20..20)for(z in -20..24)world.setBlock(BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
                    world.setDefaultSpawnPos(BlockPos(0,80,-4),0f);p.setGameMode(GameType.CREATIVE);p.teleportTo(world,.5,80.0,-3.5,0f,0f)
                    val marker=ModEntities.STRATAGEM_MARKER.get().create(world)!!;marker.moveTo(.5,80.0,.5,180f,0f)
                    marker.configure(Stratagems.get("laser_guard_dog")!!.dropId,GuardDogEntity.CALL_TICKS,p.uuid,groundY=80.0)
                    check(world.addFreshEntity(marker));ready=true
                }catch(t:Throwable){failure=t}
            }}
            if(!ready)return
            val pod=level.entitiesForRendering().filterIsInstance<ItemDeliveryHellpodEntity>().firstOrNull()
            if(!picked&&pod?.phase==AbstractHellpodEntity.Phase.ACTIVE){
                check(GuardDogBackpack.isLaser(pod.item(ItemDeliveryHellpodEntity.Side.FRONT)))
                check(pod.item(ItemDeliveryHellpodEntity.Side.REAR).isEmpty)
                val d=pod.toWorld(ItemDeliveryHellpodEntity.Side.FRONT.mount).subtract(player.eyePosition)
                player.yRot=Math.toDegrees(kotlin.math.atan2(-d.x,d.z)).toFloat();player.xRot=Math.toDegrees(kotlin.math.atan2(-d.y,d.horizontalDistance())).toFloat()
                capture(mc,"delivery");check(mc.gameMode!!.interactAt(player,pod,EntityHitResult(pod),InteractionHand.MAIN_HAND).consumesAction());picked=true;equipAt=level.gameTime+36
            }
            if (picked && !used && GuardDogBackpack.isLaser(player.mainHandItem)) {
                val heldTicks = equipAt - level.gameTime
                mc.options.cameraType = if (heldTicks > 18) CameraType.FIRST_PERSON else CameraType.THIRD_PERSON_BACK
                if (heldTicks in 20L..28L) capture(mc, "held-first-person")
                if (heldTicks in 5L..12L) capture(mc, "held-third-person")
                if (heldTicks <= 0) {
                    used = true
                    mc.gameMode!!.useItem(player, InteractionHand.MAIN_HAND)
                }
            }
            if(mc.screen is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>){player.closeContainer();return}
            val drone=level.entitiesForRendering().filterIsInstance<GuardDogEntity>().firstOrNull()
            val walking=drone?.usesPackPose==true&&drone.phaseAge in 2..40
            val flightAge=if(stage==3)level.gameTime-firingFrom else -1L
            mc.options.keyUp.setDown(walking&&drone?.flight==GuardDogEntity.Flight.LAUNCH||flightAge in 60..90)
            mc.options.keyDown.setDown(walking&&drone?.flight==GuardDogEntity.Flight.DOCK||flightAge in 120..150)
            mc.options.keyLeft.setDown(flightAge in 180..210)
            mc.options.keyRight.setDown(flightAge in 240..270)
            if(walking)player.yRot+=.6f
            if(drone!=null){
                check(drone.isLaser)
                if(drone.usesPackPose&&drone.phaseAge in listOf(0L,10L,20L,31L,45L,60L))capture(mc,"${drone.flight}-${drone.phaseAge}")
                if(drone.beamLength>0f)capture(mc,"beam")
                if(flightAge in listOf(40L,80L,140L,200L,260L))capture(mc,"thrust-$flightAge")
            }
            if(pressG&&mc.screen==null&&GuardDogBackpack.equipped(player)!=null){pressG=false;KeyMapping.click(InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_G));GuardDogVisual.pollKey()}
            if(finished){
                check(screenshots.containsAll(listOf("held-first-person", "held-third-person"))) { "Missing held-backpack views" }
                check(heldViews.containsAll(listOf(net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND,
                    net.minecraft.world.item.ItemDisplayContext.THIRD_PERSON_RIGHT_HAND))){"Handheld render contexts missing: $heldViews"}
                LOGGER.info("HELD_BACKPACK_PASSED reduced=true contexts={}",heldViews)
                for(name in listOf("guard_dog","laser_guard_dog","stun_guard_dog","flame_guard_dog","gas_guard_dog").flatMap{listOf(it,it+"_backpack")})
                    LOGGER.info("NEAR_MODEL_TRANSFORMS {} vertices={}",name,MachineMesh.transformCounts(name))
                check(pulses>=30){"Insufficient real laser damage pulses: $pulses"}
                check(rendered.getOrDefault(GuardDogEntity.Flight.LAUNCH,0)>10&&rendered.getOrDefault(GuardDogEntity.Flight.DOCK,0)>10){"Native transitions were not rendered: $rendered"}
                check(handoffs>=3){"Missing visible handoffs: $handoffs"}
                check(engineTravel.size==4&&engineTravel.values.all{it>.15f}){"Engine housings did not tilt: $engineTravel"}
                LOGGER.info("LASER_DOG_ENGINE_TRAVEL radians {}",engineTravel)
                LOGGER.info("LASER_DOG_PASSED: single-backpack delivery, Curios/chest exclusion, zero-ammo continuous beam {} ticks / {} correct pulses, 3s burning, no heat or reload, moving native release/recall, unequip removal. Handoffs {}, max step {}",beams,pulses,handoffs,maxStep)
                stop(mc);return
            }
        }catch(t:Throwable){LOGGER.error("LASER_DOG_FAILED at {}",stage,t);stop(mc)}
    }
    private fun step(level:ServerLevel,player:ServerPlayer) {
        if(lastStep==level.gameTime)return
        lastStep=level.gameTime
        val pack=GuardDogBackpack.equipped(player)
        val drone=pack?.let(GuardDogBackpack::active)?.let(level::getEntity) as? GuardDogEntity
        when(stage){
            0->if(pack!=null){
                check(GuardDogBackpack.isLaser(pack));check(!player.inventoryMenu.getSlot(6).mayPlace(ItemStack(ModItems.GUARD_DOG_BACKPACK.get())))
                // An empty ammo record must have no influence on the laser variant.
                GuardDogBackpack.updateAmmo(pack,0);repeat(8){check(GuardDogBackpack.loadSpare(pack))};GuardDogBackpack.updateAmmo(pack,0)
                val enemy=EntityType.HUSK.create(level)!!;enemy.setNoAi(true);enemy.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=1024.0;enemy.getAttribute(Attributes.ARMOR)!!.baseValue=0.0;enemy.getAttribute(Attributes.KNOCKBACK_RESISTANCE)!!.baseValue=1.0;enemy.health=enemy.maxHealth;enemy.moveTo(4.5,80.0,10.5,0f,0f);check(level.addFreshEntity(enemy));target=enemy
                player.connection.teleport(player.x,player.y,player.z,0f,-10f)
                phaseStart=player.position();pressG=true;stage=1
            }
            1->if(drone?.flight==GuardDogEntity.Flight.PATROL){check(player.position().distanceToSqr(phaseStart)>.16);check(!drone.hurt(level.damageSources().generic(),10000f));stage=2}
            2->if(drone?.beamLength?:-1f>0f&&target?.hasEffect(ModEffects.BURNING)==true){firingFrom=level.gameTime;stage=3;LOGGER.info("LASER_DOG_FIRING origin {} length {}",drone!!.laserOrigin(),drone.beamLength)}
            3->{
                check(drone?.flight==GuardDogEntity.Flight.PATROL){"Unexpected drone loss/recall: drone=$drone flight=${drone?.flight} player=${player.position()} pack=$pack active=${pack?.let(GuardDogBackpack::active)} target=${target?.position()}"}
                check(GuardDogBackpack.ammo(pack!!)==0&&GuardDogBackpack.spareMags(pack)==0)
                // Keep a real target in range past the sentry heat limit. Vanilla clamps max health.
                val enemy = checkNotNull(target)
                check(enemy.isAlive) { "Laser test target died before the heat-limit check" }
                if (level.gameTime % 20 == 0L) enemy.health = enemy.maxHealth
                if(drone!!.beamLength>0f){
                    beams++
                    val hit=LaserBeam.trace(drone,drone.laserOrigin(),Vec3.directionFromRotation(drone.xRot,drone.yRot))
                    if(hit.target===target)check(target!!.getEffect(ModEffects.BURNING)?.duration in 59..60){"Burning not refreshed for 3 seconds"}
                }
                // Longer than the actual sentry's continuous firing heat budget.
                val duration=(LaserClock.MAX_HEAT/LaserClock.HEAT_PER_SECOND*20).toLong()+80
                if(level.gameTime-firingFrom>=duration-100&&drone.beamLength>0f)finalBeams++
                // Movement may legitimately break aim; the settled final window must still fire beyond the sentry heat limit.
                if(level.gameTime-firingFrom>=duration){check(finalBeams>=90){"Final stationary beam active $finalBeams of 101 ticks (total $beams of $duration)"};phaseStart=player.position();drone.recall();stage=4}
            }
            4->{
                if(drone?.flight==GuardDogEntity.Flight.DOCK){check(drone.reload==GuardDogEntity.Reload.NONE);check(drone.beamLength<0);sawDock=true}
                if(drone==null&&pack!=null){check(sawDock);check(player.position().distanceToSqr(phaseStart)>.16);check(GuardDogBackpack.ammo(pack)==0&&GuardDogBackpack.spareMags(pack)==0);target!!.discard();pressG=true;stage=5}
            }
            5->if(drone?.flight==GuardDogEntity.Flight.PATROL){
                val back=top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).orElseThrow().getStacksHandler("back").orElseThrow().stacks
                check(!back.extractItem(0,1,false).isEmpty);detached=drone;removeAt=level.gameTime;stage=6
            }
            6->if(level.gameTime>removeAt+1){check(detached!!.isRemoved);finished=true}
        }
    }
    private fun stop(mc:Minecraft){mc.options.keyUp.setDown(false);mc.options.keyDown.setDown(false);mc.options.keyLeft.setDown(false);mc.options.keyRight.setDown(false);GuardDogVisual.poseObserver=null;GuardDogVisual.heldItemObserver=null;MachineMesh.guardDogEngineObserver=null;mc.stop()}
}
