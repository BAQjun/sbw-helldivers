package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.HellpodCapEntity
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.GsonUtil
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakerOptions
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BedrockModelBaker
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BakedModelInstance
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

/** Reuse the actual cap geometry, UVs, skull and white catches, rather than an item proxy. */
class HellpodCapRenderer(context: EntityRendererProvider.Context) : EntityRenderer<HellpodCapEntity>(context) {
    override fun getTextureLocation(entity: HellpodCapEntity) = TEXTURE

    override fun render(entity: HellpodCapEntity, yaw: Float, partial: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int) {
        val model = Model.instance ?: return
        pose.pushPose()
        val halfThickness = HellpodCapEntity.MODEL_THICKNESS / 2
        pose.translate(0.0, halfThickness, 0.0)
        pose.mulPose(Axis.YP.rotationDegrees(180f - Mth.rotLerp(partial, entity.yRotO, entity.yRot)))
        pose.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial, entity.previousVisualPitch, entity.visualPitch)))
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partial, entity.previousVisualRoll, entity.visualRoll)))
        pose.translate(0.0, -halfThickness, 0.0)
        model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, OverlayTexture.NO_OVERLAY)
        pose.popPose()
        super.render(entity, yaw, partial, pose, buffers, light)
    }

    object Model : ResourceManagerReloadListener {
        var instance: BakedModelInstance? = null
            private set
        override fun onResourceManagerReload(resources: ResourceManager) {
            resources.openAsReader(GEOMETRY).use { reader ->
                val pojo = GsonUtil.CLIENT_GSON.fromJson(reader, BedrockModelPOJO::class.java)
                instance = BakedModelInstance(BedrockModelBaker.bake(pojo, BakerOptions.defaults()))
            }
        }
    }

    companion object {
        val GEOMETRY = ResourceLocation.fromNamespaceAndPath(MOD_ID, "models/bedrock/misc/hellpod_cap.geo.json")
        val TEXTURE = ResourceLocation.fromNamespaceAndPath(MOD_ID, "textures/bedrock/vehicle/hellpod_v3.png")
    }
}
