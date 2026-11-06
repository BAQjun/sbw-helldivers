package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.CombatHostility
import com.example.sbwhelldiver.init.ModEntities
import com.mojang.authlib.GameProfile
import java.util.UUID
import java.util.function.Predicate
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.util.FakePlayer
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate

@GameTestHolder("sbw_helldiver")
@PrefixGameTestTemplate(false)
object CombatHostilityGameTests {
    @JvmStatic
    @GameTest(template = "port_test")
    fun checksTheOwnersCurrentState(h: GameTestHelper) {
        val owner = h.makeMockPlayer(GameType.SURVIVAL)
        val other = h.makeMockPlayer(GameType.SURVIVAL)
        val zombie = EntityType.ZOMBIE.create(h.level)!!
        val wolf = EntityType.WOLF.create(h.level)!!
        val cow = EntityType.COW.create(h.level)!!
        h.assertTrue(CombatHostility.isHostileTo(zombie, owner), "Zombie player-targeting goal was missed")
        h.assertTrue(!CombatHostility.isHostileTo(wolf, owner), "Calm wolf was treated as hostile")
        wolf.remainingPersistentAngerTime = 100
        wolf.persistentAngerTarget = other.uuid
        h.assertTrue(!CombatHostility.isHostileTo(wolf, owner), "Wolf was angry at a different player")
        wolf.persistentAngerTarget = owner.uuid
        h.assertTrue(CombatHostility.isHostileTo(wolf, owner), "Wolf angry at its owner was missed")
        wolf.stopBeingAngry()
        h.assertTrue(!CombatHostility.isHostileTo(wolf, owner), "Calmed wolf remained hostile")

        cow.targetSelector.addGoal(1, NearestAttackableTargetGoal(cow, Player::class.java, false) { it === other })
        h.assertTrue(!CombatHostility.isHostileTo(cow, owner), "Custom targeting predicate rejected this player")
        h.assertTrue(CombatHostility.isHostileTo(cow, other), "Custom targeting predicate accepted this player")
        cow.targetSelector.availableGoals.map { it.goal }.forEach(cow.targetSelector::removeGoal)
        var enabled = true
        cow.targetSelector.addGoal(1, CustomTargetGoal(Player::class.java, Predicate { enabled && it === owner }))
        h.assertTrue(cow.target == null, "Custom AI already selected a target")
        h.assertTrue(CombatHostility.isHostileTo(cow, owner), "Custom goal did not identify an eligible player before selection")
        h.assertTrue(!CombatHostility.isHostileTo(cow, other), "Custom goal accepted the wrong player")
        enabled = false
        h.assertTrue(!CombatHostility.isHostileTo(cow, owner), "Custom goal's current predicate was ignored")

        val piglin = EntityType.PIGLIN.create(h.level)!!
        // Keep only the vanilla Brain behavior here; the optional faction mod adds its own goals.
        piglin.targetSelector.availableGoals.map { it.goal }.forEach(piglin.targetSelector::removeGoal)
        h.assertTrue(CombatHostility.isHostileTo(piglin, owner), "Piglin without gold on the player was missed")
        owner.setItemSlot(EquipmentSlot.HEAD, ItemStack(Items.GOLDEN_HELMET))
        h.assertTrue(CombatHostility.isHostileTo(piglin, owner), "The legacy enemy rule must still recognize piglins when the player wears gold")
        owner.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY)

        cow.target = owner
        h.assertTrue(CombatHostility.isHostileTo(cow, owner), "Mob's active combat target was missed")
        val team = h.level.scoreboard.addPlayerTeam("hostility_test")
        try {
            h.level.scoreboard.addPlayerToTeam(owner.scoreboardName, team)
            h.level.scoreboard.addPlayerToTeam(cow.scoreboardName, team)
            h.assertTrue(!CombatHostility.isHostileTo(cow, owner), "Allied mob was treated as hostile")
        } finally {
            h.level.scoreboard.removePlayerTeam(team)
        }
        h.succeed()
    }

    private class CustomTargetGoal(
        private val targetType: Class<out LivingEntity>,
        private val targetPredicate: Predicate<LivingEntity>
    ) : Goal() {
        init {
            flags = java.util.EnumSet.of(Flag.TARGET)
        }

        override fun canUse(): Boolean = error("Hostility probe ran the custom AI")
    }

    @JvmStatic
    @GameTest(template = "port_test", timeoutTicks = 240)
    fun detectsRealFactionAi(h: GameTestHelper) {
        val type = BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.fromNamespaceAndPath("hundred_years_war", "bandit_soldier"))
        if (type.isEmpty) {
            h.succeed()
            return
        }
        val center = h.absolutePos(BlockPos(3, 8, 3))
        for (floor in BlockPos.betweenClosed(center.offset(-3, -1, -3), center.offset(14, -1, 3))) {
            h.level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState())
        }
        val owner = FakePlayer(h.level, GameProfile(UUID.randomUUID(), "FactionTest"))
        owner.setGameMode(GameType.SURVIVAL)
        owner.setPos(Vec3.atBottomCenterOf(center))
        h.level.addNewPlayer(owner)
        val bandit = type.get().create(h.level) as Mob
        bandit.setPos(Vec3.atBottomCenterOf(center.offset(11, 0, 0)))
        bandit.finalizeSpawn(h.level, h.level.getCurrentDifficultyAt(bandit.blockPosition()), MobSpawnType.COMMAND, null)
        h.level.addFreshEntity(bandit)

        try {
            h.assertTrue(bandit.target == null, "Bandit had a target before the probe")
            h.assertTrue(CombatHostility.isHostileTo(bandit, owner), "Bandit should be eligible before the AI acquires the player")
            h.assertTrue(bandit.target == null, "The probe changed the bandit's target")

            val strategy = bandit.javaClass.getMethod("getAttackStrategy").invoke(bandit)
            val changeStrategy = bandit.javaClass.getMethod("setAttackStrategy", (strategy as Enum<*>).declaringJavaClass)
            val ceaseFire = strategy.declaringJavaClass.enumConstants.first { it.name == "CEASE_FIRE" }
            changeStrategy.invoke(bandit, ceaseFire)
            h.assertTrue(!CombatHostility.isHostileTo(bandit, owner), "Cease-fire order was ignored")
            changeStrategy.invoke(bandit, strategy)
            h.assertTrue(CombatHostility.isHostileTo(bandit, owner), "Restoring the attack order did not restore eligibility")

            owner.setGameMode(GameType.CREATIVE)
            h.assertTrue(!bandit.canAttack(owner), "Creative must still be immune to ordinary mob targeting")
            h.assertTrue(CombatHostility.isHostileTo(bandit, owner), "Sentry should see the hostile faction in Creative")
            changeStrategy.invoke(bandit, ceaseFire)
            h.assertTrue(!CombatHostility.isHostileTo(bandit, owner), "Creative mode overrode the faction's cease-fire")
            changeStrategy.invoke(bandit, strategy)
            com.example.sbwhelldiver.LOGGER.info("HUNDRED_YEARS_WARFARE_PROACTIVE_TARGET_PASSED entity={}", bandit.type)
        } catch (e: Exception) {
            bandit.discard()
            owner.discard()
            throw e
        }

        // The wall blocks the bandit's view of the owner, while the sentry can see the bandit.
        for (wall in BlockPos.betweenClosed(center.offset(4, 0, -3), center.offset(4, 3, 3))) {
            h.level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState())
        }
        val sentry = ModEntities.MACHINE_GUN_SENTRY.get().create(h.level)!!
        sentry.setPos(Vec3.atBottomCenterOf(center.offset(5, 0, 0)))
        val state = CompoundTag()
        sentry.saveWithoutId(state)
        state.putInt("HellpodPhase", AbstractHellpodEntity.Phase.DEPLOYING.ordinal)
        state.putInt("HellpodPhaseTicks", sentry.deployTicks - 1)
        state.putBoolean("HellpodCapReleased", true)
        state.putDouble("HellpodRestY", sentry.y)
        sentry.load(state)
        sentry.setOwnerUUID(owner.uuid)
        h.level.addFreshEntity(sentry)
        h.assertTrue(sentry.owner === owner, "Sentry could not resolve the test player")

        h.runAfterDelay(200) { sentry.discard(); bandit.discard(); owner.discard() }
        h.succeedWhen {
            h.assertTrue(sentry.shotSerial > 0, "Sentry did not fire at the hostile faction unit")
            h.assertTrue(bandit.target !== owner, "Bandit acquired the owner before the sentry opened fire")
            com.example.sbwhelldiver.LOGGER.info("HUNDRED_YEARS_WARFARE_SENTRY_FIRED shots={}", sentry.shotSerial)
            sentry.discard()
            bandit.discard()
            owner.discard()
        }
    }
}
