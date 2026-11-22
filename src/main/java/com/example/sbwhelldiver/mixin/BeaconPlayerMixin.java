package com.example.sbwhelldiver.mixin;

import com.example.sbwhelldiver.item.BeaconQuickSlot;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class BeaconPlayerMixin {
    @Inject(method = "setItemSlot", at = @At("HEAD"), cancellable = true)
    private void sbwHelldiver$replaceSelectedBeacon(EquipmentSlot slot, ItemStack stack, CallbackInfo callback) {
        if (slot == EquipmentSlot.MAINHAND && BeaconQuickSlot.INSTANCE.replaceSelected((Player) (Object) this, stack)) {
            callback.cancel();
        }
    }
}
