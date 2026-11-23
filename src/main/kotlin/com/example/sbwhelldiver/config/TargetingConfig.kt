package com.example.sbwhelldiver.config

import com.example.sbwhelldiver.MOD_ID
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.neoforged.neoforge.common.ModConfigSpec

object TargetingConfig {
    enum class Mode { WHITELIST, BLACKLIST }

    private lateinit var mode: ModConfigSpec.EnumValue<Mode>
    private lateinit var entities: ModConfigSpec.ConfigValue<List<String>>
    private lateinit var attackers: ModConfigSpec.ConfigValue<List<String>>

    private data class Rule(val mode: Mode, val entities: Set<ResourceLocation>, val attackers: Set<ResourceLocation>) {
        fun decision(attacker: ResourceLocation, target: ResourceLocation): Boolean? {
            if (entities.isEmpty() || attackers.isNotEmpty() && attacker !in attackers) return null
            val listed = target in entities
            return when (mode) {
                Mode.WHITELIST -> listed
                Mode.BLACKLIST -> if (listed) false else null
            }
        }
    }

    @Volatile private var rule = Rule(Mode.WHITELIST, emptySet(), emptySet())

    fun register(builder: ModConfigSpec.Builder) {
        builder.comment("炮台与护卫犬的自动索敌名单。 / Automatic targeting list for sentries and Guard Dogs.").push("targeting")
        mode = builder.comment(
            "WHITELIST 只允许列出的生物，BLACKLIST 排除列出的生物。entities 为空时沿用原索敌。",
            "WHITELIST allows only the listed types. BLACKLIST excludes the listed types. An empty entity list uses normal targeting."
        ).defineEnum("mode", Mode.WHITELIST)
        entities = builder.comment("目标生物注册 ID，例如 minecraft:zombie 或 mymod:guard。 / Target entity IDs, e.g. minecraft:zombie or mymod:guard.")
            .defineListAllowEmpty("entities", emptyList<String>(), { "minecraft:zombie" }) { it is String && parseTargetId(it) != null }
        attackers = builder.comment(
            "应用名单的战备 ID，例如 sbw_helldiver:laser_sentry 或 sbw_helldiver:gas_guard_dog。留空则应用于所有炮台和护卫犬。",
            "Affected stratagem IDs, e.g. sbw_helldiver:laser_sentry or sbw_helldiver:gas_guard_dog. Empty applies to all sentries and Guard Dogs."
        ).defineListAllowEmpty("attackers", emptyList<String>(), { "sbw_helldiver:machine_gun_sentry" }) { it is String && parseAttackerId(it) != null }
        builder.pop()
    }

    private fun parseTargetId(id: String) = ResourceLocation.tryParse(id)?.takeIf { it.path.isNotEmpty() }

    private fun parseAttackerId(id: String) = parseTargetId(if (':' in id) id else "$MOD_ID:$id")

    fun reload() {
        rule = Rule(mode.get(), entities.get().mapNotNull(::parseTargetId).toSet(),
            attackers.get().mapNotNull(::parseAttackerId).toSet())
    }

    fun clear() {
        rule = Rule(Mode.WHITELIST, emptySet(), emptySet())
    }

    fun decision(attacker: ResourceLocation, target: LivingEntity): Boolean? =
        rule.decision(attacker, EntityType.getKey(target.type))
}
