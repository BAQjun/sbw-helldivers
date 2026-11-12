package com.example.sbwhelldiver.item



import com.example.sbwhelldiver.MOD_ID

import net.minecraft.core.registries.Registries

import net.minecraft.resources.ResourceLocation

import net.minecraft.tags.TagKey

import net.minecraft.world.entity.EquipmentSlot

import net.minecraft.world.entity.player.Player

import net.minecraft.world.item.Item

import net.minecraft.world.item.ItemStack

import net.minecraftforge.fml.ModList



/** A player can equip one backpack stratagem across the chest and back slots. */

object StratagemBackpacks {

    val tag = TagKey.create(Registries.ITEM, ResourceLocation(MOD_ID, "backpack_stratagems"))

    val usesCurios get() = ModList.get().isLoaded("curios")



    internal fun createGuardDog(properties: Item.Properties, laser: Boolean = false, stun: Boolean = false, flame: Boolean = false, gas: Boolean = false): GuardDogBackpack =

        if (usesCurios) BackpackCurios.createGuardDog(properties, laser, stun, flame, gas) else GuardDogBackpack(properties, laser, stun, flame, gas)



    internal fun chest(player: Player): ItemStack? =

        player.getItemBySlot(EquipmentSlot.CHEST).takeIf { it.`is`(tag) }



    internal fun back(player: Player): ItemStack? =

        if (usesCurios) BackpackCurios.equipped(player) else null



    fun equipped(player: Player): ItemStack? = back(player) ?: chest(player)

}
