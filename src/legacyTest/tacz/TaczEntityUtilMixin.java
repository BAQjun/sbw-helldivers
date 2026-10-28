package com.example.sbwhelldiver.mixin.tacz;

import com.example.sbwhelldiver.compat.TaczHits;
import com.tacz.guns.entity.EntityKineticBullet.EntityResult;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Pseudo
@Mixin(targets = "com.tacz.guns.util.EntityUtil", remap = false)
public abstract class TaczEntityUtilMixin {
    @Inject(method = "findEntityOnPath", at = @At("RETURN"), cancellable = true)
    private static void helldivers$single(Projectile bullet, Vec3 from, Vec3 to, CallbackInfoReturnable<EntityResult> cir) {
        cir.setReturnValue(TaczHits.single(bullet, from, to, cir.getReturnValue()));
    }

    @Inject(method = "findEntitiesOnPath", at = @At("RETURN"), cancellable = true)
    private static void helldivers$multiple(Projectile bullet, Vec3 from, Vec3 to, CallbackInfoReturnable<List<EntityResult>> cir) {
        cir.setReturnValue(TaczHits.multiple(bullet, from, to, cir.getReturnValue()));
    }
}
