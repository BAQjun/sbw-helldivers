package com.example.sbwhelldiver.config

import net.neoforged.neoforge.common.ModConfigSpec

/** Visual settings are local to each client and never change gameplay. */
object ClientModelConfig {
    private val builder = ModConfigSpec.Builder()
    val lowQualityModels: ModConfigSpec.BooleanValue = builder
        .comment("Use reduced-polygon stratagem models. Textures and animations are unchanged.")
        .translation("config.sbw_helldiver.low_quality_models")
        .define("lowQualityModels", false)
    val modelLod = builder.comment("Automatically use reduced models beyond modelLodDistance blocks.")
        .translation("config.sbw_helldiver.model_lod").define("modelLod",true)
    val modelLodDistance = builder.comment("Camera distance in blocks for reduced models. Mines also use a second tier at twice this distance.")
        .translation("config.sbw_helldiver.model_lod_distance").defineInRange("modelLodDistance",32,8,256)
    val SPEC: ModConfigSpec = builder.build()
}
