package com.example.sbwhelldiver.config

import com.atsuishio.superbwarfare.data.gun.GunData
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.world.entity.EntityType
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.registries.ForgeRegistries

/** One server-config section per turret. Geometry and animation data stay independent. */
class TurretConfig(private val builder: ForgeConfigSpec.Builder, val id: String, vehicle: JsonObject, profile: JsonObject) {
    private val manual = id in setOf("hmg_emplacement", "anti_tank_emplacement", "grenadier_battlement")
    private val energy = id in setOf("laser_sentry", "tesla_tower")
    private val weapon = vehicle.getAsJsonObject("Weapons")?.getAsJsonObject("Main") ?: JsonObject()
    private fun number(key: String, fallback: Double = 0.0) = weapon[key]?.asDouble ?: fallback
    private fun real(name: String, value: Double, min: Double = 0.0, max: Double = 100000.0, comment: String): ForgeConfigSpec.DoubleValue =
        builder.comment(comment).defineInRange(name, value, min, max)
    private fun integer(name: String, value: Int, min: Int = 0, max: Int = 100000, comment: String): ForgeConfigSpec.IntValue =
        builder.comment(comment).defineInRange(name, value, min, max)

    val maxHealth = real("maxHealth", vehicle["MaxHealth"].asDouble, 1.0, comment = "本体最大生命值。已部署炮台不会因提高上限而自动回血。 / Maximum hull health; raising it does not heal existing turrets.")
    val explosionResistance = real("explosionResistance", .5, max = 1.0, comment = "爆炸减伤比例；0 为不减伤，1 为免疫爆炸。 / Explosion resistance: 0 = none, 1 = immune.")
    val lifetimeTicks = integer("lifetimeTicks", if (manual) 0 else -1, -1, 240000,
        "工作时限，20 tick = 1 秒；-1 使用全局 sentryLifetimeTicks，0 不按时间回收；人员操作型炮位忽略此项。 / Lifetime: -1 = global, 0 = unlimited; ignored by manned emplacements.")
    val damage = real("damage", when (id) {
        "hmg_emplacement" -> 15.0; "anti_tank_emplacement" -> 200.0; "grenadier_battlement" -> 5.0
        "laser_sentry" -> 7.5; "tesla_tower" -> 80.0; else -> number("Damage")
    }, comment = "每发直击伤害；火焰为每次射流、激光为每次结算、电塔为每次放电对每个目标的伤害，不含持续燃烧。 / Direct damage per shot/pulse, excluding burning.")
    val defaultRpm = when (id) {
        "hmg_emplacement" -> 600; "anti_tank_emplacement" -> 60; "grenadier_battlement" -> 160
        "laser_sentry" -> 240; "tesla_tower" -> 30; else -> number("RPM", 60.0).toInt()
    }
    private val maximumRpm = when (id) {
        "tesla_tower" -> 600
        "autocannon_sentry" -> 900
        "mortar_sentry" -> 480
        "gas_mortar_sentry", "ems_mortar_sentry", "flame_sentry" -> 1200
        else -> if (manual || energy) 1200 else 4800
    }
    val rpm = integer("rpm", defaultRpm, 1, maximumRpm,
        "每分钟发射/伤害结算次数。连发炮台同比缩放连发间隔和组间停顿；火焰燃料消耗随射流次数变化。 / Rounds or damage pulses per minute; scales burst timing.")
    val ammunition = if (!energy) integer("ammunition", profile["total_ammo"].asInt,
        comment = "总弹药/燃料。只决定新部署容量，不会给已有炮台补弹。 / Total ammunition/fuel; does not refill deployed turrets.") else null
    val magazineSize = if (id == "rocket_sentry") integer("magazineSize", number("Magazine", 16.0).toInt(), 1,
        comment = "火箭单次装填容量，受总弹药量限制；其余弹药作为备用弹药。 / Rocket magazine capacity; remaining rounds are reserves.") else null
    val range = if (!manual) real("range", vehicle.getAsJsonObject("SeekInfo")["MaxSeekRange"].asDouble, max = 256.0,
        comment = "自动索敌最大距离，单位为格。火焰也使用此射流长度。 / Acquisition range in blocks; also sets flame reach.") else null
    val minimumRange = if (!manual) real("minimumRange", vehicle.getAsJsonObject("SeekInfo")["MinSeekRange"].asDouble, max = 256.0,
        comment = "最小索敌距离；超过最大射程时按最大射程处理。 / Minimum acquisition range, clamped to maximum range.") else null
    private val explosive = id in setOf("rocket_sentry", "autocannon_sentry", "mortar_sentry", "ems_mortar_sentry", "anti_tank_emplacement", "grenadier_battlement")
    val explosionDamage = if (explosive) real("explosionDamage", when (id) {
        "anti_tank_emplacement" -> 20.0; "grenadier_battlement" -> 60.0; else -> number("ExplosionDamage")
    }, comment = "爆炸伤害，独立于直击伤害。 / Blast damage, separate from direct damage.") else null
    val outerRadius = if (explosive) real("explosionOuterRadius", when (id) {
        "anti_tank_emplacement" -> 7.0; "grenadier_battlement" -> 7.5; else -> number("ExplosionRadius")
    }, max = 128.0, comment = "爆炸外半径；0 关闭范围伤害。 / Outer blast radius; 0 disables area damage.") else null
    val innerRadius = if (explosive) real("explosionInnerRadius", when (id) {
        "rocket_sentry", "ems_mortar_sentry" -> 1.0; "autocannon_sentry" -> 2.0
        "grenadier_battlement" -> 3.5; else -> 3.0
    }, max = 128.0, comment = "爆炸满伤内半径，实际不超过外半径。 / Full-damage inner radius, clamped to the outer radius.") else null
    val scatterRadius = if (id.endsWith("mortar_sentry")) real("scatterRadius", if (id == "mortar_sentry") 10.0 else 5.0,
        max = 64.0, comment = "迫击炮落点散布半径。 / Mortar impact scatter radius.") else null
    val beamRange = if (id == "laser_sentry") real("beamRange", 200.0, max = 512.0,
        comment = "激光光束与已锁定目标的最大距离，不能小于索敌距离。 / Beam reach and locked-target range, at least acquisition range.") else null
    val maxHeat = if (id == "laser_sentry") real("maxHeat", 250.0, 1.0,
        comment = "激光过热自爆阈值。 / Laser overload heat threshold.") else null
    val heatPerSecond = if (id == "laser_sentry") real("heatPerSecond", 8.0,
        comment = "激光开火每秒累积热量。 / Laser heat gained per second.") else null
    val jumpRange = if (id == "tesla_tower") real("jumpRange", 30.0, max = 128.0,
        comment = "电弧相邻目标间的最大跳跃距离。 / Maximum chain-lightning jump distance.") else null
    val maxVictims = if (id == "tesla_tower") integer("maxVictims", 3, 1, 32,
        comment = "每次放电最多命中的生物数。 / Maximum victims per discharge.") else null

    /** Native SBW modifiers are persisted/synchronized by modifyGunData; never replace ammunition NBT. */
    fun applyWeapon(gun: GunData) {
        val overrides = runCatching { JsonParser.parseString(gun.propertyOverrideString.get()).asJsonObject }.getOrElse { JsonObject() }
        overrides.addProperty("Damage", damage.get())
        overrides.addProperty("RPM", rpm.get())
        ammunition?.let { overrides.addProperty("Magazine", minOf(it.get(), magazineSize?.get() ?: it.get())) }
        explosionDamage?.let { overrides.addProperty("ExplosionDamage", it.get()) }
        outerRadius?.let { overrides.addProperty("ExplosionRadius", it.get()) }
        val encoded = overrides.toString()
        if (gun.propertyOverrideString.get() != encoded) gun.propertyOverrideString.set(encoded)
    }

    fun burstInterval(original: Double) = original * defaultRpm / rpm.get()
    fun effectiveBeamRange() = maxOf(beamRange?.get() ?: 0.0, range?.get() ?: 0.0)
}

object TurretConfigs {
    private var entries: Map<String, TurretConfig> = emptyMap()
    var revision = 0
        private set
    private fun json(path: String): JsonObject = requireNotNull(javaClass.classLoader.getResourceAsStream(path))
        .bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject }
    fun register(builder: ForgeConfigSpec.Builder) {
        val profiles = json("assets/sbw_helldiver/helldivers/profiles.json")
        val ids = listOf("machine_gun_sentry", "gatling_sentry", "rocket_sentry", "autocannon_sentry", "mortar_sentry",
            "gas_mortar_sentry", "ems_mortar_sentry", "flame_sentry", "laser_sentry", "tesla_tower",
            "hmg_emplacement", "anti_tank_emplacement", "grenadier_battlement")
        builder.comment("各炮台独立属性；修改后退出并重新进入存档/重启服务器。 / Per-turret settings; reopen the world or restart the server after editing.").push("turrets")
        entries = ids.associateWith { id ->
            builder.push(id)
            val settings = TurretConfig(builder, id, json("data/sbw_helldiver/sbw/vehicles/$id.json"), profiles.getAsJsonObject(id))
            builder.pop()
            settings
        }
        builder.pop()
    }
    fun changed() { revision++ }
    fun get(id: String) = entries.getValue(id)
    fun forType(type: EntityType<*>): TurretConfig? = ForgeRegistries.ENTITY_TYPES.getKey(type)
        ?.takeIf { it.namespace == "sbw_helldiver" }?.path?.let(entries::get)
}
