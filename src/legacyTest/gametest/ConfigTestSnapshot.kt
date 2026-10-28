package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.config.HelldiversConfig
import com.electronwill.nightconfig.core.CommentedConfig
import com.electronwill.nightconfig.toml.TomlParser
import com.electronwill.nightconfig.toml.TomlWriter
import net.minecraftforge.common.ForgeConfigSpec

/** Keep temporary test values off disk and away from Forge's asynchronous file watcher. */
internal class ConfigTestSnapshot : AutoCloseable {
    private val original = ForgeConfigSpec::class.java.getDeclaredField("childConfig").run {
        isAccessible = true; get(HelldiversConfig.SPEC) as CommentedConfig
    }
    init { HelldiversConfig.SPEC.setConfig(TomlParser().parse(TomlWriter().writeToString(original))) }
    override fun close() { HelldiversConfig.SPEC.setConfig(original) }
}
