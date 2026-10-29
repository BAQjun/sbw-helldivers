package com.example.sbwhelldiver.mixin;

import com.atsuishio.superbwarfare.client.particle.CustomFlareParticle;
import com.example.sbwhelldiver.client.ExplosionParticleRenderType;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** SBW uses this same animated particle for explosion dust, smoke trails and fireballs. */
@Mixin(value = CustomFlareParticle.class, remap = false)
public abstract class ExplosionSmokeRenderMixin {
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true, remap = false)
    private void helldivers$compatibleSmoke(CallbackInfoReturnable<ParticleRenderType> callback) {
        callback.setReturnValue(ExplosionParticleRenderType.SOFT);
    }
}
