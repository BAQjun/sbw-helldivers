package com.example.sbwhelldiver.gametest

import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile
import com.atsuishio.superbwarfare.world.phys.EntityResult
import com.atsuishio.superbwarfare.world.phys.ExtendedEntityRayTraceResult
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.SentryExplosion
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
object ExplosionGameTests {
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun rocketDirectHitAndBlastRadii(helper: GameTestHelper) = verify(helper, 0)

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun cannonDirectHitAndBlastRadii(helper: GameTestHelper) = verify(helper, 1)

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun mortarDirectHitAndBlastRadii(helper: GameTestHelper) = verify(helper, 2)

    private fun verify(helper: GameTestHelper, variant: Int) {
        val cannon = variant == 1
        val mortar = variant == 2
        val type = when (variant) { 1 -> ModEntities.SENTRY_CANNON_SHELL.get(); 2 -> ModEntities.MORTAR_SHELL.get(); else -> ModEntities.SENTRY_ROCKET.get() }
        val inner = if (mortar) 3.0 else if (cannon) 2.0 else 1.0
        val outer = if (mortar) 9.0 else if (cannon) 5.0 else 4.0
        val direct = if (mortar) 2f else if (cannon) 70f else 80f
        val blast = if (mortar) 64f else 20f
        val position = helper.absolutePos(BlockPos(15, 10, 15))
        val origin = Vec3(position.x + .5, position.y + 1.0, position.z + .5)
        val projectile = type.create(helper.level) as FastThrowableProjectile
        projectile.setPos(origin); projectile.tickCount = 1
        projectile.setExplosionDestroy(false)
        helper.level.addFreshEntity(projectile)
        helper.assertTrue(projectile.getDamage() == direct && projectile.getExplosionDamage() == blast && projectile.getExplosionRadius() == outer.toFloat(), "Wrong native projectile damage values")
        val projectileType = helper.level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
        helper.assertTrue(!projectileType.getHolderOrThrow(com.atsuishio.superbwarfare.init.ModDamageTypes.PROJECTILE_HIT).`is`(DamageTypeTags.IS_EXPLOSION), "Direct damage must not be explosion damage")
        helper.assertTrue(projectileType.getHolderOrThrow(com.atsuishio.superbwarfare.init.ModDamageTypes.PROJECTILE_EXPLOSION).`is`(DamageTypeTags.IS_EXPLOSION), "Blast damage must use an explosion damage type")
        fun target(distance: Double, zDirection: Boolean = false): LivingEntity {
            val mob = EntityType.RAVAGER.create(helper.level)!!
            mob.isNoAi = true; mob.isNoGravity = true
            mob.getAttribute(Attributes.ARMOR)!!.baseValue = 0.0
            mob.getAttribute(Attributes.MAX_HEALTH)!!.baseValue = 200.0
            mob.health = 200f
            // Place the nearest edge exactly the requested distance from the blast.
            val offset = distance + mob.bbWidth.toDouble() / 2
            mob.setPos(origin.x + if (zDirection) 0.0 else offset, origin.y - .5,
                origin.z + if (zDirection) offset else 0.0)
            helper.level.addFreshEntity(mob)
            return mob
        }
        val directTarget = target(0.0)
        val innerEdge = target(inner)
        val justOutside = target(inner + .01)
        val middle = target((inner + outer) / 2, true)
        val outerEdge = target(outer)
        val beyond = target(outer + .1, true)
        val protected = target(.5, true).also { it.isInvulnerable = true }
        val victims = listOf(directTarget, innerEdge, justOutside, middle, outerEdge, beyond, protected)
        // A recent separate injury must not swallow the radial blast.
        innerEdge.hurt(helper.level.damageSources().generic(), 1f)
        val before = victims.map { it.health }
        projectile.onHitEntity(ExtendedEntityRayTraceResult(EntityResult(directTarget, origin, false, false)))
        val expected = listOf(direct + blast, blast, (blast-1f) * (1f - .01f / (outer-inner).toFloat()), (blast-1f)/2, 0f, 0f, 0f)
        victims.zip(expected).forEachIndexed { index, (victim, damage) ->
            val dealt = before[index] - victim.health
            helper.assertTrue(abs(dealt - damage) < .015f, "${if (mortar) "Mortar" else if (cannon) "Cannon" else "Rocket"} target $index: expected $damage but got $dealt")
        }
        helper.assertTrue(protected.deltaMovement == Vec3.ZERO, "Invulnerable target was knocked back")
        val after = victims.map { it.health }
        projectile.causeExplode(origin)
        helper.assertTrue(victims.map { it.health } == after, "The same projectile exploded twice")
        helper.assertTrue(projectile.isRemoved, "Projectile survived its hit")
        victims.forEach { it.discard() }
        helper.succeed()
    }
}
