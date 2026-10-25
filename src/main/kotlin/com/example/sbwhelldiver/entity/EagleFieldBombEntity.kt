package com.example.sbwhelldiver.entity

import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

/** Field payloads keep SBW's real direct-hit collision without a second generic explosion. */
abstract class EagleFieldBombEntity(type: EntityType<out SmallCannonShellEntity>,level: Level):SentryCannonShellEntity(type,level) {
    protected abstract val attack: EagleAttack
    override val innerBlastRadius get() = if(attack==EagleAttack.NAPALM) 2.0 else 0.0
    init { setDamage(240f);setExplosionDamage(0f);setExplosionRadius(0f);setCustomGravity(EagleFlight.BOMB_GRAVITY.toFloat()) }
    override fun crushProjectile(velocity: Vec3) {} // Neighbouring canisters never intercept each other.
    override fun causeExplode(vec3: Vec3) {
        val server=level() as? ServerLevel ?: return
        if(exploded)return
        if(attack==EagleAttack.NAPALM)super.causeExplode(vec3) else exploded=true
        val ground=server.clip(ClipContext(vec3.add(0.0,.15,0.0),vec3.add(0.0,-64.0,0.0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this))
        val at=if(ground.type==net.minecraft.world.phys.HitResult.Type.BLOCK)ground.location else vec3
        when(attack) {
            EagleAttack.NAPALM -> GroundFireEntity.create(server,at,owner,GroundFireEntity.NAPALM_RADIUS,true)
            EagleAttack.GAS -> MortarFieldEntity.create(server,at,MortarFieldEntity.Kind.GAS,12.0,effectTicks=120)
            EagleAttack.SMOKE -> {
                MortarFieldEntity.create(server,at,MortarFieldEntity.Kind.SMOKE)
                server.playSound(null,at.x,at.y,at.z,net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,net.minecraft.sounds.SoundSource.NEUTRAL,2f,.65f)
            }
            else -> error("Not a field payload")
        }
    }
    override fun afterHitEntity(result: EntityHitResult) {causeExplode(result.location);discard()}
    override fun afterHitBlock(result: BlockHitResult) {causeExplode(result.location);discard()}
}
class EagleNapalmBombEntity(type: EntityType<out SmallCannonShellEntity>,level: Level):EagleFieldBombEntity(type,level) {
    override val attack get()=EagleAttack.NAPALM
    init {setExplosionDamage(40f);setExplosionRadius(10f)}
}
class EagleGasBombEntity(type: EntityType<out SmallCannonShellEntity>,level: Level):EagleFieldBombEntity(type,level) {
    override val attack get()=EagleAttack.GAS
}
class EagleSmokeBombEntity(type: EntityType<out SmallCannonShellEntity>,level: Level):EagleFieldBombEntity(type,level) {
    override val attack get()=EagleAttack.SMOKE
}
