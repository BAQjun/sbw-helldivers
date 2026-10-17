package com.example.sbwhelldiver.client;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.RegisterShadersEvent;
import java.io.IOException;
public final class ShieldRenderTypes extends RenderType {
    private static ShaderInstance shader;
    private ShieldRenderTypes(){super("unused",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
    public static final RenderType ENERGY=create("sbw_shield_energy",DefaultVertexFormat.POSITION_COLOR_TEX,VertexFormat.Mode.QUADS,262144,false,true,
        CompositeState.builder().setShaderState(new ShaderStateShard(()->shader)).setTransparencyState(TRANSLUCENT_TRANSPARENCY)
            .setDepthTestState(LEQUAL_DEPTH_TEST).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
    public static ShaderInstance shader(){return shader;}
    public static void registerShaders(RegisterShadersEvent event)throws IOException{
        shader=null;event.registerShader(new ShaderInstance(event.getResourceProvider(),new ResourceLocation("sbw_helldiver","shield_energy"),DefaultVertexFormat.POSITION_COLOR_TEX),s->shader=s);
    }
}
