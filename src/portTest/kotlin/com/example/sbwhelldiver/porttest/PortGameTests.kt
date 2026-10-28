package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.config.HelldiversConfig
import com.example.sbwhelldiver.entity.SentryExplosion
import com.example.sbwhelldiver.effect.StatusEffectEvents
import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.marking.MarkSettings
import com.example.sbwhelldiver.stratagem.*
import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile
import com.atsuishio.superbwarfare.config.server.ExplosionConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.*
import net.neoforged.neoforge.event.tick.EntityTickEvent

@GameTestHolder("sbw_helldiver")
@PrefixGameTestTemplate(false)
object PortGameTests {
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun beaconComponentsPersistWithoutSharingCopies(h:GameTestHelper) {
        val beacon=ItemStack(ModItems.BEACON.get())
        val ids=listOf("machine_gun_sentry","orbital_laser","eagle_airstrike","gas_minefield")
        BeaconData.setItems(beacon,ids.map { ItemStack(BuiltInRegistries.ITEM.get(Stratagems.get(it)!!.itemId)) })
        val identity=BeaconData.ensureIdentity(beacon)
        BeaconData.activate(beacon,Stratagems.get(ids[0])!!)
        val copy=beacon.copy();BeaconData.clear(copy)
        h.assertTrue(BeaconData.selected(beacon)?.id==ids[0]&&!BeaconData.hasSelection(copy),"Beacon components were shared across copies")
        val restored=ItemStack.parseOptional(h.level.registryAccess(),beacon.save(h.level.registryAccess()) as CompoundTag)
        h.assertTrue(BeaconData.identity(restored)==identity&&BeaconData.loadout(restored).map{it?.id}==ids,"Beacon loadout or identity lost during persistence")
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=30)
    fun allEntitiesConstructAndTick(h:GameTestHelper) {
        val spawned=ModEntities.ENTITIES.entries.mapIndexed { index, entry ->
            entry.get().create(h.level)!!.also { it.setPos(Vec3.atBottomCenterOf(h.absolutePos(BlockPos(8+index%8*5,160,8+index/8*5))));h.level.addFreshEntity(it) }
        }
        h.runAfterDelay(3) { spawned.forEach{it.discard()};h.succeed() }
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=30)
    fun explosionsRespectIndependentTerrainSetting(h:GameTestHelper) {
        val own=HelldiversConfig.stratagemExplosionBlockDamage.get();val sbw=ExplosionConfig.EXPLOSION_DESTROY.get()
        try {
            ExplosionConfig.EXPLOSION_DESTROY.set(false)
            val at=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(40,180,40)));val block=BlockPos.containing(at)
            h.level.getChunk(block)
            val types=listOf(ModEntities.ORBITAL_SHELL.get(),ModEntities.ORBITAL_120_SHELL.get(),ModEntities.ORBITAL_NAPALM_SHELL.get(),ModEntities.EAGLE_BOMB.get(),ModEntities.EAGLE_110MM_ROCKET.get(),ModEntities.SENTRY_ROCKET.get(),ModEntities.MORTAR_SHELL.get())
            for(enabled in listOf(false,true)) for(type in types) {
                HelldiversConfig.stratagemExplosionBlockDamage.set(enabled)
                h.level.setBlock(block,Blocks.DIRT.defaultBlockState(),3)
                val victim=EntityType.RAVAGER.create(h.level)!!;victim.setPos(at.add(1.5,0.0,0.0));victim.isNoAi=true;victim.setNoGravity(true);h.level.addFreshEntity(victim)
                val shell=type.create(h.level) as FastThrowableProjectile;shell.setPos(at);shell.setExplosionDamage(60f);shell.setExplosionRadius(3f)
                try { val before=victim.health;SentryExplosion.explode(shell,at,2.0,true)
                    h.assertTrue(victim.health<before,"Explosion damage failed for $type")
                    h.assertTrue(h.level.getBlockState(block).isAir==enabled,"Independent terrain setting failed for $type with enabled=$enabled")
                } finally { shell.discard();victim.discard() }
            }
        } finally { HelldiversConfig.stratagemExplosionBlockDamage.set(own);ExplosionConfig.EXPLOSION_DESTROY.set(sbw) }
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun confusedVillagersDoNotRequireAttackDamage(h:GameTestHelper) {
        val mob=EntityType.VILLAGER.create(h.level)!!;mob.setPos(Vec3.atBottomCenterOf(h.absolutePos(BlockPos(5,180,5))));h.level.addFreshEntity(mob)
        try {
            h.assertTrue(mob.getAttribute(Attributes.ATTACK_DAMAGE)==null,"Villager unexpectedly has attack damage")
            mob.addEffect(MobEffectInstance(ModEffects.CONFUSION,100));StatusEffectEvents.tick(EntityTickEvent.Post(mob));mob.tickCount=20
            mob.goalSelector.availableGoals.first{it.goal.javaClass.simpleName=="ConfusedGoal"}.goal.tick()
        } finally { mob.discard() }
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun ordinaryAndHeavyBurningStack(h:GameTestHelper) {
        val mob=EntityType.RAVAGER.create(h.level)!!;mob.setPos(Vec3.atBottomCenterOf(h.absolutePos(BlockPos(5,180,5))));mob.isNoAi=true;h.level.addFreshEntity(mob)
        try { val before=mob.health;mob.tickCount=10
            ModEffects.BURNING.get().applyEffectTick(mob,0);ModEffects.HEAVY_BURNING.get().applyEffectTick(mob,0)
            h.assertTrue(kotlin.math.abs(before-mob.health-30f)<.01f,"Burning damage no longer stacks to 30 per half second")
        } finally { mob.discard() }
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun registryAwareWorldSettingsSerialize(h:GameTestHelper) {
        val level=h.level
        h.assertTrue(MarkSettings.get(level.server).save(CompoundTag(),level.registryAccess()).getBoolean("Enabled"),"Marking default lost")
        h.assertTrue(CooldownSavedData.get(level.server).save(CompoundTag(),level.registryAccess()).contains("Players"),"Cooldown world persistence failed")
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun playerImmunityAndStunnedMobAiRestore(h:GameTestHelper) {
        val player=h.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL)
        h.assertTrue(!player.addEffect(MobEffectInstance(ModEffects.CONFUSION,100)),"Confusion applied to player")
        h.assertTrue(!player.addEffect(MobEffectInstance(ModEffects.PANIC,100)),"Panic applied to player")
        for(originalNoAi in listOf(false,true)) {
            val mob=EntityType.ZOMBIE.create(h.level)!!
            mob.isNoAi=originalNoAi
            mob.addEffect(MobEffectInstance(ModEffects.STUN,100))
            StatusEffectEvents.tick(EntityTickEvent.Post(mob))
            h.assertTrue(mob.isNoAi,"Stun did not suspend mob AI")
            mob.removeEffect(ModEffects.STUN)
            StatusEffectEvents.tick(EntityTickEvent.Post(mob))
            h.assertTrue(mob.isNoAi==originalNoAi,"Stun did not restore the original mob AI state")
            mob.discard()
        }
        h.succeed()
    }
}
