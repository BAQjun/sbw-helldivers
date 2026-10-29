package com.example.sbwhelldiver.client;

import com.atsuishio.superbwarfare.client.renderer.ModParticleRenderTypes;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.renderer.texture.TextureManager;

/** Keep SBW's soft particles in the translucent pass without replacing Iris's particle shader. */
public final class ExplosionParticleRenderType {
    private ExplosionParticleRenderType() {}

    // A stable queue key lets already-living smoke survive shader-pack toggles.
    public static final ParticleRenderType SOFT = new ParticleRenderType() {
        @Override
        public BufferBuilder begin(Tesselator builder, TextureManager textures) {
            BufferBuilder vertices = (ShaderPackCompat.INSTANCE.active()
                ? ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
                : ModParticleRenderTypes.INSTANCE.getPARTICLE_SHEET_SOFT_TRANSLUCENT()).begin(builder, textures);
            // A fading smoke edge must not hide the next puff behind it.
            RenderSystem.depthMask(false);
            return vertices;
        }

        @Override
        public boolean isTranslucent() { return true; }

        @Override
        public String toString() { return "SBW_HELLDIVERS_SOFT_PARTICLES"; }
    };
}
