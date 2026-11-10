package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.client.*
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.item.GuardDogBackpack
import com.example.sbwhelldiver.stratagem.Stratagems
import net.minecraft.client.*
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.world.*
import net.minecraft.world.entity.*
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.EntityHitResult
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.tick.ServerTickEvent
import kotlin.math.abs

/** Disposable third-person scene: real delivery, hostile chain, moving recall, then all engine variants. */
@EventBusSubscriber(modid=MOD_ID, value=[Dist.CLIENT])
@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
object StunGuardDogClientTest {
    private val enabled = java.lang.Boolean.getBoolean("sbw_helldiver.stunGuardDogTests")
    private var started = 0L
    private var prepared = false
    @Volatile private var ready = false
    @Volatile private var failure: Throwable? = null
    @Volatile private var stage = 0
    @Volatile private var variant = 0
    private var picked = false
    private var equipped = false
    private var serial = 0
    private var lastShot = -1L
    private var shots = 0
    private var hits = 0
    private var phaseAt = 0L
    private val rendered = mutableMapOf<GuardDogEntity.Flight, Int>()
    private val audioFrames = intArrayOf(0,0,0)
    private val soundCues = mutableMapOf<String,Int>()
    private val pictures = mutableSetOf<String>()
    private val victims = mutableListOf<net.minecraft.world.entity.monster.Husk>()

    private fun capture(mc:Minecraft, name:String) {
        if (pictures.add(name)) Screenshot.grab(mc.gameDirectory,"stun-dog-$name.png",mc.mainRenderTarget) {}
    }
    @SubscribeEvent fun sound(e:net.neoforged.neoforge.client.event.sound.PlaySoundEvent) {
        if(enabled && e.name in setOf("guard_dog_ignition","guard_dog_shutdown","guard_dog_engine"))
            soundCues.merge(e.name,1,Int::plus)
    }
    @SubscribeEvent fun damage(e:net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent) {
        if (!enabled || e.entity !in victims || e.source.msgId != "sbw_helldiver.tesla_arc") return
        if (abs(e.amount-TeslaClock.DAMAGE*.4f) > .001f) failure=IllegalStateException("Wrong electric damage ${e.amount}")
        hits++
    }
    @SubscribeEvent fun server(e:ServerTickEvent.Post) {
        if (!enabled || !ready || failure != null || stage>=6) return
        try {
            val world=e.server.overworld(); val p=e.server.playerList.players.firstOrNull()?:return
            val pack=GuardDogBackpack.equipped(p)?:return
            val drone=GuardDogBackpack.active(pack)?.let(world::getEntity) as? GuardDogEntity
            when(stage) {
                0 -> {
                    check(GuardDogBackpack.isStun(pack))
                    GuardDogBackpack.updateAmmo(pack,0)
                    repeat(8) { check(GuardDogBackpack.loadSpare(pack)) }
                    GuardDogBackpack.updateAmmo(pack,0)
                    for ((x,z) in listOf(4.5 to 13.5, 7.5 to 22.5, 2.5 to 29.5)) {
                        val enemy=EntityType.HUSK.create(world)!!; enemy.setNoAi(true)
                        enemy.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=10000.0
                        enemy.getAttribute(Attributes.ARMOR)!!.baseValue=0.0
                        enemy.getAttribute(Attributes.KNOCKBACK_RESISTANCE)!!.baseValue=1.0
                        enemy.health=10000f; enemy.moveTo(x,80.0,z,0f,0f)
                        check(world.addFreshEntity(enemy)); victims+=enemy
                    }
                    GuardDogBackpack.toggle(p); stage=1
                }
                1 -> if (drone?.flight==GuardDogEntity.Flight.PATROL) {
                    check(drone.isStun && !drone.hurt(world.damageSources().generic(),10000f)); stage=2
                }
                2 -> if (drone != null) {
                    check(drone.flight==GuardDogEntity.Flight.PATROL) { "Flight interrupted before final shot: ${drone.flight}" }
                    check(GuardDogBackpack.ammo(pack)==0 && GuardDogBackpack.spareMags(pack)==0)
                    val tag=drone.arcData
                    if (tag.getInt("Serial")!=serial) {
                        serial=tag.getInt("Serial"); shots++
                        check(tag.getInt("Count")>=3) { "Expected visible chain, got ${tag.getInt("Count")}" }
                        if (lastShot>=0) check(world.gameTime-lastShot==100L) { "Wrong shot interval ${world.gameTime-lastShot}" }
                        lastShot=world.gameTime
                        LOGGER.info("STUN_DOG_SHOT {} victims={} damage={}",shots,tag.getInt("Count"),TeslaClock.DAMAGE*.4f)
                        if (shots>=3) { check(hits>=9); drone.recall(); stage=3 }
                    }
                }
                3 -> {
                    if (drone != null) check(drone.reload==GuardDogEntity.Reload.NONE)
                    else {
                        victims.forEach { it.discard() }
                        val back=top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(p).orElseThrow()
                            .getStacksHandler("back").orElseThrow().stacks
                        back.setStackInSlot(0,ItemStack(if(variant==0)ModItems.GUARD_DOG_BACKPACK.get() else ModItems.LASER_GUARD_DOG_BACKPACK.get()))
                        variant++
                        GuardDogBackpack.toggle(p); stage=4
                    }
                }
                4 -> if (drone?.flight==GuardDogEntity.Flight.PATROL) { phaseAt=world.gameTime; stage=5 }
                5 -> if (drone == null) {
                    if (variant<2) stage=3 else stage=6
                } else if (world.gameTime-phaseAt>=30 && drone.flight==GuardDogEntity.Flight.PATROL) drone.recall()
            }
        } catch(t:Throwable) { failure=t }
    }
    @SubscribeEvent fun tick(e:ClientTickEvent.Post) {
        if(!enabled)return
        val mc=Minecraft.getInstance()
        try {
            failure?.let { throw it }
            if(started==0L && mc.screen is TitleScreen && mc.overlay==null) {
                started=System.currentTimeMillis(); mc.options.pauseOnLostFocus=false
                mc.options.cameraType=CameraType.THIRD_PERSON_BACK; mc.options.fov().set(65)
                GuardDogVisual.poseObserver={drone,_-> if(drone!=null) rendered[drone.flight]=(rendered[drone.flight]?:0)+1 }
                mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Stun dog and engine verification",
                    GameType.CREATIVE,false,Difficulty.NORMAL,true,GameRules(),WorldDataConfiguration.DEFAULT),
                    WorldOptions(39L,false,false),{it.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()},mc.screen!!)
            }
            check(started==0L || System.currentTimeMillis()-started<180000) { "Timed out: stage=$stage" }
            val player=mc.player?:return; val level=mc.level?:return; val server=mc.singleplayerServer?:return
            if(!prepared) { prepared=true; server.execute { try {
                val world=server.overworld(); val p=server.playerList.getPlayer(player.uuid)!!
                world.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server)
                world.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server); world.dayTime=6000
                for(x in -25..25)for(z in -20..70)world.setBlock(BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
                world.setDefaultSpawnPos(BlockPos(0,80,-4),0f); p.teleportTo(world,.5,80.0,-3.5,0f,-8f)
                val marker=ModEntities.STRATAGEM_MARKER.get().create(world)!!; marker.moveTo(.5,80.0,.5,180f,0f)
                marker.configure(Stratagems.get("stun_guard_dog")!!.dropId,GuardDogEntity.CALL_TICKS,p.uuid,groundY=80.0)
                check(world.addFreshEntity(marker));ready=true
            } catch(t:Throwable) { failure=t } } }
            if(!ready)return
            val pod=level.entitiesForRendering().filterIsInstance<ItemDeliveryHellpodEntity>().firstOrNull()
            if(!picked && pod?.phase==AbstractHellpodEntity.Phase.ACTIVE) {
                check(GuardDogBackpack.isStun(pod.item(ItemDeliveryHellpodEntity.Side.FRONT)))
                check(pod.item(ItemDeliveryHellpodEntity.Side.REAR).isEmpty)
                val aim=pod.toWorld(ItemDeliveryHellpodEntity.Side.FRONT.mount).subtract(player.eyePosition)
                player.yRot=Math.toDegrees(kotlin.math.atan2(-aim.x,aim.z)).toFloat()
                player.xRot=Math.toDegrees(kotlin.math.atan2(-aim.y,aim.horizontalDistance())).toFloat()
                capture(mc,"delivery")
                check(mc.gameMode!!.interactAt(player,pod,EntityHitResult(pod),InteractionHand.MAIN_HAND).consumesAction());picked=true
            }
            if(!equipped && GuardDogBackpack.isStun(player.mainHandItem)) { equipped=true;mc.gameMode!!.useItem(player,InteractionHand.MAIN_HAND) }
            if(mc.screen is net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<*>) player.closeContainer()
            val drone=level.entitiesForRendering().filterIsInstance<GuardDogEntity>().firstOrNull()
            if(drone!=null) {
                val engines=GuardDogAudio.engineSounds(drone)
                check(engines.size<=1) { "Duplicate engine loops: ${engines.size}" }
                if(drone.flight==GuardDogEntity.Flight.PATROL && engines.let {it.size==1 && it.all(mc.soundManager::isActive)}) audioFrames[variant]++
                if(drone.usesPackPose && drone.phaseAge in 8..35) {
                    mc.options.keyUp.setDown(true)
                    capture(mc,"$variant-${drone.flight}")
                } else mc.options.keyUp.setDown(false)
                if(drone.isStun && drone.tickCount-drone.arcReceivedTick in 2..3 && drone.arcData.getInt("Count")>0) {
                    capture(mc,"chain")
                    check(GuardDogVisual.firingMuzzle(drone,1f).distanceTo(drone.position())<2.0) { "Muzzle outside drone" }
                }
            }
            if(stage>=6) {
                check(rendered.getOrDefault(GuardDogEntity.Flight.LAUNCH,0)>20 && rendered.getOrDefault(GuardDogEntity.Flight.DOCK,0)>20)
                check(audioFrames.all { it>10 }) { "Missing engine audio: ${audioFrames.toList()}" }
                check(GuardDogAudio.engineStarts==3 && GuardDogAudio.engineStops==3) { "Engine restarted unexpectedly" }
                check(soundCues.values.sum()==9 && soundCues.values.all {it==3}) { "Repeated or missing engine cue: $soundCues" }
                check(StunGuardDogVisual.visibleFrames>0) { "No electric arc rendered" }
                LOGGER.info("STUN_DOG_PASSED shots={} hits={} render={} arcFrames={} engineFrames={} engineStarts={} stops={}",shots,hits,rendered,StunGuardDogVisual.visibleFrames,audioFrames.toList(),GuardDogAudio.engineStarts,GuardDogAudio.engineStops)
                LOGGER.info("STUN_DOG_AUDIO_CUES {}",soundCues)
                stop(mc)
            }
        } catch(t:Throwable) { LOGGER.error("STUN_DOG_FAILED stage=$stage",t);stop(mc) }
    }
    private fun stop(mc:Minecraft) { mc.options.keyUp.setDown(false);GuardDogVisual.poseObserver=null;mc.stop() }
}
