package com.example.sbwhelldiver.mixin.tacz;

import com.example.sbwhelldiver.entity.HmgEmplacementEntity;
import com.example.sbwhelldiver.entity.PlayerHellpodEntity;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** TACZ also smooths FOV with an empty hand; mounted sights own their camera. */
@Pseudo
@Mixin(targets = "com.tacz.guns.client.event.CameraSetupEvent", remap = false)
public abstract class TaczCameraMixin {
    @Inject(method = {"applyScopeMagnification", "applyGunModelFovModifying",
            "applyLevelCameraAnimation", "applyItemInHandCameraAnimation",
            "applyCameraRecoil", "onComputeMovementFov"}, at = @At("HEAD"), cancellable = true)
    private static void helldivers$camera(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && (player.getVehicle() instanceof HmgEmplacementEntity || player.getVehicle() instanceof PlayerHellpodEntity)) ci.cancel();
    }
}
