package com.example.sbwhelldiver.survival
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModItems
import com.google.gson.JsonObject
import net.minecraft.core.RegistryAccess
import net.minecraft.core.registries.Registries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.resources.ResourceLocation
import net.minecraft.util.RandomSource
import net.minecraft.util.GsonHelper
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.*
import net.minecraft.world.level.Level
import net.minecraftforge.registries.DeferredRegister
/** One weighted result is reserved per batch, persisted before energy is consumed. */
data class SampleRecipe(val machine:String,val ingredient:Ingredient,val count:Int,val ticks:Int,val outcomes:List<Outcome>,val recipeId:ResourceLocation=ResourceLocation(MOD_ID,"sample_processing")):Recipe<Container> {
 init {require(machine in setOf("bioprocessor","refinery"));require(count in 1..64&&ticks in 1..72000);require(outcomes.isNotEmpty()&&outcomes.sumOf{it.chance}==100)}
 data class Outcome(val tier:Int,val chance:Int,val count:Int,val maxCount:Int=count) {
  init {require(tier in 0..2&&chance in 1..100&&count in 1..4096&&maxCount in count..4096)}
  fun stack()=ItemStack(when(tier){0->ModItems.COMMON_SAMPLE.get();1->ModItems.RARE_SAMPLE.get();else->ModItems.SUPER_SAMPLE.get()},count)
 }
 fun roll(random:RandomSource):ItemStack{var roll=random.nextInt(100);for(o in outcomes){roll-=o.chance;if(roll<0)return o.stack().copyWithCount(if(o.count==o.maxCount)o.count else random.nextInt(o.maxCount-o.count+1)+o.count)};error("Invalid weights")}
 override fun matches(input:Container,level:Level)=ingredient.test(input.getItem(0))&&input.getItem(0).count>=count
 override fun assemble(input:Container,registries:RegistryAccess)=ItemStack.EMPTY
 override fun canCraftInDimensions(w:Int,h:Int)=false
 override fun getResultItem(registries:RegistryAccess)=outcomes.first().stack()
 override fun getId()=recipeId
 override fun getSerializer()=SERIALIZER.get()
 override fun getType()=TYPE.get()
 override fun isSpecial()=true
 companion object {
  val TYPES=DeferredRegister.create(Registries.RECIPE_TYPE,MOD_ID)
  val SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,MOD_ID)
  val TYPE=TYPES.register("sample_processing"){object:RecipeType<SampleRecipe>{override fun toString()="$MOD_ID:sample_processing"}}
  val SERIALIZER=SERIALIZERS.register("sample_processing"){object:RecipeSerializer<SampleRecipe>{
   override fun fromJson(id:ResourceLocation,j:JsonObject)=SampleRecipe(GsonHelper.getAsString(j,"machine"),Ingredient.fromJson(j.get("ingredient")),GsonHelper.getAsInt(j,"count"),GsonHelper.getAsInt(j,"ticks"),GsonHelper.getAsJsonArray(j,"outcomes").map{val o=it.asJsonObject;Outcome(GsonHelper.getAsInt(o,"tier"),GsonHelper.getAsInt(o,"chance"),GsonHelper.getAsInt(o,"count"),GsonHelper.getAsInt(o,"max_count",GsonHelper.getAsInt(o,"count")))},id)
   override fun fromNetwork(id:ResourceLocation,b:FriendlyByteBuf)=SampleRecipe(b.readUtf(32),Ingredient.fromNetwork(b),b.readVarInt(),b.readVarInt(),b.readList{Outcome(it.readVarInt(),it.readVarInt(),it.readVarInt(),it.readVarInt())},id)
   override fun toNetwork(b:FriendlyByteBuf,r:SampleRecipe){b.writeUtf(r.machine,32);r.ingredient.toNetwork(b);b.writeVarInt(r.count);b.writeVarInt(r.ticks);b.writeCollection(r.outcomes){buf,o->buf.writeVarInt(o.tier);buf.writeVarInt(o.chance);buf.writeVarInt(o.count);buf.writeVarInt(o.maxCount)}}
  }}
  fun forMachine(level:Level,machine:MachineDeployment)=level.recipeManager.getAllRecipesFor(TYPE.get()).filter{it.machine==machine.id}
  fun accepts(level:Level,machine:MachineDeployment,stack:ItemStack)=forMachine(level,machine).any{it.ingredient.test(stack)}
 }
}
