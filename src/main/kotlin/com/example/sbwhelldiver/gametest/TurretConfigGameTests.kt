package com.example.sbwhelldiver.gametest

import com.atsuishio.superbwarfare.data.gun.ShootParameters
import com.atsuishio.superbwarfare.entity.projectile.IBulletProperties
import com.example.sbwhelldiver.config.TurretConfigs
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.ForgeConfigSpec
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.event.entity.EntityJoinLevelEvent
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.util.function.Consumer
import kotlin.math.abs

/** Run in its own namespace: mutates server config temporarily, never concurrently with balance tests. */
@GameTestHolder("sbw_helldiver_config")
@PrefixGameTestTemplate(false)
object TurretConfigGameTests {
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 20)
    fun configuredWeaponsPersistenceRangesAndSpecialClocks(h: GameTestHelper) {
        val snapshot = ConfigTestSnapshot()
        val restore = mutableListOf<() -> Unit>()
        val actors = mutableListOf<Entity>()
        fun <T> change(value: ForgeConfigSpec.ConfigValue<T>, replacement: T) {
            val previous = value.get(); restore.add { value.set(previous) }; value.set(replacement)
        }
        fun check(condition: Boolean, message: String) = h.assertTrue(condition, message)
        val p = h.absolutePos(BlockPos(15, 100, 15))
        val origin = Vec3(p.x+.5, p.y.toDouble(), p.z+.5)
        fun sentry(type: EntityType<SentryHellpodEntity>) = type.create(h.level)!!.also {
            actors.add(it); it.setPos(origin)
            SentryHellpodEntity::class.java.getDeclaredMethod("onActivated").also { method -> method.isAccessible=true }.invoke(it)
        }
        val emitted = mutableListOf<IBulletProperties>()
        val listener = Consumer<EntityJoinLevelEvent> { event ->
            if (event.level === h.level && event.entity is IBulletProperties) {
                emitted.add(event.entity as IBulletProperties); actors.add(event.entity)
            }
        }
        MinecraftForge.EVENT_BUS.addListener(listener)
        try {
            val mg = TurretConfigs.get("machine_gun_sentry")
            change(mg.maxHealth, 321.0); change(mg.damage, 17.0); change(mg.rpm, 333)
            change(mg.ammunition!!, 37); change(mg.range!!, 12.0); change(mg.minimumRange!!, 3.0)
            change(mg.explosionResistance, .75)
            val gun = sentry(ModEntities.MACHINE_GUN_SENTRY.get())
            check(gun.getMaxHealth() == 321f && gun.health == 321f, "Configured initial hull health ignored")
            check(gun.clipRemaining == 37, "Configured ammunition ignored")
            check(gun.vehicleWeaponRpm("Main") == 333, "Native RPM did not use the config override")
            check(!gun.markPointInRange(origin.add(2.0,0.0,0.0)) && gun.markPointInRange(origin.add(10.0,0.0,0.0)) &&
                !gun.markPointInRange(origin.add(13.0,0.0,0.0)), "Target/mark range limits ignored")
            gun.hurt(h.level.damageSources().explosion(null,null),40f)
            check(gun.health == 311f, "Explosion resistance ignored: ${gun.health}")
            fun fire(pod: SentryHellpodEntity): IBulletProperties {
                val before = emitted.size
                pod.modifyGunData("Main") { data ->
                    data.shootTimer.set(0)
                    data.shoot(ShootParameters(pod.ammoSupplier,pod,h.level,origin,Vec3(0.0,0.0,1.0),data,0.0,false,null,null))
                }
                check(emitted.size == before+1, "Native weapon failed to emit one real projectile: ${pod.profile.id}")
                return emitted.last()
            }
            check(fire(gun).getDamage() == 17f && gun.getGunData("Main")!!.ammo.get() == 36,
                "Native bullet damage/ammo did not use config")
            val saved = CompoundTag(); gun.saveWithoutId(saved)
            change(mg.maxHealth, 500.0); change(mg.ammunition!!, 70)
            val loaded = ModEntities.MACHINE_GUN_SENTRY.get().create(h.level)!!.also { actors.add(it); it.load(saved) }
            check(loaded.health == 311f && loaded.clipRemaining == 36 && loaded.getMaxHealth() == 500f,
                "Capacity increase refilled or healed a saved turret")
            change(mg.maxHealth, 200.0); change(mg.ammunition!!, 20)
            loaded.load(saved)
            check(loaded.health == 200f && loaded.clipRemaining == 20, "Reduced limits did not clamp saved state")
            change(mg.ammunition!!, 0)
            check(sentry(ModEntities.MACHINE_GUN_SENTRY.get()).clipRemaining == 0, "Zero ammunition was not accepted")
            check(sentry(ModEntities.GATLING_SENTRY.get()).clipRemaining == 750, "One turret's config changed another turret")

            val explosive = listOf(ModEntities.ROCKET_SENTRY, ModEntities.AUTOCANNON_SENTRY, ModEntities.MORTAR_SENTRY, ModEntities.EMS_MORTAR_SENTRY)
            for (type in explosive) {
                val config = TurretConfigs.forType(type.get())!!
                change(config.damage, 19.0); change(config.explosionDamage!!, 33.0)
                change(config.outerRadius!!, 6.0); change(config.innerRadius!!, 2.5)
                val pod = sentry(type.get()); val shell = fire(pod)
                check(shell.getDamage() == 19f && shell.getExplosionDamage() == 33f && shell.getExplosionRadius() == 6f,
                    "Native explosive properties ignored for ${config.id}")
            }
            val rocket = TurretConfigs.get("rocket_sentry")
            change(rocket.ammunition!!, 50); change(rocket.magazineSize!!, 7)
            val launcher = sentry(ModEntities.ROCKET_SENTRY.get())
            check(launcher.clipRemaining == 7 && launcher.reserveRemaining == 43, "Rocket clip/reserve capacities ignored")
            val cannon = TurretConfigs.get("autocannon_sentry")
            change(cannon.rpm, 180)
            val burst = BurstClock(); var rounds = 0
            repeat(1200) { rounds += burst.advance(3,cannon.burstInterval(10.0),cannon.burstInterval(20.0),it.toLong()) }
            check(rounds == 180, "Burst timing did not scale to configured RPM: $rounds")

            for ((id, type) in listOf("hmg_emplacement" to ModEntities.HMG_EMPLACEMENT.get(),
                "anti_tank_emplacement" to ModEntities.ANTI_TANK_EMPLACEMENT.get(),
                "grenadier_battlement" to ModEntities.GRENADIER_BATTLEMENT.get())) {
                val config = TurretConfigs.get(id)
                change(config.maxHealth, 432.0); change(config.ammunition!!, 17); change(config.rpm, 160)
                val pod = type.create(h.level)!!.also { actors.add(it) } as HmgEmplacementEntity
                check(pod.health == 432f && pod.ammoRemaining == 17, "Manual emplacement capacity ignored: $id")
                val cadence = HmgEmplacementEntity::class.java.getDeclaredMethod("nextShotDelayTicks").also { it.isAccessible=true }
                val ticks = (0 until 160).sumOf { cadence.invoke(pod) as Int }
                check(ticks == 1200, "Fractional manual firing rate drifted: $id/$ticks")
                val tag = CompoundTag(); pod.health=123f; pod.saveWithoutId(tag); tag.putInt("HmgAmmo",6)
                change(config.ammunition!!, 30); pod.load(tag)
                check(pod.health == 123f && pod.ammoRemaining == 6, "Manual save gained health/ammo: $id")
            }
            val at = TurretConfigs.get("anti_tank_emplacement")
            val gl = TurretConfigs.get("grenadier_battlement")
            change(at.damage, 61.0); change(at.explosionDamage!!, 44.0); change(at.outerRadius!!, 8.0)
            change(gl.damage, 13.0); change(gl.explosionDamage!!, 49.0); change(gl.outerRadius!!, 9.0)
            val atShell = ModEntities.ANTI_TANK_EMPLACEMENT_SHELL.get().create(h.level)!!.also {actors.add(it)}
            val grenade = ModEntities.GRENADIER_GRENADE.get().create(h.level)!!.also {actors.add(it)}
            check(atShell.getDamage()==61f && atShell.getExplosionDamage()==44f && atShell.getExplosionRadius()==8f &&
                grenade.getDamage()==13f && grenade.getExplosionDamage()==49f && grenade.getExplosionRadius()==9f,
                "Manual projectile damage/radius ignored")
            val wall = ModEntities.GRENADIER_BATTLEMENT.get().create(h.level)!!.also {actors.add(it);it.setPos(origin)}
            val emptyWall = CompoundTag(); wall.saveWithoutId(emptyWall); emptyWall.putInt("HmgAmmo",0)
            emptyWall.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal); wall.load(emptyWall); wall.tick()
            check(wall.phase == AbstractHellpodEntity.Phase.RETRACTING, "Empty unoccupied grenade wall did not retract")

            val flame = TurretConfigs.get("flame_sentry")
            change(flame.rpm, 480); change(flame.damage, 6.0); change(flame.range!!, 18.0)
            val fc = FlameClock(); repeat(20) { check(!fc.advance(it.toLong(),true,true,900),"Flame bypassed ignition") }
            var jets=0; repeat(1200) { if(fc.advance((it+20).toLong(),true,true,900)) jets++ }
            check(jets==480 && FlameClock.DIRECT_DAMAGE_PER_JET==6f && FlameClock.RANGE==18.0,"Flame settings ignored: $jets")
            val laser = TurretConfigs.get("laser_sentry")
            change(laser.rpm, 333); change(laser.damage, 11.0); change(laser.heatPerSecond!!,0.0)
            change(laser.beamRange!!,110.0); change(laser.range!!,120.0)
            val lc=LaserClock();repeat(10) {lc.advance(true,true,5.0)}
            var pulses=0;repeat(1200) {lc.advance(true,true,5.0);if(lc.damagePulse)pulses++}
            check(pulses==333 && LaserClock.DIRECT_DAMAGE==11f && LaserClock.BEAM_RANGE==120.0,"Laser settings/cadence ignored: $pulses")
            change(laser.maxHeat!!,10.0);change(laser.heatPerSecond!!,20.0)
            val hot=LaserClock();repeat(20) {hot.advance(true,true,5.0)}
            check(hot.state==LaserClock.State.OVERLOADED,"Configured heat threshold/rate ignored")
            val tesla=TurretConfigs.get("tesla_tower")
            change(tesla.rpm,45);change(tesla.damage,21.0);change(tesla.maxVictims!!,5);change(tesla.jumpRange!!,12.0)
            val tc=TeslaClock();tc.advance(true);var arcs=0;repeat(1200) {if(tc.advance(true))arcs++}
            check(arcs==45 && TeslaClock.DAMAGE==21f && TeslaClock.MAX_VICTIMS==5 && TeslaClock.JUMP_RANGE==12.0,
                "Tesla settings/cadence ignored: $arcs")

            // An excessive inner radius must remain safe and deal full damage inside the outer sphere.
            val victim=EntityType.HUSK.create(h.level)!!.also {
                actors.add(it);it.setPos(origin.add(1.0,0.0,0.0))
                it.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR)!!.baseValue=0.0
                h.level.addFreshEntity(it)
            }
            grenade.setDamage(0f);grenade.setExplosionDamage(7f);grenade.setExplosionRadius(2f)
            SentryExplosion.explode(grenade,origin,50.0,false,genericEffects=false)
            check(abs(victim.health-13f)<.001,"Clamped inner blast radius lost its damage: health=${victim.health}, registered=${h.level.getEntity(victim.id)===victim}")
        } finally {
            MinecraftForge.EVENT_BUS.unregister(listener)
            actors.forEach { it.discard() }
            restore.asReversed().forEach { it() }
            snapshot.close()
            TurretConfigs.changed()
        }
        h.succeed()
    }
}
