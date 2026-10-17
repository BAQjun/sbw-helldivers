package com.example.sbwhelldiver.init

import com.example.sbwhelldiver.MOD_ID
import net.minecraft.core.particles.SimpleParticleType
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries

object ModParticles {
    val PARTICLES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, MOD_ID)
    val GAS_CLOUD = PARTICLES.register("gas_cloud") { SimpleParticleType(false) }
    val EMS_CLOUD = PARTICLES.register("ems_cloud") { SimpleParticleType(false) }
    val MACHINE_GUN_CASING = PARTICLES.register("machine_gun_casing") { SimpleParticleType(false) }
    val GATLING_CASING = PARTICLES.register("gatling_casing") { SimpleParticleType(false) }
    val CANNON_CASING = PARTICLES.register("cannon_casing") { SimpleParticleType(false) }
    val HELLPOD_JET = PARTICLES.register("hellpod_jet") { SimpleParticleType(false) }
    val FLAME_STREAM = PARTICLES.register("flame_stream") { SimpleParticleType(false) }
    val IGNITION_SMOKE = PARTICLES.register("ignition_smoke") { SimpleParticleType(false) }
    val FLAME_SOOT = PARTICLES.register("flame_soot") { SimpleParticleType(false) }
}
