package com.example.sbwhelldiver.mixin;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(PathNavigation.class)
public interface SmokeNavigationAccess {
    @Accessor("speedModifier") double getSmokeSpeedModifier();
    @Accessor("mob") Mob getSmokeMob();
}
