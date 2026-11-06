package com.example.sbwhelldiver.mixin;

import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NearestAttackableTargetGoal.class)
public interface AttackGoalAccess {
    @Accessor("targetType")
    Class<?> getHelldiversTargetType();

    @Accessor("targetConditions")
    TargetingConditions getHelldiversTargetConditions();
}
