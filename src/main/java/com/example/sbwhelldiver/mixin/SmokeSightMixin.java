package com.example.sbwhelldiver.mixin;
import com.example.sbwhelldiver.effect.SmokeOcclusion;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(LivingEntity.class)
public abstract class SmokeSightMixin {
    @Inject(method="hasLineOfSight",at=@At("HEAD"),cancellable=true)
    private void smokeSight(Entity target,CallbackInfoReturnable<Boolean> cir) {
        if(SmokeOcclusion.blocks((LivingEntity)(Object)this,target))cir.setReturnValue(false);
    }
}
