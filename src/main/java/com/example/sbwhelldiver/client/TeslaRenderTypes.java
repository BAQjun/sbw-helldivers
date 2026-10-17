package com.example.sbwhelldiver.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import java.io.IOException;
import java.util.function.Supplier;

/** Additive light, normal depth testing, and no translucent depth writes. */
public final class TeslaRenderTypes extends RenderType {
    private static ShaderInstance arcShader;
    private static ShaderInstance plasmaShader;
    private TeslaRenderTypes() {
        super("tesla_unused", DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS,
                256, false, false, () -> {}, () -> {});
    }
    public static final RenderType ARC = light("tesla_arc", () -> arcShader);
    public static final RenderType PLASMA = light("tesla_plasma", () -> plasmaShader);

    // KotlinForForge subscribes Kotlin objects, not ordinary Java classes.
    // ClientSetup forwards the registration event here.
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        arcShader = null;
        plasmaShader = null;
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("sbw_helldiver", "tesla_arc"), DefaultVertexFormat.POSITION_COLOR_TEX), shader -> arcShader=shader);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),
                new ResourceLocation("sbw_helldiver", "tesla_plasma"), DefaultVertexFormat.POSITION_COLOR_TEX), shader -> plasmaShader=shader);
    }

    public static boolean isReady() {
        return arcShader != null && plasmaShader != null;
    }

    private static RenderType light(String name, Supplier<ShaderInstance> shader) {
        return create("sbw_" + name, DefaultVertexFormat.POSITION_COLOR_TEX, VertexFormat.Mode.QUADS,
                262144, false, false, CompositeState.builder()
                        .setShaderState(new ShaderStateShard(shader))
                        .setTransparencyState(LIGHTNING_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }
}
