package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.MOD_ID
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.AABB

/** A direct crush by the common pod hull, never an area explosion. */
object HellpodImpact {
    const val DAMAGE=400f
    const val HULL_RADIUS=1.325
    private val TYPE=ResourceKey.create(Registries.DAMAGE_TYPE,ResourceLocation(MOD_ID,"hellpod_impact"))
    fun apply(pod:AbstractHellpodEntity,surfaceY:Double) {
        val world=pod.level() as? ServerLevel?:return
        val source=DamageSource(world.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(TYPE),pod)
        val bounds=AABB(pod.x-HULL_RADIUS*2,surfaceY-.5,pod.z-HULL_RADIUS*2,
            pod.x+HULL_RADIUS*2,surfaceY+AbstractHellpodEntity.ORIGIN_ABOVE_BOTTOM,pod.z+HULL_RADIUS*2)
        val victims=world.getEntities(pod,bounds){!it.isRemoved && !it.isSpectator && it.rootVehicle!==pod}.toList()
        for(victim in victims) {
            if(victim is AbstractHellpodEntity || victim is GrenadierWallPanelEntity)continue
            val box=victim.boundingBox
            val dx=(pod.x-box.maxX).coerceAtLeast(0.0)+(box.minX-pod.x).coerceAtLeast(0.0)
            val dz=(pod.z-box.maxZ).coerceAtLeast(0.0)+(box.minZ-pod.z).coerceAtLeast(0.0)
            if(dx*dx+dz*dz<=HULL_RADIUS*HULL_RADIUS)victim.hurt(source,DAMAGE)
        }
        // Some pods deliberately have no vanilla parent AABB (the wall uses parts).
        // Resolve loaded pod hulls independently of that broad-phase index.
        // Destroy first to play fuel/wreck effects, then remove the lower shell and
        // attached collision entities so two pods never continue pushing each other.
        for(victim in world.allEntities.filterIsInstance<AbstractHellpodEntity>().toList()) {
            val dx=pod.x-victim.x;val dz=pod.z-victim.z
            if(victim===pod || victim.isRemoved || victim.y>pod.y+.05 || victim.y<pod.y-AbstractHellpodEntity.ORIGIN_ABOVE_BOTTOM || dx*dx+dz*dz>4*HULL_RADIUS*HULL_RADIUS)continue
            victim.hurt(source,DAMAGE)
            if(!victim.isRemoved)victim.destroy()
            if(victim is GrenadierBattlementEntity)world.getEntitiesOfClass(GrenadierWallPanelEntity::class.java,victim.boundingBox.inflate(8.0)){it.battlement===victim}.forEach{it.discard()}
            if(!victim.isRemoved)victim.remove(Entity.RemovalReason.KILLED)
        }
    }
}
