package com.example.sbwhelldiver.mixin;

import com.example.sbwhelldiver.item.BeaconQuickSlot;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Gui.class)
abstract class BeaconHotbarMixin {
    @WrapWithCondition(method = "renderItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V",
            ordinal = 1))
    private boolean sbwHelldiver$showNormalSelection(GuiGraphics gui, ResourceLocation sprite, int x, int y, int w, int h) {
        Player player = Minecraft.getInstance().player;
        return player == null || BeaconQuickSlot.INSTANCE.selected(player) == null;
    }
}
