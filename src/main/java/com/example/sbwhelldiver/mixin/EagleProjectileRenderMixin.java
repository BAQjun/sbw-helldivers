package com.example.sbwhelldiver.mixin;

import com.atsuishio.superbwarfare.client.ClientRenderHandler;
import com.example.sbwhelldiver.entity.EagleCannonRoundEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.projectile.Projectile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Aircraft shells originate at the Eagle, even when their damage owner is a player. */
@Mixin(value = ClientRenderHandler.class, remap = false)
public abstract class EagleProjectileRenderMixin {
    @Inject(method = "transformVirtualRenderPosition", at = @At("HEAD"), cancellable = true, remap = false)
    private static void helldivers$keepEagleMuzzle(PoseStack pose, Projectile projectile, float partialTick, CallbackInfo ci) {
        // The native renderer also handles handheld guns. Their global muzzle offset is unrelated
        // to the Eagle and would pull its first few tracer frames towards the player's weapon.
        if (projectile instanceof EagleCannonRoundEntity) ci.cancel();
    }
}
