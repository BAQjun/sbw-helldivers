package com.example.sbwhelldiver.effect

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.MortarFieldEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.entity.EntityJoinLevelEvent
import net.minecraftforge.event.entity.EntityLeaveLevelEvent
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent
import net.minecraftforge.event.entity.living.LivingEvent
import net.minecraftforge.event.entity.living.LivingHurtEvent
import net.minecraftforge.event.level.LevelEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.util.UUID
import java.util.WeakHashMap
import kotlin.math.*

/** A visibility constraint on vanilla sensing/target conditions/navigation, not an AI replacement. */
@Mod.EventBusSubscriber(modid=MOD_ID)
object SmokeOcclusion {
    const val CLOSE_SIGHT = 4.0
    private const val SHOT_ORIGIN = "HelldiverSmokeShotOrigin"
    private data class Memory(val target: UUID,var point: Vec3,var blocked: Boolean=false,var attackedAt: Long=Long.MIN_VALUE)
    private val fields=WeakHashMap<Level,MutableSet<MortarFieldEntity>>()
    private val memories=WeakHashMap<Mob,Memory>()
    fun register(field: MortarFieldEntity) {
        if(!field.level().isClientSide && field.kind==MortarFieldEntity.Kind.SMOKE)
            fields.getOrPut(field.level()){hashSetOf()}.add(field)
    }
    private fun clouds(level: Level)=fields[level]?.filter { it.isAlive && it.remainingTicks>0 } ?: emptyList()
    private fun contains(field: MortarFieldEntity,p: Vec3)=p.y>=field.y && p.y<=field.y+field.fieldHeight &&
        (p.x-field.x).pow(2)+(p.z-field.z).pow(2)<field.radius*field.radius
    /** Exact segment/finite-cylinder intersection; smoke does not block projectiles or movement. */
    fun intersects(field: MortarFieldEntity,a: Vec3,b: Vec3): Boolean {
        val d=b.subtract(a);val x=a.x-field.x;val z=a.z-field.z
        var lo=0.0;var hi=1.0
        if(abs(d.y)<1e-9) {if(a.y<field.y || a.y>field.y+field.fieldHeight)return false}
        else {val t1=(field.y-a.y)/d.y;val t2=(field.y+field.fieldHeight-a.y)/d.y;lo=max(lo,min(t1,t2));hi=min(hi,max(t1,t2))}
        val aa=d.x*d.x+d.z*d.z;val cc=x*x+z*z-field.radius*field.radius
        if(aa<1e-9) {if(cc>=0)return false}
        else {
            val bb=2*(x*d.x+z*d.z);val disc=bb*bb-4*aa*cc
            if(disc<=0)return false
            val root=sqrt(disc);lo=max(lo,(-bb-root)/(2*aa));hi=min(hi,(-bb+root)/(2*aa))
        }
        return hi>lo
    }
    @JvmStatic fun blocks(observer: LivingEntity?,target: Entity?): Boolean {
        if(observer !is Mob || target !is Player || observer.level().isClientSide)return false
        val clouds=clouds(observer.level());if(clouds.isEmpty())return false
        val eye=observer.eyePosition;val end=target.eyePosition
        // Close sight is a cap on the mob's existing range, never an added detection ability.
        if(observer.distanceToSqr(target)<=CLOSE_SIGHT*CLOSE_SIGHT && clouds.any{contains(it,eye)})return false
        return clouds.any { intersects(it,eye,end) }
    }
    private fun recentHit(mob: Mob,target: Entity): Boolean {
        val m=memories[mob] ?: return false
        return m.target==target.uuid && m.attackedAt!=Long.MIN_VALUE && mob.level().gameTime-m.attackedAt in 0..20
    }
    @JvmStatic fun preventsAcquisition(observer: LivingEntity?,target: LivingEntity): Boolean =
        blocks(observer,target) && observer is Mob && observer.target!==target && !recentHit(observer,target)
    /** Return null to let vanilla build the path normally; only obscured live target positions are masked. */
    @JvmStatic fun knownPosition(mob: Mob,target: Entity): Vec3? {
        if(!blocks(mob,target))return null
        return memories[mob]?.takeIf {it.target==target.uuid}?.point ?: mob.position()
    }
    @JvmStatic fun maskDestination(mob: Mob,wanted: Vec3): Vec3 {
        val target=mob.target ?: return wanted
        if(wanted.distanceToSqr(target.position())>4.0)return wanted
        return knownPosition(mob,target) ?: wanted
    }
    @SubscribeEvent fun joined(event: EntityJoinLevelEvent) {
        if(event.level.isClientSide)return
        (event.entity as? MortarFieldEntity)?.let{register(it)}
        val projectile=event.entity as? Projectile ?: return
        val owner=projectile.owner as? Player ?: return
        projectile.persistentData.putLongArray(SHOT_ORIGIN,longArrayOf(
            java.lang.Double.doubleToRawLongBits(owner.x),java.lang.Double.doubleToRawLongBits(owner.y),java.lang.Double.doubleToRawLongBits(owner.z)))
    }
    @SubscribeEvent fun left(event: EntityLeaveLevelEvent) {
        (event.entity as? MortarFieldEntity)?.let{fields[event.level]?.remove(it)}
        (event.entity as? Mob)?.let{memories.remove(it)}
    }
    @SubscribeEvent fun unload(event: LevelEvent.Unload) { fields.remove(event.level) }
    @SubscribeEvent fun target(event: LivingChangeTargetEvent) {
        val mob=event.entity as? Mob ?: return
        val target=event.newTarget ?: return
        if(preventsAcquisition(mob,target))event.isCanceled=true
        else if(target is Player && !blocks(mob,target) && mob.hasLineOfSight(target))memories[mob]=Memory(target.uuid,target.position())
    }
    @SubscribeEvent fun attacked(event: LivingHurtEvent) {
        if(event.isCanceled || event.amount<=0)return
        val mob=event.entity as? Mob ?: return
        val player=event.source.entity as? Player ?: return
        if(!blocks(mob,player))return
        val shot=event.source.directEntity as? Projectile
        val raw=shot?.persistentData?.getLongArray(SHOT_ORIGIN)
        val at=if(raw!=null && raw.size==3)Vec3(java.lang.Double.longBitsToDouble(raw[0]),java.lang.Double.longBitsToDouble(raw[1]),java.lang.Double.longBitsToDouble(raw[2])) else player.position()
        memories[mob]=Memory(player.uuid,at,true,mob.level().gameTime)
        // HurtByTargetGoal decides if this species retaliates. This only refreshes its existing route.
        if(mob.target===player)redirectExistingPath(mob,at)
    }
    private fun redirectExistingPath(mob: Mob,at: Vec3) {
        val nav=mob.navigation
        if(nav.isDone)return
        val speed=(nav as com.example.sbwhelldiver.mixin.SmokeNavigationAccess).smokeSpeedModifier
        nav.moveTo(nav.createPath(net.minecraft.core.BlockPos.containing(at),0),speed)
    }
    @SubscribeEvent fun tick(event: LivingEvent.LivingTickEvent) {
        val mob=event.entity as? Mob ?: return
        if(mob.level().isClientSide)return
        val target=mob.target as? Player
        if(target==null) {
            val memory=memories[mob]
            if(memory==null || memory.attackedAt==Long.MIN_VALUE || mob.level().gameTime-memory.attackedAt>20)memories.remove(mob)
            return
        }
        if(blocks(mob,target)) {
            val memory=memories[mob]?.takeIf{it.target==target.uuid}
            if(memory!=null && !memory.blocked) {memory.blocked=true;redirectExistingPath(mob,memory.point)}
        } else if(mob.hasLineOfSight(target)) memories[mob]=Memory(target.uuid,target.position())
        // Vanilla target goals retain/expire aggro with their own unseen-memory timers.
    }
}
