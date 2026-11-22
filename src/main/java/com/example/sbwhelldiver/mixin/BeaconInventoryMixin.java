package com.example.sbwhelldiver.mixin;

import com.example.sbwhelldiver.item.BeaconQuickSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
abstract class BeaconInventoryMixin {
    @Shadow @Final public Player player;

    @Inject(method = "getSelected", at = @At("HEAD"), cancellable = true)
    private void sbwHelldiver$selectedBeacon(CallbackInfoReturnable<ItemStack> callback) {
        ItemStack beacon = BeaconQuickSlot.INSTANCE.selected(this.player);
        if (beacon != null) callback.setReturnValue(beacon);
    }

    @Inject(method = "removeFromSelected", at = @At("HEAD"), cancellable = true)
    private void sbwHelldiver$dropSelectedBeacon(boolean entireStack, CallbackInfoReturnable<ItemStack> callback) {
        ItemStack removed = BeaconQuickSlot.INSTANCE.removeSelected(this.player);
        if (removed != null) callback.setReturnValue(removed);
    }
}
