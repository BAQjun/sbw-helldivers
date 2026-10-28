package com.example.sbwhelldiver.init

import com.example.sbwhelldiver.MOD_ID
import net.minecraft.core.particles.SimpleParticleType
import net.neoforged.neoforge.registries.DeferredRegister
import net.minecraft.core.registries.BuiltInRegistries

object ModParticles {
    val PARTICLES = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, MOD_ID)
    val GAS_CLOUD = PARTICLES.register("gas_cloud") { _ -> SimpleParticleType(false) }
    val EMS_CLOUD = PARTICLES.register("ems_cloud") { _ -> SimpleParticleType(false) }
    val MACHINE_GUN_CASING = PARTICLES.register("machine_gun_casing") { _ -> SimpleParticleType(false) }
    val GATLING_CASING = PARTICLES.register("gatling_casing") { _ -> SimpleParticleType(false) }
    val CANNON_CASING = PARTICLES.register("cannon_casing") { _ -> SimpleParticleType(false) }
    val HELLPOD_JET = PARTICLES.register("hellpod_jet") { _ -> SimpleParticleType(false) }
    val FLAME_STREAM = PARTICLES.register("flame_stream") { _ -> SimpleParticleType(false) }
    val IGNITION_SMOKE = PARTICLES.register("ignition_smoke") { _ -> SimpleParticleType(false) }
    val FLAME_SOOT = PARTICLES.register("flame_soot") { _ -> SimpleParticleType(false) }
}
