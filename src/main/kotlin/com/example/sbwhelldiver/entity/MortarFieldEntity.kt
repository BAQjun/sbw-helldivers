package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.init.ModEffects
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModParticles
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.network.NetworkHooks
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A server-owned circular area; particles are generated locally without per-particle networking. */
class MortarFieldEntity(type: EntityType<out MortarFieldEntity>,level: Level) : Entity(type,level) {
    enum class Kind(val radius: Double,val lifetime: Int,val effectTicks: Int) { GAS(7.0,300,100), EMS(10.0,140,60) }
    val mineCloud get()=entityData.get(MINE_CLOUD)
    val kind get() = Kind.entries[entityData.get(KIND).coerceIn(0,1)]
    val radius get() = entityData.get(AREA_RADIUS).toDouble().takeIf { it>0 } ?: kind.radius
    val remainingTicks get() = entityData.get(REMAINING)
    init { noPhysics = true }
    override fun defineSynchedData() { entityData.define(MINE_CLOUD,false);entityData.define(AREA_RADIUS,0f);entityData.define(KIND,0);entityData.define(REMAINING,300) }
    override fun readAdditionalSaveData(tag: CompoundTag) {
        entityData.set(MINE_CLOUD,tag.getBoolean("MineCloud"))
        entityData.set(KIND,tag.getInt("Kind").coerceIn(0,1));entityData.set(AREA_RADIUS,if(tag.contains("Radius"))tag.getDouble("Radius").coerceIn(.1,16.0).toFloat() else 0f)
        entityData.set(REMAINING,tag.getInt("Remaining").coerceIn(0,kind.lifetime))
    }
    override fun addAdditionalSaveData(tag: CompoundTag) {
        tag.putBoolean("MineCloud",mineCloud);tag.putDouble("Radius",radius);tag.putInt("Kind",kind.ordinal);tag.putInt("Remaining",remainingTicks)
    }
    override fun getAddEntityPacket(): Packet<ClientGamePacketListener> = NetworkHooks.getEntitySpawningPacket(this)
    override fun tick() {
        super.tick()
        if (!level().isClientSide) {
            if (remainingTicks <= 0) { discard();return }
            val box=AABB(x-radius,y-.25,z-radius,x+radius,y+3.0,z+radius)
            level().getEntitiesOfClass(LivingEntity::class.java,box) {
                it.isAlive && !it.isSpectator && (it.x-x)*(it.x-x)+(it.z-z)*(it.z-z) <= radius*radius
            }.forEach { target ->
                if (kind == Kind.GAS) {
                    target.addEffect(MobEffectInstance(ModEffects.GAS.get(),100,0,false,true,true))
                    if (target !is Player) target.addEffect(MobEffectInstance(ModEffects.CONFUSION.get(),100,0,false,true,true))
                } else target.addEffect(MobEffectInstance(ModEffects.STUN.get(),60,0,false,true,true))
            }
            entityData.set(REMAINING,remainingTicks-1)
        }
    }
    override fun shouldRenderAtSqrDistance(distance: Double)=distance<128.0*128.0
    companion object {
        private val MINE_CLOUD=SynchedEntityData.defineId(MortarFieldEntity::class.java,EntityDataSerializers.BOOLEAN)
        private val AREA_RADIUS=SynchedEntityData.defineId(MortarFieldEntity::class.java,EntityDataSerializers.FLOAT)
        private val KIND=SynchedEntityData.defineId(MortarFieldEntity::class.java,EntityDataSerializers.INT)
        private val REMAINING=SynchedEntityData.defineId(MortarFieldEntity::class.java,EntityDataSerializers.INT)
        fun create(level: ServerLevel,at: Vec3,kind: Kind,radius: Double=kind.radius,mineCloud: Boolean=false): MortarFieldEntity {
            val field=MortarFieldEntity(ModEntities.MORTAR_FIELD.get(),level)
            field.setPos(at.add(0.0,.02,0.0))
            field.entityData.set(MINE_CLOUD,mineCloud)
            field.entityData.set(AREA_RADIUS,radius.coerceIn(.1,16.0).toFloat())
            field.entityData.set(KIND,kind.ordinal)
            field.entityData.set(REMAINING,kind.lifetime)
            level.addFreshEntity(field)
            return field
        }
    }
}
