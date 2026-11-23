package com.example.sbwhelldiver.mixin;

import com.example.sbwhelldiver.item.HoverPackFlight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class HoverPackFloatingMixin {
    @Shadow public ServerPlayer player;
    @Shadow private int aboveGroundTickCount;

    @Inject(method = "tick", at = @At("HEAD"))
    private void sbwHelldiver$allowHoverPackFlight(CallbackInfo ci) {
        if (HoverPackFlight.INSTANCE.controlsFlight(player)) {
            aboveGroundTickCount = 0;
        }
    }
}
