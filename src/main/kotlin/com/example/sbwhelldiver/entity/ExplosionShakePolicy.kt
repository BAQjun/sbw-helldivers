package com.example.sbwhelldiver.entity

import net.minecraft.world.entity.Entity

object ExplosionShakePolicy {
 @JvmStatic fun scale(source:Entity?):Double=when {
  source is OrbitalGatlingRoundEntity -> 0.0
  source?.type === com.example.sbwhelldiver.init.ModEntities.SENTRY_CANNON_SHELL.get() -> .35
  source is SentryRocketEntity -> .35
  else -> 1.0
 }
}
