package com.example.sbwhelldiver.config

import com.example.sbwhelldiver.MOD_ID
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.common.ModConfigSpec

object GuardDogConfigs {
    enum class Variant(val id: String) {
        BALLISTIC("guard_dog"),
        LASER("laser_guard_dog"),
        ELECTRIC("stun_guard_dog"),
        FLAME("flame_guard_dog"),
        GAS("gas_guard_dog");

        val targetingId: ResourceLocation = requireNotNull(ResourceLocation.tryParse("$MOD_ID:$id"))

        companion object {
            fun of(laser: Boolean, stun: Boolean, flame: Boolean, gas: Boolean) = when {
                laser -> LASER
                stun -> ELECTRIC
                flame -> FLAME
                gas -> GAS
                else -> BALLISTIC
            }
        }
    }

    class Settings(builder: ModConfigSpec.Builder, variant: Variant) {
        private val gun = variant == Variant.BALLISTIC
        private val gas = variant == Variant.GAS
        private val flame = variant == Variant.FLAME
        private val electric = variant == Variant.ELECTRIC
        private val sprayer = flame || gas

        val maxHealth = builder.comment("最大生命值。需允许护卫犬受伤。 / Maximum health when damageable.")
            .defineInRange("maxHealth", 100.0, 1.0, 100000.0)
        val damageable = builder.comment("部署中的护卫犬是否会受伤。 / Whether the deployed drone can take damage.")
            .define("damageable", gun)
        val repairTicks = builder.comment("带伤对接后的维修时间。20 刻 = 1 秒。 / Repair time after docking while damaged.")
            .defineInRange("repairTicks", 50, 0, 12000)
        val callDelayTicks = builder.comment("信标呼叫倒计时。20 刻 = 1 秒。 / Beacon call-in countdown in ticks.")
            .defineInRange("callDelayTicks", 195, 0, 1200)
        val targetRange = builder.comment("距离佩戴者多远以内可以索敌。喷射型也以此为追击范围。 / Acquisition radius around the wearer; also the pursuit radius for sprayers.")
            .defineInRange("targetRange", if (sprayer) 20.0 else 50.0, 1.0, 128.0)
        val flightSpeed = builder.comment("巡航及追击速度，格/秒。 / Standard patrol and pursuit speed in blocks per second.")
            .defineInRange("flightSpeed", 6.0, 1.0, 16.0)
        val rpm = builder.comment("每分钟发射或结算次数。激光与火焰的 0 值继承对应炮台射速。 / Attacks per minute; 0 inherits the corresponding sentry RPM for laser and flame.")
            .defineInRange("rpm", when (variant) {
                Variant.BALLISTIC -> 660
                Variant.ELECTRIC -> 12
                Variant.GAS -> 600
                else -> 0
            }, if (gun || gas || electric) 1 else 0, 1200)

        val ammunition = if (gun || sprayer) builder.comment("单匣弹药或单罐容量。 / Rounds or fuel units per magazine or tank.")
            .defineInRange("ammunition", if (gas) 100 else if (flame) 80 else 45, if (flame) 2 else 1, 10000) else null
        val reserves = if (gun || sprayer) builder.comment("背包备用弹匣或罐体数量。 / Spare magazines or tanks in the backpack.")
            .defineInRange("reserves", if (gun) 8 else 6, 0, if (gun) 8 else 100) else null
        val damage = if (gun) builder.comment("子弹基础直击伤害。 / Base damage per bullet.")
            .defineInRange("damage", 10.0, 0.0, 100000.0) else null
        val damageMultiplier = if (!gun && !gas) builder.comment("相对对应炮台的直击伤害倍率。 / Direct damage multiplier relative to the corresponding sentry.")
            .defineInRange("damageMultiplier", if (flame) .25 else if (electric) .4 else .6, 0.0, 100.0) else null
        val sprayRange = if (sprayer) builder.comment("喷射的最大距离，单位为格。 / Maximum spray range in blocks.")
            .defineInRange("sprayRange", 10.0, 2.0, 64.0) else null
        val gasExposureTicks = if (gas) builder.comment("连续暴露多少刻后获得毒气效果。 / Continuous exposure before applying gas effects.")
            .defineInRange("gasExposureTicks", 20, 1, 1200) else null
        val gasEffectTicks = if (gas) builder.comment("毒气效果持续时间，单位为刻。 / Gas effect duration in ticks.")
            .defineInRange("gasEffectTicks", 180, 1, 12000) else null
        val gasCloudTicks = if (gas) builder.comment("滞留毒雾的持续时间，单位为刻。 / Lingering cloud lifetime in ticks.")
            .defineInRange("gasCloudTicks", 60, 1, 1200) else null
        val arcJumpRange = if (electric) builder.comment("电弧跳跃在正前方的最大距离，锥形边缘按比例缩短。 / Maximum forward arc jump distance, reduced toward the cone edge.")
            .defineInRange("arcJumpRange", 55.0, 0.0, 128.0) else null
        val arcBranches = if (electric) builder.comment("每个电弧节点最多派生的分支数。 / Maximum branches from each arc node.")
            .defineInRange("arcBranches", 5, 0, 5) else null
    }

    private lateinit var entries: Map<Variant, Settings>
    private val variantsById = Variant.entries.associateBy(Variant::id)

    fun register(builder: ModConfigSpec.Builder) {
        builder.comment("各护卫犬独立属性。 / Per-variant Guard Dog settings.").push("guardDogs")
        entries = Variant.entries.associateWith { variant ->
            builder.push(variant.id)
            Settings(builder, variant).also { builder.pop() }
        }
        builder.pop()
    }

    fun get(variant: Variant) = entries.getValue(variant)
    fun byId(id: String) = variantsById[id]?.let(::get)

    fun ammoCapacity(variant: Variant) = get(variant).ammunition?.let { if (HelldiversConfig.SPEC.isLoaded) it.get() else it.default } ?: 0
    fun reserveCapacity(variant: Variant) = get(variant).reserves?.let { if (HelldiversConfig.SPEC.isLoaded) it.get() else it.default } ?: 0
    fun flameRange() = get(Variant.FLAME).sprayRange!!.let { if (HelldiversConfig.SPEC.isLoaded) it.get() else it.default }
    fun gasCloudLifetime() = get(Variant.GAS).gasCloudTicks!!.let { if (HelldiversConfig.SPEC.isLoaded) it.get() else it.default }
}
