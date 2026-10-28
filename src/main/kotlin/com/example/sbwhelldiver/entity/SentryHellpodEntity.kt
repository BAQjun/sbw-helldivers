package com.example.sbwhelldiver.entity

import com.atsuishio.superbwarfare.tools.CustomExplosion
import com.atsuishio.superbwarfare.tools.forceHurt
import com.atsuishio.superbwarfare.data.gun.GunProp
import com.atsuishio.superbwarfare.data.gun.ShootParameters
import com.atsuishio.superbwarfare.data.gun.value.ReloadState
import com.example.sbwhelldiver.config.HelldiversConfig
import com.example.sbwhelldiver.marking.TargetMarks
import com.example.sbwhelldiver.init.ModParticles
import net.minecraft.core.Direction
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.TamableAnimal
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/** Shared native SBW projectile path, with an explicit deploy/fire/reload lifecycle. */
class SentryHellpodEntity(type: EntityType<*>, level: Level) : AbstractHellpodEntity(type, level) {
    private var target: LivingEntity? = null
    private var mortarBurstPoint: Vec3? = null
    private var mortarShotPoint: Vec3? = null
    private var mortarMarkSerial: Long? = null
    internal val selectedTarget get() = target
    private var gatlingEngaged = false
    private val shotClock = ShotClock()
    private val burstClock = BurstClock()
    private val laserClock = LaserClock()
    private val teslaClock = TeslaClock()
    private var idleArcTicks=20
    val teslaState get() = TeslaClock.State.entries[entityData.get(DATA_TESLA_STATE).coerceIn(0,2)]
    val teslaChargeAge get() = entityData.get(DATA_TESLA_AGE)
    val teslaArcData get() = entityData.get(DATA_TESLA_ARC)
    fun teslaOrigin(partial: Float=1f) = getPosition(partial).add(0.0,profile.aimHeight,0.0)
    private fun syncTesla() {
        entityData.set(DATA_TESLA_STATE,teslaClock.state.ordinal)
        entityData.set(DATA_TESLA_AGE,teslaClock.age)
    }
    private fun publishTeslaArc(points: List<Vec3>,damaging: Boolean) {
        val tag=CompoundTag();tag.putInt("Serial",teslaArcData.getInt("Serial")+1);tag.putBoolean("Attack",damaging)
        tag.putInt("Count",points.size);tag.putLong("Time",level().gameTime)
        points.forEachIndexed { i,p -> tag.putDouble("X$i",p.x);tag.putDouble("Y$i",p.y);tag.putDouble("Z$i",p.z) }
        entityData.set(DATA_TESLA_ARC,tag)
    }
    val laserHeat get() = entityData.get(DATA_LASER_HEAT)
    val laserState get() = LaserClock.State.entries[entityData.get(DATA_LASER_STATE).coerceIn(0,3)]
    val beamLength get() = entityData.get(DATA_LASER_LENGTH)
    val laserBackoff get() = entityData.get(DATA_LASER_BACKOFF)
    val laserAlarm get() = laserHeat >= LaserClock.WARNING_HEAT - .0001
    private fun syncLaser() {
        entityData.set(DATA_LASER_HEAT,laserClock.heat.toFloat())
        entityData.set(DATA_LASER_STATE,laserClock.state.ordinal)
    }
    private val flameClock = FlameClock()
    val flameState get() = FlameClock.State.entries[entityData.get(DATA_FLAME_STATE).coerceIn(0,3)]
    private fun extinguishFlame() { flameClock.reset();entityData.set(DATA_FLAME_STATE,0) }
    val rotorClock = RotorClock()
    private var rotorStepped = false
    private var visualRotorAngle = 0f
    private var previousRotorAngle = 0f
    private var clientAimPhase: Phase? = null
    val rotorAngle get() = entityData.get(DATA_ROTOR_ANGLE)
    fun interpolatedRotorAngle(partial: Float) = net.minecraft.util.Mth.lerp(partial, previousRotorAngle, visualRotorAngle)
    // Losing aim resets automatic-fire credit, never the cannon's global burst/cooldown.
    private fun resetFireClock() { shotClock.reset(); gatlingEngaged = false }
    private var initializedAmmo = false
    private var appliedConfigRevision = -1
    private val configuredAmmo get() = turretConfig?.ammunition?.get() ?: profile.totalAmmo
    private val unlimitedAmmo get() = HelldiversConfig.infiniteTurretAmmo.get() && configuredAmmo > 0

    /** Restore an empty active weapon when unlimited ammunition is enabled at runtime. */
    private fun prepareUnlimitedAmmo() {
        if (!unlimitedAmmo || profile.isLaser || profile.isTesla || profile.isRocket || reloading) return
        val minimum = 1
        if ((getGunData(WEAPON_NAME)?.ammo?.get() ?: minimum) >= minimum) return
        modifyGunData(WEAPON_NAME) { gun ->
            gun.ammo.set(minOf(gun.get(GunProp.MAGAZINE), configuredAmmo))
            entityData.set(DATA_CLIP, gun.ammo.get())
        }
    }
    private fun burstInterval() = turretConfig?.burstInterval(profile.burstInterval) ?: profile.burstInterval
    private fun burstPause() = turretConfig?.burstInterval(profile.burstPause) ?: profile.burstPause
    private fun applyTurretConfig() {
        val settings = turretConfig ?: return
        modifyGunData(WEAPON_NAME) { gun ->
            settings.applyWeapon(gun)
            if (initializedAmmo && !profile.isLaser && !profile.isTesla) {
                gun.ammo.set(gun.ammo.get().coerceIn(0, minOf(gun.get(GunProp.MAGAZINE), configuredAmmo)))
                reserve = if (profile.isRocket) reserve.coerceIn(0, (configuredAmmo - gun.ammo.get()).coerceAtLeast(0)) else 0
                entityData.set(DATA_CLIP, gun.ammo.get())
            }
        }
        // Keep saved damage, including negative wreck health; never heal on config reload.
        if (health > getMaxHealth()) health = getMaxHealth()
        appliedConfigRevision = com.example.sbwhelldiver.config.TurretConfigs.revision
    }
    private var reserve = 0
    private var reloadTicksLeft = 0
    private var spinTicks = 0
    val reloading get() = entityData.get(DATA_RELOADING)
    val spinning get() = entityData.get(DATA_SPINNING)
    val clipRemaining get() = entityData.get(DATA_CLIP)
    val reloadAge get() = if (level().isClientSide) entityData.get(DATA_RELOAD_AGE) else profile.reloadTicks - reloadTicksLeft
    val reserveRemaining get() = reserve
    var ejectedCasingCount = 0
        private set

    override fun defineSynchedData() {
        super.defineSynchedData()
        entityData.define(DATA_RELOADING, false)
        entityData.define(DATA_RELOAD_START, 0L)
        entityData.define(DATA_RELOAD_AGE, 0)
        entityData.define(DATA_SPINNING, false)
        entityData.define(DATA_CLIP, 0)
        entityData.define(DATA_ROTOR_ANGLE, 0f)
        entityData.define(DATA_SERVER_TURRET_YAW, 0f)
        entityData.define(DATA_SERVER_TURRET_PITCH, 0f)
        entityData.define(DATA_FLAME_STATE, 0)
        entityData.define(DATA_LASER_STATE, 0)
        entityData.define(DATA_LASER_HEAT, 0f)
        entityData.define(DATA_LASER_LENGTH, 0f)
        entityData.define(DATA_LASER_BACKOFF, 0f)
        entityData.define(DATA_TESLA_STATE,0)
        entityData.define(DATA_TESLA_AGE,0)
        entityData.define(DATA_TESLA_ARC,CompoundTag())
    }

    override fun tick() {
        if (!level().isClientSide && appliedConfigRevision != com.example.sbwhelldiver.config.TurretConfigs.revision) applyTurretConfig()
        rotorStepped = false
        val profileWreck = !level().isClientSide && phase == Phase.DESTROYED && health <= -getMaxHealth() &&
            java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest")
        val startedNanos = if (profileWreck) System.nanoTime() else 0L
        super.tick()
        if (!level().isClientSide) {
            entityData.set(DATA_SERVER_TURRET_YAW, turretYRot)
            entityData.set(DATA_SERVER_TURRET_PITCH, turretXRot)
        }
        if (profileWreck) com.example.sbwhelldiver.LOGGER.info("WRECK_SERVER_PROFILE: ms={}, phase={}",(System.nanoTime()-startedNanos)/1_000_000.0,phase)
        if (!level().isClientSide && profile.isGatling && !rotorStepped) tickRotor(false)
    }

    private fun tickRotor(spin: Boolean): Int {
        if (!profile.isGatling) return 0
        rotorStepped = true
        entityData.set(DATA_SPINNING, spin)
        val slots = rotorClock.advance(spin, vehicleWeaponRpm(WEAPON_NAME))
        entityData.set(DATA_ROTOR_ANGLE, rotorClock.angle.toFloat())
        return slots
    }

    override fun hurt(source: DamageSource, amount: Float): Boolean {
        if (level().isClientSide || isRemoved || isWreck || phase == Phase.DONE || phase == Phase.SPENT ||
            !amount.isFinite() || amount <= 0f || isInvulnerableTo(source)) return false
        val explosion = source.`is`(DamageTypeTags.IS_EXPLOSION)
        val accepted = if (explosion || source.entity is Player) {
            // Allow the deployer to damage their own sentry. Native ownership protection
            // is for driven vehicles; damage still uses native modifiers and health handling.
            lastDamageSource = source
            lastDamageStamp = level().gameTime
            source.entity?.let { lastAttackerUUID = it.stringUUID }
            val computed = if (explosion) amount * (1.0 - (turretConfig?.explosionResistance?.get() ?: .5)).toFloat()
                else getDamageModifier().compute(this, source, amount)
            onHurt(computed, source.entity, true)
            true
        } else {
            val before = health
            super.hurt(source, amount) || health < before
        }
        if (accepted && health <= 0f) destroy()
        return accepted
    }

    override fun onHurt(amount: Float, attacker: Entity?, send: Boolean) {
        // Native burn damage is only for wrecks; low-health working sentries stay usable.
        if ((!send && amount > 0f && !isWreck) || phase == Phase.SPENT || phase == Phase.DONE) return
        super.onHurt(amount, attacker, send)
    }

    override fun remove(reason: Entity.RemovalReason) {
        // SBW calls discard just before its final wreck explosion. Preserve only the pod
        // and let that original explosion execute once without retracting the turret.
        if (reason == Entity.RemovalReason.DISCARDED &&
            phase == Phase.DESTROYED && isWreck && health <= -getMaxHealth()) {
            // A client can see the last negative-health packet before the pod-only
            // phase arrives; never locally discard the surviving pod in that gap.
            if (!level().isClientSide) {
                isWreck = false
                health = getMaxHealth()
                enterPhase(Phase.SPENT)
            }
            return
        }
        super.remove(reason)
    }

    override fun destroy() {
        if (level().isClientSide || isRemoved || phase in listOf(Phase.DESTROYED, Phase.SPENT, Phase.DONE)) return
        if (profile.isFlame) {
            // Commit the terminal phase before damaging neighbours, so chain blasts
            // and reloaded pods cannot detonate this fuel tank again.
            target = null
            isWreck = false
            health = getMaxHealth()
            enterPhase(Phase.SPENT)
            burstFuel()
            generateWreckageLoot()
            return
        }
        // Enter at 1 HP, then allow the native burning-wreck countdown to finish.
        isWreck = true
        health = 1f
        super.destroy()
        health = 1f
        target = null
        laserClock.stop();syncLaser()
        teslaClock.reset();syncTesla()
        extinguishFlame()
        enterPhase(Phase.DESTROYED)
    }

    private fun burstFuel() {
        val world = level() as? ServerLevel ?: return
        val origin = position().add(0.0, .1, 0.0)
        val source = world.damageSources().explosion(this, lastAttacker as? LivingEntity)
        world.getEntities(this, AABB(origin, origin).inflate(4.0)) { !it.isRemoved && !it.ignoreExplosion() }
            .forEach { victim ->
                val damage = SentryExplosion.damageAt(SentryExplosion.distanceToBody(origin, victim.boundingBox), 60f, .4, 4.0)
                if (damage <= 0f) return@forEach
                val cooldown = victim.invulnerableTime
                try {
                    victim.invulnerableTime = 0
                    victim.forceHurt(source, damage)
                } finally {
                    victim.invulnerableTime = cooldown
                }
            }
        // Native sound/flash/smoke only: radial damage was already settled above.
        CustomExplosion.Builder(this).attacker(lastAttacker).position(origin).radius(4f).damage(0f).keepBlock().explode()
        world.sendParticles(ParticleTypes.FLAME, origin.x, origin.y + .3, origin.z, 48, .8, .4, .8, .08)
        val floor = world.clip(ClipContext(position().add(0.0, .5, 0.0), position().add(0.0, -4.0, 0.0),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
        if (floor.type == HitResult.Type.BLOCK && floor.direction == Direction.UP) {
            // Share the jet's fire profile and custom burn, with shorter-lived outer rings.
            GroundFireEntity.create(world, floor.location, this, 3.0 + random.nextDouble(), innerRadius=.4)
        }
    }

    override fun onActivated() {
        if (initializedAmmo) return
        applyTurretConfig()
        initializedAmmo = true
        burstClock.reset()
        // The active pose starts exactly where the authored unfolding animation ends.
        turretYRot = 0f
        turretYRotO = turretYRot
        turretXRot = if (profile.isMortar) -70f else 0f
        turretXRotO = turretXRot
        turretAimPitch = -turretXRot
        if (profile.isTesla) { turretAimYaw=0f;entityData.set(DATA_CLIP,0);return }
        val deployedDirection = getShootVec(WEAPON_NAME, 1f)
        turretAimYaw = Math.toDegrees(atan2(deployedDirection.x, deployedDirection.z)).toFloat()
        if (profile.isLaser || profile.isTesla) { entityData.set(DATA_CLIP,0); return }
        modifyGunData(WEAPON_NAME) { gun ->
            val capacity = gun.get(GunProp.MAGAZINE).coerceIn(0, configuredAmmo)
            gun.ammo.set(capacity)
            reserve = if (profile.isRocket) (configuredAmmo - capacity).coerceAtLeast(0) else 0
            gun.virtualAmmo.set(0)
            gun.backupAmmoCount.set(0)
            gun.reload.setState(ReloadState.NOT_RELOADING)
            gun.reload.setTime(0)
            gun.shootTimer.set(0)
            entityData.set(DATA_CLIP, capacity)
        }
    }

    override fun tickActive() {
        if (isWreck) return
        prepareUnlimitedAmmo()
        if (profile.isTesla) { tickTesla(); return }
        if (profile.isLaser) { tickLaser(); return }
        if (profile.isMortar) { tickMortar(); return }
        if (profile.isFlame) { tickFlame(); return }
        if (reloading) {
            tickRotor(false)
            resetFireClock()
            reloadTicksLeft--
            entityData.set(DATA_RELOAD_AGE, reloadAge)
            if (reloadTicksLeft <= 0) finishReload()
            return
        }
        val gun = getGunData(WEAPON_NAME) ?: return
        if (gun.ammo.get() <= 0) {
            tickRotor(false)
            resetFireClock()
            entityData.set(DATA_SPINNING, false)
            spinTicks = 0
            if (profile.isRocket && (reserve > 0 || unlimitedAmmo)) startReload()
            return
        }
        updateTarget(activeTicks % 10 == 0)
        val enemy = target
        if (enemy == null) {
            tickRotor(false)
            resetFireClock()
            spinTicks = 0
            entityData.set(DATA_SPINNING, false)
            return
        }
        aimAt(enemy)
        val muzzle = getShootPos(WEAPON_NAME, 1f)
        val direction = getShootVec(WEAPON_NAME, 1f).normalize()
        val origin = firingOrigin(enemy, muzzle, direction)
        val aligned = if (profile.isRocket) {
            val toward = muzzle.vectorTo(enemy.boundingBox.center)
            toward.lengthSqr() > .001 && direction.dot(toward.normalize()) >= cos(Math.toRadians(4.0))
        } else SentryAim.aligned(origin, direction, enemy.boundingBox)
        // Once firing, keep sweeping the current barrel direction while tracking a new target.
        // Initial acquisition still requires alignment; losing every valid target stops firing.
        val sweeping = profile.isGatling && gatlingEngaged
        if (!sweeping && (!aligned || !clearSight(origin, enemy.boundingBox.center))) {
            tickRotor(false)
            resetFireClock()
            return
        }
        val server = level() as? ServerLevel ?: return
        val shots = if (profile.isGatling) tickRotor(true)
            else if (profile.isCannon) burstClock.advance(profile.burstSize, burstInterval(), burstPause(), level().gameTime)
            else shotClock.advance(vehicleWeaponRpm(WEAPON_NAME))
        repeat(shots) {
            var fired = false
            // Synchronous native shooting: count/animate only when ammunition was consumed.
            modifyGunData(WEAPON_NAME) { data ->
                data.shootTimer.set(0) // ShotClock is the single rate limiter, including >1200 RPM.
                if (data.ammo.get() > 0 && data.canShoot(ammoSupplier)) {
                    val before = data.ammo.get()
                    if (java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest") && shotSerial in listOf(0,10,100)) {
                        com.example.sbwhelldiver.LOGGER.info("SERVER_MUZZLE_STATE {}: entity={} muzzle={} barrelPitch={} aimPitch={} bodyPitch={} roll={}",
                            profile.id,position(),getShootPos(WEAPON_NAME,1f),turretXRot,turretAimPitch,xRot,roll)
                    }
                    // The firing entity is the sentry, so its deployer and other players can be hit.
                    // Keep deployment ownership separately for management and target filtering.
                    data.shoot(ShootParameters(ammoSupplier, this, server,
                        firingOrigin(enemy, getShootPos(WEAPON_NAME, 1f), getShootVec(WEAPON_NAME, 1f)), getShootVec(WEAPON_NAME, 1f), data,
                        data.get(GunProp.SPREAD), false,
                        if (profile.isGatling) null else enemy.uuid,
                        if (profile.isGatling) null else enemy.boundingBox.center))
                    fired = data.ammo.get() < before
                    if (fired && unlimitedAmmo && !profile.isRocket) data.ammo.set(before)
                    entityData.set(DATA_CLIP, data.ammo.get())
                }
            }
            if (fired) {
                if (profile.isGatling) gatlingEngaged = true
                markFired()
                ejectCasing(server)
            }
        }
    }

    private fun tickTesla() {
        if (level().isClientSide) return
        // Scan all headings every tick. Tesla has no yaw/pitch drive or alignment gate.
        turretYRot=0f;turretYRotO=0f;turretXRot=0f;turretXRotO=0f
        turretAimYaw=0f;turretAimPitch=0f
        val enemy=acquireTarget();target=enemy
        val fire=teslaClock.advance(enemy!=null);syncTesla()
        if(fire && enemy!=null) {
            val victims=TeslaArc.chain(this,enemy)
            publishTeslaArc(listOf(teslaOrigin())+victims.map { it.boundingBox.center },true)
            TeslaArc.discharge(this,victims)
            markFired()
        }
        if(!fire && teslaClock.state!=TeslaClock.State.CHARGING) {
            if(--idleArcTicks<=0) {
                idleArcTicks=20+random.nextInt(41)
                val origin=teslaOrigin();val angle=random.nextDouble()*Math.PI*2
                val reach=2.5+random.nextDouble()*3.5
                publishTeslaArc(listOf(origin,origin.add(cos(angle)*reach,-2.0-random.nextDouble()*3.0,sin(angle)*reach)),false)
            }
        }
    }

    private fun tickLaser() {
        val server = level() as? ServerLevel ?: return
        // Retain the current target until death/loss of the full beam range; acquisition is 50m.
        updateTarget()
        val enemy = target
        if (enemy != null && laserClock.state != LaserClock.State.OVERLOADED) aimAt(enemy)
        val muzzle = getShootPos(WEAPON_NAME,1f)
        val direction = getShootVec(WEAPON_NAME,1f).normalize()
        val origin = enemy?.let { firingOrigin(it,muzzle,direction) } ?: muzzle
        val aligned = enemy != null && SentryAim.aligned(origin,direction,enemy.boundingBox) && clearSight(origin,enemy.boundingBox.center)
        val biome = server.getBiome(blockPosition()).value()
        val cooling = LaserClock.coolingRate(server.dimension()==Level.NETHER,biome.baseTemperature)
        val fire = laserClock.advance(enemy!=null,aligned,cooling)
        syncLaser()
        if (laserClock.destroyNow) {
            target = null
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,x,y+profile.aimHeight,z,1,0.0,0.0,0.0,0.0)
            server.playSound(null,x,y+profile.aimHeight,z,net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE,net.minecraft.sounds.SoundSource.NEUTRAL,2f,1f)
            isWreck = false;health = getMaxHealth()
            enterPhase(Phase.SPENT)
            return
        }
        if (!fire) { entityData.set(DATA_LASER_LENGTH,0f); return }
        val hit = LaserBeam.trace(this,origin,direction)
        entityData.set(DATA_LASER_BACKOFF,muzzle.distanceTo(origin).toFloat())
        entityData.set(DATA_LASER_LENGTH,origin.distanceTo(hit.end).toFloat())
        LaserBeam.fire(this,hit,laserClock.damagePulse)
        markFired()
    }

    private fun tickFlame() {
        val server = level() as? ServerLevel ?: return
        val gun = getGunData(WEAPON_NAME) ?: return
        updateTarget(activeTicks % 10 == 0)
        val enemy = target
        val aimPoint = enemy?.let(::flameAimPoint)
        if(aimPoint != null) aimAt(aimPoint)
        val muzzle = getShootPos(WEAPON_NAME,1f)
        val direction = getShootVec(WEAPON_NAME,1f).normalize()
        val origin = enemy?.let { firingOrigin(it,muzzle,direction) } ?: muzzle
        val aligned = enemy != null && aimPoint != null &&
            SentryAim.aligned(origin,direction,enemy.boundingBox) && clearSight(origin,aimPoint)
        val fuel = if (unlimitedAmmo) maxOf(gun.ammo.get(), FlameClock.AMMO_PER_JET) else gun.ammo.get()
        val fire = flameClock.advance(server.gameTime,enemy != null,aligned,fuel)
        entityData.set(DATA_FLAME_STATE,flameClock.state.ordinal)
        if(!fire) return
        modifyGunData(WEAPON_NAME) { data ->
            if (!unlimitedAmmo) data.ammo.set((data.ammo.get()-FlameClock.AMMO_PER_JET).coerceAtLeast(0))
            entityData.set(DATA_CLIP,data.ammo.get())
        }
        FlameJet.spray(server,this,origin,direction)
        markFired()
    }

    private fun tickMortar() {
        val server = level() as? ServerLevel ?: return
        val gun = getGunData(WEAPON_NAME) ?: return
        if (gun.ammo.get() <= 0) return
        val command=TargetMarks.preferred(this)
        if(command?.serial!=mortarMarkSerial){
            mortarMarkSerial=command?.serial;mortarBurstPoint=null;mortarShotPoint=null;target=null
        }
        if(command?.target!=null)target=TargetMarks.resolve(server,command) as? LivingEntity
        if (burstClock.remaining > 0 && burstClock.cooldownTicks(server.gameTime) > 0.0) return
        if (command==null && (burstClock.remaining == 0 || mortarBurstPoint==null)) {
            val previous = target
            updateTarget(activeTicks % 10 == 0)
            if (target !== previous) { mortarBurstPoint = null; mortarShotPoint = null }
            if (target == null) { mortarBurstPoint = null; mortarShotPoint = null; return }
        }
        val center = mortarBurstPoint ?: command?.let {TargetMarks.point(server,it)} ?: target?.boundingBox?.center ?: return
        if (burstClock.remaining == 0 || mortarBurstPoint==null) {
            // Aim at the volley center once. Subsequent rounds retain this exact bore pose.
            val axis = position().add(0.0, profile.aimHeight, 0.0)
            var origin = axis
            var aimSolution: MortarBallistics.Solution? = null
            repeat(5) {
                aimSolution = MortarBallistics.solve(origin, center)
                val shot = aimSolution ?: return
                val pitch = asin(shot.velocity.normalize().y)
                val heading = atan2(shot.velocity.x, shot.velocity.z)
                val local = profile.muzzleOffsets.first()
                val along = local.z*cos(pitch)-local.y*sin(pitch)
                origin = axis.add(sin(heading)*along,local.y*cos(pitch)+local.z*sin(pitch),cos(heading)*along)
            }
            val direction = (aimSolution ?: return).velocity.normalize()
            val angle = Math.toDegrees(asin(direction.y))
            if (angle !in 35.0..89.0) return
            turretAimYaw = Math.toDegrees(atan2(direction.x,direction.z)).toFloat()
            turretAimPitch = angle.toFloat()
            driveAim()
            if (getShootVec(WEAPON_NAME,1f).normalize().dot(direction) < cos(Math.toRadians(.12))) return
        }
        val muzzle = getShootPos(WEAPON_NAME,1f)
        val point = mortarShotPoint ?: chooseMortarShotPoint(server,center).also { mortarShotPoint = it }
        // Scatter changes only the round's launch velocity, never the barrel or a shell in flight.
        val shot = MortarBallistics.solve(muzzle,point) ?: run { mortarShotPoint = null; return }
        if (!MortarBallistics.clearPath(server,this,muzzle,shot.velocity,shot.flightTicks)) { mortarShotPoint = null; return }
        if (burstClock.advance(profile.burstSize,burstInterval(),burstPause(),server.gameTime) == 0) return
        var fired = false
        modifyGunData(WEAPON_NAME) { data ->
            data.shootTimer.set(0)
            if (data.ammo.get() > 0 && data.canShoot(ammoSupplier)) {
                val before = data.ammo.get()
                data.shoot(ShootParameters(ammoSupplier,this,server,muzzle,shot.velocity.normalize(),data,0.0,false,null,null))
                fired = data.ammo.get() < before
                if (fired && unlimitedAmmo) data.ammo.set(before)
                entityData.set(DATA_CLIP,data.ammo.get())
            }
        }
        if (fired) {
            mortarBurstPoint = if (burstClock.remaining > 0) center else null
            mortarShotPoint = null
            markFired()
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,muzzle.x,muzzle.y,muzzle.z,5,.10,.10,.10,.04)
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.FLASH,muzzle.x,muzzle.y,muzzle.z,1,0.0,0.0,0.0,0.0)
        }
    }

    private fun chooseMortarShotPoint(server: ServerLevel, center: Vec3): Vec3 {
        val scatter = MortarBallistics.scatterPoint(center, random.nextDouble(), random.nextDouble(), turretConfig?.scatterRadius?.get() ?: profile.mortarScatterRadius)
        val ground = server.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, floor(scatter.x).toInt(), floor(scatter.z).toInt())
        if (ground <= server.minBuildHeight) return scatter
        val above = Vec3(scatter.x, ground + 1.0, scatter.z)
        val hit = server.clip(ClipContext(above, above.add(0.0, -2.0, 0.0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this))
        return if (hit.type == HitResult.Type.BLOCK) hit.location.add(0.0, .01, 0.0) else Vec3(scatter.x, ground.toDouble(), scatter.z)
    }

    fun shellExitPosition(partial: Float = 1f): Vec3? {
        val offset = profile.shellOffset ?: return null
        val p = org.joml.Vector3d(offset.x, offset.y, offset.z)
        getBarrelTransform(partial).transformPosition(p)
        return Vec3(p.x, p.y, p.z)
    }

    private fun ejectCasing(server: ServerLevel) {
        val pos = shellExitPosition() ?: return
        val localDirection = profile.shellDirection ?: return
        val v = org.joml.Vector3d(localDirection.x, localDirection.y, localDirection.z)
        getBarrelTransform(1f).transformDirection(v)
        val speed = (if (profile.isCannon) 0.28 else 0.12) + random.nextDouble() * 0.06
        val velocity = Vec3(v.x * speed, v.y * speed + if (profile.isCannon) 0.0 else 0.09, v.z * speed)
        val effect = when {
            profile.isCannon -> ModParticles.CANNON_CASING.get()
            profile.isGatling -> ModParticles.GATLING_CASING.get()
            else -> ModParticles.MACHINE_GUN_CASING.get()
        }
        server.sendParticles(effect, pos.x, pos.y, pos.z, 0, velocity.x, velocity.y, velocity.z, 1.0)
        ejectedCasingCount++
    }

    private fun seekRange() = turretConfig?.range?.get() ?: data().compute().seekInfo?.maxSeekRange ?: 48.0
    private fun minRange() = (turretConfig?.minimumRange?.get() ?: data().compute().seekInfo?.minSeekRange ?: 0.0).coerceAtMost(seekRange())
    internal fun validTarget(candidate: LivingEntity, marked:Boolean=false): Boolean {
        if (profile.isTesla && !TeslaArc.eligible(candidate)) return false
        if (!candidate.isAlive || candidate.isRemoved || candidate is Player || candidate === owner || candidate.isAlliedTo(this)) return false
        if (candidate is TamableAnimal && candidate.isTame) return false
        if (!marked && candidate !is Enemy && !(candidate is Mob && HelldiversConfig.attackNeutralMobs.get())) return false
        if (!marked && profile.isGasMortar && candidate.hasEffect(com.example.sbwhelldiver.init.ModEffects.GAS.get())) return false
        val distance = distanceToSqr(candidate)
        if (!marked && profile.isLaser && candidate === target) return distance <= LaserClock.BEAM_RANGE.pow(2)
        return distance in minRange().pow(2)..seekRange().pow(2) &&
            (profile.isMortar || clearSight(position().add(0.0, profile.aimHeight, 0.0), candidate.boundingBox.center))
    }
    internal fun markPointInRange(point:Vec3):Boolean = distanceToSqr(point) in minRange().pow(2)..seekRange().pow(2)
    private fun markedTarget():LivingEntity? {
        val server=level() as? ServerLevel ?: return null
        val m=TargetMarks.preferred(this) ?: return null
        return TargetMarks.resolve(server,m) as? LivingEntity
    }
    private fun updateTarget(rescan:Boolean=false){
        val marked=markedTarget()
        if(marked!=null)target=marked
        else if(target?.let {validTarget(it)}!=true || rescan)target=acquireTarget()
    }
    private fun acquireTarget(): LivingEntity? {
        markedTarget()?.let {return it}
        var nearest:LivingEntity?=null
        var best=Double.POSITIVE_INFINITY
        // A farther candidate cannot replace an already visible nearer target.
        // Preserve the original iteration order for ties and blocked candidates.
        for(candidate in level().getEntitiesOfClass(LivingEntity::class.java,boundingBox.inflate(seekRange()))){
            val distance=distanceToSqr(candidate)
            if(distance<best&&validTarget(candidate)){nearest=candidate;best=distance}
        }
        return nearest
    }
    private fun clearSight(start: Vec3, end: Vec3) = level().clip(
        ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)
    ).type == HitResult.Type.MISS

    private fun flameAimPoint(enemy: LivingEntity): Vec3 {
        val bounds = enemy.boundingBox
        val lower = Vec3(bounds.center.x, bounds.minY + minOf(.25, bounds.ysize * .15), bounds.center.z)
        val axis = position().add(0.0, profile.aimHeight, 0.0)
        return if (clearSight(axis, lower)) lower else bounds.center
    }
    private fun aimAt(enemy: LivingEntity) = aimAt(enemy.boundingBox.center)
    private fun firingOrigin(enemy: LivingEntity, muzzle: Vec3, direction: Vec3): Vec3 {
        if (profile.isRocket || profile.isMortar) return muzzle
        val origin = SentryAim.firingOrigin(muzzle,direction,profile.muzzleOffsets.first().z,enemy.boundingBox)
        // A barrel overlap must never allow a projectile to skip a solid wall.
        val root = muzzle.subtract(direction.normalize().scale(profile.muzzleOffsets.first().z))
        return if (origin == muzzle || clearSight(root,origin)) origin else muzzle
    }
    private fun aimAt(point: Vec3) {
        val axis = position().add(0.0, profile.aimHeight, 0.0)
        val delta = axis.vectorTo(point)
        val offset = profile.muzzleOffsets.firstOrNull() ?: Vec3.ZERO
        val angles = SentryAim.angles(delta,if (profile.isRocket) Vec3.ZERO else barrelPosition ?: Vec3.ZERO,offset)
        turretAimYaw = Math.toDegrees(angles.yaw).toFloat()
        turretAimPitch = Math.toDegrees(angles.pitch).toFloat()
        driveAim()
    }
    private fun driveAim() {
        val yaw = Math.toRadians(turretAimYaw.toDouble())
        val pitch = Math.toRadians(turretAimPitch.toDouble())
        turretAutoAimFromVector(Vec3(sin(yaw) * cos(pitch), sin(pitch), cos(yaw) * cos(pitch)))
    }
    override fun onClientPhaseChanged(next: Phase) {
        if (next == Phase.ACTIVE && clientAimPhase == Phase.DEPLOYING) {
            // Phase packets can be rendered before the next client tick. Set both
            // interpolation endpoints immediately, while the unfold pose is known.
            turretYRot = 0f
            turretXRot = if (profile.isMortar) -70f else 0f
            turretYRotO = turretYRot
            turretXRotO = turretXRot
        }
        clientAimPhase = next
    }

    override fun tickClient() {
        previousRotorAngle = visualRotorAngle
        visualRotorAngle = rotorAngle
        if (phase == Phase.ACTIVE || phase == Phase.DESTROYED) {
            val serverYaw = entityData.get(DATA_SERVER_TURRET_YAW)
            val serverPitch = entityData.get(DATA_SERVER_TURRET_PITCH)
            if (clientAimPhase == null) {
                // Joining an already active turret must preserve its current pose.
                turretYRot = serverYaw
                turretXRot = serverPitch
                turretYRotO = turretYRot
                turretXRotO = turretXRot
            } else {
                // Follow one authoritative pose; independently aiming on both sides
                // produces a false first turn and different scatter-point transitions.
                val yawStep = net.minecraft.util.Mth.wrapDegrees(serverYaw - turretYRotO)
                turretYRot = turretYRotO + yawStep.coerceIn(-turretTurnYSpeed, turretTurnYSpeed)
                val pitchStep = serverPitch - turretXRotO
                turretXRot = turretXRotO + pitchStep.coerceIn(-turretTurnXSpeed, turretTurnXSpeed)
            }
        }
        clientAimPhase = phase
    }

    private fun startReload() {
        reloadTicksLeft = profile.reloadTicks
        entityData.set(DATA_RELOADING, true)
        entityData.set(DATA_RELOAD_START, level().gameTime)
        entityData.set(DATA_RELOAD_AGE, 0)
    }
    private fun finishReload() {
        modifyGunData(WEAPON_NAME) { gun ->
            val count = min(gun.get(GunProp.MAGAZINE), if (unlimitedAmmo) configuredAmmo else reserve)
            if (!unlimitedAmmo) reserve -= count
            gun.ammo.set(count)
            gun.virtualAmmo.set(0)
            gun.reload.setState(ReloadState.NOT_RELOADING)
            gun.reload.setTime(0)
            entityData.set(DATA_CLIP, count)
        }
        entityData.set(DATA_RELOADING, false)
    }
    override fun shouldRetract(): Boolean = if (profile.isTesla) super.shouldRetract() else if (profile.isLaser) laserClock.state != LaserClock.State.OVERLOADED && super.shouldRetract() else super.shouldRetract() ||
        (initializedAmmo && !unlimitedAmmo && !firingRecently && !reloading && reserve <= 0 && (getGunData(WEAPON_NAME)?.ammo?.get() ?: 1) <= 0)
    override fun onRetracting() {
        laserClock.stop();syncLaser()
        teslaClock.reset();syncTesla()
        extinguishFlame()
        entityData.set(DATA_SPINNING, false)
        entityData.set(DATA_RELOADING, false)
        resetFireClock()
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        tag.putBoolean("SentryInitialized", initializedAmmo)
        tag.putInt("SentryReserve", reserve)
        tag.putInt("SentryReloadTicks", reloadTicksLeft)
        tag.putInt("SentrySpinTicks", spinTicks)
        tag.putInt("SentryCasings", ejectedCasingCount)
        tag.putBoolean("GatlingEngaged", gatlingEngaged)
        mortarBurstPoint?.let { tag.putDouble("MortarTargetX",it.x);tag.putDouble("MortarTargetY",it.y);tag.putDouble("MortarTargetZ",it.z) }
        mortarShotPoint?.let { tag.putDouble("MortarShotX",it.x);tag.putDouble("MortarShotY",it.y);tag.putDouble("MortarShotZ",it.z) }
        tag.putInt("CannonBurstRemaining", burstClock.remaining)
        tag.putDouble("CannonCooldownTicks", burstClock.cooldownTicks(level().gameTime))
        tag.putDouble("RotorAngle", rotorClock.angle)
        tag.putDouble("RotorSpeed", rotorClock.speed)
        flameClock.save(tag,level().gameTime)
        laserClock.save(tag)
        teslaClock.save(tag);tag.putInt("TeslaIdleArcTicks",idleArcTicks)
        target?.let { if(profile.isLaser)tag.putUUID("LaserLockedTarget",it.uuid) }
    }
    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        applyTurretConfig()
        mortarBurstPoint = if (tag.contains("MortarTargetX")) Vec3(tag.getDouble("MortarTargetX"),tag.getDouble("MortarTargetY"),tag.getDouble("MortarTargetZ")) else null
        mortarShotPoint = if (tag.contains("MortarShotX")) Vec3(tag.getDouble("MortarShotX"),tag.getDouble("MortarShotY"),tag.getDouble("MortarShotZ")) else null
        initializedAmmo = tag.getBoolean("SentryInitialized") ||
            (tag.contains("sbw_helldiver:phase") && phase == Phase.ACTIVE)
        if (phase == Phase.SPENT) {
            isWreck = false
            health = getMaxHealth()
        } else if (isWreck && phase != Phase.DESTROYED) {
            health = 1f
            enterPhase(Phase.DESTROYED)
        }
        val savedCooldown = tag.getDouble("CannonCooldownTicks")
        burstClock.restore(tag.getInt("CannonBurstRemaining").coerceIn(0, (profile.burstSize-1).coerceAtLeast(0)),
            if (savedCooldown.isFinite()) savedCooldown.coerceIn(0.0, maxOf(burstInterval(),burstPause())) else 0.0,
            level().gameTime)
        reserve = tag.getInt("SentryReserve")
        reloadTicksLeft = tag.getInt("SentryReloadTicks")
        spinTicks = tag.getInt("SentrySpinTicks")
        ejectedCasingCount = tag.getInt("SentryCasings")
        gatlingEngaged = tag.getBoolean("GatlingEngaged") && profile.isGatling && phase == Phase.ACTIVE && !isWreck
        if(profile.isFlame && phase == Phase.ACTIVE && !isWreck) flameClock.restore(tag,level().gameTime) else flameClock.reset()
        entityData.set(DATA_FLAME_STATE,flameClock.state.ordinal)
        if(profile.isLaser) { laserClock.restore(tag);syncLaser();target=if(tag.hasUUID("LaserLockedTarget")) (level() as? ServerLevel)?.getEntity(tag.getUUID("LaserLockedTarget")) as? LivingEntity else null }
        if(profile.isTesla) {
            if(phase==Phase.ACTIVE && !isWreck)teslaClock.restore(tag) else teslaClock.reset()
            syncTesla();idleArcTicks=tag.getInt("TeslaIdleArcTicks").coerceIn(1,60)
        }
        rotorClock.angle = tag.getDouble("RotorAngle")
        rotorClock.speed = tag.getDouble("RotorSpeed")
        entityData.set(DATA_ROTOR_ANGLE, rotorClock.angle.toFloat())
        visualRotorAngle = rotorAngle; previousRotorAngle = rotorAngle
        entityData.set(DATA_RELOADING, profile.isRocket && reloadTicksLeft > 0 && phase == Phase.ACTIVE)
        entityData.set(DATA_RELOAD_START, level().gameTime - (profile.reloadTicks - reloadTicksLeft))
        entityData.set(DATA_RELOAD_AGE, reloadAge)
        modifyGunData(WEAPON_NAME) { gun ->
            val capacity = min(gun.get(GunProp.MAGAZINE), configuredAmmo)
            gun.ammo.set(gun.ammo.get().coerceIn(0, capacity.coerceAtLeast(0)))
            reserve = if (profile.isRocket) reserve.coerceIn(0, (configuredAmmo - gun.ammo.get()).coerceAtLeast(0)) else 0
            entityData.set(DATA_CLIP, gun.ammo.get())
        }
    }

    companion object {
        const val EXPLOSION_MULTIPLIER = 0.5f
        const val WEAPON_NAME = "Main"
        private val DATA_RELOADING = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val DATA_RELOAD_START = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.LONG)
        private val DATA_RELOAD_AGE = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.INT)
        private val DATA_SPINNING = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.BOOLEAN)
        private val DATA_CLIP = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.INT)
        private val DATA_ROTOR_ANGLE = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.FLOAT)
        private val DATA_SERVER_TURRET_YAW = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.FLOAT)
        private val DATA_SERVER_TURRET_PITCH = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.FLOAT)
        private val DATA_LASER_STATE = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.INT)
        private val DATA_TESLA_STATE = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.INT)
        private val DATA_TESLA_AGE = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.INT)
        private val DATA_TESLA_ARC = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.COMPOUND_TAG)
        private val DATA_LASER_HEAT = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.FLOAT)
        private val DATA_LASER_LENGTH = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.FLOAT)
        private val DATA_LASER_BACKOFF = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.FLOAT)
        private val DATA_FLAME_STATE = SynchedEntityData.defineId(SentryHellpodEntity::class.java, EntityDataSerializers.INT)
    }
}
