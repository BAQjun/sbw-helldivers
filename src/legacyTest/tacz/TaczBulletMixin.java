package com.example.sbwhelldiver.mixin.tacz;

import com.example.sbwhelldiver.compat.TaczBulletAccess;
import com.example.sbwhelldiver.compat.TaczHits;
import com.tacz.guns.util.TacHitResult;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.tacz.guns.entity.EntityKineticBullet", remap = false)
public abstract class TaczBulletMixin implements TaczBulletAccess {
    @Shadow private boolean explosion;
    @Shadow private float explosionDamage;
    @Shadow private int pierce;
    @Shadow public abstract float getDamage(Vec3 hit);

    @Unique
    @Override public float helldivers$shieldDamage(Vec3 hit) {
        float damage = getDamage(hit) + (explosion ? explosionDamage : 0f);
        return Float.isFinite(damage) ? Math.max(0f, damage) : 0f;
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true)
    private void helldivers$hit(TacHitResult hit, Vec3 from, Vec3 to, CallbackInfo ci) {
        Projectile bullet = (Projectile) (Object) this;
        if (bullet.level().isClientSide()) return;
        if (TaczHits.absorb(bullet, hit)) {
            // The caller decrements pierce and discards before processing later hits.
            // Keep ignition and explosions out of the protected volume.
            pierce = 1;
            ci.cancel();
        } else if (TaczHits.isStructure(hit.getEntity())) {
            // These are solid metal cover, not flesh that an AP round can traverse.
            // Native TACZ damage, AP split, ignition and explosion handling still run.
            pierce = 1;
        }
    }
}
