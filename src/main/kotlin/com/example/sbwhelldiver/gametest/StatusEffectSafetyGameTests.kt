package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.effect.StatusEffectEvents
import com.example.sbwhelldiver.init.ModEffects
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModSounds
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.sounds.SoundSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.projectile.Snowball
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.PlayLevelSoundEvent
import net.minecraftforge.event.entity.living.LivingEvent
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.util.function.Consumer

@GameTestHolder("sbw_helldiver_status")
@PrefixGameTestTemplate(false)
object StatusEffectSafetyGameTests {
    private fun origin(h: GameTestHelper) = Vec3.atCenterOf(h.absolutePos(BlockPos(15, 200, 15)))
    private fun <T : Mob> spawn(h: GameTestHelper, type: EntityType<T>, at: Vec3): T = type.create(h.level)!!.also {
        it.isNoAi = true
        it.isNoGravity = true
        it.setPos(at)
        h.level.addFreshEntity(it)
    }
    private fun attackOnce(mob: Mob) {
        StatusEffectEvents.tick(LivingEvent.LivingTickEvent(mob))
        val goal = mob.goalSelector.availableGoals.single { it.goal.javaClass.simpleName == "ConfusedGoal" }.goal
        check(goal.canUse()) { "Confused goal was not usable" }
        mob.tickCount = 20
        goal.tick()
    }
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun passiveMobsWithoutAttackDamageSurviveConfusionAndPanic(h: GameTestHelper) {
        val entities = mutableListOf<Mob>()
        try {
            for ((i, type) in listOf(EntityType.VILLAGER, EntityType.COW).withIndex()) {
                val at = origin(h).add(i * 30.0, 0.0, 0.0)
                val passive = spawn(h, type, at); entities.add(passive)
                val victim = spawn(h, EntityType.COW, at.add(1.0, 0.0, 0.0)); entities.add(victim)
                h.assertTrue(passive.getAttribute(Attributes.ATTACK_DAMAGE) == null, "Fixture already has attack damage")
                for (effect in listOf(ModEffects.CONFUSION.get(), ModEffects.PANIC.get())) {
                    passive.addEffect(MobEffectInstance(effect, 100))
                    attackOnce(passive)
                    h.assertTrue(victim.health == victim.maxHealth && passive.target == null, "Passive confusion dealt unsupported melee damage")
                    h.assertTrue(passive.getAttribute(Attributes.ATTACK_DAMAGE) == null, "Confusion invented an attack attribute")
                    passive.removeEffect(effect)
                    StatusEffectEvents.tick(LivingEvent.LivingTickEvent(passive))
                    h.assertTrue(passive.goalSelector.availableGoals.none { it.goal.javaClass.simpleName == "ConfusedGoal" }, "Expired confusion left its goal installed")
                }
            }
        } finally { entities.forEach { it.discard() } }
        h.succeed()
    }
    // Confusion deliberately chooses a random nearby living entity; keep other
    // fixtures out of this batch so the asserted cow is its only possible victim.
    @JvmStatic @GameTest(template = "sentry_test", batch = "status_confused_melee", timeoutTicks = 10)
    fun confusedMeleeStillAttacks(h: GameTestHelper) {
        val mob = spawn(h, EntityType.ZOMBIE, origin(h))
        val victim = spawn(h, EntityType.COW, origin(h).add(1.0, 0.0, 0.0))
        try {
            mob.addEffect(MobEffectInstance(ModEffects.CONFUSION.get(), 100))
            attackOnce(mob)
            h.assertTrue(victim.health < victim.maxHealth && mob.target == null, "Attack guard disabled valid confused melee")
        } finally { mob.discard(); victim.discard() }
        h.succeed()
    }
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun confusedRangedMobWithoutAttackDamageStillFires(h: GameTestHelper) {
        val at = origin(h)
        val mob = spawn(h, EntityType.SNOW_GOLEM, at)
        val victim = spawn(h, EntityType.COW, at.add(1.0, 0.0, 0.0))
        try {
            h.assertTrue(mob.getAttribute(Attributes.ATTACK_DAMAGE) == null, "Snow golem fixture has melee damage")
            mob.addEffect(MobEffectInstance(ModEffects.CONFUSION.get(), 100))
            attackOnce(mob)
            val shots = h.level.getEntitiesOfClass(Snowball::class.java, mob.boundingBox.inflate(4.0)) { it.owner === mob }
            h.assertTrue(shots.size == 1 && shots.single().persistentData.getBoolean("HelldiverConfusedAttack"), "Attribute guard disabled legitimate confused ranged attack")
        } finally {
            h.level.getEntitiesOfClass(Snowball::class.java, mob.boundingBox.inflate(4.0)) { it.owner === mob }.forEach { it.discard() }
            mob.discard(); victim.discard()
        }
        h.succeed()
    }
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun heavyBombCuesUseLongRangeWithoutReloadDuplicates(h: GameTestHelper) {
        val bomb = ModEntities.EAGLE_500KG_BOMB.get().create(h.level)!!
        val at = origin(h); bomb.setPos(at)
        val counts = mutableMapOf<String, Int>()
        val keys = listOf("sfx_eagle_500kg_impact", "sfx_eagle_500kg_fuse")
        val listener = Consumer<PlayLevelSoundEvent.AtPosition> { event ->
            if (event.level === h.level && event.position.distanceToSqr(at) < .0001) {
                val key = keys.firstOrNull { ModSounds.EVENTS.getValue(it).get() === event.sound?.value() }
                if (key != null) {
                    counts[key] = (counts[key] ?: 0) + 1
                    h.assertTrue(event.source == SoundSource.PLAYERS && event.sound!!.value().getRange(1f) == 128f,
                        "500kg cue still broadcasts only to nearby players: $key")
                }
            }
        }
        MinecraftForge.EVENT_BUS.addListener(listener)
        try {
            bomb.afterHitBlock(BlockHitResult(at, Direction.UP, BlockPos.containing(at), false))
            val tag = CompoundTag(); bomb.saveWithoutId(tag)
            tag.putLong("ClickAt", h.level.gameTime); tag.putLong("DetonateAt", h.level.gameTime + 100)
            bomb.load(tag); bomb.tick(); bomb.tick()
            bomb.saveWithoutId(tag); bomb.load(tag); bomb.tick()
            h.assertTrue(keys.all { counts[it] == 1 }, "Impact/fuse cue was lost or replayed after loading: $counts")
            val json = StatusEffectSafetyGameTests::class.java.getResourceAsStream("/assets/sbw_helldiver/sounds.json")!!.bufferedReader().use {
                com.google.gson.JsonParser.parseReader(it).asJsonObject
            }
            for (key in keys) h.assertTrue(json.getAsJsonObject(key).getAsJsonArray("sounds").all {
                it.asJsonObject.get("attenuation_distance").asInt == 128
            }, "Client falloff disagrees with server broadcast: $key")
        } finally { MinecraftForge.EVENT_BUS.unregister(listener); bomb.discard() }
        h.succeed()
    }
}
