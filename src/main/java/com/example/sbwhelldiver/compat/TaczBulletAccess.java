package com.example.sbwhelldiver.compat;

import net.minecraft.world.phys.Vec3;

/** Implemented only on TACZ bullets; safe to reference without installing TACZ. */
public interface TaczBulletAccess {
    float helldivers$shieldDamage(Vec3 hit);
}
