package com.example.sbwhelldiver.mixin;

import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile;
import com.atsuishio.superbwarfare.tools.CustomExplosion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Give SBW's terrain pass blast power without repeating our separately calculated entity damage. */
@Mixin(value = CustomExplosion.class, remap = false)
public abstract class StratagemBlockDamageMixin {
    /** SBW's global switch is only consulted for explosions that are not ours. */
    @Redirect(method = "explode", at = @At(value = "INVOKE",
        target = "Lnet/neoforged/neoforge/common/ModConfigSpec$BooleanValue;get()Ljava/lang/Object;", ordinal = 0), remap = false)
    private Object helldivers$independentTerrainSwitch(ModConfigSpec.BooleanValue setting) {
        CustomExplosion explosion = (CustomExplosion) (Object) this;
        return isStratagem(explosion.getDirectSourceEntity()) ? explosion.interactsWithBlocks() : setting.get();
    }

    private static boolean isStratagem(Entity source) {
        return source instanceof FastThrowableProjectile
            && BuiltInRegistries.ENTITY_TYPE.getKey(source.getType()).getNamespace().equals("sbw_helldiver");
    }

    // SBW 0.8.9.2 split terrain processing out of explode(), renaming this
    // Kotlin helper. Select both supported layouts without disabling checks.
    @Redirect(method = {"explode$lambda$2", "explodeBlocks$lambda$2"}, at = @At(value = "FIELD",
        target = "Lcom/atsuishio/superbwarfare/tools/CustomExplosion;damage:F"), remap = false)
    private static float helldivers$terrainPower(CustomExplosion explosion) {
        float damage = explosion.getDamage();
        Entity source = explosion.getDirectSourceEntity();
        if (damage != 0 || !(source instanceof FastThrowableProjectile projectile)
                || !isStratagem(source)) {
            return damage;
        }
        // The interaction already combines the per-stratagem choice, our independent world setting.
        return explosion.interactsWithBlocks() ? projectile.getExplosionDamage() : 0;
    }
}
