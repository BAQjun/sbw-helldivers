package com.example.sbwhelldiver.entity

import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity
import com.example.sbwhelldiver.asset.AssetCatalog
import com.example.sbwhelldiver.client.HellpodAnimations
import com.example.sbwhelldiver.config.HelldiversConfig
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityDimensions
import net.minecraft.world.entity.Pose
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.Vec3

/** The entity origin is the pod rim. The buried hull never changes terrain. */
abstract class AbstractHellpodEntity(type: EntityType<*>, level: Level) : AutoAimableEntity(type, level) {
    enum class Phase { FALLING, IMPACT, DEPLOYING, ACTIVE, RETRACTING, DONE, DESTROYED, SPENT }

    val profile get() = AssetCatalog.profile(type)
    var phase: Phase
        get() = Phase.entries[entityData.get(DATA_PHASE).coerceIn(0, Phase.entries.lastIndex)]
        private set(value) { entityData.set(DATA_PHASE, value.ordinal) }
    val phaseAge get() = if (level().isClientSide) entityData.get(DATA_PHASE_AGE) else phaseTicks
    val braking get() = entityData.get(DATA_BRAKING)
    val brakeAge get() = if (level().isClientSide) entityData.get(DATA_BRAKE_AGE) else
        (level().gameTime - entityData.get(DATA_BRAKE_START)).coerceAtLeast(0).toInt()
    val firingRecently get() = entityData.get(DATA_FIRING)
    val capReleased get() = entityData.get(DATA_CAP_RELEASED)
    val shotSerial get() = entityData.get(DATA_SHOT_SERIAL)
    var turretAimYaw: Float
        get() = entityData.get(DATA_TURRET_YAW)
        protected set(value) { entityData.set(DATA_TURRET_YAW, value) }
    var turretAimPitch: Float
        get() = entityData.get(DATA_TURRET_PITCH)
        protected set(value) { entityData.set(DATA_TURRET_PITCH, value) }

    open val packedAnimation = "animation.hellpod.packed"
    open val deployAnimation = "animation.hellpod.deploy"
    open val retractAnimation = "animation.hellpod.retract"
    open val deployTicks get() = profile.deployTicks
    open val retractTicks get() = profile.retractTicks
    protected var activeTicks = 0
    private var phaseTicks = 0
    private var restY = Double.NaN
    private var phaseStartedAt = -1L
    private var lastShotTick = -100L
    private var scheduledLaunch = -1L
    private var scheduledArrival = -1L
    private var scheduledStartY = 0.0
    val scheduledFlight get() = entityData.get(DATA_ARRIVAL) >= 0
    val impactDeadline get() = entityData.get(DATA_ARRIVAL)
    val landingY get() = entityData.get(DATA_REST_Y).toDouble()
    private var previousFlightY = Double.NaN
    var flightSampleTime = Double.NaN
        private set

    fun scheduleArrival(surfaceY: Double, arrivalGameTime: Long) {
        restY = surfaceY - HelldiversConfig.effectiveBurialDepth() + profile.podHeight
        entityData.set(DATA_REST_Y, restY.toFloat())
        scheduledStartY = y
        scheduledLaunch = level().gameTime
        scheduledArrival = arrivalGameTime.coerceAtLeast(scheduledLaunch + 1)
        syncFlight()
    }

    private fun syncFlight() {
        entityData.set(DATA_LAUNCH, scheduledLaunch)
        entityData.set(DATA_ARRIVAL, scheduledArrival)
        entityData.set(DATA_START_Y, scheduledStartY.toFloat())
        entityData.set(DATA_BRAKE_DISTANCE, descentBrakeHeight().toFloat())
    }

    /** Both sides evaluate one monotonic descent, rather than predicting vehicle gravity. */
    fun scheduledHeight(gameTime: Double): Double {
        val launch = entityData.get(DATA_LAUNCH)
        val arrival = impactDeadline
        val duration = (arrival - launch).coerceAtLeast(1)
        val age = (gameTime - launch).coerceIn(0.0, duration.toDouble())
        val brakeTicks = minOf(SCHEDULED_BRAKE_TICKS.toLong(), maxOf(1L, duration - 1))
        val fastTicks = duration - brakeTicks
        val fullDistance = (entityData.get(DATA_START_Y).toDouble() - landingY).coerceAtLeast(0.0)
        val brakeDistance = minOf(entityData.get(DATA_BRAKE_DISTANCE).toDouble(), fullDistance)
        val distance = if (age < fastTicks && fastTicks > 0)
            fullDistance - (fullDistance - brakeDistance) * age / fastTicks
        else {
            val remaining = ((duration - age) / brakeTicks).coerceIn(0.0, 1.0)
            // Keep a substantial terminal speed: braking is not a soft landing.
            brakeDistance * (0.28 * remaining + 0.72 * remaining * remaining)
        }
        return landingY + distance
    }

    // A pod follows its own descent and then stays buried. SBW vehicle gravity and
    // step-up collision must never move it before our path is applied each tick.
    override fun move(type: MoverType, movement: Vec3) {}

    override fun shouldRenderAtSqrDistance(distance: Double): Boolean =
        if (phase == Phase.FALLING) distance < 1024.0 * 1024.0 else super.shouldRenderAtSqrDistance(distance)

    override fun getDimensions(pose: Pose): EntityDimensions =
        if (entityData.hasItem(DATA_PHASE) && phase in listOf(Phase.DONE, Phase.SPENT))
            EntityDimensions.scalable(2.65f, .13f) else super.getDimensions(pose)

    override fun onSyncedDataUpdated(key: EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(key)
        if (key == DATA_PHASE) {
            refreshDimensions()
            if (level().isClientSide) onClientPhaseChanged(phase)
        }
    }

    override fun handleClientSync() {
        if (!scheduledFlight) super.handleClientSync()
    }

    override fun defineSynchedData() {
        super.defineSynchedData()
        entityData.define(DATA_PHASE, Phase.FALLING.ordinal)
        entityData.define(DATA_PHASE_START, 0L)
        entityData.define(DATA_BRAKING, false)
        entityData.define(DATA_BRAKE_START, 0L)
        entityData.define(DATA_FIRING, false)
        entityData.define(DATA_SHOT_SERIAL, 0)
        entityData.define(DATA_TURRET_YAW, 0f)
        entityData.define(DATA_TURRET_PITCH, 0f)
        entityData.define(DATA_PHASE_AGE, 0)
        entityData.define(DATA_BRAKE_AGE, 0)
        entityData.define(DATA_CAP_RELEASED, false)
        entityData.define(DATA_REST_Y, Float.NaN)
        entityData.define(DATA_LAUNCH, -1L)
        entityData.define(DATA_ARRIVAL, -1L)
        entityData.define(DATA_START_Y, 0f)
        entityData.define(DATA_BRAKE_DISTANCE, 12f)
    }

    // SBW's automatic targeting must not compete with the sentry's synchronized aim.
    override fun autoAim() {}
    protected fun markFired() {
        lastShotTick = level().gameTime
        entityData.set(DATA_FIRING, true)
        entityData.set(DATA_SHOT_SERIAL, shotSerial + 1)
    }

    override fun tick() {
        val previousY = if (level().isClientSide && !previousFlightY.isNaN()) previousFlightY else y
        isNoGravity = true
        noPhysics = phase == Phase.FALLING
        super.tick()
        if (level().isClientSide) {
            // Native vehicle collision can pull the buried hull down to the block surface.
            // Keep the client model at the server's landing height, where its projectiles originate.
            val anchorY = entityData.get(DATA_REST_Y).toDouble()
            if (!anchorY.isNaN() && (scheduledFlight || phase != Phase.FALLING)) {
                // Time-sync packets can correct the client clock backwards by a tick.
                // Keep the sampled flight clock monotonic so the fast pod never jumps upwards.
                val now = level().gameTime.toDouble()
                flightSampleTime = if (flightSampleTime.isNaN()) now else maxOf(now, flightSampleTime)
                val renderedY = if (phase == Phase.FALLING) scheduledHeight(flightSampleTime) else anchorY
                setPos(x, renderedY, z)
                yo = if (phase == Phase.FALLING) previousY else anchorY
                previousFlightY = renderedY
                deltaMovement = Vec3.ZERO
            }
            tickClient()
            HellpodAnimations.tick(this)
            return
        }
        if (phaseStartedAt < 0) {
            phaseStartedAt = level().gameTime - phaseTicks
            entityData.set(DATA_PHASE_START, phaseStartedAt)
        }
        active = phase == Phase.ACTIVE
        ensureRestY()
        phaseTicks++
        entityData.set(DATA_PHASE_AGE, phaseTicks)
        if (braking) entityData.set(DATA_BRAKE_AGE, brakeAge)
        if (firingRecently && (phase != Phase.ACTIVE || level().gameTime - lastShotTick > 4)) {
            entityData.set(DATA_FIRING, false)
        }
        if (phase != Phase.FALLING) {
            deltaMovement = Vec3.ZERO
            if (y != restY) setPos(x, restY, z)
        }
        when (phase) {
            Phase.FALLING -> tickFalling()
            Phase.IMPACT -> if (phaseTicks >= 10) enterPhase(Phase.DEPLOYING)
            Phase.DEPLOYING -> {
                releaseCap()
                if (phaseTicks >= deployTicks) enterPhase(Phase.ACTIVE)
            }
            Phase.ACTIVE -> {
                activeTicks++
                tickActive()
                if (shouldRetract()) enterPhase(Phase.RETRACTING)
            }
            Phase.RETRACTING -> if (phaseTicks >= retractTicks) enterPhase(Phase.DONE)
            Phase.DONE -> if (phaseTicks >= CLOSED_LINGER_TICKS) discard()
            Phase.DESTROYED -> Unit // SBW drains the 1-HP wreck and detonates it.
            Phase.SPENT -> if (phaseTicks >= DESTROY_CLOSE_TICKS + CLOSED_LINGER_TICKS) discard()
        }
    }

    private fun ensureRestY() {
        if (!restY.isNaN()) return
        val surface = level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ)
        restY = surface - HelldiversConfig.effectiveBurialDepth() + profile.podHeight
        entityData.set(DATA_REST_Y, restY.toFloat())
    }

    private fun tickFalling() {
        noPhysics = true
        if (scheduledArrival < 0) {
            val surface = restY + HelldiversConfig.effectiveBurialDepth() - profile.podHeight
            setPos(x, surface + descentSpawnHeight() + ORIGIN_ABOVE_BOTTOM, z)
            scheduleArrival(surface, level().gameTime + FALL_TICKS)
        }
        tickScheduledFall()
    }

    private fun tickScheduledFall() {
        val duration = (scheduledArrival - scheduledLaunch).coerceAtLeast(1)
        val age = (level().gameTime - scheduledLaunch).coerceIn(0, duration)
        val brakeTicks = minOf(SCHEDULED_BRAKE_TICKS.toLong(), maxOf(1L, duration - 1))
        val fastTicks = duration - brakeTicks
        if (age >= fastTicks) {
            if (!braking) {
                entityData.set(DATA_BRAKING, true)
                entityData.set(DATA_BRAKE_START, level().gameTime)
            }
        }
        deltaMovement = Vec3.ZERO
        setPos(x, if (age >= duration) restY else scheduledHeight(level().gameTime.toDouble()), z)
        if (age >= duration) {
            noPhysics = false
            onImpact()
            enterPhase(Phase.IMPACT)
        }
    }

    protected open fun onImpact() {
        val server = level() as? ServerLevel ?: return
        server.sendParticles(ParticleTypes.CLOUD, x, y + 0.05, z, 32, 0.8, 0.12, 0.8, 0.04)
    }
    protected open fun onActivated() {}
    protected open fun onRetracting() {}
    protected open fun tickActive() {}
    protected open fun tickClient() {}
    protected open fun onClientPhaseChanged(next: Phase) {}
    protected open fun shouldRetract(): Boolean {
        val lifetime = HelldiversConfig.effectiveSentryLifetimeTicks()
        return lifetime > 0 && activeTicks >= lifetime
    }

    private fun releaseCap() {
        if (capReleased) return
        val cap = ModEntities.HELLPOD_CAP.get().create(level()) ?: return
        cap.ejectFrom(this)
        if (level().addFreshEntity(cap)) entityData.set(DATA_CAP_RELEASED, true)
    }

    protected fun enterPhase(next: Phase) {
        phase = next
        phaseTicks = 0
        entityData.set(DATA_PHASE_AGE, 0)
        phaseStartedAt = level().gameTime
        entityData.set(DATA_PHASE_START, phaseStartedAt)
        active = next == Phase.ACTIVE
        if (next == Phase.DEPLOYING) releaseCap()
        if (next == Phase.ACTIVE) { activeTicks = 0; onActivated() }
        if (next == Phase.RETRACTING || next == Phase.DESTROYED || next == Phase.SPENT) {
            entityData.set(DATA_FIRING, false)
            onRetracting()
        }
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        tag.putInt("HellpodPhase", phase.ordinal)
        tag.putInt("HellpodPhaseTicks", phaseTicks)
        tag.putInt("HellpodActiveTicks", activeTicks)
        tag.putBoolean("HellpodBraking", braking)
        tag.putInt("HellpodBrakeAge", brakeAge)
        tag.putInt("HellpodShotSerial", shotSerial)
        tag.putBoolean("HellpodCapReleased", capReleased)
        tag.putLong("ScheduledLaunch", scheduledLaunch)
        tag.putLong("ScheduledArrival", scheduledArrival)
        tag.putDouble("ScheduledStartY", scheduledStartY)
        if (!restY.isNaN()) tag.putDouble("HellpodRestY", restY)
    }
    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        scheduledLaunch = if (tag.contains("ScheduledLaunch")) tag.getLong("ScheduledLaunch") else -1L
        scheduledArrival = if (tag.contains("ScheduledArrival")) tag.getLong("ScheduledArrival") else -1L
        scheduledStartY = tag.getDouble("ScheduledStartY")
        syncFlight()
        val phaseKey = if (tag.contains("HellpodPhase")) "HellpodPhase" else "sbw_helldiver:phase"
        val ticksKey = if (tag.contains("HellpodPhaseTicks")) "HellpodPhaseTicks" else "sbw_helldiver:phase_ticks"
        phase = Phase.entries[tag.getInt(phaseKey).coerceIn(0, Phase.entries.lastIndex)]
        phaseTicks = tag.getInt(ticksKey)
        entityData.set(DATA_PHASE_AGE, phaseTicks)
        activeTicks = tag.getInt("HellpodActiveTicks")
        phaseStartedAt = level().gameTime - phaseTicks
        entityData.set(DATA_PHASE_START, phaseStartedAt)
        entityData.set(DATA_BRAKING, tag.getBoolean("HellpodBraking"))
        entityData.set(DATA_BRAKE_START, level().gameTime - tag.getInt("HellpodBrakeAge"))
        entityData.set(DATA_BRAKE_AGE, tag.getInt("HellpodBrakeAge"))
        entityData.set(DATA_SHOT_SERIAL, tag.getInt("HellpodShotSerial"))
        // Old saves already past opening must not suddenly eject another cap.
        entityData.set(DATA_CAP_RELEASED, if (tag.contains("HellpodCapReleased")) tag.getBoolean("HellpodCapReleased")
            else phase.ordinal >= Phase.DEPLOYING.ordinal)
        if (tag.contains("HellpodRestY")) restY = tag.getDouble("HellpodRestY")
        else if (tag.contains("sbw_helldiver:rest_y")) restY = tag.getDouble("sbw_helldiver:rest_y")
        entityData.set(DATA_REST_Y, restY.toFloat())
        noPhysics = phase == Phase.FALLING
    }

    companion object {
        const val CLOSED_LINGER_TICKS = 200
        const val DESTROY_CLOSE_TICKS = 8
        const val ORIGIN_ABOVE_BOTTOM = 5.5456
        const val IGNITE_TICKS = 9
        const val FALL_TICKS = 95
        const val SCHEDULED_BRAKE_TICKS = 44
        fun descentSpawnHeight() = maxOf(720, HelldiversConfig.spawnHeight.get())
        fun descentBrakeHeight() = maxOf(336.0, HelldiversConfig.brakeHeight.get())
        private val DATA_PHASE = data(EntityDataSerializers.INT)
        private val DATA_PHASE_START = data(EntityDataSerializers.LONG)
        private val DATA_BRAKING = data(EntityDataSerializers.BOOLEAN)
        private val DATA_BRAKE_START = data(EntityDataSerializers.LONG)
        private val DATA_FIRING = data(EntityDataSerializers.BOOLEAN)
        private val DATA_SHOT_SERIAL = data(EntityDataSerializers.INT)
        private val DATA_TURRET_YAW = data(EntityDataSerializers.FLOAT)
        private val DATA_TURRET_PITCH = data(EntityDataSerializers.FLOAT)
        private val DATA_PHASE_AGE = data(EntityDataSerializers.INT)
        private val DATA_BRAKE_AGE = data(EntityDataSerializers.INT)
        private val DATA_CAP_RELEASED = data(EntityDataSerializers.BOOLEAN)
        private val DATA_REST_Y = data(EntityDataSerializers.FLOAT)
        private val DATA_LAUNCH = data(EntityDataSerializers.LONG)
        private val DATA_ARRIVAL = data(EntityDataSerializers.LONG)
        private val DATA_START_Y = data(EntityDataSerializers.FLOAT)
        private val DATA_BRAKE_DISTANCE = data(EntityDataSerializers.FLOAT)
        private fun <T> data(serializer: net.minecraft.network.syncher.EntityDataSerializer<T>): EntityDataAccessor<T> =
            SynchedEntityData.defineId(AbstractHellpodEntity::class.java, serializer)
    }
}
