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
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.util.concurrent.ConcurrentHashMap

@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object StatusMortarClientSmokeTest {
    private var started=false
    private var tick=0
    private val shots=ConcurrentHashMap<String,MutableList<Long>>()
    private val observedFields=mutableSetOf<MortarFieldEntity.Kind>()
    private val pods=mutableSetOf<String>()
    private val poisonedExclusionChecked=java.util.concurrent.atomic.AtomicBoolean(false)
    private val serverEffects=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @SubscribeEvent fun tick(event: TickEvent.ClientTickEvent) {
        if(!java.lang.Boolean.getBoolean("sbw_helldiver.statusMortarSmokeTest") || event.phase!=TickEvent.Phase.END)return
        val mc=Minecraft.getInstance();val player=mc.player ?: return;val server=mc.singleplayerServer ?: return
        if(!started) {
            started=true;mc.options.hideGui=true
            java.nio.file.Files.createDirectories(mc.gameDirectory.toPath().resolve("screenshots/特殊迫击炮验收_0.3.12"))
            server.execute {
                val level=server.overworld()
                level.getEntitiesOfClass(net.minecraft.world.entity.Entity::class.java,AABB(600.0,250.0,600.0,690.0,350.0,715.0)).filter { it !is net.minecraft.world.entity.player.Player }.forEach { it.discard() }
                level.dayTime=1000
                for(x in 603..681)for(z in 603..710)level.setBlock(BlockPos(x,256,z),Blocks.BEDROCK.defaultBlockState(),2)
                val listener=java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> { e ->
                    val shell=e.entity as? MortarShellEntity;val gun=shell?.owner as? SentryHellpodEntity
                    if(e.level===level && shell!=null && gun!=null && (gun.profile.isGasMortar || gun.profile.isEmsMortar)) {
                        shots.computeIfAbsent(gun.profile.id) { java.util.Collections.synchronizedList(mutableListOf()) }.add(level.gameTime)
                        val saved=net.minecraft.nbt.CompoundTag();gun.saveWithoutId(saved)
                        val point=Vec3(saved.getDouble("MortarShotX"),saved.getDouble("MortarShotY"),saved.getDouble("MortarShotZ"))
                        LOGGER.info("STATUS_MORTAR_SHOT: type={} time={} velocity={} randomImpact={}",gun.profile.id,level.gameTime,shell.deltaMovement,point)
                        check(shell.position().distanceTo(gun.getShootPos("Main",1f))<1e-5)
                    }
                }
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener)
                for ((index,id) in listOf("gas_mortar_sentry","ems_mortar_sentry").withIndex()) {
                    val x=619.5+index*40.0
                    val marker=ModEntities.STRATAGEM_MARKER.get().create(level)!!
                    marker.moveTo(x,257.01,612.5,0f,0f)
                    marker.configure(net.minecraft.resources.ResourceLocation(MOD_ID,id),80,player.uuid,Direction.UP,257.0);level.addFreshEntity(marker)
                    val enemy=EntityType.HUSK.create(level)!!
                    enemy.isNoAi=true;enemy.isInvulnerable=true;enemy.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=2000.0;enemy.health=2000f
                    enemy.moveTo(x,257.0,632.5,0f,0f);level.addFreshEntity(enemy)
                    if(index==0)for(j in 1..3)EntityType.HUSK.create(level)!!.let {
                        it.isNoAi=true;it.isInvulnerable=true;it.setPos(x,257.0,632.5+j*17);level.addFreshEntity(it)
                    }
                    // Nearby ordinary units expose friendly status effects without affecting target selection.
                    for(i in -2..2) EntityType.COW.create(level)!!.let {
                        it.isNoAi=true;it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=2000.0;it.health=2000f
                        it.setPos(x+i*1.5,257.0,633.5);level.addFreshEntity(it)
                    }
                }
                server.playerList.getPlayer(player.uuid)!!.teleportTo(level,626.0,260.0,607.5,55f,38f)
            }
        }
        tick++
        player.yRot=55f;player.yRotO=55f;player.xRot=38f;player.xRotO=38f;
        mc.options.gamma().set(1.0)
        if(tick%100==0)LOGGER.info("STATUS_MORTAR_CAMERA: tick={} xyz={} yaw={} pitch={}",tick,player.position(),player.yRot,player.xRot)
        val entities=mc.level!!.entitiesForRendering().toList()
        entities.filterIsInstance<SentryHellpodEntity>().filter { it.profile.isGasMortar || it.profile.isEmsMortar }.forEach { if(it.phase==AbstractHellpodEntity.Phase.ACTIVE)pods.add(it.profile.id) }
        entities.filterIsInstance<MortarFieldEntity>().forEach { observedFields.add(it.kind) }
        if(tick==350)server.execute { server.playerList.getPlayer(player.uuid)!!.teleportTo(server.overworld(),666.0,261.5,608.0,55f,28f) }
        if(tick%20==0)server.execute {
            val level=server.overworld()
            level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity::class.java,AABB(600.0,250.0,600.0,690.0,270.0,715.0)).forEach {
                if(it.hasEffect(ModEffects.GAS.get())) {
                    serverEffects.add("gas")
                    level.getEntitiesOfClass(SentryHellpodEntity::class.java,AABB(600.0,250.0,600.0,690.0,350.0,715.0)).filter { pod -> pod.profile.isGasMortar }.forEach { pod ->
                        check(!pod.validTarget(it));poisonedExclusionChecked.set(true)
                    }
                }
                if(it.hasEffect(ModEffects.CONFUSION.get()))serverEffects.add("confusion")
                if(it.hasEffect(ModEffects.STUN.get()))serverEffects.add("stun")
            }
        }
        if(tick in 180..680 && tick%2==0) Screenshot.grab(mc.gameDirectory,"特殊迫击炮验收_0.3.12/帧_%04d.png".format((tick-180)/2),mc.mainRenderTarget) {}
        if(tick in listOf(220,290,330,430,520,590)) Screenshot.grab(mc.gameDirectory,"特殊迫击炮验收_0.3.12/视图_"+tick+".png",mc.mainRenderTarget) {}
        if(tick==690) {
            check(pods.size==2 && observedFields.size==2 && serverEffects.size==3) { "Missing rendered pods/fields/status effects: "+pods+observedFields+serverEffects }
            check(shots["gas_mortar_sentry"]?.size ?: 0 >= 4)
            check(poisonedExclusionChecked.get()) { "Poisoned target exclusion was not tested" }
            check(shots["ems_mortar_sentry"]?.size ?: 0 >= 3)
            for((id,times) in shots) {
                val minimum=if(id.startsWith("gas"))80L else 120L
                check(times.zipWithNext().all { it.second-it.first>=minimum }) { "Single shot global cooldown violated: "+id+times }
            }
            check(SoundTimeline.startedCount("sfx_mortar_deploy")==2) { SoundTimeline.debugReport() }
            check(MortarFieldAudio.startedCount("sfx_gas_mortar_impact_1") >= 3) { MortarFieldAudio.debugReport() }
            check((1..6).map { MortarFieldAudio.startedCount("sfx_gas_mortar_impact_$it") }.distinct().size == 1)
            check(MortarFieldAudio.startedCount("sfx_ems_mortar_impact_1") >= 3) { MortarFieldAudio.debugReport() }
            check((1..4).all { MortarFieldAudio.startedCount("sfx_ems_mortar_field_$it") >= 3 }) { MortarFieldAudio.debugReport() }
            check((1..6).all { MortarFieldAudio.allocatedChannel("sfx_gas_mortar_impact_$it") }) { MortarFieldAudio.debugReport() }
            check(MortarFieldAudio.allocatedChannel("sfx_gas_mortar_loop") && MortarFieldAudio.allocatedChannel("sfx_ems_mortar_impact_1")) { MortarFieldAudio.debugReport() }
            check((1..4).all { MortarFieldAudio.allocatedChannel("sfx_ems_mortar_field_$it") }) { MortarFieldAudio.debugReport() }
            LOGGER.info("STATUS_MORTAR_IMPACT_AUDIO_PASSED: {}", MortarFieldAudio.debugReport())
            LOGGER.info("STATUS_MORTAR_CLIENT_PASSED: pods={} fields={} effects={} shots={}",pods,observedFields,serverEffects,shots)
            server.execute { server.overworld().getEntitiesOfClass(SentryHellpodEntity::class.java,AABB(600.0,250.0,600.0,690.0,350.0,715.0)).forEach { it.modifyGunData("Main") { g -> g.ammo.set(0) } } }
        }
        if(tick==740) server.execute {
            server.overworld().getEntitiesOfClass(MortarFieldEntity::class.java,AABB(600.0,250.0,600.0,690.0,350.0,715.0)).forEach { it.discard() }
        }
        if(tick==800) {
            check(MortarFieldAudio.gasFadeCompletions > 0 && MortarFieldAudio.activeGasSounds == 0) { MortarFieldAudio.debugReport() }
            LOGGER.info("STATUS_MORTAR_IMPACT_AUDIO_STOP_PASSED: {}", MortarFieldAudio.debugReport())
            check(SoundTimeline.startedCount("sfx_mortar_retract")==2)
            LOGGER.info("STATUS_MORTAR_CLIENT_COMPLETE: deployment, single-shot firing, original sound layers, gas/EMS fields, three debuffs and recovery rendered")
            mc.stop()
        }
    }
}
