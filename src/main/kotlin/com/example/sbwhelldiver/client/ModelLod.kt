package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.config.ClientModelConfig
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.atsuishio.superbwarfare.client.model.entity.VehicleModelInstance
import com.atsuishio.superbwarfare.entity.vehicle.VehicleModelEntry
import com.atsuishio.superbwarfare.resource.model.VehicleModelReloadListener
import com.atsuishio.superbwarfare.resource.vehicle.VehicleResource
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.GsonUtil
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockAnimationFile
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedBedrockModel
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakerOptions
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.*
import java.nio.file.Files
import java.util.WeakHashMap

/** Distance affects visuals only. Both tiers retain the animation skeleton. */
object ModelLod:ResourceManagerReloadListener {
    fun reduced(distanceSquared:Double):Boolean {
        if(!ClientModelConfig.SPEC.isLoaded)return false
        if(ClientModelConfig.lowQualityModels.get())return true
        val distance=ClientModelConfig.modelLodDistance.get().toDouble()
        return ClientModelConfig.modelLod.get() && distanceSquared>=distance*distance
    }
    fun mineTier(distanceSquared:Double):Int {
        if(!ClientModelConfig.SPEC.isLoaded)return 0
        if(!ClientModelConfig.modelLod.get())return if(ClientModelConfig.lowQualityModels.get())1 else 0
        // Preserve the existing short mine thresholds in dense minefields.
        val scale=ClientModelConfig.modelLodDistance.get()/32.0
        return if(distanceSquared>196.0*scale*scale)2
            else if(ClientModelConfig.lowQualityModels.get() || distanceSquared>36.0*scale*scale)1 else 0
    }
    private val models=hashMapOf<ResourceLocation,BakedBedrockModel>()
    private val boneNames=hashMapOf<ResourceLocation,List<String>>()
    private val entries=WeakHashMap<AbstractHellpodEntity,VehicleModelEntry>()
    override fun onResourceManagerReload(resources:ResourceManager) {
        models.clear();entries.clear();boneNames.clear()
        val root=net.neoforged.fml.ModList.get().getModFileById(MOD_ID).file.findResource("resourcepacks/low_quality_models/assets/$MOD_ID/models/bedrock/vehicle")
        Files.list(root).use{paths->paths.filter{it.fileName.toString().endsWith(".geo.json")}.forEach{path->
            val name=path.fileName.toString()
            val text=Files.readString(path)
            val pojo=GsonUtil.CLIENT_GSON.fromJson(text,BedrockModelPOJO::class.java)
            val animation=ResourceLocation.fromNamespaceAndPath(MOD_ID,"animations/bedrock/vehicle/"+name.removeSuffix(".geo.json")+".animation.json")
            val file=resources.getResource(animation).orElse(null)?.openAsReader()?.use{GsonUtil.CLIENT_GSON.fromJson(it,BedrockAnimationFile::class.java)}
            val options=(if(file!=null)BakerOptions.ofAnimationFile(file)else BakerOptions.defaults()).withPreservedBoneRegexes(VehicleModelReloadListener.PATTERNS)
            val id=ResourceLocation.fromNamespaceAndPath(MOD_ID,"models/bedrock/vehicle/$name")
            models[id]=BakedBedrockModel.bake(pojo,options)
            boneNames[id]=com.google.gson.JsonParser.parseString(text).asJsonObject["minecraft:geometry"].asJsonArray[0].asJsonObject["bones"].asJsonArray.map{it.asJsonObject["name"].asString}
        }}
    }
    fun verifySkeletons():Int {
        var count=0
        for((id,model)in models) {
            val high=VehicleModelReloadListener.getModel(id)?:continue
            val a=VehicleModelInstance(high);val b=VehicleModelInstance(model)
            for(name in boneNames.getValue(id))check(a.getIndex(name)==b.getIndex(name)){"LOD animation skeleton differs: $id / $name"}
            count++
        }
        return count
    }
    fun entry(entity:AbstractHellpodEntity):VehicleModelEntry? {
        if(!reduced(entity.position().distanceToSqr(net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera.position)))return null
        // First-person operators always keep their original sight and geometry.
        if(entity.hasPassenger(net.minecraft.client.Minecraft.getInstance().player?:return null))return null
        return entries.getOrPut(entity){
            val pojo=VehicleResource.compute(entity).getModels().firstOrNull()?:return null
            val model=models[pojo.model]?:return null
            VehicleModelEntry(VehicleModelInstance(model),pojo.texture?:return null,pojo.emissiveTexture,0)
        }
    }
}
