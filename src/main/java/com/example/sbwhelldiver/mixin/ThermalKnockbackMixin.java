package com.example.sbwhelldiver.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Heat has no mechanical impulse; preserve all unrelated hit and blast impulses. */
@Mixin(LivingEntity.class)
public abstract class ThermalKnockbackMixin {
    @Unique
    private static final TagKey<DamageType> HELLDIVERS_NO_KNOCKBACK = TagKey.create(
        Registries.DAMAGE_TYPE, new ResourceLocation("sbw_helldiver", "no_knockback"));

    @Redirect(method = "hurt", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/LivingEntity;knockback(DDD)V"))
    private void helldivers$thermalImpact(LivingEntity victim, double strength, double x, double z,
                                         DamageSource source, float amount) {
        if (!source.is(HELLDIVERS_NO_KNOCKBACK)) victim.knockback(strength, x, z);
    }
}
