package com.example.sbwhelldiver.compat.jei

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.survival.*
import mezz.jei.api.*
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder
import mezz.jei.api.gui.drawable.IDrawable
import mezz.jei.api.gui.ingredient.IRecipeSlotsView
import mezz.jei.api.helpers.IGuiHelper
import mezz.jei.api.recipe.*
import mezz.jei.api.recipe.category.IRecipeCategory
import mezz.jei.api.registration.*
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack

/** Discovered by JEI only; no common-side class references this plugin. */
@JeiPlugin
class SampleJeiPlugin:IModPlugin {
    override fun getPluginUid()=ResourceLocation.fromNamespaceAndPath(MOD_ID,"samples")
    override fun registerCategories(r:IRecipeCategoryRegistration) {
        val gui=r.jeiHelpers.guiHelper
        for(m in MachineDeployment.entries)r.addRecipeCategories(SampleCategory(m,gui))
        r.addRecipeCategories(DeploymentCategory(gui))
    }
    override fun registerRecipes(r:IRecipeRegistration) {
        val level=Minecraft.getInstance().level?:return
        for(m in MachineDeployment.entries)r.addRecipes(type(m),SampleRecipe.forMachine(level,m))
        r.addRecipes(DEPLOYMENT,MachineDeployment.entries.map(::DeploymentRecipe))
    }
    override fun registerRecipeCatalysts(r:IRecipeCatalystRegistration) {
        for(m in MachineDeployment.entries)r.addRecipeCatalyst(MachineContainerItem.stack(m),type(m))
        r.addRecipeCatalyst(ItemStack(ModItems.BEACON.get()),DEPLOYMENT)
    }
    companion object {
        val BIO=RecipeType.create(MOD_ID,"bioprocessor",SampleRecipe::class.java)
        val REFINERY=RecipeType.create(MOD_ID,"refinery",SampleRecipe::class.java)
        val DEPLOYMENT=RecipeType.create(MOD_ID,"equipment_deployment",DeploymentRecipe::class.java)
        fun type(m:MachineDeployment)=if(m==MachineDeployment.BIOPROCESSOR)BIO else REFINERY
    }
}
data class DeploymentRecipe(val machine:MachineDeployment)
private class SampleCategory(val machine:MachineDeployment,gui:IGuiHelper):IRecipeCategory<SampleRecipe> {
    private val background=gui.createBlankDrawable(178,78)
    private val icon=gui.createDrawableIngredient(VanillaTypes.ITEM_STACK,MachineContainerItem.stack(machine))
    override fun getRecipeType()=SampleJeiPlugin.type(machine)
    override fun getTitle()=Component.translatable(machine.translation)
    override fun getIcon():IDrawable=icon
    override fun getBackground():IDrawable=background
    override fun getWidth()=178
    override fun getHeight()=78
    override fun setRecipe(b:IRecipeLayoutBuilder,r:SampleRecipe,focus:IFocusGroup) {
        b.addInputSlot(8,14).addItemStacks(r.ingredient.items.map{it.copyWithCount(r.count)}).setStandardSlotBackground()
        r.outcomes.forEachIndexed{i,out->b.addOutputSlot(78+i*33,14).addItemStack(out.stack()).setStandardSlotBackground().addTooltipCallback{_,tips->tips.add(Component.translatable("jei.sbw_helldiver.chance",out.chance))}}
    }
    override fun draw(r:SampleRecipe,slots:IRecipeSlotsView,g:GuiGraphics,x:Double,y:Double) {
        val font=Minecraft.getInstance().font
        g.drawString(font,"→",45,18,0x666666,false)
        for((i,out)in r.outcomes.withIndex())g.drawString(font,"${out.chance}%",77+i*33,37,0x444444,false)
        g.drawString(font,Component.translatable("jei.sbw_helldiver.processing",r.ticks/20.0,SampleMachineBlockEntity.POWER_PER_TICK),3,54,0x444444,false)
        g.drawString(font,Component.translatable("jei.sbw_helldiver.one_result"),3,67,0x666666,false)
    }
}
private class DeploymentCategory(gui:IGuiHelper):IRecipeCategory<DeploymentRecipe> {
    private val background=gui.createBlankDrawable(178,82)
    private val icon=gui.createDrawableIngredient(VanillaTypes.ITEM_STACK,ItemStack(ModItems.BEACON.get()))
    override fun getRecipeType()=SampleJeiPlugin.DEPLOYMENT
    override fun getTitle()=Component.translatable("jei.sbw_helldiver.deployment")
    override fun getIcon():IDrawable=icon
    override fun getBackground():IDrawable=background
    override fun getWidth()=178
    override fun getHeight()=82
    override fun setRecipe(b:IRecipeLayoutBuilder,r:DeploymentRecipe,focus:IFocusGroup) {
        for((i,m)in r.machine.materials.withIndex())b.addInputSlot(8+i%3*24,12+i/3*26).addItemStack(ItemStack(m.item,m.count)).setStandardSlotBackground()
        b.addOutputSlot(140,25).addItemStack(MachineContainerItem.stack(r.machine)).setStandardSlotBackground()
    }
    override fun draw(r:DeploymentRecipe,slots:IRecipeSlotsView,g:GuiGraphics,x:Double,y:Double) {
        val font=Minecraft.getInstance().font;g.drawString(font,"→",106,29,0x666666,false)
        g.drawString(font,Component.translatable(r.machine.translation),6,69,0x444444,false)
    }
}
