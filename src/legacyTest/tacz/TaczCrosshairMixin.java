package com.example.sbwhelldiver.mixin.tacz;

import com.example.sbwhelldiver.entity.HmgEmplacementEntity;
import com.example.sbwhelldiver.entity.PlayerHellpodEntity;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TACZ's crosshair handler receives even cancelled vanilla overlay events. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.event.RenderCrosshairEvent", remap = false)
public abstract class TaczCrosshairMixin {
    @Inject(method = "onRenderOverlay", at = @At("HEAD"), cancellable = true)
    private static void helldivers$crosshair(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && (player.getVehicle() instanceof HmgEmplacementEntity || player.getVehicle() instanceof PlayerHellpodEntity)) ci.cancel();
    }
}
