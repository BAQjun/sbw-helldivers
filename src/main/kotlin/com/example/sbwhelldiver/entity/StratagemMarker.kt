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

/** Hellpods have a landing grace period; orbital fire begins only after its countdown. */
class StratagemMarker(type: EntityType<*>, level: Level) : Entity(type, level) {
    val callStartedAt get() = entityData.get(START)
    val countdownEndsAt get() = callStartedAt + duration
    val landingGraceTicks get() = entityData.get(GRACE)
    val arrivalDeadline get() = countdownEndsAt + landingGraceTicks
    val ticksLeft get() = if (level().isClientSide && callStartedAt >= 0) (duration - elapsedTicks).coerceAtLeast(0) else entityData.get(LEFT)
    val elapsedTicks get() = if (level().isClientSide && callStartedAt >= 0) (level().gameTime - callStartedAt).coerceAtLeast(0).toInt() else entityData.get(AGE)
    val hasArrived get()=if(isSustainedOrbital)orbitalFinishedAt>=0 else if(isOrbital)orbitalFiredAt>=0 else elapsedTicks>=duration+landingGraceTicks
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
    val isOrbital get()=(dropTypeId?.path ?: entityData.get(STRATAGEM)).startsWith("orbital_")
    val isOrbitalLaser get()=(dropTypeId?.path ?: entityData.get(STRATAGEM))=="orbital_laser"
    val hasBeaconBeam get() = !isOrbitalRailcannon && (!isOrbitalLaser || elapsedTicks<duration)
    val isOrbitalRailcannon get()=(dropTypeId?.path ?: entityData.get(STRATAGEM))=="orbital_railcannon_strike"
    val railcannonSettings get()=com.example.sbwhelldiver.config.RailcannonSettings.load(entityData.get(RAIL_SETTINGS))
    val railLockStartedAt get()=entityData.get(RAIL_LOCK_START)
    private var railTargetUUID:UUID?=null
    val railTargetId get()=entityData.get(RAIL_TARGET)
    fun orbitalTarget(partial:Float=0f):Vec3 {
        if(!isOrbitalRailcannon)return if(level().isClientSide)position() else Vec3(x,surfaceY+.02,z)
        if(orbitalFiredAt<0 && level().isClientSide) {
            val target=level().getEntity(railTargetId) as? net.minecraft.world.entity.LivingEntity
            if(target?.isAlive==true)return target.getPosition(partial).add(0.0,target.bbHeight*.5,0.0)
        }
        val at=entityData.get(RAIL_AIM)
        return if(at.contains("X"))Vec3(at.getDouble("X"),at.getDouble("Y"),at.getDouble("Z")) else position()
    }
    fun railBeamActive(partial:Float=0f)=isOrbitalRailcannon && railLockStartedAt>=0 &&
        level().gameTime-railLockStartedAt+partial < railcannonSettings.beamTicks
    private fun setRailAim(at:Vec3){entityData.set(RAIL_AIM,CompoundTag().also{it.putDouble("X",at.x);it.putDouble("Y",at.y);it.putDouble("Z",at.z)})}
    /** Match Eagle rocket targeting; neutral mobs retain the existing user-configured policy. */
    fun selectRailTarget():net.minecraft.world.entity.LivingEntity? {
        val owner=ownerUuid?.let{(level() as? ServerLevel)?.getEntity(it)}
        val range=railcannonSettings.range
        return level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity::class.java,net.minecraft.world.phys.AABB(position(),position()).inflate(range)) {
            it.isAlive && !it.isRemoved && !it.isInvulnerable && it !is net.minecraft.world.entity.player.Player && it!=owner &&
            !(it is net.minecraft.world.entity.TamableAnimal && it.isTame) && !it.isAlliedTo(this) && (owner==null || !it.isAlliedTo(owner)) &&
            (it is net.minecraft.world.entity.monster.Enemy || it is net.minecraft.world.entity.Mob && com.example.sbwhelldiver.config.HelldiversConfig.attackNeutralMobs.get()) && it.distanceToSqr(position())<=range*range
        }.minWithOrNull(compareByDescending<net.minecraft.world.entity.LivingEntity>{it.maxHealth}.thenBy{it.distanceToSqr(position())}.thenBy{it.id})
    }
    private fun tickRailLock(server:ServerLevel) {
        if(orbitalFiredAt>=0 || elapsedTicks<railcannonSettings.callTicks)return
        if(railLockStartedAt<0){
            if(orbitalOrigin==null){updateCooldown(true);discard();return}
            val target=selectRailTarget();railTargetUUID=target?.uuid
            entityData.set(RAIL_TARGET,target?.id?:-1);entityData.set(RAIL_LOCK_START,server.gameTime)
            // A call restored after an unloaded interval still gets the complete audible lock phase.
            entityData.set(DURATION,elapsedTicks+railcannonSettings.lockTicks)
            updateCooldown()
        }
        val target=railTargetUUID?.let(server::getEntity) as? net.minecraft.world.entity.LivingEntity
        if(target?.isAlive==true){entityData.set(RAIL_TARGET,target.id);setRailAim(target.boundingBox.center)}
        else {entityData.set(RAIL_TARGET,-1);setRailAim(Vec3(x,surfaceY+.02,z))}
    }
    val isOrbitalGatling get()=(dropTypeId?.path ?: entityData.get(STRATAGEM))=="orbital_gatling_barrage"
    val isOrbitalField get()=(dropTypeId?.path ?: entityData.get(STRATAGEM)) in com.example.sbwhelldiver.config.OrbitalFieldSettings.ids
    val fieldSettings get()=com.example.sbwhelldiver.config.OrbitalFieldSettings.load(entityData.get(FIELD_SETTINGS))
    val isOrbitalBarrage get()=isOrbitalField || (dropTypeId?.path ?: entityData.get(STRATAGEM)) in com.example.sbwhelldiver.config.BarrageSettings.ids
    val barrageSettings get()=com.example.sbwhelldiver.config.BarrageSettings.load(entityData.get(BARRAGE_SETTINGS))
    private var barrageNextShot=0
    private var barrageReports=0
    private val barrageActive=mutableListOf<UUID>()
    private var barragePath:List<Vec3>?=null
    fun barrageTargets():List<Vec3>{
        val t=entityData.get(BARRAGE_TARGETS);if(t.isEmpty)return emptyList()
        return barragePath?:t.getList("Points",net.minecraft.nbt.Tag.TAG_COMPOUND.toInt()).map{val p=it as CompoundTag;Vec3(p.getDouble("X"),p.getDouble("Y"),p.getDouble("Z"))}.also{barragePath=it}
    }
    private fun prepareBarrage(){
        val settings=if(isOrbitalField)fieldSettings.delivery() else com.example.sbwhelldiver.config.OrbitalBarrageConfig.snapshot(dropTypeId!!.path)
        entityData.set(BARRAGE_SETTINGS,settings.save())
        val world=level() as ServerLevel
        val targets=com.example.sbwhelldiver.stratagem.OrbitalBarragePattern.offsets(uuid.mostSignificantBits xor uuid.leastSignificantBits,settings,yRot).map{offset->
            val at=position().add(offset);val block=net.minecraft.core.BlockPos.containing(at)
            val ground=if(world.hasChunkAt(block))world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,block.x,block.z).toDouble() else surfaceY
            Vec3(at.x,ground+.02,at.z)
        }
        entityData.set(BARRAGE_TARGETS,CompoundTag().also{t->t.put("Points",net.minecraft.nbt.ListTag().also{list->targets.forEach{p->list.add(CompoundTag().also{v->v.putDouble("X",p.x);v.putDouble("Y",p.y);v.putDouble("Z",p.z)})}})})
        barragePath=null
    }
    val isOrbitalAirburst get()=(dropTypeId?.path ?: entityData.get(STRATAGEM))=="orbital_airburst_strike"
    val airburstSettings get()=com.example.sbwhelldiver.config.AirburstSettings.load(entityData.get(AIRBURST_SETTINGS))
    private var airburstNextShot=0
    private var airburstReports=0
    private val airburstActive=mutableListOf<UUID>()
    fun trackAirburst(entity:Entity){airburstActive.add(entity.uuid)}
    fun airburstPoint():Vec3=com.example.sbwhelldiver.stratagem.OrbitalAirburstPattern.burstPoint(orbitalOrigin?:position().add(0.0,900.0,0.0),position(),airburstSettings.height)
    val gatlingSettings get()=com.example.sbwhelldiver.config.GatlingSettings.load(entityData.get(GATLING_SETTINGS))
    private var gatlingNextShot=0
    private val gatlingActive=mutableListOf<UUID>()
    private var gatlingPath:List<Vec3>?=null
    fun gatlingTargets():List<Vec3> = gatlingPath ?: com.example.sbwhelldiver.stratagem.OrbitalGatlingPattern.offsets(uuid.mostSignificantBits xor uuid.leastSignificantBits,gatlingSettings).map{position().add(it)}.also{gatlingPath=it}
    val orbitalOrigin:Vec3? get()=entityData.get(ORIGIN).takeIf{it.contains("X")}?.let{Vec3(it.getDouble("X"),it.getDouble("Y"),it.getDouble("Z"))}
    val orbitalFiredAt get()=entityData.get(ORBITAL_FIRED_AT)
    val orbitalFlightTicks get()=entityData.get(ORBITAL_FLIGHT_TICKS)
    val isSustainedOrbital get()=isOrbitalBarrage || isOrbitalGatling || isOrbitalAirburst
    val orbitalFinishedAt get()=entityData.get(ORBITAL_FINISHED_AT)
    val orbitalActivityTicks get()=kotlin.math.ceil(when {
        isOrbitalBarrage -> barrageSettings.lastShotTime.toDouble()+orbitalFlightTicks
        isOrbitalGatling -> gatlingSettings.lastShotTime+orbitalFlightTicks
        isOrbitalAirburst -> airburstSettings.shotTime(airburstSettings.rounds-1).toDouble()+orbitalFlightTicks+
            com.example.sbwhelldiver.stratagem.OrbitalAirburstPattern.fallTicks(orbitalOrigin?:position(),position(),airburstSettings)
        else -> orbitalFlightTicks.toDouble()
    }).toInt()
    val orbitalActivityLeft get()=if(orbitalFiredAt<0)orbitalActivityTicks else
        (orbitalFiredAt+orbitalActivityTicks-level().gameTime).coerceAtLeast(0).toInt()
    private fun beginOrbitalActivity(server:ServerLevel) {
        val owner=ownerUuid?:return;val token=managedRequest?:return;val id=dropTypeId?.path?:return
        val ledger=com.example.sbwhelldiver.stratagem.StratagemCooldowns
        ledger.change(server.server,owner){state,now->state.beginActivity(id,token,now,now+orbitalActivityTicks)}
    }
    private fun finishOrbitalActivity(server:ServerLevel) {
        if(orbitalFinishedAt>=0)return
        entityData.set(ORBITAL_FINISHED_AT,server.gameTime)
        val owner=ownerUuid?:return;val token=managedRequest?:return;val id=dropTypeId?.path?:return
        com.example.sbwhelldiver.stratagem.StratagemCooldowns.change(server.server,owner){state,now->state.finishActivity(id,token,now)}
    }
    val orbitalImpactAt get()=orbitalFiredAt+orbitalFlightTicks
    fun orbitalProgress(partial:Float=0f)=if(orbitalFiredAt<0)-1.0 else (level().gameTime-orbitalFiredAt+partial.toDouble())/orbitalFlightTicks
    private val isEagle get()=dropTypeId?.path?.startsWith("eagle_")==true
    private val eagleCallTicks get()=EagleAttack.fromId(dropTypeId?.path).callTicks
    private var descentTicks = AbstractHellpodEntity.FALL_TICKS

    fun configure(dropType: ResourceLocation, delayTicks: Int, owner: UUID?, normal: Direction = Direction.UP, groundY: Double = y, beamColor: Int = 0x27BAFF) {
        dropTypeId = dropType
        ownerUuid = owner
        startedAt = level().gameTime
        entityData.set(START, startedAt)
        entityData.set(GRACE, if(isEagle || isOrbital) 0 else LANDING_GRACE_TICKS)
        surfaceY = groundY
        descentTicks = if(isOrbital) orbitalTerminalTicks() else if(isEagle) eagleCallTicks else AbstractHellpodEntity.FALL_TICKS
        if(isOrbitalLaser)entityData.set(LASER_SETTINGS,com.example.sbwhelldiver.config.OrbitalLaserConfig.snapshot().save())
        if(isOrbitalField)entityData.set(FIELD_SETTINGS,com.example.sbwhelldiver.config.OrbitalFieldConfig.snapshot(dropType.path).save())
        if(isOrbitalBarrage)prepareBarrage()
        if(isOrbitalRailcannon)entityData.set(RAIL_SETTINGS,com.example.sbwhelldiver.config.OrbitalRailcannonConfig.snapshot().save())
        entityData.set(DURATION, if(isOrbitalLaser)com.example.sbwhelldiver.config.OrbitalLaserSettings.load(entityData.get(LASER_SETTINGS)).callTicks else if(isOrbitalRailcannon)railcannonSettings.callTicks+railcannonSettings.lockTicks else if(isOrbitalBarrage)barrageSettings.callTicks else if(isOrbitalAirburst) com.example.sbwhelldiver.config.OrbitalAirburstConfig.callTicks() else if(isOrbitalGatling) com.example.sbwhelldiver.config.OrbitalGatlingConfig.callTicks() else if(isOrbital) com.example.sbwhelldiver.config.OrbitalConfig.callTicks() else if(isEagle) eagleCallTicks else delayTicks.coerceAtLeast(maxOf(20, descentTicks - landingGraceTicks)))
        entityData.set(LEFT, duration)
        entityData.set(AGE, 0)
        entityData.set(FACE, normal.get3DDataValue())
        entityData.set(COLOR, beamColor)
        entityData.set(STRATAGEM, dropType.path)
        if(isOrbital){
            if(isOrbitalAirburst)entityData.set(AIRBURST_SETTINGS,com.example.sbwhelldiver.config.OrbitalAirburstConfig.snapshot().save())
            if(isOrbitalGatling)entityData.set(GATLING_SETTINGS,com.example.sbwhelldiver.config.OrbitalGatlingConfig.snapshot().save())
            entityData.set(ORBITAL_FLIGHT_TICKS,if(isOrbitalRailcannon)railcannonSettings.flightTicks else if(isOrbitalBarrage)barrageSettings.flightTicks else if(isOrbitalAirburst)airburstSettings.flightTicks else if(isOrbitalGatling)gatlingSettings.flightTicks else com.example.sbwhelldiver.config.OrbitalConfig.flightTicks())
            entityData.set(ORBITAL_FIRED_AT,-1L)
            val server=level().server
            val origin=server?.let{com.example.sbwhelldiver.stratagem.SuperDestroyers.origin(it,owner,level().dimension().location().toString(),position())}
            entityData.set(ORIGIN,CompoundTag().also{t->origin?.let{t.putDouble("X",it.x);t.putDouble("Y",it.y);t.putDouble("Z",it.z)}})
        }
        if(isOrbital) descentTicks=orbitalTerminalTicks()
        // Start the 4.75-second flight in time for impact one second after countdown zero.
        // Waiting happens before spawning, never by making the pod hover near the ground.
        isNoGravity = true
        noPhysics = true
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return
        if (!Stratagems.dimensionAllowed(level().dimension().location().toString())) { updateCooldown(true); discard(); return }
        if (startedAt < 0 && dropTypeId == null) {
            startedAt = level().gameTime
            entityData.set(START, startedAt)
        }
        val age = (level().gameTime - startedAt).coerceAtLeast(0).toInt()
        entityData.set(AGE, age)
        entityData.set(LEFT, (duration - age).coerceAtLeast(0))
        playActivationSound()
        if(isOrbital){tickOrbital();return}
        if (!podSpawned && age >= duration + landingGraceTicks - descentTicks) {
            if (!(if(isEagle) spawnEagle() else spawnHellpod())) { updateCooldown(true); discard(); return }
            podSpawned = true
            updateCooldown()
        }
        if (age >= duration + landingGraceTicks) {
            if(isEagle){
                val aircraft=podUUID?.let{(level() as ServerLevel).getEntity(it)} as? EagleEntity
                // Arrival and strike completion are separate phases. Flying ordnance keeps the call visible.
                val missingFlightTimeout=duration+EagleFlight.end(EagleAttack.fromId(dropTypeId?.path))-eagleCallTicks+20
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

    private fun tickOrbital() {
        val server=level() as ServerLevel
        if(isSustainedOrbital && orbitalFinishedAt>=0){
            if(server.gameTime-orbitalFinishedAt>=20)discard()
            return
        }
        if(isOrbitalLaser){
            if(elapsedTicks<duration)return
            val ship=orbitalOrigin?:run{updateCooldown(true);discard();return}
            val laser=com.example.sbwhelldiver.init.ModEntities.ORBITAL_LASER.get().create(server)?:run{updateCooldown(true);discard();return}
            laser.configure(Vec3(x,surfaceY+.1,z),ship,ownerUuid,com.example.sbwhelldiver.config.OrbitalLaserSettings.load(entityData.get(LASER_SETTINGS)))
            if(!server.addFreshEntity(laser)){updateCooldown(true);discard();return}
            // Arrival is the start of continuous irradiation, not the end of its lifetime.
            entityData.set(ORBITAL_FIRED_AT,server.gameTime);updateCooldown();discard();return
        }
        if(isOrbitalRailcannon){tickRailLock(server);if(isRemoved)return}
        if(orbitalFiredAt<0 && elapsedTicks>=duration) {
            if(orbitalOrigin==null){updateCooldown(true);discard();return}
            entityData.set(ORBITAL_FIRED_AT,server.gameTime)
            if(!isOrbitalGatling&&!isOrbitalAirburst&&!isOrbitalBarrage)com.example.sbwhelldiver.stratagem.SuperDestroyers.fire(server,orbitalOrigin!!,orbitalTarget())
            updateCooldown()
            if(isSustainedOrbital)beginOrbitalActivity(server)
        }
        if(orbitalFiredAt<0)return
        if(isOrbitalBarrage){tickBarrage(server);return}
        if(isOrbitalAirburst){tickAirburst(server);return}
        if(isOrbitalGatling){tickGatling(server);return}
        if(!podSpawned && server.gameTime>=orbitalImpactAt-orbitalTerminalTicks()) {
            if(!spawnOrbitalShell()){discard();return}
            podSpawned=true
        }
        // Keep the beam until the real terminal shell hits terrain or a creature.
        if(podSpawned && (podUUID?.let(server::getEntity)==null || server.gameTime>orbitalImpactAt+40) && (!isOrbitalRailcannon || !railBeamActive()))discard()
    }
    private fun tickBarrage(server:ServerLevel){
        val s=barrageSettings;val targets=barrageTargets();val origin=orbitalOrigin?:run{discard();return}
        if(targets.size!=s.total){updateCooldown(true);discard();return}
        val age=(server.gameTime-orbitalFiredAt).toDouble()
        val pattern=com.example.sbwhelldiver.stratagem.OrbitalBarragePattern
        val reports=pattern.due(age,s)
        while(barrageReports<reports){com.example.sbwhelldiver.stratagem.SuperDestroyers.fire(server,origin,targets[barrageReports]);barrageReports++}
        // Spawn collidable shells only for the final <=128 blocks; the ship's distant chunks stay unloaded.
        repeat(minOf(if(isOrbitalField)20 else 4,s.total-barrageNextShot)){
            val index=barrageNextShot;val target=targets[index]
            val speed=origin.distanceTo(target)/s.flightTicks
            val terminal=minOf(3,(128.0/speed.coerceAtLeast(.01)).toInt().coerceAtLeast(1))
            if(age<s.shotTime(index)+s.flightTicks-terminal)return@repeat
            val shell=(if(isOrbitalField)com.example.sbwhelldiver.init.ModEntities.ORBITAL_FIELD_SHELL.get() else if(s.napalm)com.example.sbwhelldiver.init.ModEntities.ORBITAL_NAPALM_SHELL.get() else if(s.kind==com.example.sbwhelldiver.config.BarrageSettings.ids[0])com.example.sbwhelldiver.init.ModEntities.ORBITAL_120_SHELL.get() else com.example.sbwhelldiver.init.ModEntities.ORBITAL_SHELL.get()).create(server)?:return
            shell.configure(s.direct,s.blast,s.inner,s.outer)
            if(shell is OrbitalFieldShellEntity)shell.configureField(fieldSettings)
            if(shell is OrbitalNapalmShellEntity)shell.configureFire(s.fireRadius,s.fireTicks,s.inner)
            val direction=target.subtract(origin).normalize();shell.setPos(target.subtract(direction.scale(speed*terminal)))
            ownerUuid?.let(server::getEntity)?.let{shell.owner=it};shell.shoot(direction.x,direction.y,direction.z,speed.toFloat(),0f)
            if(!server.addFreshEntity(shell))return
            barrageActive.add(shell.uuid);barrageNextShot++
        }
        barrageActive.removeAll{server.getEntity(it)==null}
        if(barrageNextShot==s.total&&(barrageActive.isEmpty()||age>s.lastShotTime+s.flightTicks+200)){
            barrageActive.forEach{server.getEntity(it)?.discard()};finishOrbitalActivity(server)
        }
    }
    private fun tickAirburst(server:ServerLevel) {
        val settings=airburstSettings
        val origin=orbitalOrigin?:run{discard();return}
        val age=server.gameTime-orbitalFiredAt
        while(airburstReports<settings.rounds&&age>=settings.shotTime(airburstReports)){
            com.example.sbwhelldiver.stratagem.SuperDestroyers.fire(server,origin,position());airburstReports++
        }
        val target=airburstPoint();val direction=target.subtract(origin).normalize()
        val speed=origin.distanceTo(target)/settings.flightTicks
        val terminal=minOf(3,(128.0/speed.coerceAtLeast(.01)).toInt().coerceAtLeast(1))
        while(airburstNextShot<settings.rounds&&age>=settings.shotTime(airburstNextShot)+settings.flightTicks-terminal){
            val shell=com.example.sbwhelldiver.init.ModEntities.ORBITAL_AIRBURST_SHELL.get().create(server)?:return
            val launch=target.subtract(direction.scale(speed*terminal))
            shell.configure(settings,uuid,target.y,direction);shell.setPos(launch)
            ownerUuid?.let(server::getEntity)?.let{shell.owner=it}
            shell.shoot(direction.x,direction.y,direction.z,speed.toFloat(),0f)
            if(!server.addFreshEntity(shell))return
            trackAirburst(shell);airburstNextShot++
        }
        airburstActive.removeAll{server.getEntity(it)==null}
        // Keep the beam through the last child's real collision, not just the fourth carrier's fuse.
        if(airburstNextShot==settings.rounds&&(airburstActive.isEmpty()||age>settings.shotTime(settings.rounds-1)+settings.flightTicks+200)){
            airburstActive.forEach{server.getEntity(it)?.discard()};finishOrbitalActivity(server)
        }
    }
    private fun tickGatling(server:ServerLevel) {
        val settings=gatlingSettings
        val origin=orbitalOrigin?:run{discard();return}
        val age=(server.gameTime-orbitalFiredAt).toDouble()
        val terminal=orbitalTerminalTicks()
        val due=com.example.sbwhelldiver.stratagem.OrbitalGatlingPattern.due(age-settings.flightTicks+terminal,settings)
        // At most eight catch-up shells per tick after unloading; ordinary cadence is one or two.
        repeat(minOf(8,(due-gatlingNextShot).coerceAtLeast(0))){
            val index=gatlingNextShot
            val target=gatlingTargets()[index].add(0.0,.005,0.0)
            val direction=target.subtract(origin).normalize()
            val speed=origin.distanceTo(target)/settings.flightTicks
            val launch=target.subtract(direction.scale(speed*terminal))
            val shell=com.example.sbwhelldiver.init.ModEntities.ORBITAL_GATLING_ROUND.get().create(server)?:return
            shell.configure(settings,com.example.sbwhelldiver.stratagem.OrbitalGatlingPattern.highExplosive(index))
            shell.setPos(launch);ownerUuid?.let(server::getEntity)?.let{shell.owner=it}
            shell.shoot(direction.x,direction.y,direction.z,speed.toFloat(),0f)
            if(!server.addFreshEntity(shell))return
            gatlingActive.add(shell.uuid);gatlingNextShot++
        }
        gatlingActive.removeAll{server.getEntity(it)==null}
        if(gatlingNextShot>=settings.total && (gatlingActive.isEmpty() || age>settings.lastShotTime+settings.flightTicks+40)) {
            gatlingActive.forEach{server.getEntity(it)?.discard()};finishOrbitalActivity(server)
        }
    }
    private fun orbitalTerminalTicks():Int {
        val speed=(orbitalOrigin?.distanceTo(orbitalTarget())?:1.0)/orbitalFlightTicks
        return minOf(3,(128.0/speed.coerceAtLeast(.01)).toInt().coerceAtLeast(1))
    }
    private fun spawnOrbitalShell():Boolean {
        val server=level() as? ServerLevel?:return false
        val origin=orbitalOrigin?:return false
        val target=orbitalTarget()
        val inward=target.subtract(origin).normalize()
        // Match the distant visual trajectory exactly, enabling collision only near the target.
        val terminal=orbitalTerminalTicks()
        val speed=origin.distanceTo(target)/orbitalFlightTicks
        val launch=target.subtract(inward.scale(speed*terminal))
        val shell=(if(isOrbitalRailcannon)com.example.sbwhelldiver.init.ModEntities.ORBITAL_RAILCANNON_SHELL.get() else com.example.sbwhelldiver.init.ModEntities.ORBITAL_SHELL.get()).create(server)?:return false
        if(isOrbitalRailcannon){val s=railcannonSettings;shell.configure(s.direct,s.blast,s.inner,s.outer)}
        shell.moveTo(launch.x,launch.y,launch.z,0f,0f)
        ownerUuid?.let{server.getEntity(it)}?.let{shell.owner=it}
        shell.shoot(inward.x,inward.y,inward.z,speed.toFloat(),0f)
        if(!server.addFreshEntity(shell))return false
        podUUID=shell.uuid
        return true
    }
    private fun spawnEagle():Boolean {
        val server=level() as? ServerLevel?:return false
        val mode=EagleAttack.fromId(dropTypeId?.path)
        val f=Vec3.directionFromRotation(0f,yRot)
        val direction=if(mode.perpendicular)Vec3(-f.z,0.0,f.x) else f
        val eagle=com.example.sbwhelldiver.init.ModEntities.EAGLE.get().create(server)?:return false
        eagle.configure(Vec3(x,surfaceY,z),direction,mode,ownerUuid,if(server.gameTime-startedAt<=1)startedAt else server.gameTime)
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
        entityData.define(FIELD_SETTINGS,com.example.sbwhelldiver.config.OrbitalFieldSettings().save())
        entityData.define(LASER_SETTINGS,com.example.sbwhelldiver.config.OrbitalLaserSettings().save())
        entityData.define(RAIL_SETTINGS,com.example.sbwhelldiver.config.RailcannonSettings().save())
        entityData.define(RAIL_LOCK_START,-1L);entityData.define(RAIL_TARGET,-1);entityData.define(RAIL_AIM,CompoundTag())
        entityData.define(BARRAGE_SETTINGS,com.example.sbwhelldiver.config.BarrageSettings().save())
        entityData.define(BARRAGE_TARGETS,CompoundTag())
        entityData.define(AIRBURST_SETTINGS,com.example.sbwhelldiver.config.AirburstSettings().save())
        entityData.define(GATLING_SETTINGS,com.example.sbwhelldiver.config.GatlingSettings().save())
        entityData.define(ORIGIN,CompoundTag())
        entityData.define(ORBITAL_FIRED_AT,-1L)
        entityData.define(ORBITAL_FINISHED_AT,-1L)
        entityData.define(ORBITAL_FLIGHT_TICKS,30)
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
        if(tag.contains("FieldSettings"))entityData.set(FIELD_SETTINGS,tag.getCompound("FieldSettings"))
        if(tag.contains("LaserSettings"))entityData.set(LASER_SETTINGS,tag.getCompound("LaserSettings"))
        if(tag.contains("RailSettings"))entityData.set(RAIL_SETTINGS,tag.getCompound("RailSettings"))
        entityData.set(RAIL_LOCK_START,if(tag.contains("RailLockStart"))tag.getLong("RailLockStart") else -1L)
        entityData.set(RAIL_TARGET,-1);entityData.set(RAIL_AIM,tag.getCompound("RailAim"))
        railTargetUUID=if(tag.hasUUID("RailTarget"))tag.getUUID("RailTarget") else null
        entityData.set(ORBITAL_FINISHED_AT,if(tag.contains("OrbitalFinishedAt"))tag.getLong("OrbitalFinishedAt") else -1L)
        if(tag.contains("BarrageSettings"))entityData.set(BARRAGE_SETTINGS,tag.getCompound("BarrageSettings"))
        if(tag.contains("BarrageTargets"))entityData.set(BARRAGE_TARGETS,tag.getCompound("BarrageTargets"))
        barragePath=null;barrageNextShot=tag.getInt("BarrageNextShot").coerceIn(0,barrageSettings.total);barrageReports=tag.getInt("BarrageReports").coerceIn(0,barrageSettings.total)
        barrageActive.clear();tag.getList("BarrageActive",net.minecraft.nbt.Tag.TAG_COMPOUND.toInt()).forEach{val t=it as CompoundTag;if(t.hasUUID("Id"))barrageActive.add(t.getUUID("Id"))}
        if(tag.contains("AirburstSettings"))entityData.set(AIRBURST_SETTINGS,tag.getCompound("AirburstSettings"))
        airburstNextShot=tag.getInt("AirburstNextShot").coerceIn(0,airburstSettings.rounds)
        airburstReports=tag.getInt("AirburstReports").coerceIn(0,airburstSettings.rounds)
        airburstActive.clear();tag.getList("AirburstActive",net.minecraft.nbt.Tag.TAG_COMPOUND.toInt()).forEach{val t=it as CompoundTag;if(t.hasUUID("Id"))airburstActive.add(t.getUUID("Id"))}
        if(tag.contains("GatlingSettings"))entityData.set(GATLING_SETTINGS,tag.getCompound("GatlingSettings"))
        gatlingNextShot=tag.getInt("GatlingNextShot").coerceIn(0,gatlingSettings.total)
        gatlingActive.clear();tag.getList("GatlingActive",net.minecraft.nbt.Tag.TAG_COMPOUND.toInt()).forEach{val t=it as CompoundTag;if(t.hasUUID("Id"))gatlingActive.add(t.getUUID("Id"))}
        gatlingPath=null
        entityData.set(ORIGIN,tag.getCompound("OrbitalOrigin"))
        entityData.set(ORBITAL_FIRED_AT,if(tag.contains("OrbitalFiredAt"))tag.getLong("OrbitalFiredAt") else -1L)
        entityData.set(ORBITAL_FLIGHT_TICKS,if(tag.contains("OrbitalFlightTicks"))tag.getInt("OrbitalFlightTicks").coerceAtLeast(10) else com.example.sbwhelldiver.config.OrbitalConfig.flightTicks())
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
        if(isOrbital && podSpawned && !tag.contains("OrbitalFiredAt")) entityData.set(ORBITAL_FIRED_AT,startedAt+length-orbitalFlightTicks)
        activationPlayed = tag.getBoolean("ActivationPlayed")
        descentTicks = if(isOrbital)orbitalTerminalTicks() else if(isEagle)eagleCallTicks else AbstractHellpodEntity.FALL_TICKS
        isNoGravity = true
        noPhysics = true
    }
    override fun addAdditionalSaveData(tag: CompoundTag) {
        tag.put("FieldSettings",entityData.get(FIELD_SETTINGS))
        tag.put("LaserSettings",entityData.get(LASER_SETTINGS))
        tag.put("RailSettings",entityData.get(RAIL_SETTINGS));tag.putLong("RailLockStart",railLockStartedAt)
        tag.put("RailAim",entityData.get(RAIL_AIM));railTargetUUID?.let{tag.putUUID("RailTarget",it)}
        tag.put("BarrageSettings",entityData.get(BARRAGE_SETTINGS));tag.put("BarrageTargets",entityData.get(BARRAGE_TARGETS));tag.putInt("BarrageNextShot",barrageNextShot);tag.putInt("BarrageReports",barrageReports)
        tag.put("BarrageActive",net.minecraft.nbt.ListTag().also{list->barrageActive.forEach{id->list.add(CompoundTag().also{it.putUUID("Id",id)})}})
        tag.put("AirburstSettings",entityData.get(AIRBURST_SETTINGS));tag.putInt("AirburstNextShot",airburstNextShot);tag.putInt("AirburstReports",airburstReports)
        tag.put("AirburstActive",net.minecraft.nbt.ListTag().also{list->airburstActive.forEach{id->list.add(CompoundTag().also{it.putUUID("Id",id)})}})
        tag.put("GatlingSettings",entityData.get(GATLING_SETTINGS));tag.putInt("GatlingNextShot",gatlingNextShot)
        tag.put("GatlingActive",net.minecraft.nbt.ListTag().also{list->gatlingActive.forEach{id->list.add(CompoundTag().also{it.putUUID("Id",id)})}})
        tag.put("OrbitalOrigin",entityData.get(ORIGIN))
        tag.putLong("OrbitalFiredAt",orbitalFiredAt);tag.putLong("OrbitalFinishedAt",orbitalFinishedAt)
        tag.putInt("OrbitalFlightTicks",orbitalFlightTicks)
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
        private val FIELD_SETTINGS=SynchedEntityData.defineId(StratagemMarker::class.java,EntityDataSerializers.COMPOUND_TAG)
        private val LASER_SETTINGS=SynchedEntityData.defineId(StratagemMarker::class.java,EntityDataSerializers.COMPOUND_TAG)
        private val RAIL_SETTINGS=SynchedEntityData.defineId(StratagemMarker::class.java,EntityDataSerializers.COMPOUND_TAG)
        private val RAIL_LOCK_START=SynchedEntityData.defineId(StratagemMarker::class.java,EntityDataSerializers.LONG)
        private val RAIL_TARGET=SynchedEntityData.defineId(StratagemMarker::class.java,EntityDataSerializers.INT)
        private val RAIL_AIM=SynchedEntityData.defineId(StratagemMarker::class.java,EntityDataSerializers.COMPOUND_TAG)
        private val BARRAGE_SETTINGS=SynchedEntityData.defineId(StratagemMarker::class.java,EntityDataSerializers.COMPOUND_TAG)
        private val BARRAGE_TARGETS=SynchedEntityData.defineId(StratagemMarker::class.java,EntityDataSerializers.COMPOUND_TAG)
        private val AIRBURST_SETTINGS=SynchedEntityData.defineId(StratagemMarker::class.java,EntityDataSerializers.COMPOUND_TAG)
        private val GATLING_SETTINGS=SynchedEntityData.defineId(StratagemMarker::class.java,EntityDataSerializers.COMPOUND_TAG)
        private val ORBITAL_FINISHED_AT = SynchedEntityData.defineId(StratagemMarker::class.java, EntityDataSerializers.LONG)
        private val ORBITAL_FIRED_AT=SynchedEntityData.defineId(StratagemMarker::class.java,EntityDataSerializers.LONG)
        private val ORBITAL_FLIGHT_TICKS=SynchedEntityData.defineId(StratagemMarker::class.java,EntityDataSerializers.INT)
        private val ORIGIN=SynchedEntityData.defineId(StratagemMarker::class.java,EntityDataSerializers.COMPOUND_TAG)
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
