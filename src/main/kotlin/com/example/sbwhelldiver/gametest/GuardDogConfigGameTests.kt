package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.config.GuardDogConfigs
import com.example.sbwhelldiver.config.HelldiversConfig
import com.example.sbwhelldiver.config.TargetingConfig
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.item.GuardDogBackpack
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.EntityType
import net.minecraft.world.item.ItemStack
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
object GuardDogConfigGameTests {
    @JvmStatic
    @GameTest(template = "sentry_test", timeoutTicks = 20)
    fun targetingModeAndScope(helper: GameTestHelper) {
        val values = HelldiversConfig.SPEC.values
        val mode = values.get<ForgeConfigSpec.EnumValue<TargetingConfig.Mode>>("targeting.mode")
        val entities = values.get<ForgeConfigSpec.ConfigValue<List<String>>>("targeting.entities")
        val attackers = values.get<ForgeConfigSpec.ConfigValue<List<String>>>("targeting.attackers")
        val cow = EntityType.COW.create(helper.level)!!
        val zombie = EntityType.ZOMBIE.create(helper.level)!!
        val machineGun = EntityType.getKey(ModEntities.MACHINE_GUN_SENTRY.get())
        val laser = GuardDogConfigs.Variant.LASER.targetingId
        val ballistic = GuardDogConfigs.Variant.BALLISTIC.targetingId
        val snapshot = ConfigTestSnapshot()
        try {
            mode.set(TargetingConfig.Mode.WHITELIST)
            entities.set(listOf("minecraft:cow", "another_mod:creature"))
            attackers.set(listOf("machine_gun_sentry", "$MOD_ID:laser_guard_dog"))
            TargetingConfig.reload()
            for (attacker in listOf(machineGun, laser)) {
                helper.assertTrue(TargetingConfig.decision(attacker, cow) == true, "The whitelist did not enable a neutral creature")
                helper.assertTrue(TargetingConfig.decision(attacker, zombie) == false, "The whitelist retained an unlisted hostile")
            }
            helper.assertTrue(TargetingConfig.decision(ballistic, zombie) == null, "A scoped rule spilled over to another Guard Dog")

            mode.set(TargetingConfig.Mode.BLACKLIST)
            entities.set(listOf("minecraft:zombie"))
            attackers.set(emptyList())
            TargetingConfig.reload()
            for (attacker in listOf(machineGun, laser, ballistic)) {
                helper.assertTrue(TargetingConfig.decision(attacker, zombie) == false, "The global blacklist missed a listed creature")
                helper.assertTrue(TargetingConfig.decision(attacker, cow) == null, "The blacklist replaced normal hostility for other creatures")
            }

            entities.set(emptyList())
            TargetingConfig.reload()
            helper.assertTrue(TargetingConfig.decision(machineGun, zombie) == null, "An empty list must use ordinary targeting")
        } finally {
            snapshot.close()
            TargetingConfig.reload()
        }
        helper.succeed()
    }

    @JvmStatic
    @GameTest(template = "sentry_test", timeoutTicks = 20)
    fun variantHealthAmmoAndRepair(helper: GameTestHelper) {
        val settings = GuardDogConfigs.get(GuardDogConfigs.Variant.BALLISTIC)
        val health = settings.maxHealth
        val repair = settings.repairTicks
        val ammo = settings.ammunition!!
        val reserves = settings.reserves!!
        val pack = ItemStack(ModItems.GUARD_DOG_BACKPACK.get())
        val gas = ItemStack(ModItems.GAS_GUARD_DOG_BACKPACK.get())
        val gasCapacity = GuardDogBackpack.capacity(gas)
        val snapshot = ConfigTestSnapshot()
        try {
            health.set(180.0)
            repair.set(0)
            ammo.set(21)
            reserves.set(3)
            helper.assertTrue(GuardDogBackpack.health(pack) == 180f, "A fresh pack ignored its configured maximum health")
            GuardDogBackpack.updateHealth(pack, 30f)
            GuardDogBackpack.dock(pack, helper.level.gameTime)
            helper.assertTrue(GuardDogBackpack.health(pack) == 180f, "Zero-tick repair did not restore health on docking")
            GuardDogBackpack.refill(pack)
            helper.assertTrue(GuardDogBackpack.ammo(pack) == 21 && GuardDogBackpack.spareMags(pack) == 3, "Resupply did not use the configured capacities")
            ammo.set(14)
            reserves.set(1)
            helper.assertTrue(GuardDogBackpack.ammo(pack) == 14 && GuardDogBackpack.spareMags(pack) == 1, "Existing pack contents exceeded the reduced capacity")
            helper.assertTrue(GuardDogBackpack.capacity(gas) == gasCapacity, "Ballistic capacity leaked into the gas variant")
        } finally {
            snapshot.close()
        }
        helper.succeed()
    }
}
