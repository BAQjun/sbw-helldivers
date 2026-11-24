package com.example.sbwhelldiver.mixin;

import com.example.sbwhelldiver.item.HoverPackFlight;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class HoverPackFloatingMixin {
    @ModifyExpressionValue(method = "tick", at = @At(value = "FIELD",
        target = "Lnet/minecraft/server/network/ServerGamePacketListenerImpl;clientIsFloating:Z",
        opcode = Opcodes.GETFIELD))
    private boolean sbwHelldiver$allowHoverPackFlight(boolean floating) {
        if (!floating) return false;
        ServerGamePacketListenerImpl connection = (ServerGamePacketListenerImpl) (Object) this;
        return !HoverPackFlight.INSTANCE.controlsFlight(connection.getPlayer());
    }
}
