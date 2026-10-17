package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.config.HelldiversConfig
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.NetworkHooks
import java.util.UUID
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

/** Debris owns its physics and lifetime, independently of the deployed/retracted pod. */
class HellpodCapEntity(type: EntityType<HellpodCapEntity>, level: Level) : Entity(type, level) {
    var sourcePodUUID: UUID? = null
        private set
    var groundedAge = 0
        private set
    var groundLifetimeTicks = 600
        private set
    private var totalAge = 0
    private var pitchSpeed = 17f
    private var rollSpeed = 3f
    val settled get() = entityData.get(DATA_SETTLED)
    val capPitch get() = entityData.get(DATA_PITCH)
    val capRoll get() = entityData.get(DATA_ROLL)
    var previousVisualPitch = 0f
        private set
    var previousVisualRoll = 0f
        private set
    var visualPitch = 0f
        private set
    var visualRoll = 0f
        private set
    private var lerpSteps = 0
    private var lerpPosition = Vec3.ZERO
    private var lerpYaw = 0f

    override fun defineSynchedData() {
        entityData.define(DATA_SETTLED, false)
        entityData.define(DATA_PITCH, 0f)
        entityData.define(DATA_ROLL, 0f)
    }

    fun ejectFrom(pod: AbstractHellpodEntity) {
        sourcePodUUID = pod.uuid
        groundLifetimeTicks = HelldiversConfig.capLifetimeTicks.get()
        pitchSpeed = 10f + random.nextFloat() * 14f
        rollSpeed = 1f + random.nextFloat() * 4f
        // The extracted model's bottom is at 3.3225/16 above the pod rim.
        moveTo(pod.x, pod.y + MODEL_BOTTOM, pod.z, pod.yRot, 0f)
        val direction = Math.toRadians((pod.yRot + 110 + random.nextFloat() * 40).toDouble())
        deltaMovement = Vec3(-sin(direction) * 0.24, 0.48, cos(direction) * 0.24)
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) {
            if (lerpSteps > 0) {
                setPos(position().add(lerpPosition.subtract(position()).scale(1.0 / lerpSteps)))
                yRot += Mth.wrapDegrees(lerpYaw - yRot) / lerpSteps
                lerpSteps--
            }
            previousVisualPitch = visualPitch
            previousVisualRoll = visualRoll
            visualPitch += (capPitch - visualPitch) * 0.7f
            visualRoll += (capRoll - visualRoll) * 0.7f
            return
        }
        totalAge++
        deltaMovement = deltaMovement.add(0.0, -0.04, 0.0)
        move(MoverType.SELF, deltaMovement)
        if (onGround()) {
            if (!settled) {
                entityData.set(DATA_SETTLED, true)
                // Let either the decorated face or the white catches face upwards.
                entityData.set(DATA_PITCH, round(capPitch / 180f) * 180f)
                entityData.set(DATA_ROLL, 0f)
                pitchSpeed = 0f
                rollSpeed = 0f
            }
            deltaMovement = deltaMovement.multiply(0.55, 0.0, 0.55)
            if (deltaMovement.horizontalDistanceSqr() < 0.00001) deltaMovement = Vec3.ZERO
            groundedAge++
        } else {
            entityData.set(DATA_SETTLED, false)
            entityData.set(DATA_PITCH, capPitch + pitchSpeed)
            entityData.set(DATA_ROLL, capRoll + rollSpeed)
            deltaMovement = deltaMovement.scale(0.98)
        }
        if (groundedAge >= groundLifetimeTicks || totalAge >= 6000 || y < level().minBuildHeight - 32) discard()
    }

    // Server-authoritative block collision; smooth network updates without client physics drift.
    override fun lerpTo(x: Double, y: Double, z: Double, yaw: Float, pitch: Float, steps: Int, teleport: Boolean) {
        lerpPosition = Vec3(x, y, z)
        lerpYaw = yaw
        lerpSteps = if (teleport) 1 else steps.coerceAtLeast(1)
    }

    override fun isPushedByFluid() = false
    override fun getAddEntityPacket(): Packet<ClientGamePacketListener> = NetworkHooks.getEntitySpawningPacket(this)

    override fun addAdditionalSaveData(tag: CompoundTag) {
        sourcePodUUID?.let { tag.putUUID("SourcePod", it) }
        tag.putInt("CapAge", totalAge)
        tag.putInt("GroundedAge", groundedAge)
        tag.putInt("GroundLifetimeTicks", groundLifetimeTicks)
        tag.putBoolean("CapSettled", settled)
        tag.putFloat("CapPitch", capPitch)
        tag.putFloat("CapRoll", capRoll)
        tag.putFloat("PitchSpeed", pitchSpeed)
        tag.putFloat("RollSpeed", rollSpeed)
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        sourcePodUUID = if (tag.hasUUID("SourcePod")) tag.getUUID("SourcePod") else null
        totalAge = tag.getInt("CapAge")
        groundedAge = tag.getInt("GroundedAge")
        groundLifetimeTicks = if (tag.contains("GroundLifetimeTicks")) tag.getInt("GroundLifetimeTicks").coerceAtLeast(1) else HelldiversConfig.capLifetimeTicks.get()
        entityData.set(DATA_SETTLED, tag.getBoolean("CapSettled"))
        entityData.set(DATA_PITCH, tag.getFloat("CapPitch"))
        entityData.set(DATA_ROLL, tag.getFloat("CapRoll"))
        pitchSpeed = tag.getFloat("PitchSpeed")
        rollSpeed = tag.getFloat("RollSpeed")
        visualPitch = capPitch; previousVisualPitch = capPitch
        visualRoll = capRoll; previousVisualRoll = capRoll
    }

    companion object {
        const val MODEL_BOTTOM = 3.3225 / 16.0
        const val MODEL_THICKNESS = 1.04 / 16.0
        private val DATA_SETTLED = SynchedEntityData.defineId(HellpodCapEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val DATA_PITCH = SynchedEntityData.defineId(HellpodCapEntity::class.java, EntityDataSerializers.FLOAT)
        private val DATA_ROLL = SynchedEntityData.defineId(HellpodCapEntity::class.java, EntityDataSerializers.FLOAT)
    }
}
