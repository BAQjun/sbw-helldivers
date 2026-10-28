package com.example.sbwhelldiver.compat;

import com.example.sbwhelldiver.entity.AbstractHellpodEntity;
import com.example.sbwhelldiver.entity.AntiPersonnelMineEntity;
import com.example.sbwhelldiver.entity.GrenadierWallPanelEntity;
import com.example.sbwhelldiver.entity.ShieldGeneratorEntity;
import com.example.sbwhelldiver.entity.ShieldInterception;
import com.tacz.guns.entity.EntityKineticBullet.EntityResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Called exclusively by optional TACZ mixins, never by the normal mod bootstrap. */
public final class TaczHits {
    private TaczHits() {}

    public static boolean isStructure(Entity entity) {
        return entity instanceof AbstractHellpodEntity || entity instanceof GrenadierWallPanelEntity
                || entity instanceof AntiPersonnelMineEntity;
    }

    private static EntityResult withoutHeadshot(EntityResult result) {
        return result != null && isStructure(result.getEntity())
                ? new EntityResult(result.getEntity(), result.getHitPos(), false) : result;
    }

    private static EntityResult shieldEntry(Projectile bullet, Vec3 from, Vec3 to) {
        if (!(bullet.level() instanceof ServerLevel level) || bullet.isRemoved()) return null;
        EntityResult nearest = null;
        double distance = Double.MAX_VALUE;
        // The ray has already been clipped by TACZ's own block/material rules.
        for (ShieldGeneratorEntity shield : level.getEntitiesOfClass(ShieldGeneratorEntity.class,
                new AABB(from, to).inflate(ShieldGeneratorEntity.RADIUS + 2))) {
            if (!shield.getShieldOnline()) continue;
            Double t = ShieldInterception.INSTANCE.entry(from, to, shield.shieldCenter(1f), ShieldGeneratorEntity.RADIUS);
            if (t != null && t < distance) {
                distance = t;
                nearest = new EntityResult(shield, from.lerp(to, t), false);
            }
        }
        return nearest;
    }

    public static EntityResult single(Projectile bullet, Vec3 from, Vec3 to, EntityResult original) {
        if (!(bullet instanceof TaczBulletAccess)) return original;
        EntityResult shield = shieldEntry(bullet, from, to);
        if (shield != null && (original == null || from.distanceToSqr(shield.getHitPos()) < from.distanceToSqr(original.getHitPos())))
            return shield;
        return withoutHeadshot(original);
    }

    public static List<EntityResult> multiple(Projectile bullet, Vec3 from, Vec3 to, List<EntityResult> original) {
        if (!(bullet instanceof TaczBulletAccess)) return original;
        List<EntityResult> hits = new ArrayList<>();
        for (EntityResult result : original) hits.add(withoutHeadshot(result));
        EntityResult shield = shieldEntry(bullet, from, to);
        if (shield != null) hits.add(shield);
        // TACZ sorts and processes these hits, preserving creatures before the shield.
        return hits;
    }

    public static boolean absorb(Projectile bullet, EntityHitResult hit) {
        if (!(hit.getEntity() instanceof ShieldGeneratorEntity shield)) return false;
        double radius = ShieldGeneratorEntity.RADIUS;
        // A synthetic sphere hit is distinct from a real hit on the generator body.
        if (Math.abs(hit.getLocation().distanceToSqr(shield.shieldCenter(1f)) - radius * radius) > 1e-4) return false;
        if (!shield.absorb(((TaczBulletAccess) bullet).helldivers$shieldDamage(hit.getLocation()), hit.getLocation())) return false;
        bullet.setPos(hit.getLocation());
        return true;
    }
}
