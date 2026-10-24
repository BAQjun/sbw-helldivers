package com.example.sbwhelldiver.init

import com.example.sbwhelldiver.MOD_ID
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.RegistryObject

/**
 * 创造模式标签页。
 *
 * 战备物品目前**只从这里拿**，没有合成配方 —— 先把功能跑通、数值好调，
 * 等玩法定型再考虑加配方。
 */
object ModCreativeTabs {

    val TABS: DeferredRegister<CreativeModeTab> =
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID)

    val MAIN: RegistryObject<CreativeModeTab> = TABS.register("main") {
        CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.${MOD_ID}.main"))
            .icon { ItemStack(ModItems.BEACON.get()) }
            .displayItems { _, output ->
                output.accept(ModItems.BEACON.get())
                output.accept(ModItems.EAGLE_CLUSTER_BOMB.get())
                output.accept(ModItems.EAGLE_110MM_ROCKET_PODS.get())
                output.accept(ModItems.EAGLE_500KG_BOMB.get())
                output.accept(ModItems.EAGLE_AIRSTRIKE.get())
                output.accept(ModItems.EAGLE_STRAFING_RUN.get())
                output.accept(ModItems.SHIELD_GENERATOR.get())
                output.accept(ModItems.GRENADIER_BATTLEMENT.get())
                output.accept(ModItems.HMG_EMPLACEMENT.get())
                output.accept(ModItems.ANTI_TANK_EMPLACEMENT.get())
                output.accept(ModItems.MACHINE_GUN_SENTRY.get())
                output.accept(ModItems.GATLING_SENTRY.get())
                output.accept(ModItems.ROCKET_SENTRY.get())
                output.accept(ModItems.AUTOCANNON_SENTRY.get())
                output.accept(ModItems.MORTAR_SENTRY.get())
                output.accept(ModItems.GAS_MORTAR_SENTRY.get())
                output.accept(ModItems.EMS_MORTAR_SENTRY.get())
                output.accept(ModItems.FLAME_SENTRY.get())
                output.accept(ModItems.LASER_SENTRY.get())
                output.accept(ModItems.TESLA_TOWER.get())
                output.accept(ModItems.ANTI_PERSONNEL_MINEFIELD.get())
                output.accept(ModItems.INCENDIARY_MINEFIELD.get())
                output.accept(ModItems.GAS_MINEFIELD.get())
                output.accept(ModItems.ANTI_TANK_MINEFIELD.get())
            }
            .build()
    }
}
