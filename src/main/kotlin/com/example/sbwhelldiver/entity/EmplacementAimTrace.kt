package com.example.sbwhelldiver.entity

import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

/** The camera query and muzzle obstruction query share exactly the same collision policy. */
object EmplacementAimTrace {
    fun trace(pod:HmgEmplacementEntity,start:Vec3,direction:Vec3,range:Double=EmplacementAimMath.RANGE):Vec3 {
        val end=start.add(direction.normalize().scale(range.coerceIn(0.0,220.0)))
        var point=pod.level().clip(ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,pod)).location
        var nearest=start.distanceToSqr(point)
        for(entity in pod.level().getEntities(pod,AABB(start,end).inflate(1.0))) {
            if(entity===pod.operator || pod.hasPassenger(entity) || entity.isRemoved || !entity.isPickable || entity.isSpectator)continue
            if(entity is GrenadierWallPanelEntity && entity.battlement===pod)continue
            // The ring describes the shot centre, so do not add an aim-assist pick radius.
            val box=entity.boundingBox
            val hit=if(box.contains(start))start else box.clip(start,end).orElse(null) ?: continue
            val distance=start.distanceToSqr(hit)
            if(distance<nearest){point=hit;nearest=distance}
        }
        return point
    }
}
