package com.example.sbwhelldiver.entity

import com.atsuishio.superbwarfare.entity.projectile.DestroyableProjectile
import com.atsuishio.superbwarfare.entity.projectile.SmallCannonShellEntity
import net.minecraft.server.level.ServerLevel
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.Vec3

open class SentryCannonShellEntity(type: EntityType<out SmallCannonShellEntity>, level: Level) : SmallCannonShellEntity(type, level) {
    protected open val configId get() = "autocannon_sentry"
    protected open val innerBlastRadius get() = com.example.sbwhelldiver.config.TurretConfigs.get(configId).innerRadius!!.get()
    protected open val explosionParticleType: com.atsuishio.superbwarfare.tools.ParticleTool.ParticleType? get() = null
    init {
        setDamage(70f)
        setExplosionDamage(20f)
        setExplosionRadius(5f)
        setHeadShot(1f)
        setLegShot(1f)
        val settings = com.example.sbwhelldiver.config.TurretConfigs.get(configId)
        setDamage(settings.damage.get().toFloat())
        setExplosionDamage(settings.explosionDamage!!.get().toFloat())
        setExplosionRadius(settings.outerRadius!!.get().toFloat())
    }

    private var receivedFullMotion = false
    // Snapshot the decoded spawn packet so diagnostics do not confuse a later gravity tick with clipping.
    internal var initialSpawnMotion: Vec3? = null
        private set
    var launchPosition = Vec3.ZERO
        private set

    override fun shoot(x: Double, y: Double, z: Double, velocity: Float, inaccuracy: Float) {
        launchPosition = position()
        super.shoot(x, y, z, velocity, inaccuracy)
    }

    override fun writeSpawnData(buffer: FriendlyByteBuf) {
        super.writeSpawnData(buffer)
        buffer.writeDouble(deltaMovement.x)
        buffer.writeDouble(deltaMovement.y)
        buffer.writeDouble(deltaMovement.z)
        buffer.writeFloat(getCustomGravity())
        buffer.writeDouble(launchPosition.x)
        buffer.writeDouble(launchPosition.y)
        buffer.writeDouble(launchPosition.z)
        buffer.writeInt(owner?.id ?: 0)
    }

    override fun readSpawnData(buffer: FriendlyByteBuf) {
        super.readSpawnData(buffer)
        deltaMovement = Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
        setCustomGravity(buffer.readFloat())
        launchPosition = Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
        owner = level().getEntity(buffer.readInt())
        initialSpawnMotion = deltaMovement
        receivedFullMotion = true
        updateRotation()
        xRotO = xRot
        yRotO = yRot
    }

    override fun lerpMotion(x: Double, y: Double, z: Double) {
        // Ignore vanilla's clipped tracking packet; SBW's full motion updates remain active.
        if (level().isClientSide && receivedFullMotion &&
            (kotlin.math.abs(x) == 3.9 || kotlin.math.abs(y) == 3.9 || kotlin.math.abs(z) == 3.9)) return
        super.lerpMotion(x, y, z)
    }

    // SBW enables projectile interception on small shells; it must use the same dual-radius blast.
    override fun crushProjectile(velocity: Vec3) {
        if (level() !is ServerLevel || exploded) return
        val target = level().getEntitiesOfClass(Projectile::class.java, boundingBox.inflate(.5).expandTowards(velocity)) {
            it !== this && it !is SmallCannonShellEntity && (it.bbWidth >= .3f || it.bbHeight >= .3f)
        }.minByOrNull { it.position().distanceToSqr(position()) } ?: return
        causeExplode(target.position())
        if (target is DestroyableProjectile) performDamage(target, getDamage(), false) else target.discard()
        discard()
    }

    override fun causeExplode(vec3: Vec3) = SentryExplosion.explode(this, vec3, innerBlastRadius, hasExplosionDestroy(), particleType = explosionParticleType)
    override fun afterHitEntity(result: EntityHitResult) {
        SentryExplosion.explode(this, result.location, innerBlastRadius, false, particleType = explosionParticleType)
        discard()
    }
    override fun afterHitBlock(result: BlockHitResult) {
        causeExplode(result.location)
        discard()
    }
}
