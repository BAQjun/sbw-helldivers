package com.example.sbwhelldiver.client

import com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance
import com.atsuishio.superbwarfare.entity.vehicle.VehicleModelEntry
import com.atsuishio.superbwarfare.resource.model.VehicleLODModelReloadListener
import com.atsuishio.superbwarfare.resource.model.VehicleModelReloadListener
import com.atsuishio.superbwarfare.resource.vehicle.VehicleResource
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.config.ClientModelConfig
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.PathPackResources
import net.minecraft.server.packs.repository.Pack
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraftforge.event.AddPackFindersEvent
import net.minecraftforge.fml.ModList
import java.util.Collections
import java.util.WeakHashMap

/** A hidden built-in overlay lets every model loader use the same quality setting. */
object ModelQualityResources : ResourceManagerReloadListener {
    const val PACK_ID = "sbw_helldiver:low_quality_models"
    private val refreshed = Collections.newSetFromMap(WeakHashMap<AbstractHellpodEntity, Boolean>())

    fun addPack(event: AddPackFindersEvent) {
        if (event.packType != PackType.CLIENT_RESOURCES) return
        val root = ModList.get().getModFileById(MOD_ID).file.findResource("resourcepacks/low_quality_models")
        event.addRepositorySource { consumer ->
            Pack.readMetaAndCreate(PACK_ID, Component.translatable("pack.sbw_helldiver.low_quality_models"),
                true, { id ->
                    // Snapshot the setting when opening a pack. The live resource manager
                    // keeps its old geometry until the replacement reload has completed.
                    val reduced = ClientModelConfig.SPEC.isLoaded && ClientModelConfig.lowQualityModels.get()
                    object : PathPackResources(id, root, true) {
                        override fun isHidden() = true
                        override fun getNamespaces(type: PackType): Set<String> =
                            if (reduced) super.getNamespaces(type) else emptySet()
                        override fun getResource(type: PackType, location: ResourceLocation) =
                            if (reduced) super.getResource(type, location) else null
                        override fun listResources(type: PackType, namespace: String, path: String, output: PackResources.ResourceOutput) {
                            if (reduced) super.listResources(type, namespace, path, output)
                        }
                    }
                }, PackType.CLIENT_RESOURCES, Pack.Position.TOP, PackSource.BUILT_IN)?.let(consumer::accept)
        }
    }

    override fun onResourceManagerReload(resources: ResourceManager) {
        refreshed.clear()
        CachedMeshRenderer.clear()
        BinaryEntityTextures.reload(resources)
    }

    /** SBW caches model entries on entities; update existing pods after resource reloads too. */
    fun refresh(entity: AbstractHellpodEntity) {
        if (entity in refreshed) return
        val entries = entity.getModelEntries() as MutableList<VehicleModelEntry>
        var index = 0
        val replacements = VehicleResource.compute(entity).getModels().mapNotNull { pojo ->
            val path = pojo.model ?: return@mapNotNull null
            val texture = pojo.texture ?: return@mapNotNull null
            val baked = if (pojo.distance > 0) VehicleLODModelReloadListener.getModel(path)
                else VehicleModelReloadListener.getModel(path)
            baked ?: return@mapNotNull null
            val old = entries.getOrNull(index++)
            if (old != null && old.instance.baseModel() === baked && old.texture == texture &&
                old.emissiveTexture == pojo.emissiveTexture && old.lodDistance == pojo.distance) old
            else VehicleModelEntry(VehicleModelInstance(baked), texture, pojo.emissiveTexture, pojo.distance)
        }
        if (replacements.isEmpty()) return
        if (entries != replacements) { entries.clear(); entries.addAll(replacements) }
        refreshed.add(entity)
    }
}
