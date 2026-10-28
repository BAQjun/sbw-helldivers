package com.example.sbwhelldiver.init

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.effect.*
import net.minecraft.world.effect.MobEffect
import net.neoforged.neoforge.registries.DeferredRegister
import net.minecraft.core.registries.BuiltInRegistries

object ModEffects {
    val EFFECTS: DeferredRegister<MobEffect> = DeferredRegister.create(BuiltInRegistries.MOB_EFFECT,MOD_ID)
    val GAS = EFFECTS.register("gas", ::GasEffect)
    val CONFUSION = EFFECTS.register("confusion", ::ConfusionEffect)
    val STUN = EFFECTS.register("stun", ::StunEffect)
    val HEAVY_BURNING = EFFECTS.register("heavy_burning", ::HeavyBurningEffect)
    val BURNING = EFFECTS.register("sentry_burning", ::SentryBurningEffect)
    val PANIC = EFFECTS.register("panic", ::PanicEffect)
}
