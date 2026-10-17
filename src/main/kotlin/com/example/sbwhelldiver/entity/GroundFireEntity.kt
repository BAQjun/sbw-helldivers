package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.effect.FlameDamage
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.NetworkHooks
import java.util.UUID

/** Lifetime/source tracker for real mod fire blocks. */
class GroundFireEntity(type: EntityType<out GroundFireEntity>,level: Level) : Entity(type,level) {
    val remainingTicks get() = entityData.get(REMAINING)
    val radius get() = entityData.get(AREA_RADIUS).toDouble()
    private var sourceUuid: UUID? = null
    init { noPhysics = true }
    override fun defineSynchedData() { entityData.define(REMAINING,LIFETIME);entityData.define(AREA_RADIUS,RADIUS.toFloat()) }
    override fun getAddEntityPacket(): Packet<ClientGamePacketListener> = NetworkHooks.getEntitySpawningPacket(this)
    override fun addAdditionalSaveData(tag: CompoundTag) {
        tag.putInt("Remaining",remainingTicks);tag.putDouble("Radius",radius); sourceUuid?.let { tag.putUUID("Source",it) }
    }
    override fun readAdditionalSaveData(tag: CompoundTag) {
        entityData.set(REMAINING,tag.getInt("Remaining").coerceIn(0,LIFETIME))
        entityData.set(AREA_RADIUS,if(tag.contains("Radius"))tag.getDouble("Radius").coerceIn(.1,16.0).toFloat() else RADIUS.toFloat())
        sourceUuid = if(tag.hasUUID("Source")) tag.getUUID("Source") else null
    }
    override fun tick() {
        super.tick()
        if (!level().isClientSide) {
            if (remainingTicks <= 0) { discard(); return }
            if(tickCount==1)placeFlames()
            level().getEntitiesOfClass(LivingEntity::class.java,AABB(x-radius,y-.1,z-radius,x+radius,y+.45,z+radius)).forEach{igniteInside(it)}
            entityData.set(REMAINING,remainingTicks-1)
        }
    }
    fun coversCell(pos:net.minecraft.core.BlockPos):Boolean {
        if(kotlin.math.abs(pos.y-y)>.1)return false
        if(radius<2.0)return pos.x==blockX && pos.z==blockZ
        val dx=pos.x+.5-x;val dz=pos.z+.5-z
        return dx*dx+dz*dz<=radius*radius
    }
    fun igniteInside(target:LivingEntity) {
        val feet=net.minecraft.core.BlockPos.containing(target.x,kotlin.math.floor(y),target.z)
        if(!level().getBlockState(feet).`is`(com.example.sbwhelldiver.init.ModBlocks.SENTRY_FIRE.get()))return
        if(target.isAlive && !target.isSpectator && target.y<=y+.45 && target.boundingBox.maxY>=y &&
            (target.x-x)*(target.x-x)+(target.z-z)*(target.z-z)<=radius*radius) {
            val source=sourceUuid?.let{(level() as? ServerLevel)?.getEntity(it)}
            FlameDamage.ignite(target,source)
        }
    }
    private fun placeFlames() {
        val fire=com.example.sbwhelldiver.init.ModBlocks.SENTRY_FIRE.get().defaultBlockState()
        val reach=if(radius<2.0)0 else kotlin.math.ceil(radius).toInt()
        for(dx in -reach..reach)for(dz in -reach..reach) {
            val pos=net.minecraft.core.BlockPos(blockX+dx,kotlin.math.floor(y).toInt(),blockZ+dz)
            if(coversCell(pos) && level().getBlockState(pos).isAir && fire.canSurvive(level(),pos))level().setBlock(pos,fire,3)
        }
    }
    override fun shouldRenderAtSqrDistance(distance: Double)=distance<128.0*128.0

    companion object {
        const val LIFETIME = 300
        const val RADIUS = .75
        private val AREA_RADIUS=SynchedEntityData.defineId(GroundFireEntity::class.java,EntityDataSerializers.FLOAT)
        private val REMAINING=SynchedEntityData.defineId(GroundFireEntity::class.java,EntityDataSerializers.INT)
        fun create(level: ServerLevel,at: Vec3,source: Entity? = null,radius: Double=RADIUS): GroundFireEntity {
            val nearby=level.getEntitiesOfClass(GroundFireEntity::class.java,AABB(at,at).inflate(.6)) {
                kotlin.math.abs(it.radius-radius)<.001 && kotlin.math.abs(it.y-at.y)<.25 && it.position().distanceToSqr(at)<.36
            }.firstOrNull()
            if(nearby!=null) { nearby.entityData.set(REMAINING,LIFETIME);nearby.sourceUuid=source?.uuid;nearby.placeFlames();return nearby }
            return GroundFireEntity(ModEntities.GROUND_FIRE.get(),level).also {
                it.entityData.set(AREA_RADIUS,radius.coerceIn(.1,16.0).toFloat());it.setPos(at.add(0.0,.015,0.0));it.sourceUuid=source?.uuid;level.addFreshEntity(it);it.placeFlames()
            }
        }
    }
}
