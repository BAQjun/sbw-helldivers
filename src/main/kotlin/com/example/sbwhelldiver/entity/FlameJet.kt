package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.effect.FlameDamage
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object FlameJet {
    const val HALF_WIDTH = .3
    fun radiusAt(distance: Double) = HALF_WIDTH + distance.coerceAtLeast(0.0) * .045
    fun spray(level: ServerLevel, emitter: Entity, start: Vec3, direction: Vec3,
        range: Double = FlameClock.RANGE, damage: Float = FlameClock.DIRECT_DAMAGE_PER_JET, attacker: Entity = emitter) {
        val axis = direction.normalize()
        val block = level.clip(ClipContext(start,start.add(axis.scale(range)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,emitter))
        val end = block.location
        val length=start.distanceTo(end)
        // Each unit intersecting the stream receives one direct hit; solid blocks stop it.
        level.getEntitiesOfClass(LivingEntity::class.java,AABB(start,end).inflate(radiusAt(range))) {
            val bounds=it.boundingBox
            val along=bounds.center.subtract(start).dot(axis)
            val halfExtent=(kotlin.math.abs(axis.x)*bounds.xsize+kotlin.math.abs(axis.y)*bounds.ysize+kotlin.math.abs(axis.z)*bounds.zsize)*.5
            it.isAlive && !it.isSpectator &&
                along-halfExtent<=length && along+halfExtent>=0 &&
                bounds.inflate(radiusAt(along.coerceAtMost(range))).clip(start,end).isPresent &&
                level.clip(ClipContext(start,bounds.center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,emitter)).type==HitResult.Type.MISS
        }.forEach { FlameDamage.hit(it,attacker,damage) }
        level.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(start,end).inflate(radiusAt(range))) {
            val bounds=it.boundingBox;val along=bounds.center.subtract(start).dot(axis)
            !it.isRemoved && along>=-.3 && along<=length+.3 && bounds.inflate(radiusAt(along.coerceAtMost(range))).clip(start,end).isPresent &&
                level.clip(ClipContext(start,bounds.center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,emitter)).type==HitResult.Type.MISS
        }.forEach { it.detonate() }
        if(block.type == HitResult.Type.BLOCK && block.direction == Direction.UP)
            GroundFireEntity.create(level,end,attacker)
        // A stream grazing the floor leaves a continuous trail. A downward probe must
        // touch an upward surface within the actual stream width, so it cannot burn through walls.
        var distance=0.0
        while(distance<=length) {
            val sample=start.add(axis.scale(distance))
            val floor=level.clip(ClipContext(sample,sample.add(0.0,-.45,0.0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,emitter))
            if(floor.type==HitResult.Type.BLOCK && floor.direction==Direction.UP) GroundFireEntity.create(level,floor.location,attacker)
            distance+=1.0
        }
    }
}
