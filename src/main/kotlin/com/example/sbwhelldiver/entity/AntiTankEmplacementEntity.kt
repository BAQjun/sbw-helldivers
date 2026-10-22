package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3

/** One server clock owns the entire recoil, extraction and feed cycle. */
class AntiTankEmplacementEntity(type: EntityType<*>,level: Level): HmgEmplacementEntity(type,level) {
    companion object {
        const val OPTIC_LOWER_TICKS = 6
        const val OPTIC_STOWED_PITCH = 80.26107f
        private val OPTIC_PROGRESS = net.minecraft.network.syncher.SynchedEntityData.defineId(
            AntiTankEmplacementEntity::class.java, net.minecraft.network.syncher.EntityDataSerializers.INT)
    }
    private var previousOpticProgress = 0
    val opticLowerTicks get() = entityData.get(OPTIC_PROGRESS)
    override fun defineSynchedData() {
        super.defineSynchedData()
        entityData.define(OPTIC_PROGRESS, 0)
    }
    override fun tick() {
        previousOpticProgress = opticLowerTicks
        super.tick()
        if (!level().isClientSide) {
            val lowering = phase == Phase.ACTIVE && operator != null
            entityData.set(OPTIC_PROGRESS, (opticLowerTicks + if (lowering) 1 else -1).coerceIn(0, OPTIC_LOWER_TICKS))
        }
    }
    fun opticPitchDegrees(partial: Float = 1f): Float {
        val t = (net.minecraft.util.Mth.lerp(partial, previousOpticProgress.toFloat(), opticLowerTicks.toFloat()) / OPTIC_LOWER_TICKS).coerceIn(0f, 1f)
        return OPTIC_STOWED_PITCH * (1f - t*t*(3f - 2f*t))
    }
    override val needsFirstMountLevelling get() = false
    override val ergonomics get() = 32.5
    override val mechanicalErgonomics get() = 32.5
    override val nextBarrel get() = 0
    override val verticalAimKick get() = 4.5f
    override val maximumAimKick get() = 4.75f
    override val singleShotAimRecoil get() = true
    override val waitForManualDismount get() = true
    override val horizontalAimKick get() = .28
    override val aimSpeedMultiplier get() = .65
    override val minimumClosingSpeed get() = 15.0
    override val aimZoom get() = 4.0
    val mechanicalFeedAge get() = reloadAge
    val isMechanicallyFeeding get() = isFeeding
    override val weaponPivot get() = Vec3(.0298997853,1.415645339,-.160337929)
    override val operatorOffset get() = Vec3(1.2115366,.059375,-.9247971)
    override fun getCameraPosition(partial: Float,player: Player,zoom: Boolean,isFirstPerson: Boolean): Vec3 {
        if(!level().isClientSide || !com.example.sbwhelldiver.client.HmgClient.aiming())
            return super.getCameraPosition(partial,player,zoom,isFirstPerson)
        // Independent rear-breech viewpoint, centred on the bore. The operator is seated to its side.
        val yaw=Math.toRadians(player.getViewYRot(partial).toDouble())
        val back=Vec3(kotlin.math.sin(yaw)*1.85,weaponPivot.y+.50,-kotlin.math.cos(yaw)*1.85)
        val centre=position().add(0.0,weaponPivot.y+.50,0.0)
        val wanted=position().add(back)
        val hit=level().clip(net.minecraft.world.level.ClipContext(centre,wanted,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player))
        return if(hit.type==net.minecraft.world.phys.HitResult.Type.MISS)wanted else centre.add(wanted.subtract(centre).normalize().scale((centre.distanceTo(hit.location)-.12).coerceAtLeast(.05)))
    }
    override fun getCameraRotation(partial: Float,player: Player,zoom: Boolean,isFirstPerson: Boolean): net.minecraft.world.phys.Vec2 {
        val base=super.getCameraRotation(partial,player,zoom,isFirstPerson)
        if(operator!==player || phase!=Phase.ACTIVE || shotSerial<=0)return base
        val age=shotAgeSeconds(partial)
        val side=EmplacementAimMath.shotHorizontal(uuid.leastSignificantBits,shotSerial,.16).toDouble()
        val kick=EmplacementRecoil.cameraKick(age,side)
        return net.minecraft.world.phys.Vec2(base.x+kick.x,(base.y+kick.y).coerceIn(-89.9f,89.9f))
    }
    override fun shouldRiderSit() = true
    override fun fireRound(server: ServerLevel,player: Player): Boolean {
        val shell=ModEntities.ANTI_TANK_EMPLACEMENT_SHELL.get().create(server) ?: return false
        val direction=firingDirection(0);shell.owner=player;shell.setPos(muzzlePosition(0));shell.setCustomGravity(.01f)
        shell.shoot(direction.x,direction.y,direction.z,14f,0f)
        if(!server.addFreshEntity(shell))return false
        return true
    }
}
