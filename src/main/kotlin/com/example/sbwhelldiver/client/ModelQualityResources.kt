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
import net.neoforged.neoforge.event.AddPackFindersEvent
import net.neoforged.fml.ModList
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
            val location=net.minecraft.server.packs.PackLocationInfo(PACK_ID,Component.translatable("pack.sbw_helldiver.low_quality_models"),PackSource.BUILT_IN,java.util.Optional.empty())
            val supplier=object:Pack.ResourcesSupplier {
                override fun openPrimary(info:net.minecraft.server.packs.PackLocationInfo):PackResources = open(info)
                override fun openFull(info:net.minecraft.server.packs.PackLocationInfo,metadata:Pack.Metadata):PackResources = open(info)
                private fun open(info:net.minecraft.server.packs.PackLocationInfo):PackResources {
                    val reduced=ClientModelConfig.SPEC.isLoaded && ClientModelConfig.lowQualityModels.get()
                    return object:PathPackResources(info,root) {
                        override fun isHidden()=true
                        override fun getNamespaces(type:PackType):Set<String> = if(reduced)super.getNamespaces(type) else emptySet()
                        override fun getResource(type:PackType,location:ResourceLocation)=if(reduced)super.getResource(type,location) else null
                        override fun listResources(type:PackType,namespace:String,path:String,output:PackResources.ResourceOutput) {
                            if(reduced)super.listResources(type,namespace,path,output)
                        }
                    }
                }
            }
            Pack.readMetaAndCreate(location,supplier,PackType.CLIENT_RESOURCES,net.minecraft.server.packs.PackSelectionConfig(true,Pack.Position.TOP,true))?.let(consumer::accept)
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
