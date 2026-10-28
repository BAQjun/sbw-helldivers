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
    var maximumLifetime = LIFETIME; private set
    var innerRadius = 0.0; private set
    var heavyBurning = false; private set
    private val terrainCells = hashSetOf<net.minecraft.core.BlockPos>()
    init { noPhysics = true }
    override fun defineSynchedData() { entityData.define(REMAINING,LIFETIME);entityData.define(AREA_RADIUS,RADIUS.toFloat()) }
    override fun getAddEntityPacket(): Packet<ClientGamePacketListener> = NetworkHooks.getEntitySpawningPacket(this)
    override fun addAdditionalSaveData(tag: CompoundTag) {
        tag.putInt("MaximumLifetime",maximumLifetime);tag.putDouble("InnerRadius",innerRadius)
        tag.putBoolean("HeavyBurning",heavyBurning);tag.putLongArray("TerrainCells",terrainCells.map { it.asLong() });tag.putInt("Remaining",remainingTicks);tag.putDouble("Radius",radius); sourceUuid?.let { tag.putUUID("Source",it) }
    }
    override fun readAdditionalSaveData(tag: CompoundTag) {
        heavyBurning=tag.getBoolean("HeavyBurning")
        terrainCells.clear();tag.getLongArray("TerrainCells").forEach { terrainCells.add(net.minecraft.core.BlockPos.of(it)) }
        entityData.set(REMAINING,tag.getInt("Remaining").coerceIn(0,72000))
        entityData.set(AREA_RADIUS,if(tag.contains("Radius"))tag.getDouble("Radius").coerceIn(.1,16.0).toFloat() else RADIUS.toFloat())
        // Older saves did not record elapsed age: keep their remaining life without extending it.
        maximumLifetime=if(tag.contains("MaximumLifetime"))tag.getInt("MaximumLifetime").coerceIn(1,72000) else remainingTicks.coerceAtLeast(1)
        innerRadius=if(tag.contains("InnerRadius"))tag.getDouble("InnerRadius").coerceIn(0.0,radius) else if(heavyBurning)2.0.coerceAtMost(radius) else 0.0
        sourceUuid = if(tag.hasUUID("Source")) tag.getUUID("Source") else null
    }
    override fun tick() {
        super.tick()
        if (!level().isClientSide) {
            if (remainingTicks <= 0) { discard(); return }
            if(tickCount==1)placeFlames()
            level().getEntitiesOfClass(LivingEntity::class.java,AABB(x-radius,y-(if(heavyBurning) 9.0 else .1),z-radius,x+radius,y+(if(heavyBurning) 5.0 else .45),z+radius)).forEach{igniteInside(it)}
            entityData.set(REMAINING,remainingTicks-1)
        }
    }
    /** Absolute age is shared; each horizontal ring has its own deadline, including on slopes. */
    fun remainingAt(pos:net.minecraft.core.BlockPos):Int {
        if(!containsCell(pos))return 0
        val lifetime=if(radius<2.0 || radius<=innerRadius)maximumLifetime else {
            val dx=pos.x+.5-x;val dz=pos.z+.5-z
            val u=((kotlin.math.sqrt(dx*dx+dz*dz)-innerRadius)/(radius-innerRadius)).coerceIn(0.0,1.0)
            // Quantize the native squared radial falloff into six extinction batches.
            val batch=kotlin.math.ceil(6*(1-u*u)).toInt().coerceIn(1,6)
            (maximumLifetime*batch/6).coerceAtLeast(1)
        }
        return (remainingTicks-(maximumLifetime-lifetime)).coerceAtLeast(0)
    }
    fun coversCell(pos:net.minecraft.core.BlockPos)=remainingAt(pos)>0
    private fun containsCell(pos:net.minecraft.core.BlockPos):Boolean {
        if(heavyBurning)return pos in terrainCells
        if(kotlin.math.abs(pos.y-y)>.1)return false
        if(radius<2.0)return pos.x==blockX && pos.z==blockZ
        val dx=pos.x+.5-x;val dz=pos.z+.5-z
        return dx*dx+dz*dz<=radius*radius
    }
    fun igniteInside(target:LivingEntity) {
        val feet=net.minecraft.core.BlockPos.containing(target.x,if(heavyBurning) target.y else kotlin.math.floor(y),target.z)
        if(!level().getBlockState(feet).`is`(com.example.sbwhelldiver.init.ModBlocks.SENTRY_FIRE.get()))return
        if(target.isAlive && !target.isSpectator && coversCell(feet) && target.y<=feet.y+.45 && target.boundingBox.maxY>=feet.y &&
            (target.x-x)*(target.x-x)+(target.z-z)*(target.z-z)<=radius*radius) {
            val source=sourceUuid?.let{(level() as? ServerLevel)?.getEntity(it)}
            if(heavyBurning) FlameDamage.igniteHeavy(target,source) else FlameDamage.ignite(target,source)
        }
    }
    private fun placeFlames() {
        val fire=com.example.sbwhelldiver.init.ModBlocks.SENTRY_FIRE.get().defaultBlockState()
        if(heavyBurning) {
            if(terrainCells.isEmpty()) {
                val reach=kotlin.math.ceil(radius).toInt()
                for(dx in -reach..reach)for(dz in -reach..reach) {
                    val px=blockX+dx;val pz=blockZ+dz
                    if((px+.5-x)*(px+.5-x)+(pz+.5-z)*(pz+.5-z)>radius*radius)continue
                    // Use loaded terrain only; no new chunks or floating horizontal fire discs.
                    for(py in blockY+4 downTo blockY-8) {
                        val cell=net.minecraft.core.BlockPos(px,py,pz)
                        if(!level().hasChunkAt(cell))break
                        val state=level().getBlockState(cell)
                        if((state.isAir || state.`is`(com.example.sbwhelldiver.init.ModBlocks.SENTRY_FIRE.get())) && fire.canSurvive(level(),cell)) {
                            terrainCells.add(cell);break
                        }
                    }
                }
            }
            for(cell in terrainCells)if(coversCell(cell) && level().getBlockState(cell).isAir && fire.canSurvive(level(),cell))level().setBlock(cell,fire,3)
            return
        }
        val reach=if(radius<2.0)0 else kotlin.math.ceil(radius).toInt()
        for(dx in -reach..reach)for(dz in -reach..reach) {
            val pos=net.minecraft.core.BlockPos(blockX+dx,kotlin.math.floor(y).toInt(),blockZ+dz)
            if(coversCell(pos) && level().getBlockState(pos).isAir && fire.canSurvive(level(),pos))level().setBlock(pos,fire,3)
        }
    }
    override fun shouldRenderAtSqrDistance(distance: Double)=distance<128.0*128.0

    companion object {
        // Native template 4: fuel 50/intensity 100; nominal maximum 13.2 s.
        const val NAPALM_LIFETIME = 264
        const val NAPALM_RADIUS = 10.0
        // Native template 3: fuel 40/intensity 80; 10.56 s rounded to a game tick.
        const val LIFETIME = 211
        const val RADIUS = .75
        private val AREA_RADIUS=SynchedEntityData.defineId(GroundFireEntity::class.java,EntityDataSerializers.FLOAT)
        private val REMAINING=SynchedEntityData.defineId(GroundFireEntity::class.java,EntityDataSerializers.INT)
        fun create(level: ServerLevel,at: Vec3,source: Entity? = null,radius: Double=RADIUS,heavyBurning: Boolean=false,lifetimeTicks:Int=if(heavyBurning) NAPALM_LIFETIME else LIFETIME,innerRadius:Double=if(heavyBurning)2.0 else 0.0): GroundFireEntity {
            val lifetime=lifetimeTicks.coerceIn(1,72000)
            val inner=innerRadius.coerceIn(0.0,radius.coerceIn(.1,16.0))
            val nearby=level.getEntitiesOfClass(GroundFireEntity::class.java,AABB(at,at).inflate(.6)) {
                it.heavyBurning==heavyBurning && it.maximumLifetime==lifetime && kotlin.math.abs(it.innerRadius-inner)<.001 && kotlin.math.abs(it.radius-radius)<.001 && kotlin.math.abs(it.y-at.y)<.25 && it.position().distanceToSqr(at)<.36
            }.firstOrNull()
            if(nearby!=null) { nearby.entityData.set(REMAINING,lifetime);nearby.sourceUuid=source?.uuid;nearby.placeFlames();return nearby }
            return GroundFireEntity(ModEntities.GROUND_FIRE.get(),level).also {
                it.heavyBurning=heavyBurning;it.maximumLifetime=lifetime;it.innerRadius=inner;it.entityData.set(REMAINING,lifetime);it.entityData.set(AREA_RADIUS,radius.coerceIn(.1,16.0).toFloat());it.setPos(at.add(0.0,.015,0.0));it.sourceUuid=source?.uuid;level.addFreshEntity(it);it.placeFlames()
            }
        }
    }
}
