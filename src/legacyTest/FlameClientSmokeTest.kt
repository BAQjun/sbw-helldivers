package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.GameRules
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.util.UUID

/** Opt-in real client probe in IntegrationTest, dormant in normal worlds. */
@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object FlameClientSmokeTest {
    private var started=false
    private var ticks=0
    @Volatile private var prepared=false
    @Volatile private var complete=false
    @Volatile private var regressionReady=false
    @Volatile private var podId: UUID?=null
    private var enemy: net.minecraft.world.entity.LivingEntity?=null
    private var ignitionAt=-1L
    private var pauseAt=-1L
    private var resumeAt=-1L
    private var finalGapAt=-1L
    private var shutdownAt=-1L
    private var doneAt=-1L
    private var serial=0
    private var sawBurn=false
    private var sawPanic=false
    private var sawGround=false
    private var sawDirectDamage=false
    private var sawBurnDamage=false
    private var previousTargetHp=500f
    @Volatile private var regressionSpawned=false
    private val shots=mutableListOf<Long>()
    private val states=mutableSetOf<FlameClock.State>()
    private val captured=mutableSetOf<String>()
    private val box=AABB(780.0,-64.0,780.0,910.0,320.0,910.0)
    private const val FOLDER="火焰炮台验收_0.3.13"
    private fun enemy(level: net.minecraft.server.level.ServerLevel)=EntityType.HUSK.create(level)!!.also {
        it.isNoAi=true;it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=500.0;it.health=500f
        it.moveTo(821.5,257.0,834.5,0f,0f);level.addFreshEntity(it)
    }
    @SubscribeEvent fun server(event: TickEvent.ServerTickEvent) {
        if(!java.lang.Boolean.getBoolean("sbw_helldiver.flameSmokeTest") || event.phase!=TickEvent.Phase.END || !prepared)return
        val level=event.server.overworld();val now=level.gameTime
        val pod=(podId?.let { level.getEntity(it) } as? SentryHellpodEntity) ?: level.getEntitiesOfClass(SentryHellpodEntity::class.java,box) { it.profile.isFlame }.firstOrNull()?.also { podId=it.uuid }
        if(pod==null) { finishRegression(level,now);return }
        states.add(pod.flameState)
        if(pod.flameState==FlameClock.State.IGNITING && ignitionAt<0) { ignitionAt=now;check(pod.clipRemaining==900);LOGGER.info("FLAME_IGNITION_BEGIN: time={} ammo={}",now,pod.clipRemaining) }
        if(pod.shotSerial!=serial) {
            serial=pod.shotSerial;shots.add(now)
            check(pod.clipRemaining==900-2*serial) { "Flame ammo incorrect: ${pod.clipRemaining}, serial=$serial" }
            if(serial==1)check(now-ignitionAt==20L) { "Flame ignition duration was ${now-ignitionAt} ticks" }
            enemy?.let { val dealt=previousTargetHp-it.health;if(dealt==1f || dealt==17f)sawDirectDamage=true;if(dealt==17f)sawBurnDamage=true;previousTargetHp=it.health }
            LOGGER.info("FLAME_JET: time={} serial={} ammo={} targetHp={} state={} ref={} removed={} nearby={}",now,serial,pod.clipRemaining,enemy?.health,pod.flameState,enemy?.id,enemy?.isRemoved,level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity::class.java,box).map { "${it.id}:${it.type}:${it.health}:${it.position()}:${it.isRemoved}" })
        }
        enemy?.let { if(!it.isRemoved && previousTargetHp-it.health==16f)sawBurnDamage=true;previousTargetHp=it.health }
        enemy?.let { sawBurn=sawBurn || it.hasEffect(ModEffects.BURNING.get());sawPanic=sawPanic || it.hasEffect(ModEffects.PANIC.get()) }
        sawGround=sawGround || level.getEntitiesOfClass(GroundFireEntity::class.java,box).isNotEmpty()
        if(serial>=12 && pauseAt<0) { enemy?.discard();pauseAt=now;LOGGER.info("FLAME_TARGET_GAP: time={}",now) }
        if(pauseAt>=0 && resumeAt<0 && now-pauseAt>=50) {
            check(pod.flameState==FlameClock.State.READY) { "Still spraying; target reference=${enemy?.id}/${enemy?.isRemoved}, nearby="+level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity::class.java,box).map { "${it.id}:${it.type}:${it.health}:${it.position()}" } };enemy=enemy(level);previousTargetHp=500f;resumeAt=now
            LOGGER.info("FLAME_TARGET_RESUMED: time={} phase={}",now,pod.flameState)
        }
        if(resumeAt>=0 && finalGapAt<0 && serial>=20) { enemy?.discard();finalGapAt=now;LOGGER.info("FLAME_FINAL_GAP: time={}",now) }
        if(finalGapAt>=0 && shutdownAt<0 && now-finalGapAt>=241) {
            check(pod.flameState==FlameClock.State.OFF) { "Flame did not extinguish after twelve seconds" }
            check(shots.zipWithNext().all { it.second-it.first>=5 })
            check(shots.first { it>resumeAt }-resumeAt<=6) { "Idle resumption repeated ignition" }
            check(sawBurn && sawPanic && sawGround && states.size==4) { "Missing flame states/effects: burn=$sawBurn panic=$sawPanic ground=$sawGround states=$states" }
            check(sawDirectDamage && sawBurnDamage) { "Actual client world did not receive exact 1 HP direct / 16 HP burn" }
            shutdownAt=now;LOGGER.info("FLAME_SERVER_PASSED: shots={} states={} burn={} panic={} ground={} hp={}",shots,states,sawBurn,sawPanic,sawGround,pod.health)
        }
        if(shutdownAt>=0 && now-shutdownAt==25L)pod.modifyGunData("Main") { it.ammo.set(0) }
        if(pod.phase==AbstractHellpodEntity.Phase.DONE && doneAt<0) { doneAt=now;LOGGER.info("FLAME_RETRACTION_DONE: time={}",now) }
        if(doneAt>=0 && !regressionSpawned) {
            regressionSpawned=true
            for((i,type) in listOf(ModEntities.MACHINE_GUN_SENTRY,ModEntities.GATLING_SENTRY,ModEntities.ROCKET_SENTRY,ModEntities.AUTOCANNON_SENTRY,ModEntities.MORTAR_SENTRY,ModEntities.GAS_MORTAR_SENTRY,ModEntities.EMS_MORTAR_SENTRY).withIndex()) {
                val sentry=type.get().create(level)!!;sentry.moveTo(852.5+(i%4)*10,257+10+sentry.profile.podHeight,818.5+(i/4)*14,0f,0f);level.addFreshEntity(sentry)
            }
            event.server.playerList.players.first().teleportTo(level,868.0,264.0,805.0,0f,20f)
        }
        finishRegression(level,now)
    }
    private fun finishRegression(level: net.minecraft.server.level.ServerLevel,now: Long) {
        if(doneAt>=0 && now-doneAt>=220 && !complete) {
            val others=level.getEntitiesOfClass(SentryHellpodEntity::class.java,box).filter { !it.profile.isFlame }
            check(others.size==7 && others.all { it.phase==AbstractHellpodEntity.Phase.ACTIVE }) { "Regression turrets did not deploy: "+others.map { "${it.profile.id}:${it.phase}/${it.phaseAge}" } }
            regressionReady=true;complete=true
        }
    }
    @SubscribeEvent fun client(event: TickEvent.ClientTickEvent) {
        if(!java.lang.Boolean.getBoolean("sbw_helldiver.flameSmokeTest") || event.phase!=TickEvent.Phase.END)return
        val mc=Minecraft.getInstance();val player=mc.player ?: return;val server=mc.singleplayerServer ?: return
        player.abilities.flying=true;player.deltaMovement=Vec3.ZERO
        if(!started) {
            started=true;mc.options.hideGui=true;mc.options.gamma().set(1.0)
            java.nio.file.Files.createDirectories(mc.gameDirectory.toPath().resolve("screenshots/$FOLDER"))
            server.execute {
                val level=server.overworld()
                // Load the entire test area before removing actors left by a previous run.
                for(cx in 48..56)for(cz in 48..56)level.getChunk(cx,cz)
                level.getEntitiesOfClass(net.minecraft.world.entity.Entity::class.java,box).filter { it !is net.minecraft.world.entity.player.Player }.forEach { it.discard() }
                level.dayTime=1000;level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server)
                for(x in 803..892)for(z in 803..884)level.setBlock(BlockPos(x,256,z),Blocks.BEDROCK.defaultBlockState(),2)
                val user=server.playerList.getPlayer(player.uuid)!!;user.setGameMode(GameType.CREATIVE);user.abilities.flying=true;user.onUpdateAbilities()
                user.teleportTo(level,828.0,260.0,816.0,42f,15f)
                // Exercise the actual beacon's chosen payload and marker-based pod call.
                val card=net.minecraft.world.item.ItemStack(ModItems.FLAME_SENTRY.get())
                val beacon=net.minecraft.world.item.ItemStack(ModItems.BEACON.get())
                com.example.sbwhelldiver.stratagem.BeaconData.setItems(beacon,listOf(card))
                com.example.sbwhelldiver.stratagem.BeaconData.activate(beacon,com.example.sbwhelldiver.stratagem.Stratagems.get("flame_sentry")!!)
                check(com.example.sbwhelldiver.stratagem.BeaconData.selected(beacon)?.id=="flame_sentry")
                user.inventory.add(beacon)
                val marker=ModEntities.STRATAGEM_MARKER.get().create(level)!!
                marker.moveTo(821.5,257.01,818.5,0f,0f);marker.configure(net.minecraft.resources.ResourceLocation(MOD_ID,"flame_sentry"),80,user.uuid,Direction.UP,257.0);level.addFreshEntity(marker)
                enemy=enemy(level);prepared=true;LOGGER.info("FLAME_TEST_SCENE: world=IntegrationTest marker={} target={} camera={}",marker.position(),enemy?.position(),user.position())
            }
        }
        ticks++
        val reg=regressionSpawned
        player.yRot=if(reg)0f else 42f;player.yRotO=player.yRot;player.xRot=if(reg)20f else 15f;player.xRotO=player.xRot
        val entities=mc.level!!.entitiesForRendering().toList()
        val pod=entities.filterIsInstance<SentryHellpodEntity>().firstOrNull { it.uuid==podId }
        fun capture(name: String) { if(captured.add(name))Screenshot.grab(mc.gameDirectory,"$FOLDER/$name.png",mc.mainRenderTarget) {} }
        if(pod!=null) {
            if(pod.phase==AbstractHellpodEntity.Phase.DEPLOYING)capture("01_空投展开")
            if(pod.flameState==FlameClock.State.IGNITING && FlameSentryClient.smokeSamples>=24)capture("02_点火白烟")
            if(pod.flameState==FlameClock.State.SPRAYING && pod.shotSerial>=6)capture("03_火焰射流")
            if(pod.flameState==FlameClock.State.READY && pauseAt>=0)capture("04_停止喷射保持点火")
            if(shutdownAt>=0 && pod.flameState==FlameClock.State.OFF)capture("05_十二秒后熄火")
            if(pod.phase==AbstractHellpodEntity.Phase.RETRACTING && pod.phaseAge>=8)capture("06_空投回收")
            if(pod.phase==AbstractHellpodEntity.Phase.DONE)capture("07_回收完成")
        }
        if(entities.any { it is GroundFireEntity })capture("地面持续燃烧")
        if(prepared && !reg && ticks%2==0)Screenshot.grab(mc.gameDirectory,"$FOLDER/帧_%04d.png".format(ticks/2),mc.mainRenderTarget) {}
        if(complete && regressionReady) {
            capture("08_现有七种炮台贴图回归")
            check(FlameSentryClient.smokeSamples>0 && FlameSentryClient.flameSamples>0 && FlameSentryClient.sootSamples>0)
            check(SoundTimeline.startedCount("sfx_flame_ignition_1")==1) { SoundTimeline.debugReport() }
            check((1..4).all { SoundTimeline.startedCount("sfx_flame_fire_start_$it")>=2 }) { SoundTimeline.debugReport() }
            check(SoundTimeline.startedCount("sfx_flame_fire_stop_1")>=2) { SoundTimeline.debugReport() }
            check(SoundTimeline.allocatedChannel("sfx_flame_ignition_1") && (1..4).all { SoundTimeline.allocatedChannel("sfx_flame_fire_start_$it") }) { "Original flame sounds did not receive real sound channels: "+SoundTimeline.debugReport() }
            check(captured.containsAll(listOf("01_空投展开","02_点火白烟","03_火焰射流","04_停止喷射保持点火","05_十二秒后熄火","06_空投回收","07_回收完成"))) { "Missing captured game states: $captured" }
            LOGGER.info("FLAME_CLIENT_COMPLETE: smoke={} flame={} captured={} audio={}",FlameSentryClient.smokeSamples,FlameSentryClient.flameSamples,captured,SoundTimeline.debugReport())
            mc.stop()
        }
        if(ticks>1500)error("Flame client test timed out: shots=$serial ignition=$ignitionAt gap=$finalGapAt shutdown=$shutdownAt done=$doneAt captured=$captured")
    }
}
