package com.example.sbwhelldiver.entity

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import kotlin.math.*

/** Three broad wall sections: tall centre and two lower wings. */
class GrenadierWallPanelEntity(type: EntityType<*>,level: Level): Entity(type,level) {
    companion object {
        const val COUNT=3
        private val PARENT=SynchedEntityData.defineId(GrenadierWallPanelEntity::class.java,EntityDataSerializers.INT)
        private val INDEX=SynchedEntityData.defineId(GrenadierWallPanelEntity::class.java,EntityDataSerializers.INT)
    }
    val battlement get() = level().getEntity(entityData.get(PARENT)) as? GrenadierBattlementEntity
    override fun defineSynchedData(builder: SynchedEntityData.Builder) {builder.define(PARENT,-1);builder.define(INDEX,0)}
    fun bind(parent:GrenadierBattlementEntity,index:Int) {entityData.set(PARENT,parent.id);entityData.set(INDEX,index);syncBounds(parent)}
    private fun syncBounds(parent:GrenadierBattlementEntity) {
        val index=entityData.get(INDEX)
        val left=when(index){0 -> -3.2801875;1 -> -.63;else -> .63}
        val right=when(index){0 -> -.63;1 -> .63;else -> 3.2801875}
        val localX=(left+right)*.5;val halfWidth=(right-left)*.5
        val top=if(index==1)1.46 else 1.10
        val bottom=-.1262;val angle=Math.toRadians(parent.yRot.toDouble())
        val x=parent.x+localX*cos(angle)-.063*sin(angle)
        val z=parent.z+localX*sin(angle)+.063*cos(angle)
        val hx=abs(cos(angle))*halfWidth+abs(sin(angle))*.035
        val hz=abs(sin(angle))*halfWidth+abs(cos(angle))*.035
        setPos(x,parent.y+bottom,z)
        // SBW ray casts compensate position - xOld. A stationary cell must not look
        // like it flew from the origin on its first active tick.
        xOld=x;yOld=y;zOld=z
        xo=x;yo=y;zo=z
        boundingBox=AABB(x-hx,parent.y+bottom,z-hz,x+hx,parent.y+top,z+hz)
    }
    override fun tick() {
        super.tick();val parent=battlement
        if(parent==null) {if(!level().isClientSide&&tickCount>40)discard();return}
        if(parent.isRemoved||parent.isWreck||parent.phase!=AbstractHellpodEntity.Phase.ACTIVE) {
            if(!level().isClientSide)discard();return
        }
        syncBounds(parent)
    }
    override fun isPickable() = battlement?.let {!it.isWreck&&it.phase==AbstractHellpodEntity.Phase.ACTIVE}==true
    override fun canBeCollidedWith() = isPickable()
    override fun isPushable() = false
    override fun canCollideWith(other:Entity) = canBeCollidedWith() && other!==battlement && other.vehicle!==battlement && other !is GrenadierWallPanelEntity
    override fun hurt(source:DamageSource,amount:Float) = battlement?.hurt(source,amount) ?: false
    override fun interact(player:Player,hand:InteractionHand):InteractionResult = battlement?.interact(player,hand) ?: InteractionResult.PASS
    override fun readAdditionalSaveData(tag:CompoundTag) {}
    override fun addAdditionalSaveData(tag:CompoundTag) {}

}
