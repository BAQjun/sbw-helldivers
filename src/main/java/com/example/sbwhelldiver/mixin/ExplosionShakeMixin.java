package com.example.sbwhelldiver.mixin;

import com.atsuishio.superbwarfare.tools.CustomExplosion;
import com.atsuishio.superbwarfare.network.message.receive.ShakeClientMessage;
import com.example.sbwhelldiver.entity.ExplosionShakePolicy;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Scale only our selected projectile blasts; preserve SBW sound, particles and duration. */
@Mixin(value = CustomExplosion.class, remap = false)
public abstract class ExplosionShakeMixin {
    @Redirect(method = "<init>(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;FDDDFLnet/minecraft/world/level/Explosion$BlockInteraction;)V",
        at = @At(value = "INVOKE", target = "Lcom/atsuishio/superbwarfare/network/message/receive/ShakeClientMessage$Companion;sendToNearbyPlayers(Lnet/minecraft/world/level/Level;DDDDDD)V"), remap = false)
    private void helldivers$blastShake(ShakeClientMessage.Companion sender, Level level,
            double x, double y, double z, double radius, double time, double amplitude,
            Level sourceLevel, Entity source, DamageSource damage, float amount,
            double px, double py, double pz, float blastRadius, Explosion.BlockInteraction interaction) {
        double scale = ExplosionShakePolicy.scale(source);
        if (scale > 0) sender.sendToNearbyPlayers(level, x, y, z, radius, time, amplitude * scale);
    }
}
