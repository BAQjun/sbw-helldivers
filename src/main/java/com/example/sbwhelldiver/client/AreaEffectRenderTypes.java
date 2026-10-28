package com.example.sbwhelldiver.client;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import java.io.IOException;
import java.util.function.Supplier;
/** Animated procedural flames and ray-integrated cloud density, without bitmap sprites. */
public final class AreaEffectRenderTypes extends RenderType {
    private static ShaderInstance mistShader,jetShader;
    private AreaEffectRenderTypes() { super("unused",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{}); }
    public static final RenderType JET=jet();
    public static final RenderType MIST=effect("field_volume",()->mistShader,true);
    private static RenderType effect(String name,Supplier<ShaderInstance> shader,boolean sort) {
        return create("sbw_"+name,DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,262144,false,sort,
            CompositeState.builder().setShaderState(new ShaderStateShard(shader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    }
    private static RenderType jet() {
        return create("sbw_flame_jet",DefaultVertexFormat.POSITION_TEX_COLOR,VertexFormat.Mode.QUADS,65536,false,false,
            CompositeState.builder().setShaderState(new ShaderStateShard(()->jetShader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    }
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        mistShader=null;jetShader=null;
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("sbw_helldiver","field_volume"),DefaultVertexFormat.POSITION_TEX_COLOR),s->mistShader=s);
        event.registerShader(new ShaderInstance(event.getResourceProvider(),ResourceLocation.fromNamespaceAndPath("sbw_helldiver","flame_jet"),DefaultVertexFormat.POSITION_TEX_COLOR),s->jetShader=s);
    }
    public static ShaderInstance jetShader() { return jetShader; }
    public static boolean isReady() { return mistShader!=null && jetShader!=null; }
}
