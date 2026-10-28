package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.config.HelldiversConfig
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.DamageTypeTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/** Stationary, player operated. Ammunition and the firing clock are server authoritative. */
open class HmgEmplacementEntity(type: EntityType<*>, level: Level): AbstractHellpodEntity(type,level) {
    open val ammunitionCapacity get() = turretConfig?.ammunition?.get() ?: CAPACITY
    open val ergonomics get() = 65.0
    open val mechanicalErgonomics get() = 65.0
    open val fireNodePoseLimitDegrees get() = 5.0
    open val nextBarrel get() = shotSerial and 1
    protected open val verticalAimKick get() = .32f
    protected open val horizontalAimKick get() = .10
    open val aimSpeedMultiplier get() = 1.0
    open val minimumClosingSpeed get() = 0.0
    protected open val movesWithoutOperator get() = false
    protected open val waitForManualDismount get() = true
    protected open val singleShotAimRecoil get() = false
    protected open val maximumAimKick get() = 4f
    open val aimZoom get() = 1.5
    open val shotInterval get() = ceil(1200.0 / (turretConfig?.rpm?.get() ?: 600)).toInt().coerceAtLeast(1)
    protected open val needsFirstMountLevelling get() = true
    val awaitingFirstMount get() = needsFirstMountLevelling && entityData.get(FIRST_MOUNT_START)==Long.MIN_VALUE
    val firstMountAge get() = if(entityData.get(FIRST_MOUNT_START)==Long.MIN_VALUE)0 else (level().gameTime-entityData.get(FIRST_MOUNT_START)).coerceIn(0L,FIRST_MOUNT_TICKS.toLong()).toInt()
    val isFirstMountLevelling get() = needsFirstMountLevelling && !awaitingFirstMount && firstMountAge<FIRST_MOUNT_TICKS
    open val weaponPivot get() = Vec3(0.0,28.10731/16,.06009/16)
    open val operatorOffset get() = OPERATOR_OFFSET
    val reloadAge get() = (level().gameTime-entityData.get(LAST_SHOT)).coerceIn(0,shotInterval.toLong()).toInt()
    val isFeeding get() = entityData.get(LAST_SHOT) >= 0L && reloadAge < shotInterval
    val ammoRemaining get() = entityData.get(AMMO)
    val weaponYaw get() = turretAimYaw
    val weaponPitch get() = turretAimPitch
    var previousWeaponYaw = 0f; private set
    var previousWeaponPitch = 0f; private set
    val handlingYaw get() = entityData.get(HANDLING_YAW)
    val handlingPitch get() = entityData.get(HANDLING_PITCH)
    val recoilYaw get() = entityData.get(RECOIL_YAW)
    val recoilPitch get() = entityData.get(RECOIL_PITCH)
    private var previousHandlingYaw=0f
    private var previousHandlingPitch=0f
    private var previousRecoilYaw=0f
    private var previousRecoilPitch=0f
    private var desiredAimPoint:Vec3?=null
    private var trigger = false
    private var inputAt = -100L
    private var operatorId = -1
    private var nextShot = 0L
    private var cadenceError = 0.0
    protected var requestedYaw = 0f
    protected var requestedPitch = 0f
    var lastBarrel = -1; private set
    val operator get() = firstPassenger as? Player

    override fun defineSynchedData(builder: SynchedEntityData.Builder) { super.defineSynchedData(builder);builder.define(AMMO,ammunitionCapacity);builder.define(LAST_SHOT,-100L)
        builder.define(HANDLING_YAW,0f);builder.define(HANDLING_PITCH,0f)
        builder.define(RECOIL_YAW,0f);builder.define(RECOIL_PITCH,0f);builder.define(AIM_DISTANCE,200f)
        builder.define(FIRST_MOUNT_START,Long.MIN_VALUE)
    }
    override fun getSensitivity(original: Double, zoom: Boolean, seatIndex: Int, onGround: Boolean): Double {
        val base = super.getSensitivity(original, zoom, seatIndex, onGround)
        if (!level().isClientSide || com.example.sbwhelldiver.client.HmgClient.mounted() !== this ||
            !com.example.sbwhelldiver.client.HmgClient.aiming()) return base
        // SBW scales vanilla's sensitivity base before it is cubed in MouseHandler.
        // Cube-root compensation gives 1 / aimZoom angular speed without changing Options.
        return base * Math.cbrt(1.0 / aimZoom)
    }
    override fun adjustTurretAngle() {} // Native vehicle input must not bypass the mechanical motor profile.
    override fun shouldRiderSit() = false
    private fun operatorEye(partial:Float,player:Player) = operatorPosition(partial).add(0.0,player.eyeHeight.toDouble(),0.0)
    fun rearCameraPosition(partial:Float,player:Player):Vec3 {
        val eye=operatorEye(partial,player)
        val offset=rotateYaw(Vec3(0.0,1.10,-1.75),Math.toRadians(player.getViewYRot(partial).toDouble()))
        val wanted=eye.add(offset)
        // Keep the centred head-height camera in front of walls behind the operator.
        val hit=level().clip(net.minecraft.world.level.ClipContext(eye,wanted,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player))
        if(hit.type==net.minecraft.world.phys.HitResult.Type.MISS)return wanted
        return eye.add(offset.normalize().scale((eye.distanceTo(hit.location)-.12).coerceAtLeast(.05)))
    }
    override val thirdPersonCameraPosition:Vec3 get() = Vec3.ZERO // Native tail hook must not add another offset.
    override fun getCameraPosition(partial: Float,player: Player,zoom: Boolean,isFirstPerson: Boolean): Vec3 =
        if(isFirstPerson)operatorEye(partial,player) else rearCameraPosition(partial,player)
    override fun getCameraRotation(partial: Float,player: Player,zoom: Boolean,isFirstPerson: Boolean) =
        net.minecraft.world.phys.Vec2(player.getViewYRot(partial),player.getViewXRot(partial))
    fun cameraAimDirection(partial: Float,player: Player,isFirstPerson: Boolean):Vec3 {
        val rotation=getCameraRotation(partial,player,false,isFirstPerson)
        return EmplacementAimMath.direction(rotation.x,rotation.y)
    }
    /** Solve the central bore against a world target, including the bore's offset above its pivot. */
    open fun aimAnglesAt(point:Vec3):net.minecraft.world.phys.Vec2 {
        val local=profile.muzzleOffsets.first().add(profile.muzzleOffsets.last()).scale(.5)
        return HmgAimMath.angles(point.subtract(position().add(0.0,weaponPivot.y,0.0)),local.add(weaponPivot.x,0.0,0.0),weaponPivot.z)
    }
    override fun canAddPassenger(passenger: Entity) = passenger is Player && !passenger.isSpectator &&
        phase == Phase.ACTIVE && !isWreck && ammoRemaining > 0 && passengers.isEmpty()
    override fun interact(player: Player,hand: InteractionHand): InteractionResult {
        if(hand!=InteractionHand.MAIN_HAND || !canAddPassenger(player))return InteractionResult.PASS
        if(!level().isClientSide) {
            trigger=false;operatorId=player.id;requestedYaw=weaponYaw;requestedPitch=weaponPitch
            nextShot=maxOf(nextShot,level().gameTime);player.startRiding(this,true)
        }
        return InteractionResult.sidedSuccess(level().isClientSide)
    }
    fun acceptInput(player: Player, yaw: Float, pitch: Float, fire: Boolean, aimPoint:Vec3?=null) {
        if(level().isClientSide || player!==operator || phase!=Phase.ACTIVE || isWreck ||
            !yaw.isFinite() || !pitch.isFinite())return
        if(aimPoint!=null && (!aimPoint.x.isFinite() || !aimPoint.y.isFinite() || !aimPoint.z.isFinite() ||
            aimPoint.distanceToSqr(position())>220.0*220.0))return
        val mechanical=aimPoint?.let(::aimAnglesAt)
        requestedYaw=Mth.wrapDegrees(mechanical?.x ?: yaw);requestedPitch=(mechanical?.y ?: pitch).coerceIn(-65f,75f)
        desiredAimPoint=aimPoint ?: aimAnchor().add(EmplacementAimMath.direction(yaw,pitch).scale(EmplacementAimMath.RANGE))
        trigger=fire;inputAt=level().gameTime
    }
    override fun tick() {
        previousWeaponYaw=weaponYaw;previousWeaponPitch=weaponPitch
        previousHandlingYaw=handlingYaw;previousHandlingPitch=handlingPitch
        previousRecoilYaw=recoilYaw;previousRecoilPitch=recoilPitch
        super.tick()
        if(!level().isClientSide && operator==null)decayAimRecoil()
        // This fixed stand is grounded after deployment, even though its buried hull bypasses move().
        // SBW asks for a second dismount press when a vehicle is airborne or moving.
        if (phase == Phase.ACTIVE) { setOnGround(true); deltaMovement = Vec3.ZERO }
        // Native model rotation signs differ from Minecraft view angles.
        turretYRotO=yRot-previousWeaponYaw;turretYRot=yRot-weaponYaw
        turretXRotO=previousWeaponPitch;turretXRot=weaponPitch
    }
    override fun onActivated() {
        turretAimYaw=yRot;turretAimPitch=0f;previousWeaponYaw=weaponYaw;previousWeaponPitch=0f
        entityData.set(HANDLING_YAW,yRot);entityData.set(HANDLING_PITCH,0f)
        previousHandlingYaw=yRot;previousHandlingPitch=0f;desiredAimPoint=null
    }
    protected open fun updateWeaponAim() {
        turretAimYaw=HmgErgonomics.step(weaponYaw,requestedYaw,true,mechanicalErgonomics,aimSpeedMultiplier,minimumClosingSpeed)
        turretAimPitch=HmgErgonomics.step(weaponPitch,requestedPitch,false,mechanicalErgonomics,aimSpeedMultiplier,minimumClosingSpeed).coerceIn(-65f,75f)
    }
    override fun tickActive() {
        if (HelldiversConfig.infiniteTurretAmmo.get() && ammoRemaining == 0)
            entityData.set(AMMO, ammunitionCapacity)
        val player=operator
        if(player==null || !player.isAlive || player.isSpectator || isWreck) {
            if(movesWithoutOperator)updateWeaponAim()
            trigger=false;operatorId=-1;if(player!=null)ejectPassengers();return
        }
        if(operatorId!=player.id) { operatorId=player.id;trigger=false;inputAt=-100;nextShot=maxOf(nextShot,level().gameTime) }
        if(awaitingFirstMount)entityData.set(FIRST_MOUNT_START,level().gameTime)
        if(isFirstMountLevelling) { trigger=false;return }
        updateGameplayAim()
        updateWeaponAim()
        player.yBodyRot=weaponYaw
        if(!trigger || level().gameTime-inputAt>5) { trigger=false;return }
        if(level().gameTime<nextShot || ammoRemaining<=0)return
        val server=level() as? ServerLevel ?: return
        if(!fireRound(server,player))return
        addAimRecoil()
        if (!HelldiversConfig.infiniteTurretAmmo.get()) entityData.set(AMMO,ammoRemaining-1)
        entityData.set(LAST_SHOT,server.gameTime);markFired();nextShot=server.gameTime+nextShotDelayTicks()
        if(ammoRemaining==0) { trigger=false;if(automaticRetractionEnabled() && shouldRetract())enterPhase(Phase.RETRACTING) }
    }
    protected open fun nextShotDelayTicks(): Int {
        val interval = 1200.0 / (turretConfig?.rpm?.get() ?: 600)
        val delay = ceil(interval - cadenceError - 1e-9).toInt().coerceAtLeast(1)
        cadenceError = (cadenceError + delay - interval).coerceIn(0.0, .999999999)
        return delay
    }
    protected open fun fireRound(server: ServerLevel,player: Player): Boolean {
        val barrel=shotSerial and 1
        val muzzle=muzzlePosition(barrel);val direction=firingDirection(barrel)
        val bullet=ModEntities.SENTRY_BULLET.get().create(server) as? SentryBulletEntity ?: return false
        bullet.owner=player;bullet.setPos(muzzle);bullet.setDamage(turretConfig?.damage?.get()?.toFloat() ?: DAMAGE);bullet.setCustomGravity(.01f)
        bullet.shoot(direction.x,direction.y,direction.z,12f,.12f)
        if(!server.addFreshEntity(bullet))return false
        lastBarrel=barrel;return true
    }
    private fun aimAnchor(partial:Float=1f)=getPosition(partial).add(weaponPivot)
    fun shotAgeSeconds(partial:Float=1f):Double = if(shotSerial<=0)Double.POSITIVE_INFINITY else
        ((level().gameTime-entityData.get(LAST_SHOT)).toDouble()+partial-1.0).coerceAtLeast(0.0)*.05
    private fun singleShotRecoilYaw(partial:Float=1f):Float =
        EmplacementAimMath.shotHorizontal(uuid.leastSignificantBits,(shotSerial-1).coerceAtLeast(0),horizontalAimKick)*
            EmplacementRecoil.aimEnvelope(shotAgeSeconds(partial)).toFloat()
    private fun decayAimRecoil() {
        if(singleShotAimRecoil) {
            entityData.set(RECOIL_YAW,singleShotRecoilYaw())
            entityData.set(RECOIL_PITCH,-verticalAimKick*EmplacementRecoil.aimEnvelope(shotAgeSeconds()).toFloat())
            return
        }
        val decay=exp(-6.0*.05).toFloat()
        entityData.set(RECOIL_YAW,recoilYaw*decay);entityData.set(RECOIL_PITCH,recoilPitch*decay)
    }
    protected open val immediateGameplayAim get()=false
    private fun updateGameplayAim() {
        decayAimRecoil()
        val point=desiredAimPoint ?: aimAnchor().add(EmplacementAimMath.direction(requestedYaw,requestedPitch).scale(EmplacementAimMath.RANGE))
        val delta=point.subtract(aimAnchor());val target=EmplacementAimMath.angles(delta)
        entityData.set(HANDLING_YAW,Mth.wrapDegrees(if(immediateGameplayAim)target.x else EmplacementAimMath.follow(handlingYaw,target.x,true,ergonomics=ergonomics)))
        entityData.set(HANDLING_PITCH,(if(immediateGameplayAim)target.y else EmplacementAimMath.follow(handlingPitch,target.y,false,ergonomics=ergonomics)).coerceIn(-89f,89f))
        entityData.set(AIM_DISTANCE,delta.length().coerceIn(.05,210.0).toFloat())
    }
    private fun addAimRecoil() {
        val kick=EmplacementAimMath.shotHorizontal(uuid.leastSignificantBits,shotSerial,horizontalAimKick)
        entityData.set(RECOIL_YAW,(recoilYaw+kick).coerceIn(-1.5f,1.5f))
        entityData.set(RECOIL_PITCH,(recoilPitch-verticalAimKick).coerceAtLeast(-maximumAimKick))
    }
    fun gameplayAimDirection(partial:Float=1f):Vec3 {
        // A short single-shot impulse must be evaluated at render time, not smeared
        // between a full tick kick and the already decayed next sample.
        val kickYaw=if(singleShotAimRecoil)singleShotRecoilYaw(partial) else Mth.lerp(partial,previousRecoilYaw,recoilYaw)
        val kickPitch=if(singleShotAimRecoil)-verticalAimKick*EmplacementRecoil.aimEnvelope(shotAgeSeconds(partial)).toFloat()
            else Mth.lerp(partial,previousRecoilPitch,recoilPitch)
        val yaw=previousHandlingYaw+Mth.wrapDegrees(handlingYaw-previousHandlingYaw)*partial+kickYaw
        val pitch=Mth.lerp(partial,previousHandlingPitch,handlingPitch)+kickPitch
        return EmplacementAimMath.direction(yaw.toFloat(),pitch.toFloat().coerceIn(-89f,89f))
    }
    /** HUD and rounds share the native 5-degree firing cone and actual muzzle obstruction. */
    private fun unconstrainedAimDelta(barrel:Int,partial:Float):Vec3 =
        aimAnchor(partial).add(gameplayAimDirection(partial).scale(entityData.get(AIM_DISTANCE).toDouble()))
            .subtract(muzzlePosition(barrel,partial))
    private fun limitedFiringDirection(barrel:Int,partial:Float):Vec3 =
        EmplacementAimMath.constrain(unconstrainedAimDelta(barrel,partial),boreDirection(partial),fireNodePoseLimitDegrees)
    fun effectiveAimTarget(barrel:Int=nextBarrel,partial:Float=1f):Vec3 {
        val start=muzzlePosition(barrel,partial)
        val range=unconstrainedAimDelta(barrel,partial).length().coerceAtLeast(.05)
        return EmplacementAimTrace.trace(this,start,limitedFiringDirection(barrel,partial),range)
    }
    fun firingDirection(barrel:Int=nextBarrel,partial:Float=1f):Vec3 {
        val delta=effectiveAimTarget(barrel,partial).subtract(muzzlePosition(barrel,partial))
        return if(delta.lengthSqr()<1e-8)limitedFiringDirection(barrel,partial) else delta.normalize()
    }
    override fun shouldRetract() = ammoRemaining <= 0 &&
        !(HelldiversConfig.infiniteTurretAmmo.get() && ammunitionCapacity > 0) &&
        (!waitForManualDismount || passengers.isEmpty())
    override fun onRetracting() { trigger=false;operatorId=-1;ejectPassengers() }
    open fun interpolatedYaw(partial: Float) = previousWeaponYaw+Mth.wrapDegrees(weaponYaw-previousWeaponYaw)*partial
    open fun interpolatedPitch(partial: Float) = Mth.lerp(partial,previousWeaponPitch,weaponPitch)
    fun boreDirection(partial: Float=1f): Vec3 {
        val yaw=Math.toRadians(interpolatedYaw(partial).toDouble());val pitch=Math.toRadians(interpolatedPitch(partial).toDouble())
        return Vec3(-sin(yaw)*cos(pitch),-sin(pitch),cos(yaw)*cos(pitch))
    }
    private fun rotateYaw(v: Vec3,yaw: Double): Vec3 = Vec3(v.x*cos(yaw)-v.z*sin(yaw),v.y,v.x*sin(yaw)+v.z*cos(yaw))
    open fun muzzlePosition(barrel: Int,partial: Float=1f): Vec3 {
        val pivot=weaponPivot
        val local=profile.muzzleOffsets[barrel.coerceIn(0,profile.muzzleOffsets.lastIndex)]
        val pitch=Math.toRadians(interpolatedPitch(partial).toDouble());val yaw=Math.toRadians(interpolatedYaw(partial).toDouble())
        val tilted=Vec3(local.x,local.y*cos(pitch)-local.z*sin(pitch),local.y*sin(pitch)+local.z*cos(pitch)).add(pivot)
        return getPosition(partial).add(rotateYaw(tilted,yaw))
    }
    /** Side ports beside the two receivers, measured from the reviewed eject covers. */
    fun casingExitPosition(barrel:Int,partial:Float=1f):Vec3 {
        val side=if(barrel and 1==0)1.0 else -1.0
        val local=Vec3(side*5.95/16.0,(34.65-28.10731)/16.0,(-4.0-.06009)/16.0)
        return getPosition(partial).add(rotateYaw(tiltWithBore(local,partial).add(weaponPivot),Math.toRadians(interpolatedYaw(partial).toDouble())))
    }
    fun casingEjectionVelocity(barrel:Int,partial:Float=1f):Vec3 {
        val side=if(barrel and 1==0)1.0 else -1.0
        val local=Vec3(side*.24,.10,-.06)
        return rotateYaw(tiltWithBore(local,partial),Math.toRadians(interpolatedYaw(partial).toDouble())).add(0.0,.04,0.0)
    }
    private fun tiltWithBore(v:Vec3,partial:Float):Vec3 {
        val pitch=Math.toRadians(interpolatedPitch(partial).toDouble())
        return Vec3(v.x,v.y*cos(pitch)-v.z*sin(pitch),v.y*sin(pitch)+v.z*cos(pitch))
    }
    open fun operatorPosition(partial: Float=1f): Vec3 = getPosition(partial).add(rotateYaw(operatorOffset,Math.toRadians(interpolatedYaw(partial).toDouble())))
    override fun positionRider(passenger: Entity,move: Entity.MoveFunction) {
        if(!hasPassenger(passenger))return
        val point=operatorPosition();move.accept(passenger,point.x,point.y,point.z)
        if(passenger is LivingEntity)passenger.yBodyRot=weaponYaw
    }
    override fun getDismountLocationForPassenger(passenger: LivingEntity): Vec3 {
        val lateral=rotateYaw(Vec3(2.0,0.0,-.65),Math.toRadians(weaponYaw.toDouble()));val p=position().add(lateral)
        val ground=level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,Mth.floor(p.x),Mth.floor(p.z))
        return Vec3(p.x,maxOf(y,ground.toDouble()),p.z)
    }
    override fun hurt(source: DamageSource,amount: Float): Boolean {
        if(level().isClientSide || isWreck || isRemoved || phase in listOf(Phase.DONE,Phase.SPENT) || !amount.isFinite() || amount<=0 || isInvulnerableTo(source))return false
        val explosion=source.`is`(DamageTypeTags.IS_EXPLOSION)
        val accepted=if(explosion || source.entity is Player) {
            // Like other deployed sentries, the operator may damage their own emplacement.
            lastDamageSource=source;lastDamageStamp=level().gameTime
            source.entity?.let { lastAttackerUUID=it.stringUUID }
            val computed=if(explosion)amount*(1.0-(turretConfig?.explosionResistance?.get() ?: .5)).toFloat()
                else getDamageModifier().compute(this,source,amount)
            val before=health
            onHurt(computed,source.entity,true)
            computed>0 && (health<before || isWreck)
        } else {
            val before=health
            super.hurt(source,amount) || health<before
        }
        if(health<=0 && !isWreck)destroy()
        return accepted
    }
    override fun destroy() {
        if(level().isClientSide || isWreck || isRemoved)return
        trigger=false;ejectPassengers();enterPhase(Phase.DESTROYED);super.destroy()
    }
    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag);tag.putInt("HmgAmmo",ammoRemaining)
        tag.putDouble("HmgCadenceError",cadenceError)
        tag.putFloat("HmgYaw",weaponYaw);tag.putFloat("HmgPitch",weaponPitch)
        tag.putBoolean("HmgHasBeenMounted",!awaitingFirstMount)
        tag.putInt("HmgFirstMountTicksRemaining",if(isFirstMountLevelling)FIRST_MOUNT_TICKS-firstMountAge else 0)
        tag.putFloat("HandlingYaw",handlingYaw);tag.putFloat("HandlingPitch",handlingPitch)
        tag.putFloat("AimRecoilYaw",recoilYaw);tag.putFloat("AimRecoilPitch",recoilPitch)
        tag.putFloat("AimDistance",entityData.get(AIM_DISTANCE))
        tag.putInt("HmgShotCooldown",(nextShot-level().gameTime).coerceIn(0L,shotInterval.toLong()).toInt())
    }
    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag);entityData.set(AMMO,if(tag.contains("HmgAmmo"))tag.getInt("HmgAmmo").coerceIn(0,ammunitionCapacity) else ammunitionCapacity)
        cadenceError=tag.getDouble("HmgCadenceError").takeIf {it.isFinite()}?.coerceIn(0.0,.999999999) ?: 0.0
        if (health > getMaxHealth()) health=getMaxHealth()
        turretAimYaw=tag.getFloat("HmgYaw").takeIf{it.isFinite()} ?: yRot
        turretAimPitch=(tag.getFloat("HmgPitch").takeIf{it.isFinite()} ?: 0f).coerceIn(-65f,75f)
        previousWeaponYaw=weaponYaw;previousWeaponPitch=weaponPitch
        // Old deployed saves were already flat; only new unused stands wait upright.
        val used=if(tag.contains("HmgHasBeenMounted"))tag.getBoolean("HmgHasBeenMounted") else phase==Phase.ACTIVE
        val remaining=tag.getInt("HmgFirstMountTicksRemaining").coerceIn(0,FIRST_MOUNT_TICKS)
        entityData.set(FIRST_MOUNT_START,if(used)level().gameTime-(FIRST_MOUNT_TICKS-remaining) else Long.MIN_VALUE)
        entityData.set(HANDLING_YAW,if(tag.contains("HandlingYaw"))tag.getFloat("HandlingYaw").takeIf{it.isFinite()} ?: weaponYaw else weaponYaw)
        entityData.set(HANDLING_PITCH,if(tag.contains("HandlingPitch"))tag.getFloat("HandlingPitch").takeIf{it.isFinite()}?.coerceIn(-89f,89f) ?: weaponPitch else weaponPitch)
        entityData.set(RECOIL_YAW,tag.getFloat("AimRecoilYaw").takeIf{it.isFinite()}?.coerceIn(-1.5f,1.5f) ?: 0f)
        entityData.set(RECOIL_PITCH,tag.getFloat("AimRecoilPitch").takeIf{it.isFinite()}?.coerceIn(-4f,0f) ?: 0f)
        entityData.set(AIM_DISTANCE,if(tag.contains("AimDistance"))tag.getFloat("AimDistance").takeIf{it.isFinite()}?.coerceIn(.05f,210f) ?: 200f else 200f)
        previousHandlingYaw=handlingYaw;previousHandlingPitch=handlingPitch;previousRecoilYaw=recoilYaw;previousRecoilPitch=recoilPitch
        desiredAimPoint=null
        val savedCooldown=tag.getInt("HmgShotCooldown").coerceIn(0,shotInterval);nextShot=level().gameTime+savedCooldown;entityData.set(LAST_SHOT,if(savedCooldown>0)nextShot-shotInterval else -100L);trigger=false;operatorId=-1
    }
    companion object {
        val OPERATOR_OFFSET=Vec3(0.0,.90,-1.0)
        const val CAPACITY=300;const val DAMAGE=15f
        const val FIRST_MOUNT_TICKS=8
        private val FIRST_MOUNT_START=SynchedEntityData.defineId(HmgEmplacementEntity::class.java,EntityDataSerializers.LONG)
        private val HANDLING_YAW=SynchedEntityData.defineId(HmgEmplacementEntity::class.java,EntityDataSerializers.FLOAT)
        private val HANDLING_PITCH=SynchedEntityData.defineId(HmgEmplacementEntity::class.java,EntityDataSerializers.FLOAT)
        private val RECOIL_YAW=SynchedEntityData.defineId(HmgEmplacementEntity::class.java,EntityDataSerializers.FLOAT)
        private val RECOIL_PITCH=SynchedEntityData.defineId(HmgEmplacementEntity::class.java,EntityDataSerializers.FLOAT)
        private val AIM_DISTANCE=SynchedEntityData.defineId(HmgEmplacementEntity::class.java,EntityDataSerializers.FLOAT)
        private val LAST_SHOT=SynchedEntityData.defineId(HmgEmplacementEntity::class.java,EntityDataSerializers.LONG)
        private val AMMO=SynchedEntityData.defineId(HmgEmplacementEntity::class.java,EntityDataSerializers.INT)
    }
}
