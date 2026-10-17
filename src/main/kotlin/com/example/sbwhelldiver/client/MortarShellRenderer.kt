package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.MortarShellEntity
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.GsonUtil
import com.github.mcmodderanchor.simplebedrockmodel.v1.common.resource.pojo.BedrockModelPOJO
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakerOptions
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BedrockModelBaker
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.runtime.BakedModelInstance
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import org.joml.Quaternionf
import org.joml.Vector3f

class MortarShellRenderer(context: EntityRendererProvider.Context) : EntityRenderer<MortarShellEntity>(context) {
    override fun getTextureLocation(entity: MortarShellEntity) = when(entity) {
        is com.example.sbwhelldiver.entity.GasMortarShellEntity -> GAS_TEXTURE
        is com.example.sbwhelldiver.entity.EmsMortarShellEntity -> EMS_TEXTURE
        else -> TEXTURE
    }
    override fun render(entity: MortarShellEntity,yaw: Float,partial: Float,pose: PoseStack,buffers: MultiBufferSource,light: Int) {
        val model = Model.instance ?: return
        val velocity=entity.deltaMovement.normalize()
        pose.pushPose()
        pose.mulPose(Quaternionf().rotationTo(Vector3f(0f,1f,0f),Vector3f(velocity.x.toFloat(),velocity.y.toFloat(),velocity.z.toFloat())))
        pose.scale(1.25f,1.25f,1.25f)
        pose.translate(0.0,-.548179,0.0)
        model.renderToBuffer(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(entity))),light,OverlayTexture.NO_OVERLAY)
        pose.popPose()
    }
    object Model : ResourceManagerReloadListener {
        var instance: BakedModelInstance?=null
        override fun onResourceManagerReload(resources: ResourceManager) {
            resources.openAsReader(ResourceLocation(MOD_ID,"models/bedrock/projectile/mortar_round.geo.json")).use {
                instance=BakedModelInstance(BedrockModelBaker.bake(GsonUtil.CLIENT_GSON.fromJson(it,BedrockModelPOJO::class.java),BakerOptions.defaults()))
            }
        }
    }
    companion object {
        val GAS_TEXTURE=ResourceLocation(MOD_ID,"textures/bedrock/projectile/gas_mortar_round.png")
        val EMS_TEXTURE=ResourceLocation(MOD_ID,"textures/bedrock/projectile/ems_mortar_round.png")
        val TEXTURE=ResourceLocation(MOD_ID,"textures/bedrock/projectile/mortar_round.png") }
}
