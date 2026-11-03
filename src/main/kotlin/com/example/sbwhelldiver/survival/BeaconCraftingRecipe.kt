package com.example.sbwhelldiver.survival
import com.google.gson.JsonObject
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.inventory.CraftingContainer
import net.minecraft.world.item.crafting.*
import net.minecraftforge.registries.DeferredRegister
class BeaconCraftingRecipe(private val original:ShapedRecipe):ShapedRecipe(original.id,original.group,original.category(),original.width,original.height,original.ingredients,original.getResultItem(RegistryAccess.EMPTY),original.showNotification()) {
 override fun assemble(input:CraftingContainer,registries:RegistryAccess):net.minecraft.world.item.ItemStack {
  val output=super.assemble(input,registries)
  (0 until input.containerSize).map{input.getItem(it)}.firstOrNull{it.`is`(com.atsuishio.superbwarfare.init.ModItems.SMALL_BATTERY_PACK.get())}?.let{BeaconEnergy.inherit(output,it)}
  return output
 }
 override fun getSerializer():RecipeSerializer<*> = SERIALIZER.get()
 companion object {
  val SERIALIZERS:DeferredRegister<RecipeSerializer<*>> = DeferredRegister.create(Registries.RECIPE_SERIALIZER,"sbw_helldiver")
  val SERIALIZER=SERIALIZERS.register("beacon_energy"){object:RecipeSerializer<BeaconCraftingRecipe>{
   private val delegate=ShapedRecipe.Serializer()
   override fun fromJson(id:ResourceLocation,json:JsonObject)=BeaconCraftingRecipe(delegate.fromJson(id,json))
   override fun fromNetwork(id:ResourceLocation,buf:FriendlyByteBuf)=BeaconCraftingRecipe(requireNotNull(delegate.fromNetwork(id,buf)))
   override fun toNetwork(buf:FriendlyByteBuf,recipe:BeaconCraftingRecipe){delegate.toNetwork(buf,recipe.original)}
  }}
 }
}
