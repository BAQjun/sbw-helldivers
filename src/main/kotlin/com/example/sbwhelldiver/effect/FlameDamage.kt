package com.example.sbwhelldiver.effect

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.FlameClock
import com.example.sbwhelldiver.init.ModEffects
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

object FlameDamage {
    private const val OWNER = "HelldiverFlameOwner"
    fun source(target: LivingEntity, id: String, attacker: Entity? = null): DamageSource {
        val key = ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation(MOD_ID,id))
        return DamageSource(target.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(key),attacker)
    }
    fun ignite(target: LivingEntity, attacker: Entity? = null) {
        if (target.fireImmune() || target.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) return
        attacker?.let { target.persistentData.putUUID(OWNER,it.uuid) }
        target.addEffect(MobEffectInstance(ModEffects.BURNING.get(),60,0,false,true,true))
        // Only the visual fire timer is vanilla. Its damage is intercepted while BURNING is present.
        target.remainingFireTicks = maxOf(target.remainingFireTicks,60)
    }
    fun hit(target: LivingEntity, attacker: Entity) {
        target.hurt(source(target,"flame_jet",attacker),FlameClock.DIRECT_DAMAGE_PER_JET)
        ignite(target,attacker)
        if (target !is Player) target.addEffect(MobEffectInstance(ModEffects.PANIC.get(),20,0,false,true,true))
    }
    fun burn(target: LivingEntity) {
        if (target.fireImmune() || target.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE)) return
        val server = target.level() as? net.minecraft.server.level.ServerLevel ?: return
        val attacker = if(target.persistentData.hasUUID(OWNER)) server.getEntity(target.persistentData.getUUID(OWNER)) else null
        target.hurt(source(target,"sentry_burning",attacker),16f)
    }
}
