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
    init { addAttributeModifier(Attributes.MOVEMENT_SPEED,"45da23bf-0ca2-49ae-9024-8f47bfc7a421",-.25,AttributeModifier.Operation.MULTIPLY_TOTAL) }
    override fun isDurationEffectTick(duration: Int,amplifier: Int) = true
    override fun applyEffectTick(entity: LivingEntity,amplifier: Int) {
        // Area refreshes duration every tick; use the unit's clock, not the remaining duration.
        if (entity.level().isClientSide || entity.tickCount % 20 != 0) return
        val key = ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation(MOD_ID,"gas"))
        val source = DamageSource(entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key))
        entity.hurt(source,4f)
    }
}
class ConfusionEffect : MobEffect(MobEffectCategory.HARMFUL,0xBACD68)
class PanicEffect : MobEffect(MobEffectCategory.HARMFUL,0xE59A42) {
    init { addAttributeModifier(Attributes.MOVEMENT_SPEED,"b8667e49-a464-43ed-afbe-20fe40f16269",-.25,AttributeModifier.Operation.MULTIPLY_TOTAL) }
}
class SentryBurningEffect : MobEffect(MobEffectCategory.HARMFUL,0xF46C25) {
    override fun isDurationEffectTick(duration: Int,amplifier: Int) = true
    override fun applyEffectTick(entity: LivingEntity,amplifier: Int) {
        if (!entity.level().isClientSide && entity.tickCount % 20 == 0) FlameDamage.burn(entity)
    }
    override fun removeAttributeModifiers(entity: LivingEntity,attributes: AttributeMap,amplifier: Int) {
        super.removeAttributeModifiers(entity,attributes,amplifier)
        entity.clearFire()
        entity.persistentData.remove("HelldiverFlameOwner")
    }
}
class StunEffect : MobEffect(MobEffectCategory.HARMFUL,0x8FCDE4) {
    init { addAttributeModifier(Attributes.MOVEMENT_SPEED,"ad037612-4101-4e90-80b7-aec4c6df2a98",-.70,AttributeModifier.Operation.MULTIPLY_TOTAL) }
    override fun addAttributeModifiers(entity: LivingEntity,attributes: AttributeMap,amplifier: Int) {
        super.addAttributeModifiers(entity,attributes,amplifier)
        if (entity !is Player) disable(entity)
    }
    override fun removeAttributeModifiers(entity: LivingEntity,attributes: AttributeMap,amplifier: Int) {
        super.removeAttributeModifiers(entity,attributes,amplifier)
        restore(entity)
    }
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
