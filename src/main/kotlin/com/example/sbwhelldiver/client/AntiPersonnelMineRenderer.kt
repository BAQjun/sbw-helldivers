package com.example.sbwhelldiver.client
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.AntiPersonnelMineEntity
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.GsonUtil
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakerOptions
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BedrockModelBaker
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BakedModelInstance
import com.github.mcmodderanchor.simplebedrockmodel.v1.client.renderer.BedrockModelRenderTypes
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraft.util.Mth
class AntiPersonnelMineRenderer(context: EntityRendererProvider.Context): EntityRenderer<AntiPersonnelMineEntity>(context) {
    init { shadowRadius=.16f }
    override fun getTextureLocation(entity: AntiPersonnelMineEntity)=Model.textures.getValue(entity.kind)
    override fun render(entity: AntiPersonnelMineEntity,yaw: Float,partial: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int) {
        val distance=entity.position().distanceToSqr(net.minecraft.client.Minecraft.getInstance().gameRenderer.mainCamera.position)
        val tier=if(distance>196.0)2 else if(distance>36.0)1 else 0
        val mesh=Model.meshes[Triple(entity.kind,tier,entity.armed)]?:return
        val texture=getTextureLocation(entity)
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(180f-Mth.rotLerp(partial,entity.yRotO,entity.yRot)));pose.scale(.62f,.62f,.62f)
        mesh.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture)),light,true)
        MinefieldClientScene.renderedMine(entity)
        pose.popPose();super.render(entity,yaw,partial,pose,buffers,light)
    }
    object Model: ResourceManagerReloadListener {
        val textures=AntiPersonnelMineEntity.Kind.entries.associateWith{kind->ResourceLocation(MOD_ID,"textures/bedrock/misc/"+when(kind){AntiPersonnelMineEntity.Kind.PERSONNEL->"contact_mine";AntiPersonnelMineEntity.Kind.INCENDIARY->"incendiary_mine";AntiPersonnelMineEntity.Kind.GAS->"gas_mine";else->"anti_tank_mine"}+".png")}
        val meshes=mutableMapOf<Triple<AntiPersonnelMineEntity.Kind,Int,Boolean>,CachedMineMesh>()
        val instances=mutableMapOf<AntiPersonnelMineEntity.Kind,BakedModelInstance>()
        val lods=mutableMapOf<Pair<Int,AntiPersonnelMineEntity.Kind>,BakedModelInstance>()
        override fun onResourceManagerReload(resources: ResourceManager) {
            instances.clear();lods.clear();meshes.clear();MountedMineRenderer.reload(resources)
            for((kind,name) in listOf(AntiPersonnelMineEntity.Kind.PERSONNEL to "contact_mine",AntiPersonnelMineEntity.Kind.INCENDIARY to "incendiary_mine",AntiPersonnelMineEntity.Kind.GAS to "gas_mine",AntiPersonnelMineEntity.Kind.ANTI_TANK to "anti_tank_mine")) {
                resources.open(textures.getValue(kind)).use{input->com.mojang.blaze3d.platform.NativeImage.read(input).use{image->
                    for(tier in 0..2){
                        val suffix=if(tier==0)"" else "_lod$tier"
                        val options=BakerOptions.ofAnimatedBones((1..4).map{"trigger_rod_$it"}.toSet())
                        val model=resources.openAsReader(ResourceLocation(MOD_ID,"models/bedrock/misc/$name$suffix.geo.json")).use{
                            BakedModelInstance(BedrockModelBaker.bake(GsonUtil.CLIENT_GSON.fromJson(it,BedrockModelPOJO::class.java),options))
                        }
                        if(tier==0)instances[kind]=model else lods[tier to kind]=model
                        for(armed in listOf(false,true)){
                            for(i in 1..4)model.getBone("trigger_rod_$i")?.visible=armed
                            meshes[Triple(kind,tier,armed)]=CachedMineMesh.bake(model,image,kind.ordinal)
                        }
                    }
                }}
            }
        }
    }

    companion object {
        val GEOMETRY=ResourceLocation(MOD_ID,"models/bedrock/misc/contact_mine.geo.json")
        val TEXTURE=ResourceLocation(MOD_ID,"textures/bedrock/misc/contact_mine.png")
    }
}
