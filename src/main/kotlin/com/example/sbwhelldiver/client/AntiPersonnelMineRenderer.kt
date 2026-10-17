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
    override fun getTextureLocation(entity: AntiPersonnelMineEntity)=when(entity.kind) {
        AntiPersonnelMineEntity.Kind.INCENDIARY -> ResourceLocation(MOD_ID,"textures/bedrock/misc/incendiary_mine.png")
        AntiPersonnelMineEntity.Kind.GAS -> ResourceLocation(MOD_ID,"textures/bedrock/misc/gas_mine.png")
        AntiPersonnelMineEntity.Kind.ANTI_TANK -> ResourceLocation(MOD_ID,"textures/bedrock/misc/anti_tank_mine.png")
        else -> TEXTURE
    }
    override fun render(entity: AntiPersonnelMineEntity,yaw: Float,partial: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int) {
        val model=Model.instances[entity.kind] ?: return
        val texture=getTextureLocation(entity)
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(180f-Mth.rotLerp(partial,entity.yRotO,entity.yRot)));pose.scale(.62f,.62f,.62f)
        for(i in 1..4)model.getBone("trigger_rod_$i")?.visible=entity.armed
        model.renderToBuffer(pose,buffers,RenderType.entityCutoutNoCull(texture),BedrockModelRenderTypes.polyMeshCutout(texture),light,OverlayTexture.NO_OVERLAY)
        MinefieldClientScene.renderedMine(entity)
        pose.popPose();super.render(entity,yaw,partial,pose,buffers,light)
    }
    object Model: ResourceManagerReloadListener {
        val instances=mutableMapOf<AntiPersonnelMineEntity.Kind,BakedModelInstance>()
        override fun onResourceManagerReload(resources: ResourceManager) {
            instances.clear()
            for((kind,name) in listOf(AntiPersonnelMineEntity.Kind.PERSONNEL to "contact_mine",AntiPersonnelMineEntity.Kind.INCENDIARY to "incendiary_mine",AntiPersonnelMineEntity.Kind.GAS to "gas_mine",AntiPersonnelMineEntity.Kind.ANTI_TANK to "anti_tank_mine")) {
                resources.openAsReader(ResourceLocation(MOD_ID,"models/bedrock/misc/$name.geo.json")).use {
                    instances[kind]=BakedModelInstance(BedrockModelBaker.bake(GsonUtil.CLIENT_GSON.fromJson(it,BedrockModelPOJO::class.java),BakerOptions.defaults()))
                }
            }
        }
    }

    companion object {
        val GEOMETRY=ResourceLocation(MOD_ID,"models/bedrock/misc/contact_mine.geo.json")
        val TEXTURE=ResourceLocation(MOD_ID,"textures/bedrock/misc/contact_mine.png")
    }
}
