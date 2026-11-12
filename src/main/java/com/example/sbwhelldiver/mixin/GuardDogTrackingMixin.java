package com.example.sbwhelldiver.mixin;

import com.example.sbwhelldiver.entity.GuardDogEntity;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep rotation-only updates from cancelling a drone's pending movement. */
@Mixin(ClientPacketListener.class)
abstract class GuardDogTrackingMixin {
    @WrapOperation(method = "handleMoveEntity", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/world/entity/Entity;lerpTo(DDDFFIZ)V"))
    private void sbwHelldiver$preserveDroneDestination(Entity entity, double x, double y, double z,
            float yaw, float pitch, int steps, boolean teleport, Operation<Void> original,
            ClientboundMoveEntityPacket packet) {
        if (!packet.hasPosition() && entity instanceof GuardDogEntity drone) {
            x = drone.lerpTargetX();
            y = drone.lerpTargetY();
            z = drone.lerpTargetZ();
        }
        original.call(entity, x, y, z, yaw, pitch, steps, teleport);
    }
}
