package com.example.sbwhelldiver.mixin;

import com.example.sbwhelldiver.entity.CreativeHostilityProbe;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class CreativeHostilityPlayerMixin {
    @Inject(method = "canBeSeenAsEnemy", at = @At("HEAD"), cancellable = true)
    private void helldivers$testCreativeEligibility(CallbackInfoReturnable<Boolean> cir) {
        Player player = (Player) (Object) this;
        if (CreativeHostilityProbe.isProbing(player)) {
            // Preserve the LivingEntity checks and ignore Creative's ability-based immunity.
            cir.setReturnValue(!player.isInvulnerable() && player.canBeSeenByAnyone());
        }
    }
}
