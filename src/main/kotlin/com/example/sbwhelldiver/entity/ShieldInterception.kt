package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.compat.TaczBulletAccess
import com.atsuishio.superbwarfare.entity.projectile.IBulletProperties
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.projectile.*
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import kotlin.math.*

/** Sweeps actual projectile motion before its entity tick: fast bullets cannot tunnel through the shell. */
@Mod.EventBusSubscriber(modid=MOD_ID)
object ShieldInterception {
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
    @SubscribeEvent fun tick(event:TickEvent.LevelTickEvent){
        val level=event.level as? ServerLevel ?: return
        if(event.phase!=TickEvent.Phase.START)return
        val shields=level.allEntities.filterIsInstance<ShieldGeneratorEntity>().filter {it.shieldOnline}
        if(shields.isEmpty())return
        // TACZ inserts the sphere into its own sorted, block-clipped ray hits.
        // Sweeping it here would absorb before nearer entities and miss newly spawned bullets.
        for(p in level.allEntities.filterIsInstance<Projectile>())if(p !is TaczBulletAccess)intercept(p,shields)
    }
    fun intercept(p:Projectile,shields:List<ShieldGeneratorEntity>):Boolean {
        if(p.isRemoved || p.level().isClientSide)return false
        val from=p.position();val to=from.add(p.deltaMovement)
        val candidates=shields.filter {it.shieldOnline}.mapNotNull {s->entry(from,to,s.shieldCenter())?.let {Triple(s,it,from.lerp(to,it))}}.sortedBy {it.second}
        val candidate=candidates.firstOrNull() ?: return false
        val block=p.level().clip(ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p))
        if(block.type!=HitResult.Type.MISS && block.location.distanceToSqr(from)<candidate.third.distanceToSqr(from)-1e-6)return false
        if(!candidate.first.absorb(projectileDamage(p,candidate.third),candidate.third))return false
        p.setPos(candidate.third);p.discard() // Absorption never invokes an explosive projectile's terrain/area impact handler.
        return true
    }
}
