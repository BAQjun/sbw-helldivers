package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.config.*
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.network.AutoRetractionSettings
import com.example.sbwhelldiver.client.HudSlide
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.*
import java.util.UUID

@GameTestHolder("sbw_helldiver_config")
@PrefixGameTestTemplate(false)
object AutoRetractionGameTests {
    @JvmStatic @GameTest(templateNamespace="sbw_helldiver_config",template="sentry_test",timeoutTicks=10)
    fun disabledRetractionKeepsSentriesAndManualEmplacementsDeployed(h:GameTestHelper) {
        val snapshot=ConfigTestSnapshot()
        val previous=HelldiversConfig.autoRetract.get()
        val actors=mutableListOf<Entity>()
        val types=listOf(ModEntities.MACHINE_GUN_SENTRY.get(),ModEntities.GATLING_SENTRY.get(),ModEntities.FLAME_SENTRY.get(),ModEntities.LASER_SENTRY.get(),ModEntities.TESLA_TOWER.get(),ModEntities.SHIELD_GENERATOR.get(),ModEntities.MORTAR_SENTRY.get(),ModEntities.GAS_MORTAR_SENTRY.get(),ModEntities.EMS_MORTAR_SENTRY.get(),ModEntities.ROCKET_SENTRY.get(),ModEntities.AUTOCANNON_SENTRY.get(),ModEntities.HMG_EMPLACEMENT.get(),ModEntities.ANTI_TANK_EMPLACEMENT.get(),ModEntities.GRENADIER_BATTLEMENT.get())
        val limits=types.mapNotNull{TurretConfigs.forType(it)}.distinct().map{it.lifetimeTicks to it.lifetimeTicks.get()}
        try {
            limits.forEach{it.first.set(1)}
            for((i,type) in types.withIndex()) {
                val pod=type.create(h.level) as AbstractHellpodEntity;actors.add(pod)
                val at=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(10+i*4,150,10)))
                h.level.getChunk(BlockPos.containing(at));pod.setPos(at)
                val n=CompoundTag();pod.saveWithoutId(n)
                n.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal)
                n.putInt("HellpodActiveTicks",5000);n.putDouble("HellpodRestY",at.y)
                n.putInt("HmgAmmo",0);pod.load(n);h.level.addFreshEntity(pod)
                HelldiversConfig.autoRetract.set(false);pod.tick()
                h.assertTrue(pod.phase==AbstractHellpodEntity.Phase.ACTIVE,"Disabled retraction ignored by $type")
                HelldiversConfig.autoRetract.set(true);pod.tick()
                h.assertTrue(pod.phase==AbstractHellpodEntity.Phase.RETRACTING,"Re-enabling did not resume retraction for $type")
                pod.discard()
            }
            HelldiversConfig.autoRetract.set(false)
            val transport=ModEntities.PLAYER_HELLPOD.get().create(h.level)!!;actors.add(transport)
            val at=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(10,150,10)));transport.setPos(at)
            val n=CompoundTag();transport.saveWithoutId(n);n.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal);n.putInt("HellpodActiveTicks",80);n.putDouble("HellpodRestY",at.y);n.putBoolean("DropReleased",true)
            transport.load(n);h.level.addFreshEntity(transport);transport.tick()
            h.assertTrue(transport.phase==AbstractHellpodEntity.Phase.RETRACTING,"Stratagem option prevented player transport cleanup")
        } finally {actors.forEach{it.discard()};limits.forEach{it.first.set(it.second)};HelldiversConfig.autoRetract.set(previous);snapshot.close()}
        h.succeed()
    }
    @JvmStatic @GameTest(templateNamespace="sbw_helldiver_config",template="sentry_test",timeoutTicks=5,batch="retraction_permission")
    fun ordinaryPlayersCannotWriteWorldRetractionSetting(h:GameTestHelper) {
        val player=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"RetractionTest"))
        val original=HelldiversConfig.autoRetract.get()
        h.assertTrue(!AutoRetractionSettings.update(player,!original)&&HelldiversConfig.autoRetract.get()==original,"Ordinary client changed the server setting")
        player.discard();h.succeed()
    }
    @JvmStatic @GameTest(templateNamespace="sbw_helldiver_config",template="sentry_test",timeoutTicks=5,batch="hud_motion")
    fun automaticHudMotionIsContinuousAndReversible(h:GameTestHelper) {
        val slide=HudSlide();val t=1_000_000_000L
        slide.target(true,t)
        h.assertTrue(slide.value(t)==0.0 && slide.value(t+180_000_000)==1.0,"HUD transition lacks stable endpoints")
        val middle=slide.value(t+70_000_000)
        slide.target(false,t+70_000_000)
        h.assertTrue(slide.value(t+70_000_000)==middle && slide.value(t+100_000_000)<middle,"HUD jumps when interrupted")
        val exit=slide.value(t+100_000_000);slide.target(true,t+100_000_000)
        h.assertTrue(slide.value(t+100_000_000)==exit && slide.value(t+280_000_000)==1.0,"HUD re-entry jumps or never completes")
        slide.target(false,t+300_000_000)
        h.assertTrue(slide.value(t+480_000_000)==0.0,"HUD exit never finishes")
        h.succeed()
    }
}
