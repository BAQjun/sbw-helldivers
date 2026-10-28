package com.example.sbwhelldiver.effect

import com.example.sbwhelldiver.MOD_ID
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.AttributeMap
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player

class GasEffect : MobEffect(MobEffectCategory.HARMFUL,0x8CA644) {
    init { addAttributeModifier(Attributes.MOVEMENT_SPEED,ResourceLocation.fromNamespaceAndPath(MOD_ID,"gas_slow"),-.25,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) }
    override fun shouldApplyEffectTickThisTick(duration: Int,amplifier: Int) = true
    override fun applyEffectTick(entity: LivingEntity,amplifier: Int): Boolean {
        // Area refreshes duration every tick; use the unit's clock, not the remaining duration.
        if (entity.level().isClientSide || entity.tickCount % 20 != 0) return true
        val key = ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation.fromNamespaceAndPath(MOD_ID,"gas"))
        val source = DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key))
        entity.hurt(source,4f)
        return true
    }
}
class ConfusionEffect : MobEffect(MobEffectCategory.HARMFUL,0xBACD68)
class PanicEffect : MobEffect(MobEffectCategory.HARMFUL,0xE59A42) {
    init { addAttributeModifier(Attributes.MOVEMENT_SPEED,ResourceLocation.fromNamespaceAndPath(MOD_ID,"panic_slow"),-.25,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) }
}
class SentryBurningEffect : MobEffect(MobEffectCategory.HARMFUL,0xF46C25) {
    override fun shouldApplyEffectTickThisTick(duration: Int,amplifier: Int) = true
    override fun applyEffectTick(entity: LivingEntity,amplifier: Int): Boolean {
        if (!entity.level().isClientSide && entity.tickCount % 10 == 0) FlameDamage.burn(entity)
        return true
    }

}
class HeavyBurningEffect : MobEffect(MobEffectCategory.HARMFUL,0xFFD245) {
    override fun shouldApplyEffectTickThisTick(duration: Int,amplifier: Int) = true
    override fun applyEffectTick(entity: LivingEntity,amplifier: Int): Boolean {
        if (!entity.level().isClientSide && entity.tickCount % 10 == 0) FlameDamage.burn(entity,true)
        return true
    }

}
class StunEffect : MobEffect(MobEffectCategory.HARMFUL,0x8FCDE4) {
    init { addAttributeModifier(Attributes.MOVEMENT_SPEED,ResourceLocation.fromNamespaceAndPath(MOD_ID,"stun_slow"),-.70,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) }


    companion object {
        private const val SAVED = "HelldiverStunSavedNoAI"
        fun disable(entity: LivingEntity) {
            if (entity !is Mob) return
            if (!entity.persistentData.contains(SAVED)) entity.persistentData.putBoolean(SAVED,entity.isNoAi)
            entity.isNoAi = true
            entity.navigation.stop()
            entity.target = null
        }
        fun restore(entity: LivingEntity) {
            if (entity !is Mob || !entity.persistentData.contains(SAVED)) return
            entity.isNoAi = entity.persistentData.getBoolean(SAVED)
            entity.persistentData.remove(SAVED)
        }
    }
}
