package com.example.sbwhelldiver.client;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
/** Sky geometry uses temporary depth for self-occlusion; the sky pass clears it before terrain. */
public final class DestroyerRenderTypes extends RenderType {
 private DestroyerRenderTypes(){super("unused",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,false,()->{},()->{});}
 public static final RenderType HULL=create("sbw_destroyer",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,1048576,false,false,
  CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_SOLID_SHADER).setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("sbw_helldiver","textures/mesh/super_destroyer.png"),false,false))
   .setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setWriteMaskState(COLOR_DEPTH_WRITE).createCompositeState(false));
 public static final RenderType GLOW=create("sbw_orbital_glow",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,32768,false,false,
  CompositeState.builder().setShaderState(RENDERTYPE_EYES_SHADER).setTextureState(new TextureStateShard(ResourceLocation.fromNamespaceAndPath("sbw_helldiver","textures/mesh/white.png"),false,false))
   .setTransparencyState(LIGHTNING_TRANSPARENCY).setCullState(NO_CULL).setWriteMaskState(COLOR_WRITE).createCompositeState(false));
}
