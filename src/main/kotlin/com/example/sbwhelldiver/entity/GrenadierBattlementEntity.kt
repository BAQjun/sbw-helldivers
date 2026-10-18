package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.DamageTypeTags
import net.minecraft.util.Mth
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import kotlin.math.*

/** The cover stays fixed; only the launcher traverses. Empty cover is never reclaimed. */
class GrenadierBattlementEntity(type: EntityType<*>,level: Level): HmgEmplacementEntity(type,level) {
    companion object {
        private val DEPLOYMENT_HEADING=net.minecraft.network.syncher.SynchedEntityData.defineId(GrenadierBattlementEntity::class.java,net.minecraft.network.syncher.EntityDataSerializers.FLOAT)
    }
    override fun defineSynchedData() {super.defineSynchedData();entityData.define(DEPLOYMENT_HEADING,Float.NaN)}
    fun setDeploymentHeading(yaw:Float) {
        require(yaw.isFinite())
        entityData.set(DEPLOYMENT_HEADING,Mth.wrapDegrees(yaw))
        yRot=Mth.wrapDegrees(yaw);yRotO=yRot
    }
    override fun onSyncedDataUpdated(key:net.minecraft.network.syncher.EntityDataAccessor<*>) {
        super.onSyncedDataUpdated(key)
        if(key==DEPLOYMENT_HEADING)entityData.get(DEPLOYMENT_HEADING).takeIf {it.isFinite()}?.let {yRot=it;yRotO=it}
    }
    override fun lerpTo(x:Double,y:Double,z:Double,yaw:Float,pitch:Float,steps:Int,interpolate:Boolean) {
        super.lerpTo(x,y,z,entityData.get(DEPLOYMENT_HEADING).takeIf {it.isFinite()} ?: yaw,pitch,steps,interpolate)
    }
    override fun addAdditionalSaveData(tag:net.minecraft.nbt.CompoundTag) {
        super.addAdditionalSaveData(tag)
        tag.putFloat("GrenadierDeploymentHeading",entityData.get(DEPLOYMENT_HEADING).takeIf {it.isFinite()} ?: yRot)
    }
    override fun readAdditionalSaveData(tag:net.minecraft.nbt.CompoundTag) {
        super.readAdditionalSaveData(tag)
        setDeploymentHeading(if(tag.contains("GrenadierDeploymentHeading"))tag.getFloat("GrenadierDeploymentHeading").takeIf {it.isFinite()} ?: yRot else yRot)
    }
    override val ammunitionCapacity get() = 60
    override val ergonomics get() = 25.0
    override val mechanicalErgonomics get() = 25.0
    override val needsFirstMountLevelling get() = false
    override val immediateGameplayAim get()=true
    override val nextBarrel get() = 0
    override val shotInterval get() = 8
    // 7.5 ticks per shot, without banking unused firing time or resetting on trigger release.
    override fun nextShotDelayTicks() = if (shotSerial and 1 == 1) 8 else 7
    override val verticalAimKick get() = .70f
    override val horizontalAimKick get() = .18
    override val weaponPivot get() = Vec3(0.0,1.3831595625,.1192714375)
    override val operatorOffset get() = Vec3(0.0,.0109441256,-.6459492445)
    // Register a sight transform with SBW's seat camera resolver. Coordinates are
    // measured from this model's barrel pivot, not the standing player's eye.
    init {
        positionTransform["GrenadierOptic"]=java.util.function.Function { t ->
            val base=worldTraversePivot(t)
            org.joml.Matrix4d().translate(base.x,base.y,base.z)
                .rotateY(-Math.toRadians(interpolatedYaw(t).toDouble()))
                .translate(weaponPivot.x,weaponPivot.y-traversePivot.y,weaponPivot.z-traversePivot.z)
                .rotateX(Math.toRadians(interpolatedPitch(t).toDouble()))
        }
        vectorTransform["GrenadierBore"]=java.util.function.Function { t -> boreDirection(t) }
    }
    private val traversePivot get()=Vec3(0.0,1.289608,.11895225)
    private fun worldTraversePivot(partial:Float):Vec3 {
        // The traverse pivot is offset from the pod centre and belongs to its frame,
        // while the launcher's aiming yaw is an absolute world direction.
        val bodyYaw=Math.toRadians(getYaw(partial).toDouble())
        return getPosition(partial).add(traversePivot.x*cos(bodyYaw)-traversePivot.z*sin(bodyYaw),traversePivot.y,traversePivot.x*sin(bodyYaw)+traversePivot.z*cos(bodyYaw))
    }
    override fun muzzlePosition(barrel:Int,partial:Float):Vec3 {
        val p=Math.toRadians(interpolatedPitch(partial).toDouble())
        val y=Math.toRadians(interpolatedYaw(partial).toDouble())
        val m=profile.muzzleOffsets.first()
        val local=Vec3(m.x,m.y*cos(p)-m.z*sin(p),m.y*sin(p)+m.z*cos(p)).add(weaponPivot.subtract(traversePivot))
        return worldTraversePivot(partial).add(local.x*cos(y)-local.z*sin(y),local.y,local.x*sin(y)+local.z*cos(y))
    }
    override fun aimAnglesAt(point:Vec3):net.minecraft.world.phys.Vec2 = HmgAimMath.angles(
        point.subtract(worldTraversePivot(1f).add(0.0,weaponPivot.y-traversePivot.y,0.0)),
        profile.muzzleOffsets.first(),weaponPivot.z-traversePivot.z)
    private fun localOperator():Player? = if(level().isClientSide &&
        com.example.sbwhelldiver.client.HmgClient.mounted()===this)operator else null
    override fun interpolatedYaw(partial:Float):Float = localOperator()?.let {
        yRot+Mth.wrapDegrees(it.getViewYRot(partial)-yRot).coerceIn(-60f,60f)
    } ?: super.interpolatedYaw(partial)
    override fun interpolatedPitch(partial:Float):Float = localOperator()?.getViewXRot(partial)?.coerceIn(-45f,35f)
        ?: super.interpolatedPitch(partial)
    override fun getDimensions(pose:net.minecraft.world.entity.Pose)=net.minecraft.world.entity.EntityDimensions.fixed(0f,0f)
    override fun canBeCollidedWith()=false
    override fun canCollideWith(other:net.minecraft.world.entity.Entity)=false
    override fun isPickable()=false
    override fun shouldRenderAtSqrDistance(distance:Double)=distance<256.0*256.0
    override fun getBoundingBoxForCulling():net.minecraft.world.phys.AABB = net.minecraft.world.phys.AABB(
        x-3.6,y-.3,z-3.6,x+3.6,y+2.8,z+3.6)
    fun throughSight() = level().isClientSide &&
        net.minecraft.client.Minecraft.getInstance().options.cameraType.isFirstPerson &&
        com.example.sbwhelldiver.client.HmgClient.mounted()===this && com.example.sbwhelldiver.client.HmgClient.aiming()
    fun opticalCameraPosition(partial:Float):Vec3 {
        val pitch=Math.toRadians(interpolatedPitch(partial).toDouble())
        val yaw=Math.toRadians(interpolatedYaw(partial).toDouble())
        val eye=Vec3(-.00031694,.24681094,-.29422906)
        val local=Vec3(eye.x,eye.y*cos(pitch)-eye.z*sin(pitch),eye.y*sin(pitch)+eye.z*cos(pitch)).add(weaponPivot.subtract(traversePivot))
        return worldTraversePivot(partial).add(local.x*cos(yaw)-local.z*sin(yaw),local.y,local.x*sin(yaw)+local.z*cos(yaw))
    }
    override fun getCameraPosition(partial:Float,player:Player,zoom:Boolean,isFirstPerson:Boolean):Vec3 {
        if(!isFirstPerson) {
            val eye=operatorPosition(partial).add(0.0,player.eyeHeight.toDouble(),0.0);val yaw=Math.toRadians(player.getViewYRot(partial).toDouble())
            val offset=Vec3(2.75*sin(yaw),.70,-2.75*cos(yaw));val wanted=eye.add(offset)
            val hit=level().clip(net.minecraft.world.level.ClipContext(eye,wanted,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,player))
            return if(hit.type==net.minecraft.world.phys.HitResult.Type.MISS)wanted else eye.add(offset.normalize().scale((eye.distanceTo(hit.location)-.12).coerceAtLeast(.05)))
        }
        if(!throughSight())return super.getCameraPosition(partial,player,zoom,isFirstPerson)
        return opticalCameraPosition(partial)
    }
    override fun getCameraRotation(partial:Float,player:Player,zoom:Boolean,isFirstPerson:Boolean):net.minecraft.world.phys.Vec2 {
        if(!throughSight())return super.getCameraRotation(partial,player,zoom,isFirstPerson)
        return net.minecraft.world.phys.Vec2(interpolatedYaw(partial),interpolatedPitch(partial))
    }
    override fun shouldRetract() = false
    private val panels = mutableListOf<GrenadierWallPanelEntity>()
    override fun remove(reason:net.minecraft.world.entity.Entity.RemovalReason){
        panels.forEach{it.discard()};panels.clear();super.remove(reason)
    }
    private var explosionTick = Long.MIN_VALUE
    private val explosionHits = mutableMapOf<String,Float>()
    override fun operatorPosition(partial: Float): Vec3 {
        val angle=Math.toRadians(yRot.toDouble());val v=operatorOffset
        return getPosition(partial).add(v.x*cos(angle)-v.z*sin(angle),v.y,v.x*sin(angle)+v.z*cos(angle))
    }
    override fun updateWeaponAim() {
        // This launcher follows input directly; the two heavy manned emplacements
        // retain their independent handling and mechanical motor profiles.
        turretAimYaw=yRot+Mth.wrapDegrees(requestedYaw-yRot).coerceIn(-60f,60f)
        turretAimPitch=requestedPitch.coerceIn(-45f,35f)
    }
    override fun fireRound(server: ServerLevel,player: Player): Boolean {
        val grenade=ModEntities.GRENADIER_GRENADE.get().create(server) ?: return false
        val direction=firingDirection(0)
        grenade.owner=player;grenade.setPos(muzzlePosition(0));grenade.setCustomGravity(.025f)
        grenade.shoot(direction.x,direction.y,direction.z,5f,.25f)
        return server.addFreshEntity(grenade)
    }
    override fun tick() {
        super.tick()
        entityData.get(DEPLOYMENT_HEADING).takeIf {it.isFinite()}?.let {yRot=it;yRotO=it}
        if(level().isClientSide)return
        if(phase!=Phase.ACTIVE || isWreck || isRemoved) {
            panels.forEach {it.discard()};panels.clear();return
        }
        // Collision cells have no independent health or persistence. Rebuild after loading the parent.
        if(panels.size!=GrenadierWallPanelEntity.COUNT || panels.any {it.isRemoved}) {
            panels.forEach {it.discard()};panels.clear()
            repeat(GrenadierWallPanelEntity.COUNT) {i ->
                ModEntities.GRENADIER_WALL_PANEL.get().create(level())?.let { p ->
                    p.bind(this,i);level().addFreshEntity(p);panels.add(p)
                }
            }
        }
    }
    override fun hurt(source: DamageSource,amount: Float): Boolean {
        if(source.`is`(DamageTypeTags.IS_EXPLOSION)) {
            if(explosionTick!=level().gameTime){explosionTick=level().gameTime;explosionHits.clear()}
            val key="${source.type().msgId()}/${source.directEntity?.uuid}/${source.entity?.uuid}"
            val previous=explosionHits[key] ?: 0f
            if(amount<=previous)return false
            explosionHits[key]=amount
            return super.hurt(source,amount-previous)
        }
        return super.hurt(source,amount)
    }
}
