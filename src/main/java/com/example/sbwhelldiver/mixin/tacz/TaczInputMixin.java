package com.example.sbwhelldiver.mixin.tacz;

import com.example.sbwhelldiver.entity.HmgEmplacementEntity;
import com.example.sbwhelldiver.entity.PlayerHellpodEntity;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.tacz.guns.util.InputExtraCheck", remap = false)
public abstract class TaczInputMixin {
    @Inject(method = "isInGame", at = @At("HEAD"), cancellable = true)
    private static void helldivers$mountedInput(CallbackInfoReturnable<Boolean> cir) {
        var player = Minecraft.getInstance().player;
        if (player != null && (player.getVehicle() instanceof HmgEmplacementEntity || player.getVehicle() instanceof PlayerHellpodEntity)) cir.setReturnValue(false);
    }
}
