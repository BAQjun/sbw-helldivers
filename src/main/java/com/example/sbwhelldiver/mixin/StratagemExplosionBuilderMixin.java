package com.example.sbwhelldiver.mixin;

import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.atsuishio.superbwarfare.tools.CustomExplosion;
import com.example.sbwhelldiver.config.HelldiversConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Resolve only our projectiles against our world setting; SBW keeps its own policy. */
@Mixin(value = CustomExplosion.Builder.class, remap = false)
public abstract class StratagemExplosionBuilderMixin {
    @Shadow private Entity directSource;
    @Shadow private boolean destroyBlock;

    @Inject(method = "explode$lambda$0", at = @At("HEAD"), cancellable = true, remap = false)
    private static void helldivers$blockInteraction(CustomExplosion.Builder builder,
            CallbackInfoReturnable<Explosion.BlockInteraction> callback) {
        StratagemExplosionBuilderMixin self = (StratagemExplosionBuilderMixin) (Object) builder;
        Entity source = self.directSource;
        if (source instanceof FastThrowableProjectile
                && BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).getNamespace().equals("sbw_helldiver")) {
            callback.setReturnValue(self.destroyBlock && HelldiversConfig.INSTANCE.getStratagemExplosionBlockDamage().get()
                ? Explosion.BlockInteraction.DESTROY : Explosion.BlockInteraction.KEEP);
        }
    }
}
