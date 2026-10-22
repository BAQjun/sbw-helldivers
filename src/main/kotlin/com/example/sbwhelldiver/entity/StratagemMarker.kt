package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.init.ModSounds
import com.example.sbwhelldiver.stratagem.Stratagems
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import java.util.UUID
import net.minecraft.world.phys.Vec3

/** The visible countdown ends one second before impact; the pod flies for 95 ticks. */
class StratagemMarker(type: EntityType<*>, level: Level) : Entity(type, level) {
    val callStartedAt get() = entityData.get(START)
    val countdownEndsAt get() = callStartedAt + duration
    val landingGraceTicks get() = entityData.get(GRACE)
    val arrivalDeadline get() = countdownEndsAt + landingGraceTicks
    val ticksLeft get() = if (level().isClientSide && callStartedAt >= 0) (duration - elapsedTicks).coerceAtLeast(0) else entityData.get(LEFT)
    val elapsedTicks get() = if (level().isClientSide && callStartedAt >= 0) (level().gameTime - callStartedAt).coerceAtLeast(0).toInt() else entityData.get(AGE)
    val hasArrived get()=elapsedTicks>=duration+landingGraceTicks
    val duration get() = entityData.get(DURATION)
    val color get() = entityData.get(COLOR)
    val face get() = Direction.from3DDataValue(entityData.get(FACE))
    val stratagem get() = Stratagems.get(entityData.get(STRATAGEM))
    var podUUID: UUID? = null
        private set
    private var dropTypeId: ResourceLocation? = null
    private var ownerUuid: UUID? = null
    var managedRequest: UUID? = null
    private fun updateCooldown(cancel: Boolean = false) {
        val server = level().server ?: return
        val owner = ownerUuid ?: return
        val token = managedRequest ?: return
        val id = dropTypeId?.path ?: return
        val ledger = com.example.sbwhelldiver.stratagem.StratagemCooldowns
        if (cancel) ledger.cancel(server, owner, id, token)
        else ledger.arrival(server, owner, id, token, ledger.now(server) + arrivalDeadline - level().gameTime)
    }
    private var startedAt = -1L
    private var surfaceY = 0.0
    private var podSpawned = false
    private var activationPlayed = false
    private val isEagle get()=dropTypeId?.path?.startsWith("eagle_")==true
    private val eagleCallTicks get()=EagleFlight.attackStart(dropTypeId?.path=="eagle_airstrike")
    private var descentTicks = AbstractHellpodEntity.FALL_TICKS

    fun configure(dropType: ResourceLocation, delayTicks: Int, owner: UUID?, normal: Direction = Direction.UP, groundY: Double = y, beamColor: Int = 0x27BAFF) {
        dropTypeId = dropType
        ownerUuid = owner
        startedAt = level().gameTime
        entityData.set(START, startedAt)
        entityData.set(GRACE, if(isEagle) 0 else LANDING_GRACE_TICKS)
        surfaceY = groundY
        descentTicks = if(isEagle) eagleCallTicks else AbstractHellpodEntity.FALL_TICKS
        entityData.set(DURATION, if(isEagle) eagleCallTicks else delayTicks.coerceAtLeast(maxOf(20, descentTicks - landingGraceTicks)))
        entityData.set(LEFT, duration)
        entityData.set(AGE, 0)
        entityData.set(FACE, normal.get3DDataValue())
        entityData.set(COLOR, beamColor)
        entityData.set(STRATAGEM, dropType.path)
        // Start the 4.75-second flight in time for impact one second after countdown zero.
        // Waiting happens before spawning, never by making the pod hover near the ground.
        isNoGravity = true
        noPhysics = true
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return
        if (startedAt < 0) {
            startedAt = level().gameTime
            entityData.set(START, startedAt)
        }
        val age = (level().gameTime - startedAt).coerceAtLeast(0).toInt()
        entityData.set(AGE, age)
        entityData.set(LEFT, (duration - age).coerceAtLeast(0))
        playActivationSound()
        if (!podSpawned && age >= duration + landingGraceTicks - descentTicks) {
            if (!(if(isEagle) spawnEagle() else spawnHellpod())) { updateCooldown(true); discard(); return }
            podSpawned = true
            updateCooldown()
        }
        if (age >= duration + landingGraceTicks) {
            if(isEagle){
                val aircraft=podUUID?.let{(level() as ServerLevel).getEntity(it)} as? EagleEntity
                // Arrival and strike completion are separate phases. Flying ordnance keeps the call visible.
                val missingFlightTimeout=duration+EagleFlight.end(dropTypeId?.path=="eagle_airstrike")-eagleCallTicks+20
                if(aircraft?.strikeComplete==true || aircraft==null&&age>=missingFlightTimeout)discard()
            }else discard()
        }
    }

    /** One cue for successful unfolding and beam activation, including delayed first ticks. */
    fun playActivationSound() {
        if (level().isClientSide || activationPlayed) return
        activationPlayed = true
        level().playSound(null, x, y, z, ModSounds.EVENTS.getValue("beacon_deploy").get(), SoundSource.PLAYERS, 1f, 1f)
    }

    private fun spawnEagle():Boolean {
        val server=level() as? ServerLevel?:return false
        val bombing=dropTypeId?.path=="eagle_airstrike"
        val f=Vec3.directionFromRotation(0f,yRot)
        val direction=if(bombing)Vec3(-f.z,0.0,f.x) else f
        val eagle=com.example.sbwhelldiver.init.ModEntities.EAGLE.get().create(server)?:return false
        eagle.configure(Vec3(x,surfaceY,z),direction,bombing,ownerUuid,if(server.gameTime-startedAt<=1)startedAt else server.gameTime)
        if(!server.addFreshEntity(eagle))return false
        podUUID=eagle.uuid
        // Keep the arrival deadline; the red beam remains through payload impacts, without redispatch after save/load.
        entityData.set(DURATION,(eagle.startedAt-startedAt).toInt()+eagleCallTicks)
        entityData.set(LEFT,duration-elapsedTicks)
        return true
    }

    private fun spawnHellpod(): Boolean {
        val server = level() as? ServerLevel ?: return false
        val id = dropTypeId ?: return false
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) return false
        val pod = BuiltInRegistries.ENTITY_TYPE.get(id).create(server) as? AbstractHellpodEntity ?: return false
        val spawnY = surfaceY + AbstractHellpodEntity.descentSpawnHeight() + AbstractHellpodEntity.ORIGIN_ABOVE_BOTTOM
        pod.moveTo(x, spawnY, z, yRot, 0f)
        (pod as? GrenadierBattlementEntity)?.setDeploymentHeading(yRot)
        ownerUuid?.let(pod::setOwnerUUID)
        // Always allow the full flight duration, including a call first ticked late.
        val arrival = maxOf(arrivalDeadline, server.gameTime + AbstractHellpodEntity.FALL_TICKS)
        // A late restored call gets a full flight and keeps the one-second gap after zero.
        entityData.set(DURATION, (arrival - startedAt - landingGraceTicks).toInt())
        entityData.set(LEFT, (duration - elapsedTicks).coerceAtLeast(0))
        pod.scheduleArrival(surfaceY, arrival)
        if (!server.addFreshEntity(pod)) return false
        podUUID = pod.uuid
        return true
    }

    override fun defineSynchedData() {
        entityData.define(START, -1L)
        entityData.define(GRACE, LANDING_GRACE_TICKS)
        entityData.define(LEFT, 0)
        entityData.define(AGE, 0)
        entityData.define(DURATION, 80)
        entityData.define(COLOR, 0x27BAFF)
        entityData.define(FACE, Direction.UP.get3DDataValue())
        entityData.define(STRATAGEM, "")
    }
    override fun readAdditionalSaveData(tag: CompoundTag) {
        managedRequest = if (tag.hasUUID("CooldownRequest")) tag.getUUID("CooldownRequest") else null
        dropTypeId = ResourceLocation.tryParse(tag.getString("DropType"))
        if (tag.hasUUID("Owner")) ownerUuid = tag.getUUID("Owner")
        if (tag.hasUUID("Pod")) podUUID = tag.getUUID("Pod")
        val length = if (tag.contains("Duration")) tag.getInt("Duration") else tag.getInt("TicksLeft").coerceAtLeast(20)
        entityData.set(DURATION, length)
        startedAt = if (tag.contains("StartedAt")) tag.getLong("StartedAt") else level().gameTime
        entityData.set(START, startedAt)
        // Existing airborne calls retain their saved pod deadline; new calls use the new grace interval.
        entityData.set(GRACE, if (tag.contains("LandingGraceTicks")) tag.getInt("LandingGraceTicks") else if (tag.getBoolean("PodSpawned")) 0 else LANDING_GRACE_TICKS)
        surfaceY = if (tag.contains("SurfaceY")) tag.getDouble("SurfaceY") else y
        entityData.set(AGE, (level().gameTime - startedAt).coerceAtLeast(0).toInt())
        entityData.set(LEFT, (length - elapsedTicks).coerceAtLeast(0))
        entityData.set(COLOR, if (tag.contains("BeamColor")) tag.getInt("BeamColor") else 0x27BAFF)
        entityData.set(FACE, if (tag.contains("SurfaceFace")) tag.getInt("SurfaceFace") else Direction.UP.get3DDataValue())
        entityData.set(STRATAGEM, dropTypeId?.path ?: "")
        podSpawned = tag.getBoolean("PodSpawned")
        activationPlayed = tag.getBoolean("ActivationPlayed")
        descentTicks = if(isEagle)eagleCallTicks else AbstractHellpodEntity.FALL_TICKS
        isNoGravity = true
        noPhysics = true
    }
    override fun addAdditionalSaveData(tag: CompoundTag) {
        managedRequest?.let { tag.putUUID("CooldownRequest", it) }
        dropTypeId?.let { tag.putString("DropType", it.toString()) }
        ownerUuid?.let { tag.putUUID("Owner", it) }
        podUUID?.let { tag.putUUID("Pod", it) }
        tag.putInt("TicksLeft", ticksLeft)
        tag.putInt("Duration", duration)
        tag.putLong("StartedAt", startedAt)
        tag.putInt("LandingGraceTicks", landingGraceTicks)
        tag.putDouble("SurfaceY", surfaceY)
        tag.putInt("BeamColor", color)
        tag.putInt("SurfaceFace", face.get3DDataValue())
        tag.putBoolean("PodSpawned", podSpawned)
        tag.putBoolean("ActivationPlayed", activationPlayed)
        tag.putInt("DescentTicks", descentTicks)
    }
    companion object {
        const val LANDING_GRACE_TICKS = 20
        private val START = SynchedEntityData.defineId(StratagemMarker::class.java, EntityDataSerializers.LONG)
        private val GRACE = SynchedEntityData.defineId(StratagemMarker::class.java, EntityDataSerializers.INT)
        private val LEFT = SynchedEntityData.defineId(StratagemMarker::class.java, EntityDataSerializers.INT)
        private val AGE = SynchedEntityData.defineId(StratagemMarker::class.java, EntityDataSerializers.INT)
        private val DURATION = SynchedEntityData.defineId(StratagemMarker::class.java, EntityDataSerializers.INT)
        private val COLOR = SynchedEntityData.defineId(StratagemMarker::class.java, EntityDataSerializers.INT)
        private val FACE = SynchedEntityData.defineId(StratagemMarker::class.java, EntityDataSerializers.INT)
        private val STRATAGEM = SynchedEntityData.defineId(StratagemMarker::class.java, EntityDataSerializers.STRING)
    }
}
