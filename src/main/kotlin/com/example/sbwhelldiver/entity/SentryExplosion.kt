package com.example.sbwhelldiver.entity

import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile
import com.atsuishio.superbwarfare.init.ModDamageTypes
import com.atsuishio.superbwarfare.tools.CustomExplosion
import com.atsuishio.superbwarfare.tools.forceHurt
import net.minecraft.server.level.ServerLevel
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.enchantment.ProtectionEnchantment
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/** The inner sphere is full damage; immediately outside it starts at full damage minus one. */
object SentryExplosion {
    fun damageAt(distance: Double, damage: Float, inner: Double, outer: Double): Float {
        require(inner >= 0 && outer > inner)
        if (distance > outer || damage <= 0) return 0f
        // Body coordinates can put an exact boundary a few ULPs outside the sphere.
        if (distance <= inner + 1e-7) return damage
        return ((damage - 1f).coerceAtLeast(0f) * (outer - distance) / (outer - inner)).toFloat()
    }

    // Measure from the blast to the closest point of the body, so a direct hit is in the inner sphere.
    fun distanceToBody(origin: Vec3, box: AABB): Double {
        val dx = origin.x - origin.x.coerceIn(box.minX, box.maxX)
        val dy = origin.y - origin.y.coerceIn(box.minY, box.maxY)
        val dz = origin.z - origin.z.coerceIn(box.minZ, box.maxZ)
        return sqrt(dx * dx + dy * dy + dz * dz)
    }

    fun explode(projectile: FastThrowableProjectile, origin: Vec3, inner: Double, destroyBlocks: Boolean, genericEffects: Boolean = true, particleType: com.atsuishio.superbwarfare.tools.ParticleTool.ParticleType? = null) {
        val level = projectile.level() as? ServerLevel ?: return
        if (projectile.exploded) return
        projectile.exploded = true
        val outer = projectile.getExplosionRadius().toDouble()
        val damage = projectile.getExplosionDamage()
        val source = ModDamageTypes.causeProjectileExplosionDamage(level.registryAccess(), projectile, projectile.owner)
        if (outer > inner && damage > 0) {
            level.getEntities(projectile, AABB(origin, origin).inflate(outer)) { !it.isRemoved && !it.ignoreExplosion() }
                .forEach { target ->
                    val amount = damageAt(distanceToBody(origin, target.boundingBox), damage, inner, outer)
                    if (amount <= 0f) return@forEach
                    // Each blast and the preceding direct hit must settle independently, as SBW projectiles do.
                    val cooldown = target.invulnerableTime
                    val damaged = try {
                        target.invulnerableTime = 0
                        target.forceHurt(source, amount)
                    } finally {
                        target.invulnerableTime = cooldown
                    }
                    if (damaged && target is LivingEntity) {
                        val force = ProtectionEnchantment.getExplosionKnockbackAfterDampener(target, (amount * .015).toDouble())
                        val impulse = origin.vectorTo(target.boundingBox.center).normalize().scale(force)
                        target.push(impulse.x, impulse.y, impulse.z)
                    }
                }
        }
        if (!genericEffects) {
            // Native EMS impact audio replaces the generic explosion packet and its sound.
            level.sendParticles(ParticleTypes.EXPLOSION, origin.x, origin.y, origin.z, 1, 0.0, 0.0, 0.0, 0.0)
            return
        }
        // SBW supplies the original explosion particles, sound and block effects, with no second damage pass.
        val effects = CustomExplosion.Builder(projectile).attacker(projectile.owner).damage(0f)
            .radius(projectile.getExplosionRadius()).position(origin).destroyBlock(destroyBlocks)
        particleType?.let { effects.withParticleType(it) }
        effects.explode()
    }
}
