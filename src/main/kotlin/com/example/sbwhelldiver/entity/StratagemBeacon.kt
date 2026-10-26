package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.config.HelldiversConfig
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.stratagem.BeaconData
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.ThrowableItemProjectile
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.gameevent.GameEvent
import net.minecraft.world.level.levelgen.Heightmap
import com.example.sbwhelldiver.network.BeaconFailurePacket
import com.example.sbwhelldiver.network.BeaconNetwork
import net.minecraft.server.level.ServerPlayer
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.network.PacketDistributor
import org.joml.Quaternionf
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.ForgeEventFactory
import kotlin.math.floor
import kotlin.math.min

/** A closed beacon bounces until it reaches an exposed upward-facing surface. */
class StratagemBeacon(type: EntityType<StratagemBeacon>, level: Level) :
    ThrowableItemProjectile(type, level) {

    constructor(type: EntityType<StratagemBeacon>, shooter: LivingEntity, level: Level) :
        this(type, level) {
        setOwner(shooter)
        setPos(shooter.x, shooter.eyeY - 0.1, shooter.z)
        launchOrigin=position();launchYaw=shooter.yRot
    }

    constructor(type: EntityType<StratagemBeacon>, x: Double, y: Double, z: Double, level: Level) :
        this(type, level) { setPos(x, y, z);launchOrigin=position() }

    private var launchOrigin:Vec3?=null
    private var launchYaw=0f
    override fun shoot(x:Double,y:Double,z:Double,velocity:Float,inaccuracy:Float) {
        if(launchOrigin==null)launchOrigin=position()
        super.shoot(x,y,z,velocity,inaccuracy)
        launchYaw=yRot
    }
    fun wallDeploymentYaw(landing:Vec3):Float {
        val origin=launchOrigin ?: position()
        val dx=landing.x-origin.x;val dz=landing.z-origin.z
        return if(dx*dx+dz*dz<1e-10)launchYaw else Math.toDegrees(kotlin.math.atan2(-dx,dz)).toFloat()
    }
    private var firstBounceAt = -1L
    private var failedAt = -1L
    val deploymentFailed get() = entityData.get(FAILED)
    private var deployed = false
    var managedRequest: java.util.UUID? = null
    private var savedCallOwner: java.util.UUID? = null
    val previousRoll = Quaternionf()
    val roll = Quaternionf()
    val ballRadius get() = bbWidth.toDouble() / 2
    val ballCenter get() = position().add(0.0, ballRadius, 0.0)

    override fun defineSynchedData() {
        super.defineSynchedData()
        entityData.define(FAILED, false)
    }

    override fun getDefaultItem(): Item = Items.ENDER_PEARL

    override fun tick() {
        // ThrowableProjectile.tick would move by the entire reflected velocity after a hit.
        // Keep its entity bookkeeping, but sweep the remaining fraction of this tick ourselves
        // so a second wall cannot be skipped after a bounce.
        if(launchOrigin==null){launchOrigin=position();launchYaw=yRot}
        baseTick()
        previousRoll.set(roll)
        if (isRemoved) return
        if (!level().isClientSide && firstBounceAt >= 0 && !deploymentFailed &&
            level().gameTime - firstBounceAt >= RETRY_TICKS) failDeployment()
        if (deploymentFailed) {
            deltaMovement = Vec3.ZERO
            if (!level().isClientSide && level().gameTime - failedAt >= FAILED_LINGER_TICKS) discard()
            return
        }
        if (tickCount == 1) gameEvent(GameEvent.PROJECTILE_SHOOT, owner)
        val tickStart = position()
        var remaining = 1.0
        // Several contacts in one tick are possible in a narrow corner.
        for (contact in 0 until 4) {
            val start = position()
            val velocity = deltaMovement
            if (remaining <= 0.0001 || velocity.lengthSqr() < 1e-10) break
            val movement = velocity.scale(remaining)
            val end = start.add(movement)
            val contact = level().getBlockCollisions(this, boundingBox.expandTowards(movement).inflate(0.001))
                .flatMap { it.toAabbs() }
                .mapNotNull { BeaconCollision.sweep(ballCenter, movement, ballRadius, it) }
                .minByOrNull { it.fraction }
            if (contact == null) {
                setPos(end.x, end.y, end.z)
                break
            }
            val normal = contact.normal
            val face = Direction.getNearest(normal.x, normal.y, normal.z)
            val hit = BlockHitResult(contact.point, face, BlockPos.containing(contact.point.subtract(normal.scale(0.001))), false)
            if (ForgeEventFactory.onProjectileImpact(this, hit)) {
                setPos(end.x, end.y, end.z)
                break
            }
            remaining *= 1.0 - contact.fraction
            setPos(contact.center.x, contact.center.y - ballRadius, contact.center.z)
            // A rounded edge is a bounce, not an exposed flat upper face.
            respondToContact(hit, normal, normal.y > 0.999)
            if (isRemoved || deployed || deploymentFailed) return
        }
        if (level().isClientSide) {
            val travelled = position().subtract(tickStart).multiply(1.0, 0.0, 1.0)
            val distance = travelled.length()
            if (distance > 1e-8) roll.premul(Quaternionf().rotationAxis((distance / ballRadius).toFloat(),
                (travelled.z / distance).toFloat(), 0f, (-travelled.x / distance).toFloat()))
        }
        updateRotation()
        checkInsideBlocks()
        deltaMovement = deltaMovement.scale(if (isInWater) 0.8 else 0.99)
        if (!isNoGravity) deltaMovement = deltaMovement.add(0.0, -gravity.toDouble(), 0.0)
    }

    override fun onHit(result: HitResult) {
        if (result !is BlockHitResult || deployed || deploymentFailed || isRemoved) return
        val normal = Vec3.atLowerCornerOf(result.direction.normal)
        val center = result.location.add(normal.scale(ballRadius + CONTACT_CLEARANCE))
        setPos(center.x, center.y - ballRadius, center.z)
        respondToContact(result, normal, result.direction == Direction.UP)
    }

    private fun respondToContact(result: BlockHitResult, normal: Vec3, flatTop: Boolean) {
        if (!level().isClientSide && flatTop && hasOpenDeploymentColumns(result)) {
            val selected = BeaconData.selected(item)
            if (selected != null) {
                if(com.example.sbwhelldiver.stratagem.Stratagems.isOrbital(selected.id)){
                    val s=level().server
                    val origin=s?.let{com.example.sbwhelldiver.stratagem.SuperDestroyers.origin(it,owner?.uuid?:savedCallOwner,level().dimension().location().toString(),result.location)}
                    if(origin==null){failDeployment();return}
                }
                val marker = StratagemMarker(ModEntities.STRATAGEM_MARKER.get(), level())
                val deployYaw=if(selected.dropId.path=="grenadier_battlement" || selected.category==com.example.sbwhelldiver.stratagem.StratagemCategory.RED)wallDeploymentYaw(result.location) else yRot
                marker.moveTo(result.location.x, result.location.y + 0.015, result.location.z, deployYaw, 0f)
                marker.configure(selected.dropId, HelldiversConfig.dropDelayTicks.get(), owner?.uuid ?: savedCallOwner,
                    Direction.UP, result.location.y, selected.category.beamColor)
                val request = managedRequest
                if (request != null) {
                    val ownerId = owner?.uuid ?: savedCallOwner
                    val server = level().server
                    if (ownerId == null || server == null || !com.example.sbwhelldiver.stratagem.StratagemCooldowns.arrival(server, ownerId, selected.id, request,
                            com.example.sbwhelldiver.stratagem.StratagemCooldowns.now(server) + marker.arrivalDeadline - level().gameTime)) {
                        failDeployment(); return
                    }
                    marker.managedRequest = request
                }
                if (level().addFreshEntity(marker)) {
                    deployed = true
                    deltaMovement = Vec3.ZERO
                    marker.playActivationSound()
                    discard()
                    return
                }
                failDeployment(); return
            }
        }
        if (!level().isClientSide && firstBounceAt < 0) firstBounceAt = level().gameTime
        val velocity = deltaMovement
        val incoming = velocity.dot(normal)
        val tangent = velocity.subtract(normal.scale(incoming))
        // Preserve horizontal momentum on rejected ground so it can roll out from under a roof.
        // Low-energy contacts settle into small hops instead of bouncing high forever.
        val rebound = if (flatTop) {
            if (-incoming < 0.12) 0.018 else min(0.35, -incoming * 0.35)
        } else min(0.9, kotlin.math.abs(incoming) * 0.55)
        deltaMovement = tangent.scale(if (flatTop) 0.97 else 0.88)
            .add(normal.scale(rebound))
        val clear = position().add(normal.scale(CONTACT_CLEARANCE))
        setPos(clear.x, clear.y, clear.z)
        hasImpulse = true
    }

    private fun hasOpenDeploymentColumns(hit: BlockHitResult): Boolean {
        val center = BlockPos.containing(hit.location.x, hit.location.y + 0.001, hit.location.z)
        val surfaceY = hit.location.y
        for (dx in -1..1) for (dz in -1..1) {
            val x = center.x + dx
            val z = center.z + dz
            // MOTION_BLOCKING includes glass, leaves and fluids even when they pass skylight.
            // Compare the actual shape top so slabs and stairs are valid supporting surfaces.
            val highestY = level().getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1
            if (highestY < floor(surfaceY + 0.001).toInt()) continue
            val pos = BlockPos(x, highestY, z)
            val state = level().getBlockState(pos)
            if (!state.fluidState.isEmpty && highestY + 1.0 > surfaceY + 0.001) return false
            val shape = state.getCollisionShape(level(), pos)
            if (!shape.isEmpty && highestY + shape.max(Direction.Axis.Y) > surfaceY + 0.001) return false
        }
        return true
    }

    private fun failDeployment() {
        entityData.set(FAILED, true)
        failedAt = level().gameTime
        deltaMovement = Vec3.ZERO
        hasImpulse = true
        val player = owner as? Player
        val selected = BeaconData.selected(item)
        val server = level().server
        if (server != null && selected != null) managedRequest?.let { token -> (owner?.uuid ?: savedCallOwner)?.let { id ->
            com.example.sbwhelldiver.stratagem.StratagemCooldowns.cancel(server, id, selected.id, token)
        } }
        if (player is ServerPlayer && player !is FakePlayer && selected != null) {
            BeaconNetwork.CHANNEL.send(PacketDistributor.PLAYER.with { player },
                BeaconFailurePacket(selected.id, BeaconData.loadout(item).map { it?.id ?: "" }))
        }
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        managedRequest?.let { tag.putUUID("CooldownRequest", it) }
        (owner?.uuid ?: savedCallOwner)?.let { tag.putUUID("CooldownOwner", it) }
        launchOrigin?.let {tag.putDouble("BeaconLaunchX",it.x);tag.putDouble("BeaconLaunchY",it.y);tag.putDouble("BeaconLaunchZ",it.z)}
        tag.putFloat("BeaconLaunchYaw",launchYaw)
        tag.putLong("BeaconFirstBounceAt", firstBounceAt)
        tag.putLong("BeaconFailedAt", failedAt)
        tag.putBoolean("BeaconDeploymentFailed", deploymentFailed)
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        managedRequest = if (tag.hasUUID("CooldownRequest")) tag.getUUID("CooldownRequest") else null
        savedCallOwner = if (tag.hasUUID("CooldownOwner")) tag.getUUID("CooldownOwner") else null
        launchOrigin=if(tag.contains("BeaconLaunchX"))Vec3(tag.getDouble("BeaconLaunchX"),tag.getDouble("BeaconLaunchY"),tag.getDouble("BeaconLaunchZ")) else position()
        launchYaw=if(tag.contains("BeaconLaunchYaw"))tag.getFloat("BeaconLaunchYaw") else yRot
        firstBounceAt = if (tag.contains("BeaconFirstBounceAt")) tag.getLong("BeaconFirstBounceAt") else -1L
        failedAt = if (tag.contains("BeaconFailedAt")) tag.getLong("BeaconFailedAt") else -1L
        entityData.set(FAILED, tag.getBoolean("BeaconDeploymentFailed"))
    }

    override fun canHitEntity(entity: net.minecraft.world.entity.Entity) = false

    companion object {
        const val RETRY_TICKS = 100L
        private const val FAILED_LINGER_TICKS = 20L
        private const val CONTACT_CLEARANCE = 0.001
        const val DIAMETER = 0.32f
        private val FAILED = SynchedEntityData.defineId(StratagemBeacon::class.java, EntityDataSerializers.BOOLEAN)
    }
}
