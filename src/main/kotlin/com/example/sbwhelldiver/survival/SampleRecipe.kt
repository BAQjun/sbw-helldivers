package com.example.sbwhelldiver.survival

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModItems
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.util.RandomSource
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.*
import net.minecraft.world.level.Level
import net.neoforged.neoforge.registries.DeferredRegister

/** A single weighted choice per batch. The chosen output is persisted before work starts. */
data class SampleRecipe(val machine:String,val ingredient:Ingredient,val count:Int,val ticks:Int,val outcomes:List<Outcome>):Recipe<SingleRecipeInput> {
    init { require(machine in setOf("bioprocessor","refinery"));require(outcomes.isNotEmpty()&&outcomes.sumOf{it.chance}==100) }
    data class Outcome(val tier:Int,val chance:Int,val count:Int,val maxCount:Int=count) {
        fun stack()=ItemStack(when(tier){0->ModItems.COMMON_SAMPLE.get();1->ModItems.RARE_SAMPLE.get();else->ModItems.SUPER_SAMPLE.get()},count)
        companion object {val CODEC:Codec<Outcome> = RecordCodecBuilder.create { b->b.group(
            Codec.intRange(0,2).fieldOf("tier").forGetter(Outcome::tier),
            Codec.intRange(1,100).fieldOf("chance").forGetter(Outcome::chance),
            Codec.intRange(1,4096).fieldOf("count").forGetter(Outcome::count),
            Codec.intRange(0,4096).optionalFieldOf("max_count",0).forGetter { if(it.maxCount==it.count)0 else it.maxCount }).apply(b){tier,chance,count,max -> Outcome(tier,chance,count,if(max==0)count else max).also{require(it.maxCount>=count)} } }}
    }
    fun roll(random:RandomSource):ItemStack {var roll=random.nextInt(100);for(o in outcomes){roll-=o.chance;if(roll<0)return o.stack().copyWithCount(if(o.maxCount==o.count)o.count else random.nextInt(o.maxCount-o.count+1)+o.count)};error("Invalid sample weights")}
    override fun matches(input:SingleRecipeInput,level:Level)=ingredient.test(input.item())&&input.item().count>=count
    override fun assemble(input:SingleRecipeInput,registries:HolderLookup.Provider)=ItemStack.EMPTY
    override fun canCraftInDimensions(w:Int,h:Int)=false
    override fun getResultItem(registries:HolderLookup.Provider)=outcomes.first().stack()
    override fun getSerializer()=SERIALIZER.get()
    override fun getType()=TYPE.get()
    override fun isSpecial()=true
    companion object {
        val TYPES=DeferredRegister.create(Registries.RECIPE_TYPE,MOD_ID)
        val SERIALIZERS=DeferredRegister.create(Registries.RECIPE_SERIALIZER,MOD_ID)
        val TYPE=TYPES.register("sample_processing"){_->object:RecipeType<SampleRecipe>{override fun toString()="$MOD_ID:sample_processing"}}
        val SERIALIZER=SERIALIZERS.register("sample_processing"){_->object:RecipeSerializer<SampleRecipe>{
            override fun codec()=CODEC
            override fun streamCodec():StreamCodec<RegistryFriendlyByteBuf,SampleRecipe> = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec())
        }}
        val CODEC:MapCodec<SampleRecipe> = RecordCodecBuilder.mapCodec { b->b.group(
            Codec.STRING.fieldOf("machine").forGetter(SampleRecipe::machine),Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(SampleRecipe::ingredient),
            Codec.intRange(1,64).fieldOf("count").forGetter(SampleRecipe::count),Codec.intRange(1,72000).fieldOf("ticks").forGetter(SampleRecipe::ticks),
            Outcome.CODEC.listOf().fieldOf("outcomes").forGetter(SampleRecipe::outcomes)).apply(b,::SampleRecipe) }
        fun forMachine(level:Level,machine:MachineDeployment)=level.recipeManager.getAllRecipesFor(TYPE.get()).map{it.value}.filter{it.machine==machine.id}
        fun accepts(level:Level,machine:MachineDeployment,stack:ItemStack)=forMachine(level,machine).any{it.ingredient.test(stack)}
    }
}
