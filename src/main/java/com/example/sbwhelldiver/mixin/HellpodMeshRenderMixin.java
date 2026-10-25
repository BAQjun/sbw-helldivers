package com.example.sbwhelldiver.mixin;

import com.example.sbwhelldiver.client.CachedMeshRenderer;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedGeometryChunk;
import com.github.mcmodderanchor.simplebedrockmodel.v2.common.model.baked.BakedGeometryChunkRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Only our hellpod mesh consumers opt in; other mods keep the native renderer. */
@Mixin(value=BakedGeometryChunkRenderer.class,remap=false)
public abstract class HellpodMeshRenderMixin {
    @Inject(method="renderVertexChunk",at=@At("HEAD"),cancellable=true,remap=false)
    private void helldivers$renderIndexedMesh(BakedGeometryChunk chunk, PoseStack pose, VertexConsumer consumer,
                                             int light, int overlay, float r, float g, float b, float a, CallbackInfo ci) {
        if(consumer instanceof CachedMeshRenderer.Consumer mesh) {
            mesh.renderMesh(chunk,pose,light,overlay,r,g,b,a);
            ci.cancel();
        }
    }
    @Inject(method="renderQuadChunk(Lcom/github/mcmodderanchor/simplebedrockmodel/v2/common/model/baked/BakedGeometryChunk;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFFZ)V",at=@At("HEAD"),cancellable=true,remap=false)
    private void helldivers$batchCubeFaces(BakedGeometryChunk chunk,PoseStack pose,VertexConsumer consumer,
                                          int light,int overlay,float r,float g,float b,float a,boolean skipCull,CallbackInfo ci) {
        if(consumer instanceof CachedMeshRenderer.QuadConsumer mesh) {
            mesh.renderQuads(chunk,pose,light,overlay,r,g,b,a,skipCull);
            ci.cancel();
        }
    }

}
