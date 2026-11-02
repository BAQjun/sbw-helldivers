package com.example.sbwhelldiver.survival

import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.SynchedEntityData
import net.neoforged.neoforge.entity.PartEntity

/** Native multipart collision: no independent ticking, save data or orphaned proxy entities. */
class EquipmentBeaconCollisionPart(beacon:EquipmentDeploymentBeaconEntity):PartEntity<EquipmentDeploymentBeaconEntity>(beacon) {
    init{isNoGravity=true;noPhysics=true}
    override fun defineSynchedData(builder:SynchedEntityData.Builder){}
    override fun addAdditionalSaveData(tag:CompoundTag){}
    override fun readAdditionalSaveData(tag:CompoundTag){}
    override fun canBeCollidedWith()=!parent.isRemoved&&parent.phase!=EquipmentDeploymentBeaconEntity.FINISHED
    override fun isPickable()=false // The parent's existing screen-only hit test owns terminal interaction.
    override fun isPushable()=false
}
