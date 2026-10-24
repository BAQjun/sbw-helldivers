package com.example.sbwhelldiver

import com.example.sbwhelldiver.config.HelldiversConfig
import com.example.sbwhelldiver.init.ModCreativeTabs
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.init.ModSounds
import com.example.sbwhelldiver.init.ModParticles
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import net.minecraftforge.fml.ModLoadingContext
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.config.ModConfig
import thedarkcolour.kotlinforforge.forge.MOD_BUS

/**
 * 模组 id。
 *
 * **放在文件顶层而不是 [Mod] 的 companion 里**，有两个实际原因：
 * - `@Mod(...)` 的注解参数引用不了被注解类自己的 companion 成员，编译不过；
 * - 客户端类经常要**同时** import Forge 的 [Mod] 注解和我们这个 `Mod` 类，
 *   两者撞名会直接报「Conflicting import」。做成顶层常量，两边都不用绕。
 */
const val MOD_ID = "sbw_helldiver"

/**
 * 模组日志。放在顶层与 [MOD_ID] 并列，任何包里的类都能直接 import，
 * 不必再绕一层伴生对象。
 */
val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

/**
 * 模组主入口。
 *
 * 本模组是卓越前线（Superb Warfare）的附属，加入绝地潜兵2 的战备：
 * 投掷信标 → 空投仓从天而降 → 插入大地 → 炮台从舱内升起展开。
 *
 * 载具实体直接继承卓越前线的 `AutoAimableEntity`，索敌 / 转塔 / 弹道 / 音效
 * 全部复用本体，本模组只负责「怎么把它投下来」。
 */
@Mod(MOD_ID)
class Mod {

    init {
        val bus = MOD_BUS
        com.example.sbwhelldiver.compat.TaczBootstrap.register()
        com.example.sbwhelldiver.init.ModBlocks.BLOCKS.register(bus)
        ModItems.ITEMS.register(bus)
        ModEntities.ENTITIES.register(bus)
        ModSounds.SOUNDS.register(bus)
        ModParticles.PARTICLES.register(bus)
        com.example.sbwhelldiver.init.ModEffects.EFFECTS.register(bus)
        ModCreativeTabs.TABS.register(bus)
        com.example.sbwhelldiver.init.ModMenus.MENUS.register(bus)
        com.example.sbwhelldiver.network.BeaconNetwork.register()
        // Retire only the removed standalone call; old saves can load without a missing-item prompt.
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener<net.minecraftforge.registries.MissingMappingsEvent> { event ->
            event.getMappings(net.minecraft.core.registries.Registries.ITEM, MOD_ID)
                .filter { it.key.path == "supply_hellpod_stratagem" }.forEach { it.ignore() }
        }

        // 服务端配置：索敌规则、空投延迟、埋深、存活时间等。
        // 用 SERVER 类型而不是 COMMON —— 这些参数会同步到客户端，
        // 且必须在客户端与服务端之间保持一致，否则空投落点会两边算得不一样。
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, HelldiversConfig.SPEC)
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, com.example.sbwhelldiver.config.ClientModelConfig.SPEC)
        bus.addListener<net.minecraftforge.fml.event.config.ModConfigEvent.Loading> { event ->
            if (event.config.modId == MOD_ID && event.config.type == ModConfig.Type.SERVER) com.example.sbwhelldiver.config.TurretConfigs.changed()
        }
        bus.addListener<net.minecraftforge.fml.event.config.ModConfigEvent.Reloading> { event ->
            if (event.config.modId == MOD_ID && event.config.type == ModConfig.Type.SERVER) com.example.sbwhelldiver.config.TurretConfigs.changed()
        }
    }
}
