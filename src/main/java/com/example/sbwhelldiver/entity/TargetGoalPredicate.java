package com.example.sbwhelldiver.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Predicate;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;

public final class TargetGoalPredicate {
    private record Fields(Field targetType, Field predicate) {}

    private static final ClassValue<Fields> FIELDS = new ClassValue<>() {
        @Override
        protected Fields computeValue(Class<?> goalType) {
            Field targetType = null;
            Field predicate = null;
            // Custom target goals can store the same type and eligibility test as vanilla.
            // Discover by type so this works with either loader and obfuscated field names.
            for (Class<?> type = goalType; type != Goal.class && type != NearestAttackableTargetGoal.class;
                 type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || !Modifier.isFinal(field.getModifiers())) continue;
                    if (field.getType() == Class.class) {
                        if (targetType != null || !field.trySetAccessible()) return null;
                        targetType = field;
                    } else if (field.getType() == Predicate.class) {
                        if (predicate != null || !field.trySetAccessible()) return null;
                        predicate = field;
                    }
                }
            }
            return predicate == null ? null : new Fields(targetType, predicate);
        }
    };

    private TargetGoalPredicate() {}

    // null means the goal does not expose a single identifiable eligibility predicate.
    @SuppressWarnings("unchecked")
    public static Boolean test(Goal goal, Class<?> vanillaTargetType, LivingEntity player) {
        try {
            Fields fields = FIELDS.get(goal.getClass());
            if (fields == null) return null;
            Class<?> type = fields.targetType() == null ? vanillaTargetType : (Class<?>) fields.targetType().get(goal);
            if (type == null || !LivingEntity.class.isAssignableFrom(type)) return null;
            if (!type.isInstance(player)) return false;
            Predicate<LivingEntity> predicate = (Predicate<LivingEntity>) fields.predicate().get(goal);
            return predicate == null ? null : predicate.test(player);
        } catch (IllegalAccessException | RuntimeException | LinkageError ex) {
            // A foreign goal can have a private contract beyond its declared field types.
            return false;
        }
    }
}
