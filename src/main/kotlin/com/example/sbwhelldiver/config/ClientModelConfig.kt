package com.example.sbwhelldiver.config

import net.neoforged.neoforge.common.ModConfigSpec

/** Visual settings are local to each client and never change gameplay. */
object ClientModelConfig {
    private val builder = ModConfigSpec.Builder()
    val lowQualityModels: ModConfigSpec.BooleanValue = builder
        .comment("Use reduced-polygon stratagem models. Textures and animations are unchanged.")
        .translation("config.sbw_helldiver.low_quality_models")
        .define("lowQualityModels", false)
    val SPEC: ModConfigSpec = builder.build()
}
