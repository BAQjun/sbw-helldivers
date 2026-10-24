package com.example.sbwhelldiver.effect

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.monster.RangedAttackMob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.entity.EntityJoinLevelEvent
import net.minecraftforge.event.entity.living.LivingAttackEvent
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent
import net.minecraftforge.event.entity.living.LivingEvent
import net.minecraftforge.event.entity.living.MobEffectEvent
import net.minecraftforge.eventbus.api.Event
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.util.EnumSet
import java.util.WeakHashMap

@Mod.EventBusSubscriber(modid=MOD_ID)
object StatusEffectEvents {
    private val confusedGoals = WeakHashMap<Mob,Goal>()
    private val wildAttacker = ThreadLocal<LivingEntity?>()
    private const val WILD_PROJECTILE = "HelldiverConfusedAttack"
    private fun disoriented(entity: LivingEntity) = entity.hasEffect(ModEffects.CONFUSION.get()) || entity.hasEffect(ModEffects.PANIC.get())

    @SubscribeEvent fun leave(event: net.minecraftforge.event.entity.EntityLeaveLevelEvent) {
        (event.entity as? Mob)?.let { mob -> confusedGoals.remove(mob)?.let { mob.goalSelector.removeGoal(it) } }
    }
    @SubscribeEvent fun applicable(event: MobEffectEvent.Applicable) {
        if (event.entity is Player && (event.effectInstance.effect === ModEffects.CONFUSION.get() || event.effectInstance.effect === ModEffects.PANIC.get())) event.result = Event.Result.DENY
    }
    @SubscribeEvent fun target(event: LivingChangeTargetEvent) {
        if (disoriented(event.entity) || event.entity.hasEffect(ModEffects.STUN.get())) {
            if (event.newTarget != null) event.isCanceled = true
        }
    }
    @SubscribeEvent fun attack(event: LivingAttackEvent) {
        if (event.entity.hasEffect(ModEffects.BURNING.get()) &&
            (event.source.`is`(net.minecraft.world.damagesource.DamageTypes.ON_FIRE) || event.source.`is`(net.minecraft.world.damagesource.DamageTypes.IN_FIRE))) {
            event.isCanceled = true
            return
        }
        val attacker = event.source.entity as? LivingEntity ?: return
        if (attacker is Player) return
        if (attacker.hasEffect(ModEffects.STUN.get()) && event.source.directEntity === attacker) event.isCanceled = true
        if (disoriented(attacker) && wildAttacker.get() !== attacker &&
            event.source.directEntity?.persistentData?.getBoolean(WILD_PROJECTILE) != true) event.isCanceled = true
    }
    @SubscribeEvent fun projectile(event: EntityJoinLevelEvent) {
        if (event.level.isClientSide) return
        val projectile = event.entity as? Projectile ?: return
        val owner = projectile.owner as? LivingEntity ?: return
        if (owner !is Player && owner.hasEffect(ModEffects.STUN.get())) { event.isCanceled = true; return }
        if (wildAttacker.get() === owner) projectile.persistentData.putBoolean(WILD_PROJECTILE,true)
        else if (owner !is Player && disoriented(owner)) event.isCanceled = true
    }
    @SubscribeEvent fun tick(event: LivingEvent.LivingTickEvent) {
        val entity = event.entity
        if (entity.level().isClientSide) return
        if (entity !is Player && entity.hasEffect(ModEffects.STUN.get())) {
            StunEffect.disable(entity)
            val v = entity.deltaMovement
            entity.deltaMovement = Vec3(0.0,v.y.coerceAtMost(0.0),0.0)
            entity.xxa = 0f; entity.zza = 0f; entity.setJumping(false)
        } else StunEffect.restore(entity)
        val mob = entity as? Mob ?: return
        if (disoriented(mob)) {
            mob.target = null
            if (!confusedGoals.containsKey(mob)) {
                val goal = ConfusedGoal(mob)
                confusedGoals[mob] = goal
                mob.goalSelector.addGoal(-100,goal)
            }
        } else confusedGoals.remove(mob)?.let { mob.goalSelector.removeGoal(it); mob.navigation.stop() }
    }
    private class ConfusedGoal(private val mob: Mob) : Goal() {
        init { flags = EnumSet.of(Flag.MOVE,Flag.LOOK,Flag.TARGET,Flag.JUMP) }
        override fun canUse() = disoriented(mob) && !mob.hasEffect(ModEffects.STUN.get())
        override fun requiresUpdateEveryTick() = true
        override fun stop() { mob.navigation.stop() }
        override fun tick() {
            if (mob.tickCount % 20 != 0) return
            val angle = mob.random.nextDouble()*Math.PI*2
            val distance = 2.0+mob.random.nextDouble()*4.0
            mob.navigation.moveTo(mob.x+ kotlin.math.cos(angle)*distance,mob.y,mob.z+kotlin.math.sin(angle)*distance,1.0)
            val ranged = mob is RangedAttackMob
            val nearby = mob.level().getEntitiesOfClass(LivingEntity::class.java,mob.boundingBox.inflate(if(ranged)10.0 else 2.5)) {
                it !== mob && it.isAlive && !it.isSpectator && (it !is Player || !it.isCreative) && mob.hasLineOfSight(it)
            }
            if (nearby.isEmpty()) { mob.lookControl.setLookAt(mob.x+kotlin.math.cos(angle)*4,mob.eyeY,mob.z+kotlin.math.sin(angle)*4); return }
            val victim = nearby[mob.random.nextInt(nearby.size)]
            mob.lookControl.setLookAt(victim,180f,180f)
            wildAttacker.set(mob)
            try {
                if (ranged) (mob as RangedAttackMob).performRangedAttack(victim,1f)
                else if (mob.getAttribute(Attributes.ATTACK_DAMAGE) != null) mob.doHurtTarget(victim)
            } finally { wildAttacker.remove() }
        }
    }
}
