package com.example.sbwhelldiver.init

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.menu.BeaconLoadoutMenu
import net.minecraft.core.registries.Registries
import net.minecraft.world.InteractionHand
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension
import net.neoforged.neoforge.registries.DeferredRegister

object ModMenus {
    val MENUS = DeferredRegister.create(Registries.MENU, MOD_ID)
    val SAMPLE_CONTAINER = MENUS.register("sample_container") { _ ->
        IMenuTypeExtension.create { id, inventory, buffer -> com.example.sbwhelldiver.menu.SampleContainerMenu(id, inventory, buffer.readEnum(InteractionHand::class.java)) }
    }
    val BEACON_LOADOUT = MENUS.register("beacon_loadout") { _ ->
        IMenuTypeExtension.create { id, inventory, buffer -> BeaconLoadoutMenu(id, inventory, buffer.readEnum(InteractionHand::class.java)) }
    }
}
