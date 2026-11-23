package com.example.sbwhelldiver.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Function;

final class TacticalTerminalRenderTypes extends RenderType {
    private static final Function<ResourceLocation, RenderType> DISPLAY = Util.memoize(texture ->
        // The pixels on the glass do not contribute geometry to the block-breaking decal.
        create("sbw_tactical_terminal_display", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS,
            256, false, false, CompositeState.builder()
                .setShaderState(RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                .setTextureState(new TextureStateShard(texture, false, false))
                .setCullState(NO_CULL)
                .setLightmapState(LIGHTMAP)
                .setOverlayState(OVERLAY)
                .createCompositeState(false)));

    private TacticalTerminalRenderTypes() {
        super("unused", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, false, () -> {}, () -> {});
    }

    static RenderType display(ResourceLocation texture) {
        return DISPLAY.apply(texture);
    }
}
