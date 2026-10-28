package com.example.sbwhelldiver.client

import com.atsuishio.superbwarfare.client.renderer.TextureBrightnessHandler
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.asset.AssetCatalog
import com.google.gson.JsonParser
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener

/** Prepares native 30%-brightness wreck textures while the resource loading screen is active. */
object WreckTextureCache : ResourceManagerReloadListener {
    val preparedTextures = mutableMapOf<ResourceLocation,ResourceLocation>()
    override fun onResourceManagerReload(manager: ResourceManager) {
        val previous = preparedTextures.toMap()
        preparedTextures.clear()
        val started = System.nanoTime()
        for (profile in AssetCatalog.profiles.values.filterNot { it.isSupply }) {
            val location = ResourceLocation.fromNamespaceAndPath(MOD_ID,"sbw/vehicles/${profile.id}.json")
            val resource = manager.getResource(location).orElse(null) ?: continue
            val root = resource.openAsReader().use { JsonParser.parseReader(it).asJsonObject }
            for (model in root.getAsJsonArray("Models")) {
                val texture = model.asJsonObject.get("Texture")?.asString?.let(ResourceLocation::tryParse) ?: continue
                val cached = TextureBrightnessHandler.getBrightenedTexture(texture,.3f)
                // Native's cache is not invalidated on reload. Refresh our own entries so
                // a changed resource pack never leaves these sentries with stale wreck colours.
                if (cached != texture && previous.containsKey(texture)) {
                    val refreshed = manager.getResource(texture).orElseThrow().open().use { stream ->
                        NativeImage.read(stream).use { original -> TextureBrightnessHandler.brightenImage(original,.3f) }
                    }
                    Minecraft.getInstance().textureManager.register(cached,DynamicTexture(refreshed))
                }
                if (cached != texture) preparedTextures[texture] = cached
                else LOGGER.warn("Could not prepare sentry wreck texture {}",texture)
            }
        }
        LOGGER.info("WRECK_TEXTURE_CACHE_READY: {} textures prepared in {} ms during resource loading",preparedTextures.size,(System.nanoTime()-started)/1_000_000.0)
    }
}
