package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.init.ModEffects
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.phys.Vec3
import kotlin.math.tan

object GuardDogGas {
    const val EFFECT_TICKS = 180
    const val EXPOSURE_TICKS = 20
    const val CLOUD_LIFETIME_TICKS = 60
    const val CLOUD_INTERVAL_TICKS = 7
    const val CLOUD_MERGE_DISTANCE_SQR = .75 * .75
    // The native spray is 16 degrees wide.
    private val slope = tan(Math.toRadians(8.0))

    fun hits(target: LivingEntity, start: Vec3, axis: Vec3, range: Double): Boolean {
        val bounds = target.boundingBox
        val along = bounds.center.subtract(start).dot(axis).coerceIn(0.0, range)
        return bounds.inflate(along * slope).clip(start, start.add(axis.scale(range))).isPresent
    }

    fun poison(target: LivingEntity, owner: ServerPlayer) {
        target.addEffect(MobEffectInstance(ModEffects.GAS, EFFECT_TICKS, 0, false, true, true), owner)
        if (target !is net.minecraft.world.entity.player.Player)
            target.addEffect(MobEffectInstance(ModEffects.CONFUSION, EFFECT_TICKS, 0, false, true, true), owner)
    }
}
