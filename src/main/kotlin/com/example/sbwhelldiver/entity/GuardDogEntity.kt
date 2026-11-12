package com.example.sbwhelldiver.entity



import com.example.sbwhelldiver.init.ModEntities

import com.example.sbwhelldiver.item.GuardDogBackpack

import net.minecraft.nbt.CompoundTag

import net.minecraft.network.syncher.EntityDataSerializers

import net.minecraft.network.syncher.EntityDataAccessor

import net.minecraft.network.syncher.SynchedEntityData

import net.minecraft.server.level.ServerLevel

import net.minecraft.server.level.ServerPlayer

import net.minecraft.sounds.SoundSource

import net.minecraft.util.Mth

import net.minecraft.world.damagesource.DamageSource

import net.minecraft.world.entity.Entity

import net.minecraft.world.entity.EntityType

import net.minecraft.world.entity.LivingEntity

import net.minecraft.world.entity.player.Player

import net.minecraft.world.entity.MoverType

import net.minecraft.world.item.ItemStack

import net.minecraft.world.level.ClipContext

import net.minecraft.world.level.Level

import net.minecraft.world.phys.AABB

import net.minecraft.world.phys.HitResult

import net.minecraft.world.phys.Vec3

import java.util.UUID

import kotlin.math.atan2

import kotlin.math.hypot

import kotlin.math.sin



class GuardDogEntity(type: EntityType<*>, level: Level) : Entity(type, level) {

    // The owner-relative launch/dock pose and free-flight interpolation must keep
    // ticking even when the drone is behind the camera. Rendering still has its
    // own frustum check in DroneRenderer.
    init { noCulling = true }

    enum class Flight { LAUNCH, PATROL, RETURN, DOCK }



    private var ownerId: UUID? = null

    private var deployedPack: ItemStack? = null

    private var target: LivingEntity? = null

    enum class Reload(val clip: String?) {

        NONE(null), RIGHT("reload_r"), LEFT("reload_l"), EMPTY("empty_fold");

        val changesMagazine get() = this == RIGHT || this == LEFT

    }

    private var dockApproachOffset = Vec3.ZERO

    private var dockApproachTicks = 0

    private var recallQueued = false

    private var shotClock = 0

    private data class GasCloud(val center: Vec3, val born: Long)

    private val gasClouds = ArrayDeque<GasCloud>()

    private val gasExposure = mutableMapOf<UUID, Int>()

    private var clientPhaseStartedAt = 0
    private var lerpTicks = 0
    private var hasTrackedPosition = false

    private var lerpPosition = Vec3.ZERO

    private var lerpYaw = 0f

    private var lerpPitch = 0f

    private var thrustVelocity = Vec3.ZERO

    private var thrustAcceleration = Vec3.ZERO

    private var previousThrustVelocity = Vec3.ZERO

    private var previousThrustAcceleration = Vec3.ZERO

    private var turnRate = 0f

    private var previousTurnRate = 0f



    val isLaser get() = entityData.get(LASER)

    val isStun get() = entityData.get(STUN)

    val isGas get() = entityData.get(GAS)

    val gasSprayLength get() = entityData.get(GAS_SPRAY_LENGTH)

    val gasSpraying get() = gasSprayLength > 0f

    val isSprayer get() = isFlame || isGas

    val isFlame get() = entityData.get(FLAME)

    val flameState get() = FlameClock.State.entries[entityData.get(FLAME_STATE)]

    private val flameClock = FlameClock()

    val isEnergy get() = isLaser || isStun

    val arcData get() = entityData.get(ARC_DATA)

    var arcReceivedTick = -1000

        private set

    val beamLength get() = entityData.get(BEAM_LENGTH)

    private var laserDamageClock = 0.0



    /** Authored muzzle locator, rotated around the same model origin as the renderer. */

    fun muzzleOrigin(partial: Float = 1f): Vec3 {

        val yaw = Mth.rotLerp(partial, yRotO, yRot)

        val pitch = Mth.lerp(partial, xRotO, xRot)

        val offset = (if (isGas) GAS_MUZZLE else if (isFlame) FLAME_MUZZLE else if (isStun) STUN_MUZZLE else if (isLaser) LASER_MUZZLE else MG_MUZZLE)

            .xRot(pitch * Mth.DEG_TO_RAD).yRot((180f - yaw) * Mth.DEG_TO_RAD)

        return getPosition(partial).add(0.0, MODEL_HEIGHT, 0.0).add(offset)

    }



    fun laserOrigin(partial: Float = 1f) = muzzleOrigin(partial)



    /** Owner-relative transition; network interpolation must not leave a docking drone behind. */

    fun transitionPosition(owner: Player, age: Double, partial: Float? = null): Vec3 {

        val dock = dockPosition(owner, partial)

        if (flight == Flight.LAUNCH) {

            val lift = GuardDogMotion.liftTick(isEnergy)

            val progress = ease((age - lift) / (TRANSITION_TICKS - lift))

            return dock.lerp(freePosition(owner, true, partial), progress)

        }

        val offset = if (level().isClientSide) entityData.get(DOCK_OFFSET).let {

            Vec3(it.x.toDouble(), it.y.toDouble(), it.z.toDouble())

        } else dockApproachOffset

        val yaw = if (partial == null) owner.yBodyRot else Mth.rotLerp(partial, owner.yBodyRotO, owner.yBodyRot)

        return dock.add(offset.yRot(-yaw * Mth.DEG_TO_RAD).scale(1.0 - ease(age / GuardDogMotion.contactTick(isEnergy))))

    }



    val flight get() = Flight.entries[entityData.get(FLIGHT)]

    val dockingSpares get() = entityData.get(DOCKING_SPARES)

    val reload get() = Reload.entries[entityData.get(RELOAD)]

    val phaseAge get() = if (level().isClientSide)
        (tickCount - clientPhaseStartedAt).toLong().coerceAtLeast(0)
        else (level().gameTime - entityData.get(PHASE_START)).coerceAtLeast(0)

    fun belongsTo(player: Player) =

        if (level().isClientSide) entityData.get(OWNER) == player.id else ownerId == player.uuid



    fun clientOwner(): Player? = level().getEntity(entityData.get(OWNER)) as? Player



    val usesPackPose get() = flight == Flight.LAUNCH || flight == Flight.DOCK



    override fun onSyncedDataUpdated(key: EntityDataAccessor<*>) {

        super.onSyncedDataUpdated(key)
        // Attached poses are evaluated from the owner, never from queued flight packets.
        // Preserve the latest network destination across the animated handoff;
        // rotation-only packets must never replace it with the attached pose.
        if (level().isClientSide) {
            if (key == FLIGHT) {
                // Movement arrives before phase metadata. Keep its destination
                // while the pack animation owns the pose, then continue toward
                // that same destination instead of discarding the handoff packet.
                lerpTicks = if (!usesPackPose && hasTrackedPosition) 3 else 0
            }
            if (key == PHASE_START) {
                // Anchor once on receipt. Later world-time corrections must not rewind a flight animation.
                val elapsed = (level().gameTime - entityData.get(PHASE_START)).coerceIn(0, TRANSITION_TICKS.toLong())
                clientPhaseStartedAt = tickCount - elapsed.toInt()
            }
        }


        // A short discharge must start on receipt, not expire during transit or

        // because the client world clock was corrected by a time-sync packet.

        if (key == ARC_DATA && level().isClientSide && arcData.getInt("Count") > 0)

            arcReceivedTick = tickCount

    }



    override fun defineSynchedData() {

        entityData.define(LASER, false)

        entityData.define(STUN, false)

        entityData.define(GAS, false)

        entityData.define(GAS_SPRAY_LENGTH, 0f)

        entityData.define(FLAME, false)

        entityData.define(FLAME_STATE, FlameClock.State.OFF.ordinal)

        entityData.define(ARC_DATA, CompoundTag())

        entityData.define(BEAM_LENGTH, -1f)

        entityData.define(OWNER, -1)

        entityData.define(FLIGHT, Flight.LAUNCH.ordinal)

        entityData.define(PHASE_START, 0L)

        entityData.define(RELOAD, Reload.NONE.ordinal)

        entityData.define(DOCKING_SPARES, SPARE_MAGAZINES)

        entityData.define(DOCK_OFFSET, org.joml.Vector3f())

    }



    fun attach(player: ServerPlayer, pack: ItemStack): Boolean {

        val start = dockPosition(player)

        moveTo(start.x, start.y, start.z, player.yBodyRot, 0f)

        val exit = freePosition(player, true)

        if (!clear(start.add(0.0, .18, 0.0), exit.add(0.0, .18, 0.0))) return false

        ownerId = player.uuid

        deployedPack = pack

        entityData.set(LASER, GuardDogBackpack.isLaser(pack))

        entityData.set(STUN, GuardDogBackpack.isStun(pack))

        entityData.set(FLAME, GuardDogBackpack.isFlame(pack))

        entityData.set(GAS, GuardDogBackpack.isGas(pack))

        entityData.set(OWNER, player.id)

        enter(Flight.LAUNCH)

        return true

    }



    private fun enter(phase: Flight) {

        entityData.set(FLIGHT, phase.ordinal)

        entityData.set(PHASE_START, level().gameTime)

        target = null

        shotClock = 0

        laserDamageClock = 0.0

        flameClock.reset()

        entityData.set(GAS_SPRAY_LENGTH, 0f)

        entityData.set(FLAME_STATE, FlameClock.State.OFF.ordinal)

        entityData.set(BEAM_LENGTH, -1f)

        if (!level().isClientSide) when (phase) {

            Flight.LAUNCH -> play("guard_dog_release")

            Flight.DOCK -> play("guard_dog_return")

            else -> Unit

        }

    }



    private fun play(key: String) {

        val sound = com.example.sbwhelldiver.init.ModSounds.EVENTS.getValue(key).get()

        level().playSound(null, x, y, z, sound, SoundSource.PLAYERS, .8f, 1f)

    }



    fun recall() {

        when (flight) {

            Flight.LAUNCH -> recallQueued = true

            Flight.PATROL -> enter(Flight.RETURN)

            else -> Unit

        }

    }



    override fun tick() {

        super.tick()

        val server = level() as? ServerLevel

        if (server == null) {

            val before = position()

            val yawBefore = yRot

            val attachedOwner = clientOwner()?.takeIf { usesPackPose }

            if (attachedOwner != null) {

                setPos(transitionPosition(attachedOwner, phaseAge.toDouble()))

                yRot = attachedOwner.yBodyRot

                xRot = 0f

            } else if (lerpTicks > 0) {

                val fraction = 1.0 / lerpTicks--

                setPos(position().lerp(lerpPosition, fraction))

                yRot = Mth.rotLerp(fraction.toFloat(), yRot, lerpYaw)

                xRot = Mth.lerp(fraction.toFloat(), xRot, lerpPitch)

            }

            previousThrustVelocity = thrustVelocity

            previousThrustAcceleration = thrustAcceleration

            previousTurnRate = turnRate

            thrustVelocity = thrustVelocity.lerp(position().subtract(before), .28)

            thrustAcceleration = thrustAcceleration.lerp(thrustVelocity.subtract(previousThrustVelocity), .32)

            turnRate = Mth.lerp(.28f, turnRate, Mth.wrapDegrees(yRot - yawBefore))

            return

        }

        val owner = ownerId?.let { server.getPlayerByUUID(it) } as? ServerPlayer

        val pack = owner?.let(GuardDogBackpack::equipped)

        if (owner == null || !owner.isAlive || owner.isSpectator || pack == null || GuardDogBackpack.active(pack) != uuid) {

            clearDeployment(deployedPack, server.gameTime)

            // Picking up an armor slot's stack can put a copy on the inventory cursor.

            clearDeployment(owner?.containerMenu?.carried, server.gameTime)

            discard()

            return

        }

        // Catch up after teleports and let a blocked return end in the backpack.

        if (distanceToSqr(owner) > SENSOR_RANGE * SENSOR_RANGE || flight == Flight.RETURN && phaseAge >= 160) {

            GuardDogBackpack.dock(pack, server.gameTime)

            discard()

            return

        }

        if (isGas) tickGasClouds(server, owner)

        when (flight) {

            Flight.LAUNCH -> {

                followPack(owner, transitionPosition(owner, phaseAge.toDouble()))

                val lift = GuardDogMotion.liftTick(isEnergy)

                if (phaseAge == lift - 2L) play("guard_dog_ignition")

                if (phaseAge == lift.toLong()) play("guard_dog_latch")

                if (phaseAge == lift + 10L) play("guard_dog_rack")

                if (phaseAge >= TRANSITION_TICKS) enter(if (recallQueued) Flight.RETURN else Flight.PATROL)

            }

            Flight.PATROL -> {

                if (isGas) tickGas(server, owner, pack) else if (isFlame) tickFlame(server, owner, pack) else {

                    patrol(owner)

                    attack(server, owner, pack)

                }

            }

            Flight.RETURN -> {

                val staging = freePosition(owner, false)

                val approachSpeed = (position().distanceTo(staging) * .35).coerceIn(FLIGHT_SPEED, .8)

                flyTo(staging, approachSpeed, .7)

                aim(owner.yBodyRot, 0f)

                val aligned = phaseAge >= 4 && position().distanceToSqr(staging) < .18 &&

                    kotlin.math.abs(Mth.wrapDegrees(yRot - owner.yBodyRot)) < 16f &&

                    clear(position().add(0.0, .18, 0.0), dockPosition(owner).add(0.0, .18, 0.0))

                // Let the approach settle before transferring the pose to the backpack layer.

                dockApproachTicks = if (aligned) dockApproachTicks + 1 else 0

                if (dockApproachTicks >= 3) {

                    val spares = GuardDogBackpack.spareMags(pack)

                    val plan = when {

                        isEnergy || isSprayer && GuardDogBackpack.ammo(pack) >= (if (isGas) 1 else FlameClock.AMMO_PER_JET) -> Reload.NONE

                        isSprayer && spares == 0 -> Reload.NONE

                        spares == 0 -> Reload.EMPTY

                        GuardDogBackpack.ammo(pack) == MAGAZINE -> Reload.NONE

                        (GuardDogBackpack.reserveCapacity(pack) - spares) % 2 == 0 -> Reload.RIGHT

                        else -> Reload.LEFT

                    }

                    dockApproachOffset = position().subtract(dockPosition(owner))

                        .yRot(owner.yBodyRot * Mth.DEG_TO_RAD)

                    entityData.set(DOCK_OFFSET, dockApproachOffset.toVector3f())

                    entityData.set(RELOAD, plan.ordinal)

                    entityData.set(DOCKING_SPARES, spares)

                    enter(Flight.DOCK)

                }

            }

            Flight.DOCK -> {

                val age = phaseAge.toInt()

                followPack(owner, transitionPosition(owner, age.toDouble()))

                if (!isSprayer && age == EJECT_TICK && reload.changesMagazine) {

                    GuardDogBackpack.updateAmmo(pack, 0)

                }

                if (age == GuardDogMotion.contactTick(isEnergy)) {

                    play("guard_dog_dock")

                    play("guard_dog_shutdown")

                }

                if (age == GuardDogMotion.contactTick(isEnergy) + 11) play("guard_dog_rack")

                if (age == TRANSITION_TICKS + (if (isSprayer) FLAME_REFILL_TICK else INSERT_TICK) && reload.changesMagazine) {

                    GuardDogBackpack.loadSpare(pack)

                }

                val extra = when (reload) {

                    Reload.NONE -> 0

                    Reload.EMPTY -> 10

                    else -> if (isSprayer) FLAME_RELOAD_TICKS else RELOAD_TICKS

                }

                if (age >= TRANSITION_TICKS + extra) {

                    GuardDogBackpack.dock(pack, server.gameTime, reload == Reload.EMPTY)

                    discard()

                }

            }

        }

    }



    private fun clearDeployment(pack: ItemStack?, now: Long) {

        if (pack != null && GuardDogBackpack.active(pack) == uuid) {

            GuardDogBackpack.dock(pack, now, GuardDogBackpack.emptyFold(pack))

        }

    }



    private fun followPack(owner: Player, position: Vec3) {

        setPos(position)

        yRot = owner.yBodyRot

        xRot = 0f

    }



    private fun patrol(owner: Player) {

        val offset = Vec3(-2.2, 3.05 + sin(tickCount * .07) * .20, -1.6)

            .yRot(-owner.yBodyRot * Mth.DEG_TO_RAD)

        var destination = owner.position().add(offset)

        val pivot = owner.position().add(0.0, 2.95, 0.0)

        if (!clear(pivot, destination)) {

            destination = pivot.lerp(destination, .3)

            if (!clear(pivot, destination)) destination = pivot

        }

        if (flyTo(destination, if (distanceToSqr(owner) > 100.0) .46 else FLIGHT_SPEED) && target == null) {

            aim(owner.yBodyRot, 0f)

        }

    }



    private fun flyTo(destination: Vec3, speed: Double, response: Double = .35): Boolean {

        val start = position()

        val wanted = destination.subtract(start).scale(response)

        val motion = if (wanted.lengthSqr() > speed * speed) wanted.normalize().scale(speed) else wanted

        move(MoverType.SELF, motion)

        return position().distanceToSqr(start) > .000001

    }



    private fun selectTarget(server: ServerLevel, owner: ServerPlayer, origin: Vec3, radius: Double) {

        fun hostile(enemy: LivingEntity) = enemy.isAlive && enemy !is Player &&

            CombatHostility.isHostileTo(enemy, owner) && (!isGas || !enemy.hasEffect(com.example.sbwhelldiver.init.ModEffects.GAS.get()))

        fun visible(enemy: LivingEntity) = enemy.distanceToSqr(owner) <= radius * radius &&

            clear(origin, enemy.boundingBox.center)

        if (target?.let { hostile(it) && visible(it) } != true) target = null

        if (target == null && (isGas || (tickCount + id) % 9 == 0)) {

            target = server.getEntitiesOfClass(LivingEntity::class.java, owner.boundingBox.inflate(radius), ::hostile)

                .sortedBy { it.distanceToSqr(this) }.firstOrNull(::visible)

        }

    }



    private fun tickGas(server: ServerLevel, owner: ServerPlayer, pack: ItemStack) {

        val ammo = GuardDogBackpack.ammo(pack)

        if (ammo <= 0) { recall(); return }

        selectTarget(server, owner, position().add(0.0, MODEL_HEIGHT, 0.0), FLAME_LEASH)

        val enemy = target

        if (enemy == null) {

            entityData.set(GAS_SPRAY_LENGTH, 0f)

            shotClock = 0

            patrol(owner)

            return

        }

        val point = enemy.boundingBox.center

        if (muzzleOrigin().distanceToSqr(point) > 5.5 * 5.5 || distanceToSqr(owner) > FLAME_LEASH * FLAME_LEASH) {

            val delta = position().subtract(point)

            val horizontal = Vec3(delta.x, 0.0, delta.z).normalize()

            val wanted = point.add(horizontal.scale(5.0)).add(0.0, 1.6, 0.0)

            val relative = wanted.subtract(owner.position())

            flyTo(if (relative.lengthSqr() <= FLAME_LEASH * FLAME_LEASH) wanted

                else owner.position().add(relative.normalize().scale(FLAME_LEASH)), FLIGHT_SPEED)

        }

        val offset = point.subtract(muzzleOrigin())

        val yaw = Math.toDegrees(atan2(-offset.x, offset.z)).toFloat()

        val pitch = Math.toDegrees(atan2(-offset.y, hypot(offset.x, offset.z))).toFloat()

        aim(yaw, pitch)

        val muzzle = muzzleOrigin()

        val ready = muzzle.distanceToSqr(point) <= GAS_RANGE * GAS_RANGE &&

            kotlin.math.abs(Mth.wrapDegrees(yaw - yRot)) <= 3f && kotlin.math.abs(pitch - xRot) <= 3f &&

            clear(position().add(0.0, MODEL_HEIGHT, 0.0), muzzle) && clear(muzzle, point)

        entityData.set(GAS_SPRAY_LENGTH, if (ready) muzzle.distanceTo(point).toFloat() else 0f)

        if (!ready) {

            shotClock = 0

            return

        }



        if (gasClouds.lastOrNull()?.let { server.gameTime - it.born >= 7 } != false) {

            val axis = Vec3.directionFromRotation(xRot, yRot)

            gasClouds.addLast(GasCloud(muzzle.add(axis.scale(muzzle.distanceTo(point) * .94)), server.gameTime))

        }



        if (shotClock-- > 0) return

        shotClock = GAS_INTERVAL_TICKS - 1

        GuardDogBackpack.updateAmmo(pack, ammo - 1)

        if (ammo == 1) recall()

    }



    private fun tickGasClouds(server: ServerLevel, owner: ServerPlayer) {

        gasClouds.removeAll { server.gameTime - it.born >= 28 }

        val exposed = mutableSetOf<UUID>()

        for (cloud in gasClouds) {

            val box = AABB.ofSize(cloud.center, 4.0, 3.6, 4.0)

            for (candidate in server.getEntitiesOfClass(LivingEntity::class.java, box) {

                it.isAlive && !it.isSpectator && !it.hasEffect(com.example.sbwhelldiver.init.ModEffects.GAS.get())

            }) {

                val bounds = candidate.boundingBox

                val closest = Vec3(

                    cloud.center.x.coerceIn(bounds.minX, bounds.maxX),

                    cloud.center.y.coerceIn(bounds.minY, bounds.maxY),

                    cloud.center.z.coerceIn(bounds.minZ, bounds.maxZ)

                )

                if (cloud.center.distanceToSqr(closest) <= 4.0 && clear(cloud.center, closest)) exposed.add(candidate.uuid)

            }

        }

        gasExposure.keys.retainAll(exposed)

        for (id in exposed) {

            val ticks = (gasExposure[id] ?: 0) + 1

            if (ticks >= GuardDogGas.EXPOSURE_TICKS) {

                (server.getEntity(id) as? LivingEntity)?.let { GuardDogGas.poison(it, owner) }

                gasExposure.remove(id)

            } else gasExposure[id] = ticks

        }

    }



    private fun tickFlame(server: ServerLevel, owner: ServerPlayer, pack: ItemStack) {

        val ammo = GuardDogBackpack.ammo(pack)

        if (ammo < FlameClock.AMMO_PER_JET) {

            recall()

            return

        }

        val origin = position().add(0.0, MODEL_HEIGHT, 0.0)

        selectTarget(server, owner, origin, FLAME_LEASH)

        val enemy = target

        var inRange = false

        var aligned = false

        if (enemy == null) patrol(owner) else {

            val point = enemy.boundingBox.center

            val muzzle = muzzleOrigin()

            if (muzzle.distanceToSqr(point) > 5.5 * 5.5 || distanceToSqr(owner) > FLAME_LEASH * FLAME_LEASH) {

                val delta = position().subtract(point)

                val horizontal = Vec3(delta.x, 0.0, delta.z).normalize()

                val wanted = point.add(horizontal.scale(5.0)).add(0.0, 1.6, 0.0)

                val fromOwner = wanted.subtract(owner.position())

                val destination = if (fromOwner.lengthSqr() <= FLAME_LEASH * FLAME_LEASH) wanted

                    else owner.position().add(fromOwner.normalize().scale(FLAME_LEASH))

                flyTo(destination, FLIGHT_SPEED)

            }

            val toTarget = point.subtract(muzzleOrigin())

            val yaw = Math.toDegrees(atan2(-toTarget.x, toTarget.z)).toFloat()

            val pitch = Math.toDegrees(atan2(-toTarget.y, hypot(toTarget.x, toTarget.z))).toFloat()

            aim(yaw, pitch)

            val barrel = muzzleOrigin()

            inRange = barrel.distanceToSqr(point) <= FLAME_RANGE * FLAME_RANGE &&

                clear(position().add(0.0, MODEL_HEIGHT, 0.0), barrel) && clear(barrel, point)

            aligned = kotlin.math.abs(Mth.wrapDegrees(yaw - yRot)) <= 3f && kotlin.math.abs(pitch - xRot) <= 3f

        }

        val fire = flameClock.advance(server.gameTime, inRange, aligned, ammo)

        entityData.set(FLAME_STATE, flameClock.state.ordinal)

        if (fire) {

            FlameJet.spray(server, this, muzzleOrigin(), Vec3.directionFromRotation(xRot, yRot),

                FLAME_RANGE, FlameClock.DIRECT_DAMAGE_PER_JET * .25f, owner)

            GuardDogBackpack.updateAmmo(pack, ammo - FlameClock.AMMO_PER_JET)

        }

    }



    private fun attack(server: ServerLevel, owner: ServerPlayer, pack: ItemStack) {

        if (isStun && shotClock > 0) shotClock--

        if (!isEnergy && GuardDogBackpack.ammo(pack) <= 0) {

            recall()

            return

        }

        val origin = position().add(0.0, MODEL_HEIGHT + when { isStun -> STUN_MUZZLE.y; isLaser -> LASER_MUZZLE.y; else -> -.2125 }, 0.0)

        selectTarget(server, owner, origin, SENSOR_RANGE)

        val enemy = target

        if (enemy == null) {

            stopBeam()

            if (!isStun) shotClock = 0

            return

        }

        val point = enemy.boundingBox.center.add(enemy.deltaMovement.scale(if (isEnergy) 0.0 else enemy.distanceTo(this) / BULLET_SPEED.toDouble()))

        val toTarget = point.subtract(origin)

        val yaw = Mth.wrapDegrees(Math.toDegrees(atan2(-toTarget.x, toTarget.z)).toFloat())

        val pitch = Math.toDegrees(atan2(-toTarget.y, hypot(toTarget.x, toTarget.z))).toFloat()

        aim(yaw, pitch)

        if (kotlin.math.abs(Mth.wrapDegrees(yaw - yRot)) > 3f || kotlin.math.abs(pitch - xRot) > 3f) {

            stopBeam()

            if (!isStun) shotClock = 0

            return

        }

        if (isStun) {

            // Native ArcWeapon: 12 rounds/minute, independent of target changes.

            if (shotClock > 0) return

            val muzzle = muzzleOrigin()

            if (!clear(origin, muzzle) || !clear(muzzle, point)) return

            val links = GuardDogArc.discharge(this, owner, enemy)

            if (links.isEmpty()) return

            shotClock = GuardDogArc.INTERVAL_TICKS

            val tag = CompoundTag()

            tag.putInt("Serial", arcData.getInt("Serial") + 1)

            tag.putLong("Time", server.gameTime)

            tag.putInt("Count", links.size)

            links.forEachIndexed { i, link ->

                tag.putInt("From$i", link.parent)

                tag.putDouble("X$i", link.point.x); tag.putDouble("Y$i", link.point.y); tag.putDouble("Z$i", link.point.z)

            }

            entityData.set(ARC_DATA, tag)

            play("stun_guard_dog_fire")

            return

        }

        if (isLaser) {

            val muzzle = laserOrigin()

            if (!clear(origin, muzzle)) { stopBeam(); return }

            val hit = LaserBeam.trace(this, muzzle, Vec3.directionFromRotation(xRot, yRot), SENSOR_RANGE)

            entityData.set(BEAM_LENGTH, muzzle.distanceTo(hit.end).toFloat())

            hit.mine?.detonate()

            val pulse = laserDamageClock <= 0.0

            if (pulse) laserDamageClock += 1200.0 / com.example.sbwhelldiver.config.TurretConfigs.get("laser_sentry").rpm.get()

            laserDamageClock -= 1.0

            hit.target?.let {

                if (pulse) it.hurt(com.example.sbwhelldiver.effect.FlameDamage.source(it, "laser_beam", owner), LaserClock.DIRECT_DAMAGE * LASER_DAMAGE_MULTIPLIER)

                com.example.sbwhelldiver.effect.FlameDamage.ignite(it, owner)

            }

            return

        }

        shotClock += ROUNDS_PER_MINUTE

        if (shotClock < 1200) return

        shotClock -= 1200



        val direction = Vec3.directionFromRotation(xRot, yRot)

        val muzzle = muzzleOrigin()

        if (!clear(origin, muzzle) || !clear(muzzle, point)) return

        val bullet = ModEntities.SENTRY_BULLET.get().create(server) as? SentryBulletEntity ?: return

        bullet.owner = owner

        bullet.emitterId = id

        bullet.setPos(muzzle)

        bullet.setDamage(10f)

        bullet.setHeadShot(1f)

        bullet.setLegShot(1f)

        // SBW compares actual impact speed to this baseline to apply damage falloff.

        bullet.setVelocity(BULLET_SPEED * .75f)

        bullet.setCustomGravity(.01f)

        bullet.shoot(direction.x, direction.y, direction.z, BULLET_SPEED, .12f)

        if (!server.addFreshEntity(bullet)) return

        val remaining = GuardDogBackpack.ammo(pack) - 1

        GuardDogBackpack.updateAmmo(pack, remaining)

        val fireSound = com.example.sbwhelldiver.init.ModSounds.EVENTS.getValue("guard_dog_fire").get()

        server.playSound(null, muzzle.x, muzzle.y, muzzle.z, fireSound, SoundSource.PLAYERS, .85f, 1.08f)

        if (remaining == 0) recall()

    }



    private fun stopBeam() {

        entityData.set(BEAM_LENGTH, -1f)

        laserDamageClock = 0.0

    }



    private fun clear(from: Vec3, to: Vec3): Boolean =

        level().clip(ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).type == HitResult.Type.MISS



    private fun aim(yaw: Float, pitch: Float) {

        val step = if (isStun) (3.14 * 180.0 / Math.PI / 20.0).toFloat() else TURN_PER_TICK

        yRot = Mth.approachDegrees(yRot, yaw, step)

        xRot = Mth.approach(xRot, pitch, step)

    }



    override fun shouldRenderAtSqrDistance(distance: Double) = distance < 64.0 * 64.0



    override fun getBoundingBoxForCulling(): AABB = super.getBoundingBoxForCulling().inflate(.6)



    override fun isPickable() = isAlive

    override fun hurt(source: DamageSource, amount: Float): Boolean {

        if (isEnergy || isSprayer) return false

        val server = level() as? ServerLevel ?: return false

        if (!amount.isFinite() || amount <= 0f || isInvulnerableTo(source)) return false

        val player = ownerId?.let { server.getPlayerByUUID(it) } ?: return false

        val pack = GuardDogBackpack.equipped(player) ?: return false

        if (GuardDogBackpack.active(pack) != uuid || GuardDogBackpack.health(pack) <= 0f) return false

        val health = (GuardDogBackpack.health(pack) - amount).coerceAtLeast(0f)

        GuardDogBackpack.updateHealth(pack, health)

        if (health <= 0f) recall()

        return true

    }



    override fun lerpTo(x: Double, y: Double, z: Double, yaw: Float, pitch: Float, steps: Int, teleport: Boolean) {


        hasTrackedPosition = true
        lerpPosition = Vec3(x, y, z)

        lerpYaw = yaw

        lerpPitch = pitch

        // Respect the tracking interval, including the first free-flight update.
        lerpTicks = if (usesPackPose) 0 else steps.coerceIn(3, 10)

    }



    // Forge's rotation-only packet path uses current coordinates. The client
    // packet hook substitutes these retained targets for our drones only.
    fun lerpTargetX() = if (hasTrackedPosition) lerpPosition.x else x
    fun lerpTargetY() = if (hasTrackedPosition) lerpPosition.y else y
    fun lerpTargetZ() = if (hasTrackedPosition) lerpPosition.z else z
    fun lerpTargetYRot() = if (hasTrackedPosition) lerpYaw else yRot
    fun lerpTargetXRot() = if (hasTrackedPosition) lerpPitch else xRot

    fun thrustAlong(yaw: Float, partial: Float): Float {

        val forward = Vec3.directionFromRotation(0f, yaw)

        val velocity = previousThrustVelocity.lerp(thrustVelocity, partial.toDouble())

        val acceleration = previousThrustAcceleration.lerp(thrustAcceleration, partial.toDouble())

        return (velocity.dot(forward) * 2.8 + acceleration.dot(forward) * 8.0).toFloat()

    }



    fun thrustAcross(yaw: Float, partial: Float): Float {

        val left = Vec3.directionFromRotation(0f, yaw - 90f)

        val velocity = previousThrustVelocity.lerp(thrustVelocity, partial.toDouble())

        val acceleration = previousThrustAcceleration.lerp(thrustAcceleration, partial.toDouble())

        return (velocity.dot(left) * 2.8 + acceleration.dot(left) * 8.0).toFloat()

    }



    fun thrustTurn(partial: Float): Float = Mth.lerp(partial, previousTurnRate, turnRate) * .012f



    override fun readAdditionalSaveData(tag: CompoundTag) {}

    override fun addAdditionalSaveData(tag: CompoundTag) {}



    companion object {

        const val CALL_TICKS = 195 // 9.75 seconds at 20 ticks per second

        const val LASER_DAMAGE_MULTIPLIER = .6f

        const val GAS_MAGAZINE = 100

        const val GAS_RESERVES = 6

        const val GAS_RANGE = 10.0

        const val GAS_INTERVAL_TICKS = 2

        val GAS_MUZZLE = Vec3(-.00486 / 16, -4.73624 / 16, -7.24159 / 16)

        private val GAS = SynchedEntityData.defineId(GuardDogEntity::class.java, EntityDataSerializers.BOOLEAN)

        private val GAS_SPRAY_LENGTH = SynchedEntityData.defineId(GuardDogEntity::class.java, EntityDataSerializers.FLOAT)

        const val FLAME_MAGAZINE = 80

        const val FLAME_RESERVES = 6

        const val FLAME_RELOAD_TICKS = 40

        const val FLAME_REFILL_TICK = 20

        const val FLAME_RANGE = 10.0

        private const val FLAME_LEASH = 20.0

        val FLAME_MUZZLE = Vec3(-.004862 / 16, -4.703468 / 16, -9.312246 / 16)

        private val FLAME = SynchedEntityData.defineId(GuardDogEntity::class.java, EntityDataSerializers.BOOLEAN)

        private val FLAME_STATE = SynchedEntityData.defineId(GuardDogEntity::class.java, EntityDataSerializers.INT)

        private val LASER_MUZZLE = Vec3(-.003942 / 16, -4.602679 / 16, -4.998472 / 16)

        private val STUN_MUZZLE = Vec3(-0.0042053589005740626 / 16, -3.877977775969399 / 16, -9.98510232604056 / 16)

        private val MG_MUZZLE = Vec3(0.0, -3.39958 / 16, -5.55418 / 16)

        private val STUN = SynchedEntityData.defineId(GuardDogEntity::class.java, EntityDataSerializers.BOOLEAN)

        private val ARC_DATA = SynchedEntityData.defineId(GuardDogEntity::class.java, EntityDataSerializers.COMPOUND_TAG)

        private val LASER = SynchedEntityData.defineId(GuardDogEntity::class.java, EntityDataSerializers.BOOLEAN)

        private val BEAM_LENGTH = SynchedEntityData.defineId(GuardDogEntity::class.java, EntityDataSerializers.FLOAT)

        private val OWNER = SynchedEntityData.defineId(GuardDogEntity::class.java, EntityDataSerializers.INT)

        private val FLIGHT = SynchedEntityData.defineId(GuardDogEntity::class.java, EntityDataSerializers.INT)

        private val DOCKING_SPARES = SynchedEntityData.defineId(GuardDogEntity::class.java, EntityDataSerializers.INT)

        private val DOCK_OFFSET = SynchedEntityData.defineId(GuardDogEntity::class.java, EntityDataSerializers.VECTOR3)

        private val RELOAD = SynchedEntityData.defineId(GuardDogEntity::class.java, EntityDataSerializers.INT)

        private val PHASE_START = SynchedEntityData.defineId(GuardDogEntity::class.java, EntityDataSerializers.LONG)



        // Native ProjectileWeapon, WeaponMagazine, Health, Rotation, SensorEye, Motion and Boids components.

        const val MAGAZINE = 45

        const val SPARE_MAGAZINES = 8

        const val MAX_HEALTH = 100f

        const val REARM_TICKS = 50L

        const val TRANSITION_TICKS = 67

        const val IGNITION_TICK = 29

        const val LIFT_TICK = 31

        const val CONTACT_TICK = 20

        const val EJECT_TICK = 5

        const val RELOAD_TICKS = 34

        const val INSERT_TICK = 18

        const val PACK_LAYER_Y = .24

        const val PACK_LAYER_Z = .27

        const val MODEL_HEIGHT = .24

        private val DRONE_MOUNT = Vec3(0.0, -.01, -.23)

        private const val ROUNDS_PER_MINUTE = 660

        private const val TURN_PER_TICK = 360f / 20f

        private const val FLIGHT_SPEED = 6.0 / 20.0

        private const val SENSOR_RANGE = 50.0

        private const val BULLET_SPEED = 18f



        fun dockPosition(player: Player, partial: Float? = null): Vec3 {

            // HumanoidModel pitches the chest forward when crouching.

            val angle = if (player.isCrouching) -.5 else 0.0

            val offset = Vec3(0.0, -PACK_LAYER_Y + DRONE_MOUNT.y, -PACK_LAYER_Z + DRONE_MOUNT.z).xRot(angle.toFloat())

            val height = if (player.isCrouching) 1.30 else 1.501

            val position = if (partial == null) player.position() else player.getPosition(partial)

            val yaw = if (partial == null) player.yBodyRot else Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot)

            return position.add(offset.add(0.0, height - MODEL_HEIGHT, 0.0).yRot(-yaw * Mth.DEG_TO_RAD))

        }



        private fun freePosition(player: Player, launching: Boolean, partial: Float? = null) = dockPosition(player, partial).add(

            Vec3(0.0, if (launching) .71 else .56, if (launching) -1.11 else -.95)

                .yRot(-(if (partial == null) player.yBodyRot else Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot)) * Mth.DEG_TO_RAD))



        private fun ease(value: Double): Double {

            val t = value.coerceIn(0.0, 1.0)

            return t * t * (3.0 - 2.0 * t)

        }

    }

}
