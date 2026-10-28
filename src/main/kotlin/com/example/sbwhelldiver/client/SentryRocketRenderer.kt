package com.example.sbwhelldiver.client

import com.atsuishio.superbwarfare.client.renderer.projectile.BasicProjectileRenderer
import com.example.sbwhelldiver.entity.SentryRocketEntity
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.resources.ResourceLocation

class SentryRocketRenderer(context: EntityRendererProvider.Context) : BasicProjectileRenderer<SentryRocketEntity>(context) {
    override fun getTextureLocation(entity: SentryRocketEntity) = TEXTURE
    companion object {
        val TEXTURE = ResourceLocation.fromNamespaceAndPath("superbwarfare", "textures/bedrock/projectile/small_rocket.png")
    }
}
