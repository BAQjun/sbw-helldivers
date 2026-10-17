package com.example.sbwhelldiver.init

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.item.StratagemItem
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.Item
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
import net.minecraftforge.registries.RegistryObject

/**
 * 战备物品。
 *
 * 四种炮台战备卡装载于可重复使用的信标球。
 * 它们只在创造模式标签页里给，生存下暂不做合成配方。
 */
object ModItems {

    val ITEMS: DeferredRegister<Item> = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID)

    val EAGLE_AIRSTRIKE=ITEMS.register("eagle_airstrike_stratagem") { StratagemItem(Item.Properties().stacksTo(16),dropId("eagle_airstrike")) }
    val EAGLE_STRAFING_RUN=ITEMS.register("eagle_strafing_run_stratagem") { StratagemItem(Item.Properties().stacksTo(16),dropId("eagle_strafing_run")) }

    val SHIELD_GENERATOR = ITEMS.register("shield_generator_stratagem") { StratagemItem(Item.Properties().stacksTo(16),dropId("shield_generator")) }

    val BEACON: RegistryObject<Item> = ITEMS.register("stratagem_beacon") {
        com.example.sbwhelldiver.item.BeaconItem(Item.Properties().stacksTo(1))
    }

    /** 战备：机枪哨戒炮。 */
    val MACHINE_GUN_SENTRY: RegistryObject<Item> = ITEMS.register("machine_gun_sentry_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16), dropId("machine_gun_sentry"))
    }

    /** 战备：加特林哨戒炮。 */
    val GATLING_SENTRY: RegistryObject<Item> = ITEMS.register("gatling_sentry_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16), dropId("gatling_sentry"))
    }

    val ROCKET_SENTRY: RegistryObject<Item> = ITEMS.register("rocket_sentry_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16), dropId("rocket_sentry"))
    }

    val AUTOCANNON_SENTRY: RegistryObject<Item> = ITEMS.register("autocannon_sentry_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16), dropId("autocannon_sentry"))
    }

    val MORTAR_SENTRY: RegistryObject<Item> = ITEMS.register("mortar_sentry_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16), dropId("mortar_sentry"))
    }

    val GAS_MORTAR_SENTRY = ITEMS.register("gas_mortar_sentry_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16),dropId("gas_mortar_sentry"))
    }
    val EMS_MORTAR_SENTRY = ITEMS.register("ems_mortar_sentry_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16),dropId("ems_mortar_sentry"))
    }
    val FLAME_SENTRY = ITEMS.register("flame_sentry_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16),dropId("flame_sentry"))
    }
    val LASER_SENTRY = ITEMS.register("laser_sentry_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16),dropId("laser_sentry"))
    }
    val TESLA_TOWER = ITEMS.register("tesla_tower_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16),dropId("tesla_tower"))
    }
    val ANTI_PERSONNEL_MINEFIELD = ITEMS.register("anti_personnel_minefield_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16),dropId("anti_personnel_minefield"))
    }
    val INCENDIARY_MINEFIELD = ITEMS.register("incendiary_minefield_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16),dropId("incendiary_minefield"))
    }
    val GAS_MINEFIELD = ITEMS.register("gas_minefield_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16),dropId("gas_minefield"))
    }
    val ANTI_TANK_MINEFIELD = ITEMS.register("anti_tank_minefield_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16),dropId("anti_tank_minefield"))
    }
    val ANTI_TANK_EMPLACEMENT = ITEMS.register("anti_tank_emplacement_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16),dropId("anti_tank_emplacement"))
    }
    val HMG_EMPLACEMENT = ITEMS.register("hmg_emplacement_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16),dropId("hmg_emplacement"))
    }
    val GRENADIER_BATTLEMENT = ITEMS.register("grenadier_battlement_stratagem") {
        StratagemItem(Item.Properties().stacksTo(16),dropId("grenadier_battlement"))
    }
    /** 落点实体与物品同名同命名空间，写全名免得两边改漏一个。 */
    private fun dropId(entityId: String): ResourceLocation =
        ResourceLocation(MOD_ID, entityId)
}
