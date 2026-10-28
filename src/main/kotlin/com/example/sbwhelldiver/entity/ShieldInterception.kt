package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.compat.TaczBulletAccess
import com.atsuishio.superbwarfare.entity.projectile.IBulletProperties
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.projectile.*
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import kotlin.math.*

/** Sweeps actual projectile motion before its entity tick: fast bullets cannot tunnel through the shell. */
@EventBusSubscriber(modid=MOD_ID)
object ShieldInterception {
    // Track loaded generators instead of scanning every mob/item in every world
    // twenty times per second, including worlds with no shields at all.
    private val loaded=java.util.IdentityHashMap<ServerLevel,MutableSet<ShieldGeneratorEntity>>()
    @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    fun joined(event:net.neoforged.neoforge.event.entity.EntityJoinLevelEvent){
        val shield=event.entity as? ShieldGeneratorEntity ?: return
        val level=event.level as? ServerLevel ?: return
        loaded.getOrPut(level){linkedSetOf()}.add(shield)
    }
    @SubscribeEvent fun left(event:net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent){
        val shield=event.entity as? ShieldGeneratorEntity ?: return
        val level=event.level as? ServerLevel ?: return
        loaded[level]?.let {it.remove(shield);if(it.isEmpty())loaded.remove(level)}
    }
    @SubscribeEvent fun unload(event:net.neoforged.neoforge.event.level.LevelEvent.Unload){(event.level as? ServerLevel)?.let {loaded.remove(it)}}
    @SubscribeEvent fun stopped(event:net.neoforged.neoforge.event.server.ServerStoppedEvent){loaded.clear()}
    fun entry(from:Vec3,to:Vec3,center:Vec3,radius:Double=ShieldGeneratorEntity.RADIUS):Double? {
        val p=from.subtract(center);val v=to.subtract(from);val c=p.lengthSqr()-radius*radius
        if(c< -1e-7)return null // Outbound fire is never intercepted.
        val a=v.lengthSqr();if(a<1e-12)return null
        val b=p.dot(v);if(b>=0)return null
        val disc=b*b-a*c;if(disc<=0)return null
        val t=(-b-sqrt(disc))/a
        return if(t>=-1e-7 && t<=1.0)t.coerceAtLeast(0.0) else null
    }
    fun projectileDamage(p:Projectile,hit:Vec3=p.position()):Float=when(p){
        is TaczBulletAccess -> p.`helldivers$shieldDamage`(hit)
        is IBulletProperties -> (p.getDamage()+p.getExplosionDamage()).coerceAtLeast(0f)
        is ThrownTrident -> 8f
        is AbstractArrow -> (p.baseDamage*p.deltaMovement.length()).toFloat().coerceAtLeast(1f)
        is SmallFireball -> 5f
        is Fireball -> 6f
        is Snowball,is ThrownEgg,is ThrownEnderpearl,is ThrownPotion,is ThrownExperienceBottle -> 0f
        else -> 8f
    }
    @SubscribeEvent fun tick(event:net.neoforged.neoforge.event.tick.LevelTickEvent.Pre){
        val level=event.level as? ServerLevel ?: return
        val shields=loaded[level]?.filter {it.shieldOnline&&!it.isRemoved} ?: return
        if(shields.isEmpty())return
        // TACZ inserts the sphere into its own sorted, block-clipped ray hits.
        // Sweeping it here would absorb before nearer entities and miss newly spawned bullets.
        // Absorption removes entities; iterate a snapshot so removing one round
        // cannot invalidate the world's live iterator or skip the next round.
        for(p in level.allEntities.filterIsInstance<Projectile>())if(p !is TaczBulletAccess)intercept(p,shields)
    }
    fun intercept(p:Projectile,shields:List<ShieldGeneratorEntity>):Boolean {
        if(p.isRemoved || p.level().isClientSide)return false
        val from=p.position();val to=from.add(p.deltaMovement)
        var nearest:ShieldGeneratorEntity?=null;var fraction=Double.POSITIVE_INFINITY
        for(shield in shields)if(shield.shieldOnline){
            val t=entry(from,to,shield.shieldCenter()) ?: continue
            if(t<fraction){nearest=shield;fraction=t}
        }
        val shield=nearest ?: return false
        val point=from.lerp(to,fraction)
        val block=p.level().clip(ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p))
        if(block.type!=HitResult.Type.MISS && block.location.distanceToSqr(from)<point.distanceToSqr(from)-1e-6)return false
        if(!shield.absorb(projectileDamage(p,point),point))return false
        p.setPos(point);p.discard() // Absorption never invokes an explosive projectile's terrain/area impact handler.
        return true
    }
}
