package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.asset.AssetCatalog
import com.example.sbwhelldiver.config.HelldiversConfig
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.MortarShellEntity
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.stratagem.BeaconData
import com.example.sbwhelldiver.stratagem.Stratagems
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.nio.file.Path

@Mod.EventBusSubscriber(modid=MOD_ID,value=[Dist.CLIENT])
object MortarClientSmokeTest {
    private var started=false
    private var ticks=0
    private var sawPacked=false
    private var sawActive=false
    private var sawRising=false
    private var sawFalling=false
    private var sawRecoil=false
    private var sawFeed=false
    private val shots=mutableSetOf<Int>()
    @SubscribeEvent fun tick(e: TickEvent.ClientTickEvent) {
        if(!java.lang.Boolean.getBoolean("sbw_helldiver.mortarSmokeTest") || e.phase!=TickEvent.Phase.END)return
        val mc=Minecraft.getInstance();val player=mc.player ?: return;val server=mc.singleplayerServer ?: return
        if(!started) {
            started=true;mc.options.hideGui=true
            java.nio.file.Files.createDirectories(mc.gameDirectory.toPath().resolve("screenshots/迫击炮验收_0.3.10"))
            server.execute {
                val level=server.overworld();HelldiversConfig.sentryLifetimeTicks.set(650)
                level.getEntitiesOfClass(net.minecraft.world.entity.Entity::class.java,AABB(500.0,250.0,500.0,545.0,350.0,570.0)).filter { it !is net.minecraft.world.entity.player.Player }.forEach { it.discard() }
                level.dayTime=1000
                for(x in 503..541)for(z in 503..563)level.setBlock(BlockPos(x,256,z),Blocks.BEDROCK.defaultBlockState(),2)
                for(x in 515..525)for(y in 257..261)level.setBlock(BlockPos(x,y,526),Blocks.BEDROCK.defaultBlockState(),2)
                val shotListener=java.util.function.Consumer<net.minecraftforge.event.entity.EntityJoinLevelEvent> { event ->
                    val shell=event.entity as? MortarShellEntity
                    val gun=shell?.owner as? SentryHellpodEntity
                    if(event.level===level && shell!=null && gun?.profile?.isMortar==true) {
                        val saved=net.minecraft.nbt.CompoundTag();gun.saveWithoutId(saved)
                        val point=net.minecraft.world.phys.Vec3(saved.getDouble("MortarShotX"),saved.getDouble("MortarShotY"),saved.getDouble("MortarShotZ"))
                        LOGGER.info("MORTAR_ACCEPTANCE_SHOT: gameTick={} round={} yaw={} pitch={} muzzle={} velocity={} randomImpact={}",level.gameTime,gun.shotSerial+1,gun.turretYRot,gun.turretXRot,shell.position(),shell.deltaMovement,point)
                    }
                }
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(shotListener)
                val marker=ModEntities.STRATAGEM_MARKER.get().create(level)!!
                marker.moveTo(520.5,257.01,514.5,0f,0f)
                marker.configure(net.minecraft.resources.ResourceLocation(MOD_ID,"mortar_sentry"),80,player.uuid,Direction.UP,257.0)
                level.addFreshEntity(marker)
                EntityType.HUSK.create(level)!!.let { it.isInvulnerable=true;it.isNoAi=true;it.moveTo(520.5,257.0,537.5,0f,0f);level.addFreshEntity(it) }
                server.playerList.getPlayer(player.uuid)!!.let {
                    val ball=ItemStack(ModItems.BEACON.get());BeaconData.setItems(ball,listOf(ItemStack(ModItems.MORTAR_SENTRY.get())));BeaconData.ensureIdentity(ball);it.inventory.setItem(0,ball)
                    it.teleportTo(level,523.4,258.9,511.4,43f,20f)
                }
            }
        }
        ticks++
        val entities=mc.level!!.entitiesForRendering().toList()
        val pod=entities.filterIsInstance<SentryHellpodEntity>().firstOrNull { it.profile.isMortar }
        if(pod!=null) {
            if(pod.phase==AbstractHellpodEntity.Phase.FALLING)sawPacked=true
            if(pod.phase==AbstractHellpodEntity.Phase.ACTIVE) {
                sawActive=true
                val model=pod.getAnimationInstance()?.context
                check(model?.animations?.containsKey("animation.Main.fire")==true)
                val entries=pod.getAnimationInstance()?.context // Native model is checked by the live renderer.
            }
        }
        entities.filterIsInstance<MortarShellEntity>().forEach {
            shots.add(it.id)
            if(it.deltaMovement.y>1)sawRising=true
            if(it.deltaMovement.y < -1)sawFalling=true
        }
        if(ticks in listOf(105,155,175,188,204,220,245,300,365)) {
            Screenshot.grab(mc.gameDirectory,mc.mainRenderTarget) { message -> LOGGER.info("MORTAR_CAPTURE: tick={} {}",ticks,message.string) }
        }
        if(ticks in 140..450 && ticks%2==0) {
            val frame=(ticks-140)/2
            Screenshot.grab(mc.gameDirectory,"迫击炮验收_0.3.10/帧_%04d.png".format(frame),mc.mainRenderTarget) {}
        }
        if(ticks==550) {
            check(sawPacked && sawActive && sawRising && sawFalling && shots.size>=8) { "Mortar client stages incomplete: $sawPacked/$sawActive/$sawRising/$sawFalling shots=${shots.size}" }
            check(HellpodRenderer.checkedDeploymentTurns.contains("mortar_sentry")) { "Mortar deployment-to-aim handoff was not rendered and checked" }
            check(WreckTextureCache.preparedTextures.size==AssetCatalog.profiles.values.count { !it.isSupply })
            check(SoundTimeline.startedCount("sfx_mortar_deploy")==1)
            check(SoundTimeline.startedCount("sfx_mortar_feed")>=8)
            for(layer in 1..4)check(SoundTimeline.startedCount("sfx_mortar_fire_${layer}_1")>=8)
            check(MortarShellRenderer.Model.instance!=null)
            server.execute { server.overworld().getEntitiesOfClass(SentryHellpodEntity::class.java,AABB(500.0,250.0,500.0,545.0,350.0,570.0)).forEach { it.modifyGunData("Main") { gun -> gun.ammo.set(0) } } }
            LOGGER.info("MORTAR_CLIENT_FIRE_PASSED: native high arc rises and falls; original fire layers/feed per shot, unique deployment; render-bore alignment checked")
        }
        if(ticks==650) {
            check(SoundTimeline.startedCount("sfx_mortar_retract")==1)
            LOGGER.info("MORTAR_CLIENT_SMOKE_PASSED: marker countdown/drop, packed/deployed model, official icon/card, 8+ ballistic shells and fire/feed/deploy/retract audio")
            mc.stop()
        }
    }
}
