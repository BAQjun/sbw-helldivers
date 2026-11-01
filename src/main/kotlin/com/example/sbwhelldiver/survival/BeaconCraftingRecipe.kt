package com.example.sbwhelldiver.survival

import com.mojang.serialization.MapCodec
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.*
import net.neoforged.neoforge.registries.DeferredRegister

/** Standard shaped-recipe display and matching, with battery charge copied into the result. */
class BeaconCraftingRecipe(private val original:ShapedRecipe):ShapedRecipe(original.group,original.category(),original.pattern,
    ItemStack(com.example.sbwhelldiver.init.ModItems.BEACON.get()),original.showNotification()) {
    override fun assemble(input:CraftingInput,registries:HolderLookup.Provider):ItemStack {
        val output=super.assemble(input,registries)
        input.items().firstOrNull{it.`is`(com.atsuishio.superbwarfare.init.ModItems.SMALL_BATTERY_PACK.get())}?.let{BeaconEnergy.inherit(output,it)}
        return output
    }
    override fun getSerializer():RecipeSerializer<*> = SERIALIZER.get()
    companion object {
        val SERIALIZERS:DeferredRegister<RecipeSerializer<*>> = DeferredRegister.create(Registries.RECIPE_SERIALIZER,"sbw_helldiver")
        val SERIALIZER=SERIALIZERS.register("beacon_energy"){_->object:RecipeSerializer<BeaconCraftingRecipe>{
            override fun codec():MapCodec<BeaconCraftingRecipe> = ShapedRecipe.Serializer.CODEC.xmap(::BeaconCraftingRecipe){it.original}
            override fun streamCodec():StreamCodec<RegistryFriendlyByteBuf,BeaconCraftingRecipe> = ShapedRecipe.Serializer.STREAM_CODEC.map(::BeaconCraftingRecipe){it.original}
        }}
    }
}
