package com.example.sbwhelldiver.mixin;
import com.example.sbwhelldiver.effect.SmokeOcclusion;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(TargetingConditions.class)
public abstract class SmokeTargetConditionsMixin {
    @Inject(method="test",at=@At("HEAD"),cancellable=true)
    private void smokeTarget(LivingEntity observer,LivingEntity target,CallbackInfoReturnable<Boolean> cir) {
        if(SmokeOcclusion.preventsAcquisition(observer,target))cir.setReturnValue(false);
    }
}
