package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.effect.FlameDamage
import com.example.sbwhelldiver.init.ModEffects
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** Select the complete chain before any damage can kill or remove its endpoints. */
object TeslaArc {
    fun eligible(target: LivingEntity) = target.isAlive && !target.isRemoved && !target.isSpectator &&
        !(target is Player && target.isCreative)
    fun clear(sentry: SentryHellpodEntity, from: Vec3, to: Vec3) = sentry.level().clip(
        ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,sentry)
    ).type==HitResult.Type.MISS
    fun chain(sentry: SentryHellpodEntity, first: LivingEntity): List<LivingEntity> {
        if(!eligible(first))return emptyList()
        val victims=mutableListOf(first)
        while(victims.size<TeslaClock.MAX_VICTIMS) {
            val previous=victims.last();val start=previous.boundingBox.center
            val next=sentry.level().getEntitiesOfClass(LivingEntity::class.java,AABB(start,start).inflate(TeslaClock.JUMP_RANGE)) {
                eligible(it) && victims.none { v -> v.uuid==it.uuid } &&
                    start.distanceToSqr(it.boundingBox.center)<=TeslaClock.JUMP_RANGE*TeslaClock.JUMP_RANGE &&
                    clear(sentry,start,it.boundingBox.center)
            }.minWithOrNull(compareBy<LivingEntity> { start.distanceToSqr(it.boundingBox.center) }.thenBy { it.id }) ?: break
            victims.add(next)
        }
        return victims
    }
    fun discharge(sentry: SentryHellpodEntity,victims: List<LivingEntity>) {
        if(sentry.level().isClientSide)return
        victims.distinctBy { it.uuid }.take(TeslaClock.MAX_VICTIMS).forEach {
            if(eligible(it)) {
                it.hurt(FlameDamage.source(it,"tesla_arc",sentry),TeslaClock.DAMAGE)
                if(it.isAlive) {
                    it.addEffect(MobEffectInstance(ModEffects.STUN.get(),TeslaClock.STUN_TICKS,0,false,true,true))
                    // The brief EMS interruption stops movement at once; it never ignites ground.
                    it.deltaMovement=Vec3.ZERO
                }
            }
        }
    }
}
