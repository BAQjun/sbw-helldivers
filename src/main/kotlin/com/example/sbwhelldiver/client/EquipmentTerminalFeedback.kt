package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.survival.EquipmentDeploymentBeaconEntity

object EquipmentTerminalFeedback {
    /** One synchronized 0.3-second red flash on every terminal page. */
    fun flashing(entity:EquipmentDeploymentBeaconEntity,partial:Float=0f):Boolean {
        if(entity.inputErrorAt<0 || entity.phase!=EquipmentDeploymentBeaconEntity.ACTIVE)return false
        val age=entity.level().gameTime-entity.inputErrorAt+partial
        return age>=0 && age<6
    }
}
