package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModEntities
import com.google.gson.JsonParser
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.tags.DamageTypeTags
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.*

class MineDispenserEntity(type: EntityType<*>,level: Level): AbstractHellpodEntity(type,level) {
    val isAntiTank get()=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).path=="anti_tank_minefield"
    val minesPerPanel get()=if(isAntiTank) 3 else MinefieldClock.PER_PANEL
    val mineCount get()=MinefieldClock.PANELS*minesPerPanel
    val mountedSlots get()=if(isAntiTank) tankSlots else slots
    val mountedDirections get()=if(isAntiTank) tankLaunchDirections else launchDirections
    val releasedWaves get()=entityData.get(WAVES)
    val spreadAge get()=entityData.get(AGE)
    val spinning get()=phase==Phase.ACTIVE && spreadAge>0 && spreadAge<MinefieldClock.THROW_TICKS+MinefieldClock.SPINDOWN_TICKS
    override fun defineSynchedData() {
        super.defineSynchedData();entityData.define(WAVES,0);entityData.define(AGE,-1)
    }
    override fun shouldRetract()=false // Native payload life_time=0: empty launcher stays standing.
    override fun onActivated() { if(spreadAge<0) entityData.set(AGE,0) }
    override fun tickActive() {
        val age=(activeTicks-1).coerceAtLeast(0)
        entityData.set(AGE,age.coerceAtMost(60))
        val needed=MinefieldClock.releasedWaves(age,minesPerPanel)
        while(releasedWaves<needed) {
            val wave=releasedWaves
            for(panel in 0 until MinefieldClock.PANELS) release(panel*minesPerPanel+wave,age,wave)
            entityData.set(WAVES,wave+1)
        }
    }
    fun interpolatedSpin(partial: Float)=MinefieldClock.angleAt(spreadAge+partial.toDouble()).toFloat()
    private fun release(index: Int,age: Int,wave: Int) {
        // The renderer uses the synchronized tick, not the fractional ideal wave time.
        val rotation=Math.toRadians(MinefieldClock.angleAt(age.toDouble())-yRot).toFloat()
        val local=mountedSlots[index].yRot(rotation)
        val axis=mountedDirections[index].yRot(rotation).normalize()
        // Two consecutive waves form twelve separated lanes on each radial band.
        // Uniform random launch speeds concentrate mines near the pod and can
        // overlap adjacent waves; solve the ballistic arc for a spaced landing.
        val stagger=if((wave/2)%2==0) -4.0 else 4.0
        val horizontal=Vec3(axis.x,0.0,axis.z).normalize()
            .yRot(Math.toRadians(stagger+(random.nextDouble()-.5)*3).toFloat())
        val radius=(if(isAntiTank) 7.0+5.0*wave else MinefieldClock.landingRadius(wave))+(random.nextDouble()-.5)*.4
        val target=horizontal.scale(radius)
        val travel=Vec3(target.x-local.x,0.0,target.z-local.z)
        val ground=level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,blockX,blockZ)
        val height=(y+local.y-ground).coerceAtLeast(.1)
        val slope=axis.y/sqrt(axis.x*axis.x+axis.z*axis.z)
        val flight=(-1+sqrt(1+8*(height+travel.length()*slope)/AntiPersonnelMineEntity.GRAVITY))/2
        var velocity=travel.normalize().scale(travel.length()/flight)
        velocity=velocity.add(0.0,velocity.horizontalDistance()*slope,0.0)
        val speed=velocity.length().coerceIn(MinefieldClock.MIN_SPEED/20,MinefieldClock.MAX_SPEED/20)
        velocity=velocity.normalize().scale(speed)
        val mineType=when(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).path) {
            "incendiary_minefield" -> ModEntities.INCENDIARY_MINE
            "gas_minefield" -> ModEntities.GAS_MINE
            "anti_tank_minefield" -> ModEntities.ANTI_TANK_MINE
            else -> ModEntities.ANTI_PERSONNEL_MINE
        }
        val mine=mineType.get().create(level()) ?: return
        mine.launch(position().add(local),velocity,ownerUUID,uuid)
        level().addFreshEntity(mine)
    }
    override fun hurt(source: DamageSource,amount: Float): Boolean {
        if(level().isClientSide || isRemoved || phase==Phase.SPENT || phase==Phase.DONE ||
            !amount.isFinite() || amount<=0 || isInvulnerableTo(source))return false
        val computed=if(source.`is`(DamageTypeTags.IS_EXPLOSION)) amount*SentryHellpodEntity.EXPLOSION_MULTIPLIER
            else getDamageModifier().compute(this,source,amount)
        val before=health;onHurt(computed,source.entity,true)
        if(health<=0)destroy()
        return computed>0 && (health<before || phase==Phase.SPENT)
    }
    override fun onHurt(amount: Float,attacker: Entity?,send: Boolean) {
        if(phase==Phase.SPENT || phase==Phase.DONE || (!send && amount>0))return
        super.onHurt(amount,attacker,send)
    }
    override fun destroy() {
        if(level().isClientSide || phase==Phase.SPENT || isRemoved)return
        entityData.set(WAVES,minesPerPanel);entityData.set(AGE,60)
        isWreck=false;health=getMaxHealth();enterPhase(Phase.SPENT)
    }
    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag);tag.putInt("MineWaves",releasedWaves);tag.putInt("MineSpreadAge",spreadAge)
    }
    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag);entityData.set(WAVES,tag.getInt("MineWaves").coerceIn(0,minesPerPanel))
        entityData.set(AGE,if(tag.contains("MineSpreadAge"))tag.getInt("MineSpreadAge") else -1)
    }
    companion object {
        private val WAVES=SynchedEntityData.defineId(MineDispenserEntity::class.java,EntityDataSerializers.INT)
        private val AGE=SynchedEntityData.defineId(MineDispenserEntity::class.java,EntityDataSerializers.INT)
        private fun tankSocketVectors(key: String): List<Vec3> {
            val stream=requireNotNull(MineDispenserEntity::class.java.classLoader.getResourceAsStream("assets/$MOD_ID/helldivers/anti_tank_minefield_slots.json"))
            return stream.bufferedReader().use { reader -> JsonParser.parseReader(reader).asJsonArray.map { item ->
                val p=item.asJsonObject[key].asJsonArray;Vec3(p[0].asDouble,p[1].asDouble,p[2].asDouble)
            }}.also { require(it.size==18) }
        }
        val tankSlots= tankSocketVectors("offset")
        val tankLaunchDirections= tankSocketVectors("launch_direction")
        val launchDirections: List<Vec3> = run {
            val stream=requireNotNull(MineDispenserEntity::class.java.classLoader.getResourceAsStream("assets/$MOD_ID/helldivers/minefield_slots.json"))
            stream.bufferedReader().use { reader -> JsonParser.parseReader(reader).asJsonArray.map { item ->
                val p=item.asJsonObject["launch_direction"].asJsonArray;Vec3(p[0].asDouble,p[1].asDouble,p[2].asDouble)
            }}.also { require(it.size==MinefieldClock.COUNT) }
        }
        val slots: List<Vec3> = run {
            val stream=requireNotNull(MineDispenserEntity::class.java.classLoader.getResourceAsStream("assets/$MOD_ID/helldivers/minefield_slots.json"))
            stream.bufferedReader().use { reader -> JsonParser.parseReader(reader).asJsonArray.map { item ->
                val p=item.asJsonObject["offset"].asJsonArray;Vec3(p[0].asDouble,p[1].asDouble,p[2].asDouble)
            }}.also { require(it.size==MinefieldClock.COUNT) }
        }
    }
}
