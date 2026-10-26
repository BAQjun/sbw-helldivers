package com.example.sbwhelldiver.config

import net.minecraftforge.common.ForgeConfigSpec

/** SERVER values are synchronized; per-world serverconfig files override defaults. */
object HelldiversConfig {
    private val builder = ForgeConfigSpec.Builder()
    val dropDelayTicks = builder.comment("信标显示的呼叫倒计时，实际撞地在归零后 20 tick（1 秒）；20 tick = 1 秒。",
        "空投仓出现后固定下落 95 tick（4.75 秒）；默认仍显示四秒倒计时，归零后一秒撞地。")
        .defineInRange("dropDelayTicks", 80, 0, 1200)
    val spawnHeight = builder.comment("舱体底面在落点上方的生成高度（格）。",
        "战备信标的计时空投至少使用 720 格，旧存档的低值也会采用此下限。")
        .defineInRange("spawnHeight", 720, 16, 2048)
    val fallSpeed = builder.comment("旧版本速度选项，保留供兼容；新版所有空投仓统一采用 4.75 秒下落路径。")
        .defineInRange("fallSpeed", 2.0, 0.1, 20.0)
    val brakeHeight = builder.comment("距离最终落点多少格开始喷射减速，三块外板在此阶段打开。",
        "战备信标的计时空投至少在 336 格开始制动；喷射制动约 2.2 秒。")
        .defineInRange("brakeHeight", 336.0, 2.0, 2048.0)
    val burialDepth = builder.comment(
        "钻头尖端埋入地面的深度（格）；新版模型原点到尖端为 5.5456 格。",
        "默认 5.45 留下约 0.10 格顶沿；设为 0 可检查完整舱体。",
        "旧版默认 5.3 / 5.37 会按新版模型校准到 5.45。",
        "不改变地形方块；开仓后的内部通过舱口局部深度渲染显示。")
        .defineInRange("burialDepth", 5.45, 0.0, 6.5)
    val sentryLifetimeTicks = builder.comment("工作时限（tick），默认 150 秒；0 表示只按弹药耗尽回收。")
        .defineInRange("sentryLifetimeTicks", 3000, 0, 240000)
    val capLifetimeTicks = builder.comment("弹出的独立盖板落地后保留多久（tick），默认 600 = 30 秒。")
        .defineInRange("capLifetimeTicks", 600, 20, 12000)
    val attackPlayers = builder.comment("兼容旧存档的选项；当前炮台始终不对玩家索敌，但子弹和爆炸可以误伤玩家。")
        .define("attackPlayers", false)
    val attackNeutralMobs = builder.comment("是否攻击其他 Mob。默认只攻击 Enemy；始终排除已驯服宠物。")
        .define("attackNeutralMobs", false)
    init { TurretConfigs.register(builder); StratagemCooldownConfig.register(builder); EagleConfig.register(builder); OrbitalConfig.register(builder) }
    val SPEC: ForgeConfigSpec = builder.build()

    fun effectiveSentryLifetimeTicks(): Int = sentryLifetimeTicks.get().let { if (it == 12000) 3000 else it }

    fun effectiveBurialDepth(): Double {
        val value = burialDepth.get()
        return if (kotlin.math.abs(value - 5.3) < 1e-6 || kotlin.math.abs(value - 5.37) < 1e-6) 5.45 else value
    }
}
