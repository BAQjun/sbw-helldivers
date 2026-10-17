package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.config.HelldiversConfig
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.example.sbwhelldiver.init.ModEntities
import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile
import com.atsuishio.superbwarfare.entity.projectile.IBulletProperties
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.util.UUID

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
object FriendlyFireGameTests {
    private fun player(helper: GameTestHelper, name: String) = object : net.minecraft.world.entity.player.Player(
        helper.level, BlockPos.ZERO, 0f, GameProfile(UUID.randomUUID(),name)) {
        override fun isSpectator() = false
        override fun isCreative() = false
    }.also {
        it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue = 200.0; it.health = 200f
        it.isNoGravity = true
    }
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun deployersCanDamageOwnSentriesButPlayersNeverBecomeTargets(helper: GameTestHelper) {
        val owner = net.minecraftforge.common.util.FakePlayerFactory.get(helper.level,GameProfile(UUID.randomUUID(),"FFOwner"))
        val other = player(helper, "FFOther")
        val p = helper.absolutePos(BlockPos(15, 220, 15))
        owner.setPos(p.x.toDouble(),p.y.toDouble(),p.z+20.0)
        other.setPos(p.x+20.0,p.y.toDouble(),p.z.toDouble())
        helper.level.addFreshEntity(owner)
        val oldPlayers = HelldiversConfig.attackPlayers.get()
        HelldiversConfig.attackPlayers.set(true)
        try {
            for (type in listOf(ModEntities.MACHINE_GUN_SENTRY.get(), ModEntities.GATLING_SENTRY.get(),
                ModEntities.ROCKET_SENTRY.get(), ModEntities.AUTOCANNON_SENTRY.get())) {
                val pod = type.create(helper.level)!!; pod.setPos(p.x.toDouble(),p.y.toDouble(),p.z.toDouble())
                pod.ownerUUID = owner.uuid
                helper.assertTrue(pod.owner === owner,"Owner fixture was not registered for native ownership protection")
                helper.assertTrue(!pod.validTarget(owner) && !pod.validTarget(other), "Sentry acquired a player through the obsolete attackPlayers option")
                helper.assertTrue(pod.hurt(helper.level.damageSources().playerAttack(owner),7f) && pod.health == 113f,
                    "Deployer could not melee their own sentry")
                val bullet = ModEntities.SENTRY_BULLET.get().create(helper.level)!!; bullet.owner = owner
                val source = com.atsuishio.superbwarfare.init.ModDamageTypes.causeGunFireDamage(helper.level.registryAccess(),bullet,owner)
                helper.assertTrue(pod.hurt(source,9f) && pod.health == 104f, "Owner's gunfire was rejected or altered")
                helper.assertTrue(!pod.isWreck, "Nonlethal owner damage destroyed the turret")
            }
        } finally { HelldiversConfig.attackPlayers.set(oldPlayers); owner.discard() }
        helper.succeed()
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun liveSentryProjectilesCanPhysicallyHitTheirDeployer(helper: GameTestHelper) {
        val origin = helper.absolutePos(BlockPos(15, 218, 15))
        val variants = listOf(ModEntities.MACHINE_GUN_SENTRY.get() to ModEntities.SENTRY_BULLET.get(),
            ModEntities.GATLING_SENTRY.get() to ModEntities.SENTRY_BULLET.get(),
            ModEntities.ROCKET_SENTRY.get() to ModEntities.SENTRY_ROCKET.get(),
            ModEntities.AUTOCANNON_SENTRY.get() to ModEntities.SENTRY_CANNON_SHELL.get())
        variants.forEachIndexed { index, (type, projectileType) ->
            val victim = player(helper,"FFHit$index")
            victim.setPos(origin.x+index*6.0,origin.y.toDouble(),origin.z+5.0)
            // SBW predicts hitboxes from position minus xOld/yOld/zOld.
            victim.xOld=victim.x; victim.yOld=victim.y; victim.zOld=victim.z
            helper.level.addFreshEntity(victim)
            val pod = type.create(helper.level)!!; pod.setPos(victim.x,victim.y,victim.z-20)
            pod.ownerUUID = victim.uuid
            val projectile = projectileType.create(helper.level) as net.minecraft.world.entity.projectile.Projectile
            projectile.owner = pod; projectile.setPos(victim.x,victim.y+.8,victim.z-5)
            (projectile as IBulletProperties).setDamage(6f)
            projectile.setCustomGravity(0f)
            if (projectile is FastThrowableProjectile) projectile.setExplosionDestroy(false)
            projectile.shoot(0.0,0.0,1.0,12f,0f)
            helper.level.addFreshEntity(projectile)
            try {
                repeat(3) { if (!projectile.isRemoved) { projectile.tickCount++; projectile.tick() } }
                helper.assertTrue(victim.health < 200f,
                    "${pod.profile.id} ignored its deployer standing in the actual projectile path: health=${victim.health}, pos=${projectile.position()}")
            } finally { victim.discard(); projectile.discard() }
        }
        helper.succeed()
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun sentryBlastsHurtDeployerTeammatesAndPets(helper: GameTestHelper) {
        val p = helper.absolutePos(BlockPos(15, 210, 15))
        val owner = player(helper,"FFBlastOwner")
        val teammate = player(helper,"FFBlastFriend")
        val scoreboard = helper.level.scoreboard
        val team = scoreboard.addPlayerTeam("ff_"+UUID.randomUUID().toString().take(8))
        team.isAllowFriendlyFire = false
        scoreboard.addPlayerToTeam(owner.scoreboardName,team); scoreboard.addPlayerToTeam(teammate.scoreboardName,team)
        val pet = net.minecraft.world.entity.EntityType.WOLF.create(helper.level)!!
        pet.tame(owner); pet.isNoAi = true; pet.isNoGravity = true
        val neutral = net.minecraft.world.entity.EntityType.COW.create(helper.level)!!
        neutral.isNoAi = true; neutral.isNoGravity = true
        val victims = listOf(owner,teammate,pet,neutral)
        victims.forEachIndexed { index,it ->
            it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue = 200.0; it.health = 200f
            it.setPos(p.x+.2*index,p.y.toDouble(),p.z+.1*index); helper.level.addFreshEntity(it)
        }
        try {
            for (type in listOf(ModEntities.SENTRY_ROCKET.get(),ModEntities.SENTRY_CANNON_SHELL.get())) {
                val pod = ModEntities.ROCKET_SENTRY.get().create(helper.level)!!; pod.ownerUUID=owner.uuid
                val shot = type.create(helper.level) as FastThrowableProjectile; shot.owner=pod
                shot.setExplosionDestroy(false); shot.setPos(p.x+.2,p.y+.8,p.z+.2)
                val before = victims.map { it.health }
                shot.causeExplode(shot.position())
                victims.forEachIndexed { i,victim ->
                    // Wolves halve non-player damage under vanilla rules; friendly fire must still reach them.
                    val dealt = before[i]-victim.health
                    helper.assertTrue(if (victim === pet) dealt > 0f && dealt <= 20f else kotlin.math.abs(dealt-20f)<.001f,
                        "Sentry blast shielded victim $i: dealt=$dealt")
                }
            }
        } finally {
            victims.forEach { it.discard() }; scoreboard.removePlayerTeam(team)
        }
        helper.succeed()
    }
}

