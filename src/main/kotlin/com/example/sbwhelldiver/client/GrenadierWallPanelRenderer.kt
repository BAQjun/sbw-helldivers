package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.entity.GrenadierWallPanelEntity
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation
/** Surfaces are already drawn as part of the reviewed battlement model. */
class GrenadierWallPanelRenderer(context:EntityRendererProvider.Context):EntityRenderer<GrenadierWallPanelEntity>(context) {
    override fun getTextureLocation(entity:GrenadierWallPanelEntity)=ResourceLocation.fromNamespaceAndPath("sbw_helldiver","textures/bedrock/vehicle/grenadier_hellpod_v1.png")
}
