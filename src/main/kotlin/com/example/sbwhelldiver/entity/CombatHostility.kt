package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.mixin.AttackGoalAccess
import com.example.sbwhelldiver.mixin.MobTargetSelectorAccess
import net.minecraft.server.level.ServerPlayer
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

object CombatHostility {
    fun isHostileTo(candidate: LivingEntity, owner: LivingEntity?, includeNeutral: Boolean = false): Boolean {
        if (owner != null && (candidate === owner || candidate.isAlliedTo(owner) || owner.isAlliedTo(candidate))) return false
        // Keep the original enemy classification alongside the deployer-specific AI checks.
        if (candidate is Enemy || (includeNeutral && candidate is Mob)) return true
        if (owner == null) return false
        if (candidate !is Mob || candidate.isNoAi || !candidate.canAttackType(owner.type)) return false
        if (owner is ServerPlayer && owner.isCreative) {
            return CreativeHostilityProbe.test(owner) { checkHostility(candidate, owner) }
        }
        return checkHostility(candidate, owner)
    }

    private fun checkHostility(candidate: Mob, owner: LivingEntity): Boolean {
        if (!candidate.canAttack(owner)) return false

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

        for (wrapped in (candidate as MobTargetSelectorAccess).helldiversTargetSelector.availableGoals) {
            val goal = wrapped.goal
            if (Goal.Flag.TARGET !in goal.flags) continue
            val attack = (goal as? NearestAttackableTargetGoal<*>) as? AttackGoalAccess
            val targetType = attack?.helldiversTargetType
            if (targetType != null && !targetType.isInstance(owner)) continue
            if (goal is NonTameRandomTargetGoal<*> && (candidate as? TamableAnimal)?.isTame == true) continue

            // Only evaluate the pair's eligibility. Running canUse would advance the mob's AI scan.
            val custom = TargetGoalPredicate.test(goal, targetType, owner)
            if (custom == true) return true
            if (custom == null && attack != null &&
                attack.helldiversTargetConditions.copy().range(-1.0).ignoreLineOfSight().test(candidate, owner)
            ) return true
        }
        return false
    }
}
