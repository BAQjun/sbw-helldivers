package com.example.sbwhelldiver.config

import net.minecraftforge.common.ForgeConfigSpec

/** Visual settings are local to each client and never change gameplay. */
object ClientModelConfig {
    private val builder = ForgeConfigSpec.Builder()
    val lowQualityModels: ForgeConfigSpec.BooleanValue = builder
        .comment("Use reduced-polygon stratagem models. Textures and animations are unchanged.")
        .translation("config.sbw_helldiver.low_quality_models")
        .define("lowQualityModels", false)
    val SPEC: ForgeConfigSpec = builder.build()
}
