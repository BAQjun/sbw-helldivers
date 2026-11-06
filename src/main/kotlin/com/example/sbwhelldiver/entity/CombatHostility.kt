package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.mixin.AttackGoalAccess
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.NeutralMob
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal
import net.minecraft.world.entity.ai.goal.target.NonTameRandomTargetGoal
import net.minecraft.world.entity.ai.memory.MemoryModuleType
import net.minecraft.world.entity.ai.memory.MemoryStatus
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Spider
import net.minecraft.world.entity.monster.Zoglin
import net.minecraft.world.entity.monster.breeze.Breeze
import net.minecraft.world.entity.monster.hoglin.Hoglin
import net.minecraft.world.entity.monster.piglin.Piglin
import net.minecraft.world.entity.monster.piglin.PiglinAi
import net.minecraft.world.entity.monster.piglin.PiglinBrute
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.entity.player.Player

object CombatHostility {
    fun isHostileTo(candidate: LivingEntity, owner: LivingEntity?, includeNeutral: Boolean = false): Boolean {
        if (owner == null) return candidate is Enemy || includeNeutral && candidate is Mob
        if (candidate === owner || candidate.isAlliedTo(owner) || owner.isAlliedTo(candidate)) return false
        if (includeNeutral && candidate is Mob) return true
        if (candidate !is Mob || candidate.isNoAi || !candidate.canAttack(owner) || !candidate.canAttackType(owner.type)) return false

        // This also covers custom AI that publishes its chosen target through Mob.getTarget().
        if (candidate.target === owner) return true
        val brain = candidate.brain
        if (brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED) &&
            brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) === owner
        ) return true
        if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED) &&
            brain.getMemory(MemoryModuleType.ANGRY_AT).orElse(null) == owner.uuid
        ) return true
        if (candidate is NeutralMob && candidate.isAngryAt(owner)) return true

        if (candidate is Warden && candidate.entityAngryAt.orElse(null) === owner) return true
        if (owner is Player && isBrainThreatToPlayer(candidate, owner)) return true

        for (wrapped in candidate.targetSelector.availableGoals) {
            val goal = wrapped.goal
            if (Goal.Flag.TARGET !in goal.flags) continue
            val attack = (goal as? NearestAttackableTargetGoal<*>) as? AttackGoalAccess
            val targetType = attack?.helldiversTargetType
            if (targetType != null && !targetType.isInstance(owner)) continue
            if (goal is NonTameRandomTargetGoal<*> && (candidate as? TamableAnimal)?.isTame == true) continue
            if (goal.javaClass.enclosingClass == Spider::class.java &&
                candidate.lightLevelDependentMagicValue >= 0.5f
            ) continue

            // Only evaluate the pair's eligibility. Running canUse would advance the mob's AI scan.
            val custom = TargetGoalPredicate.test(goal, targetType, owner)
            if (custom == true) return true
            if (custom == null && attack != null &&
                attack.helldiversTargetConditions.copy().range(-1.0).ignoreLineOfSight().test(candidate, owner)
            ) return true
        }
        return false
    }

    // These vanilla mobs acquire players with Brain behaviors rather than target goals.
    private fun isBrainThreatToPlayer(mob: Mob, player: Player): Boolean = when (mob) {
        is Piglin -> mob.isAdult && !PiglinAi.isWearingGold(player) &&
            !mob.brain.hasMemoryValue(MemoryModuleType.AVOID_TARGET) &&
            !mob.brain.hasMemoryValue(MemoryModuleType.ADMIRING_ITEM)
        is Hoglin -> !mob.isInLove && !mob.brain.hasMemoryValue(MemoryModuleType.PACIFIED) &&
            !mob.brain.hasMemoryValue(MemoryModuleType.AVOID_TARGET)
        is PiglinBrute, is Zoglin, is Breeze -> true
        else -> false
    }
}
