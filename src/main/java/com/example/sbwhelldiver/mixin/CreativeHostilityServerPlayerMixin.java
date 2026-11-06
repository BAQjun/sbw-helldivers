package com.example.sbwhelldiver.mixin;

import com.example.sbwhelldiver.entity.CreativeHostilityProbe;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerPlayer.class)
public abstract class CreativeHostilityServerPlayerMixin {
    @Inject(method = "isCreative", at = @At("HEAD"), cancellable = true)
    private void helldivers$testCreativeEligibility(CallbackInfoReturnable<Boolean> cir) {
        // Vanilla and modded AI can reject Creative through this separate predicate.
        if (CreativeHostilityProbe.isProbing((ServerPlayer) (Object) this)) {
            cir.setReturnValue(false);
        }
    }
}
