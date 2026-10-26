package com.example.sbwhelldiver.asset

import com.example.sbwhelldiver.MOD_ID
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.Vec3
import net.minecraftforge.registries.ForgeRegistries

data class SentryProfile(
    val id: String, val model: String, val podHeight: Double,
    val deployTicks: Int, val retractTicks: Int, val reloadTicks: Int,
    val aimHeight: Double, val muzzleOffsets: List<Vec3>, val nozzles: List<Vec3>,
    val nozzleDirections: List<Vec3>, val nozzleModelPositions: List<Vec3>, val nozzleBones: List<String>,
    val burstSize: Int, val burstInterval: Double, val burstPause: Double, val totalAmmo: Int,
    val shellOffset: Vec3?, val shellDirection: Vec3?,
    val shellBone: String?, val shellModelPosition: Vec3?,
    val muzzleBone: String?, val muzzleModelPosition: Vec3?,
) {
    val isRocket get() = id == "rocket_sentry"
    val isGatling get() = id == "gatling_sentry"
    val isCannon get() = id == "autocannon_sentry"
    val isLaser get() = id == "laser_sentry"
    val isTesla get() = id == "tesla_tower"
    val isFlame get() = id == "flame_sentry"
    val isGasMortar get() = id == "gas_mortar_sentry"
    val isEmsMortar get() = id == "ems_mortar_sentry"
    val isMortar get() = id == "mortar_sentry" || isGasMortar || isEmsMortar
    val mortarScatterRadius get() = if (isGasMortar || isEmsMortar) 5.0 else 10.0
    val isSupply get() = id == "supply_hellpod" || id == "player_hellpod"
}

/** Generated from reviewed geometry and animation; shared by both logical sides. */
object AssetCatalog {
    private fun vector(element: com.google.gson.JsonElement?): Vec3? = element?.takeUnless { it.isJsonNull }?.asJsonArray?.let {
        Vec3(it[0].asDouble,it[1].asDouble,it[2].asDouble)
    }
    private fun vectors(array: com.google.gson.JsonArray): List<Vec3> = array.map {
        val a = it.asJsonArray
        Vec3(a[0].asDouble, a[1].asDouble, a[2].asDouble)
    }
    val profiles: Map<String, SentryProfile> = run {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("assets/$MOD_ID/helldivers/profiles.json"))
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            JsonParser.parseReader(reader).asJsonObject.entrySet().associate { (id, element) ->
                val p = element.asJsonObject
                id to SentryProfile(id, p["model"].asString, p["pod_height"].asDouble,
                    p["deploy_ticks"].asInt, p["retract_ticks"].asInt, p["reload_ticks"].asInt,
                    p["aim_height"].asDouble, vectors(p["muzzle_offsets"].asJsonArray), vectors(p["nozzles"].asJsonArray),
                    vectors(p["nozzle_directions"].asJsonArray), vectors(p["nozzle_model_positions"].asJsonArray), p["nozzle_bones"].asJsonArray.map { it.asString },
                    p["burst_size"].asInt, p["burst_interval_ticks"].asDouble, p["burst_pause_ticks"].asDouble, p["total_ammo"].asInt,
                    vector(p["shell_offset"]), vector(p["shell_direction"]),
                    p["shell_bone"]?.takeUnless { it.isJsonNull }?.asString, vector(p["shell_model_position"]),
                    p["muzzle_bone"]?.takeUnless { it.isJsonNull }?.asString, vector(p["muzzle_model_position"]))
            }
        }
    }
    fun profile(type: EntityType<*>): SentryProfile = requireNotNull(ForgeRegistries.ENTITY_TYPES.getKey(type)).path.let {if(it=="player_hellpod")profiles.getValue("supply_hellpod").copy(id=it) else profiles.getValue(it)}
    val audibleEffects: Set<String> = run {
        val stream = requireNotNull(javaClass.classLoader.getResourceAsStream("assets/$MOD_ID/sounds.json"))
        stream.bufferedReader(Charsets.UTF_8).use { JsonParser.parseReader(it).asJsonObject.keySet().toSet() }
    }
    val soundRanges:Map<String,Float> = run {
        val stream=requireNotNull(javaClass.classLoader.getResourceAsStream("assets/$MOD_ID/sounds.json"))
        stream.bufferedReader(Charsets.UTF_8).use { reader ->
            JsonParser.parseReader(reader).asJsonObject.entrySet().associate { (id,raw) ->
                id to raw.asJsonObject.getAsJsonArray("sounds").maxOfOrNull { sound ->
                    if(sound.isJsonObject) sound.asJsonObject.get("attenuation_distance")?.asFloat ?: 16f else 16f
                }.let{maxOf(64f,it?:64f)}
            }
        }
    }
    fun sound(effect: String) = ResourceLocation(MOD_ID, effect)
}
