package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.init.ModSounds
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.NetworkHooks
import java.util.UUID
import com.atsuishio.superbwarfare.tools.forceHurt

/** Physical, shootable contact mine; gas/EMS/ground-fire areas only scan living units. */
class AntiPersonnelMineEntity(type: EntityType<AntiPersonnelMineEntity>,level: Level): Entity(type,level) {
    enum class Kind(val blastDamage: Float,val blastRadius: Double,val fieldRadius: Double,val innerRadius: Double=1.2) {
        PERSONNEL(100f,5.0,0.0), INCENDIARY(50f,4.0,4.0), GAS(0f,0.0,6.0), ANTI_TANK(320f,7.0,0.0,3.0)
    }
    val kind=when(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).path) {
        "incendiary_mine" -> Kind.INCENDIARY
        "gas_mine" -> Kind.GAS
        "anti_tank_mine" -> Kind.ANTI_TANK
        else -> Kind.PERSONNEL
    }
    var ownerId: UUID?=null;private set
    var dispenserId: UUID?=null;private set
    private var age=0
    private var exploded=false
    private var chainDue=-1L
    val settled get()=entityData.get(SETTLED)
    val armed get()=entityData.get(ARMED)
    private var lerpSteps=0
    private var lerpPosition=Vec3.ZERO
    private var lerpYaw=0f
    private val supportPos=net.minecraft.core.BlockPos.MutableBlockPos()
    private fun hasSupport():Boolean {
        // Settled fields usually rest on full blocks. Prove support with one
        // cached block-shape lookup instead of allocating a collision iterator
        // and testing voxel shapes for every stationary mine on every tick.
        supportPos.set(x,boundingBox.minY-.04,z)
        if(level().getBlockState(supportPos).isCollisionShapeFullBlock(level(),supportPos))return true
        return level().getBlockCollisions(this,boundingBox.move(0.0,-.04,0.0)).iterator().hasNext()
    }
    override fun defineSynchedData() { entityData.define(SETTLED,false);entityData.define(ARMED,false) }
    fun launch(at: Vec3,velocity: Vec3,owner: UUID?,dispenser: UUID?) {
        ownerId=owner;dispenserId=dispenser;setPos(at);deltaMovement=velocity;yRot=random.nextFloat()*360f
    }
    override fun tick() {
        super.tick()
        if(level().isClientSide) {
            if(lerpSteps>0) {
                setPos(position().add(lerpPosition.subtract(position()).scale(1.0/lerpSteps)))
                yRot+=net.minecraft.util.Mth.wrapDegrees(lerpYaw-yRot)/lerpSteps;lerpSteps--
            }
            return
        }
        // Native external-mine explosion_delay is 0.002 s. A follow-up blast
        // must wait for the next 20 Hz world tick rather than recurse this tick.
        if(chainDue>=0 && level().gameTime>=chainDue) { detonate();return }
        age++
        if(!settled) {
            deltaMovement=deltaMovement.add(0.0,-GRAVITY,0.0);move(MoverType.SELF,deltaMovement)
            if(onGround()) { entityData.set(SETTLED,true);deltaMovement=Vec3.ZERO;play("sfx_ap_mine_land_1") }
        } else if(!hasSupport())entityData.set(SETTLED,false)
        if(age>=MinefieldClock.ARM_TICKS && settled)entityData.set(ARMED,true)
        val contacts=level().getEntities(this,boundingBox.inflate(.04,.10,.04)) {
            !it.isRemoved && it.isAlive && !it.isSpectator && !it.noPhysics &&
            !deploymentHardware(it) && it !is AntiPersonnelMineEntity &&
            it !is GroundFireEntity && it !is MortarFieldEntity && it !is StratagemMarker && it !is StratagemBeacon
        }
        if(contacts.any { it is Projectile } || (armed && settled && contacts.any { pressureTrigger(it) }))detonate()
        if(y<level().minBuildHeight-32)discard()
    }
    // The threshold is solely a pressure-switch rule. Attacks/projectiles and
    // explosion damage still take the unrestricted hurt()/projectile paths.
    private fun pressureTrigger(other: Entity)=kind!=Kind.ANTI_TANK || when(other) {
        is LivingEntity -> other.maxHealth>=100f
        is com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity -> other.getMaxHealth()>=100f
        else -> false
    }
    override fun hurt(source: DamageSource,amount: Float): Boolean {
        if(level().isClientSide || isRemoved || exploded || !amount.isFinite() || amount<=0 ||
            source.`is`(DamageTypeTags.IS_FIRE) ||
            ((source.entity is AntiPersonnelMineEntity || source.directEntity is AntiPersonnelMineEntity) && !source.`is`(DamageTypeTags.IS_EXPLOSION)))return false
        if(source.`is`(DamageTypeTags.IS_EXPLOSION)) {
            val mineBlast=source.directEntity is AntiPersonnelMineEntity || source.entity is AntiPersonnelMineEntity
            // Port-specific sympathetic-detonation threshold. HD2's exact runtime
            // mine impulse test is not decoded; do not treat any edge damage as a hit.
            // Direct attacks and explosions from other weapons keep their trigger rules.
            if(mineBlast && amount<CHAIN_BLAST_DAMAGE)return false
            if(chainDue<0)chainDue=level().gameTime+1
        } else detonate()
        return true
    }
    // Mines and their own deploying hardware cannot block or step on each other.
    // Attacks and explosion damage still use hurt(), including chain reactions.
    private fun deploymentHardware(other: Entity)=other.uuid==dispenserId ||
        (other is HellpodCapEntity && other.sourcePodUUID==dispenserId && dispenserId!=null)
    override fun canCollideWith(other: Entity)=other !is AntiPersonnelMineEntity &&
        !deploymentHardware(other) && super.canCollideWith(other)
    override fun canBeCollidedWith()=false
    override fun isPickable()=!isRemoved && !exploded
    override fun canBeHitByProjectile()=isPickable()
    // The vanilla size-derived cutoff hides this 0.28 m entity at about 16 m.
    // Keep its physical hitbox unchanged and explicitly cover the whole minefield.
    override fun shouldRenderAtSqrDistance(distance: Double)=distance<RENDER_DISTANCE*RENDER_DISTANCE
    override fun isPushedByFluid()=false
    override fun lerpTo(x: Double,y: Double,z: Double,yaw: Float,pitch: Float,steps: Int,teleport: Boolean) {
        lerpPosition=Vec3(x,y,z);lerpYaw=yaw;lerpSteps=if(teleport)1 else steps.coerceAtLeast(1)
    }
    private fun play(name: String) { ModSounds.EVENTS[name]?.get()?.let { level().playSound(null,x,y,z,it,SoundSource.BLOCKS,1f,1f) } }
    fun detonate() {
        if(level().isClientSide || isRemoved || exploded)return
        chainDue=-1;exploded=true;pending.addLast(this)
        if(draining)return
        draining=true
        try { while(pending.isNotEmpty())pending.removeFirst().resolveExplosion() }
        finally { pending.clear();draining=false }
    }
    private fun resolveExplosion() {
        val world=level() as? ServerLevel ?: return
        val at=position().add(0.0,.1,0.0);val owner=ownerId?.let(world::getEntity) as? LivingEntity
        val source=world.damageSources().explosion(this,owner);discard()
        val victims=if(kind.blastDamage>0)world.getEntities(this,AABB(at,at).inflate(kind.blastRadius)) { !it.isRemoved && !it.ignoreExplosion() } else emptyList()
        for(target in victims) {
            val damage=SentryExplosion.damageAt(SentryExplosion.distanceToBody(at,target.boundingBox),kind.blastDamage,kind.innerRadius,kind.blastRadius)
            if(damage<=0)continue
            val cooldown=target.invulnerableTime
            try { target.invulnerableTime=0;target.forceHurt(source,damage) }
            finally { target.invulnerableTime=cooldown }
        }
        when(kind) {
            Kind.INCENDIARY -> GroundFireEntity.create(world,position(),owner,kind.fieldRadius)
            Kind.GAS -> MortarFieldEntity.create(world,position(),MortarFieldEntity.Kind.GAS,kind.fieldRadius,true)
            else -> Unit
        }
        // The normal particle packet applies a 32 m camera cutoff, even for an
        // explosion emitter. An elevated or detached camera can see the mine
        // and its damage while that packet silently drops the entire blast.
        // Keep the original particle counts, bounded to the mine's visibility
        // range, and let the renderer show the event from aerial viewpoints.
        for(viewer in world.players())if(viewer.distanceToSqr(at)<=RENDER_DISTANCE*RENDER_DISTANCE){
            if(kind!=Kind.GAS)world.sendParticles(viewer,ParticleTypes.EXPLOSION_EMITTER,true,at.x,at.y,at.z,1,0.0,0.0,0.0,0.0)
            world.sendParticles(viewer,ParticleTypes.SMOKE,true,at.x,at.y,at.z,20,.8,.4,.8,.08)
        }
        // Minefield trigger event and ExplosionSettings blast event are distinct.
        play("sfx_ap_mine_trigger_1")
        play(when(kind) { Kind.GAS -> "sfx_gas_mine_release"; Kind.INCENDIARY -> "sfx_incendiary_mine_explosion"; else -> "sfx_ap_mine_explosion_1" })
    }
    override fun getAddEntityPacket(): Packet<ClientGamePacketListener> = NetworkHooks.getEntitySpawningPacket(this)
    override fun addAdditionalSaveData(tag: CompoundTag) {
        ownerId?.let { tag.putUUID("MineOwner",it) };dispenserId?.let { tag.putUUID("MineDispenser",it) }
        tag.putLong("MineChainDue",chainDue)
        tag.putInt("MineAge",age);tag.putBoolean("MineSettled",settled);tag.putBoolean("MineArmed",armed)
    }
    override fun readAdditionalSaveData(tag: CompoundTag) {
        ownerId=if(tag.hasUUID("MineOwner"))tag.getUUID("MineOwner") else null
        dispenserId=if(tag.hasUUID("MineDispenser"))tag.getUUID("MineDispenser") else null
        chainDue=if(tag.contains("MineChainDue"))tag.getLong("MineChainDue") else -1L
        age=tag.getInt("MineAge");entityData.set(SETTLED,tag.getBoolean("MineSettled"));entityData.set(ARMED,tag.getBoolean("MineArmed"))
    }
    companion object {
        const val RENDER_DISTANCE=128.0
        const val DAMAGE=100f
        const val CHAIN_BLAST_DAMAGE=DAMAGE*.5f
        const val INNER_RADIUS=1.2
        const val OUTER_RADIUS=5.0
        const val GRAVITY=9.81/400.0
        private val SETTLED=SynchedEntityData.defineId(AntiPersonnelMineEntity::class.java,EntityDataSerializers.BOOLEAN)
        private val ARMED=SynchedEntityData.defineId(AntiPersonnelMineEntity::class.java,EntityDataSerializers.BOOLEAN)
        private val pending=ArrayDeque<AntiPersonnelMineEntity>()
        private var draining=false
    }
}
