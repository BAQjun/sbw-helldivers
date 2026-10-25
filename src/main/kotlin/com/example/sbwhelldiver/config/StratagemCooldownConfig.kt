package com.example.sbwhelldiver.config

import net.minecraftforge.common.ForgeConfigSpec
import kotlin.math.roundToInt

/** Native StratagemSettings base values, with Synthetic Supplementation and Morale Augmentation. */
object StratagemCooldownConfig {
    val baseSeconds = linkedMapOf(
        "machine_gun_sentry" to 90.0, "gatling_sentry" to 150.0,
        "rocket_sentry" to 150.0, "autocannon_sentry" to 150.0,
        "mortar_sentry" to 180.0, "gas_mortar_sentry" to 180.0, "ems_mortar_sentry" to 180.0,
        "flame_sentry" to 100.0, "laser_sentry" to 150.0, "tesla_tower" to 120.0,
        "hmg_emplacement" to 180.0, "anti_tank_emplacement" to 180.0,
        "grenadier_battlement" to 120.0, "shield_generator" to 90.0,
        "anti_personnel_minefield" to 120.0, "anti_tank_minefield" to 120.0,
        "incendiary_minefield" to 120.0, "gas_minefield" to 120.0,
        "eagle_airstrike" to 15.0, "eagle_strafing_run" to 15.0,
        "eagle_cluster_bomb" to 15.0, "eagle_110mm_rocket_pods" to 15.0, "eagle_500kg_bomb" to 15.0, "eagle_smoke_strike" to 15.0, "eagle_gas_strike" to 15.0, "eagle_napalm_airstrike" to 15.0,
    )
    internal val cooldowns = linkedMapOf<String, ForgeConfigSpec.DoubleValue>()
    internal val uses = linkedMapOf<String, ForgeConfigSpec.IntValue>()
    internal lateinit var autoRearm: ForgeConfigSpec.DoubleValue
    internal lateinit var manualRearm: ForgeConfigSpec.DoubleValue
    fun register(builder: ForgeConfigSpec.Builder) {
        builder.comment("战备冷却（秒），默认采用满舰船升级。Cooldowns in seconds, fully upgraded defaults.").push("stratagems")
        for ((id, base) in baseSeconds) {
            builder.push(id)
            cooldowns[id] = builder.comment("抵达后开始冷却；飞鹰之间共享周转，采用刚使用战备的冷却时间。",
                "Cooldown starts on arrival; Eagle turnaround is shared. Zero disables the wait.")
                .defineInRange("cooldownSeconds", upgradedSeconds(id, base), 0.0, 86400.0)
            if (id.startsWith("eagle_")) uses[id] = builder.comment("独立使用次数；一次重新武装补满所有飞鹰。Uses per rearm, independent for each Eagle stratagem.")
                .defineInRange("uses", com.example.sbwhelldiver.entity.EagleAttack.fromId(id).defaultUses, 1, 1000)
            builder.pop()
        }
        builder.push("eagle_rearm")
        autoRearm = builder.comment("全部次数耗尽时自动补给，满升级 114 秒。Fully upgraded automatic rearm.")
            .defineInRange("automaticSeconds", 114.0, 0.0, 86400.0)
        manualRearm = builder.comment("仍有余弹时手动补给，满升级 102.6 秒。Manual rearm with remaining ordnance.")
            .defineInRange("manualSeconds", 102.6, 0.0, 86400.0)
        builder.pop(2)
    }
    fun upgradedSeconds(id: String, base: Double) = base * if (id.startsWith("eagle_")) .5 * .95 else .9 * .95
    fun ticks(id: String) = (cooldowns.getValue(id).get() * 20).roundToInt()
    fun maxUses(id: String) = uses[id]?.get() ?: -1
    fun rearmTicks(hasOrdnance: Boolean) = ((if (hasOrdnance) manualRearm else autoRearm).get() * 20).roundToInt()
}
