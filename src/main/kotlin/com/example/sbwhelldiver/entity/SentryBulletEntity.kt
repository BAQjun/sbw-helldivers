package com.example.sbwhelldiver.entity

import com.atsuishio.superbwarfare.entity.projectile.ProjectileEntity
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.world.entity.EntityType
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn

/** Vanilla spawn packets clip each velocity component to 3.9, corrupting fast tracer angles. */
class SentryBulletEntity(type: EntityType<out ProjectileEntity>, level: Level) :
    ProjectileEntity(type, level), IEntityWithComplexSpawn {
    private var flightGravity = 0.05f
    private var receivedFullMotion = false
    // Snapshot the decoded spawn packet so diagnostics do not confuse a later gravity tick with clipping.
    internal var initialSpawnMotion: Vec3? = null
        private set
    var launchPosition = Vec3.ZERO
        private set

    override fun shoot(x: Double, y: Double, z: Double, velocity: Float, inaccuracy: Float) {
        launchPosition = position()
        super.shoot(x,y,z,velocity,inaccuracy)
    }

    override fun setCustomGravity(gravity: Float) {
        flightGravity = gravity
        super.setCustomGravity(gravity)
    }



    override fun writeSpawnData(buffer: RegistryFriendlyByteBuf) {
        buffer.writeDouble(launchPosition.x)
        buffer.writeDouble(launchPosition.y)
        buffer.writeDouble(launchPosition.z)
        val motion = deltaMovement
        buffer.writeDouble(motion.x)
        buffer.writeDouble(motion.y)
        buffer.writeDouble(motion.z)
        buffer.writeFloat(flightGravity)
        buffer.writeInt(owner?.id ?: 0)
    }

    override fun readSpawnData(buffer: RegistryFriendlyByteBuf) {
        launchPosition = Vec3(buffer.readDouble(),buffer.readDouble(),buffer.readDouble())
        deltaMovement = Vec3(buffer.readDouble(),buffer.readDouble(),buffer.readDouble())
        initialSpawnMotion = deltaMovement
        receivedFullMotion = true
        setCustomGravity(buffer.readFloat())
        owner = level().getEntity(buffer.readInt())
        updateHeading()
    }

    override fun lerpMotion(x: Double, y: Double, z: Double) {
        // Tracking sends a vanilla velocity packet immediately after the Forge spawn packet.
        // It is clipped again; preserve full motion until SBW's precise motion sync takes over.
        if (level().isClientSide && receivedFullMotion &&
            (kotlin.math.abs(x) == 3.9 || kotlin.math.abs(y) == 3.9 || kotlin.math.abs(z) == 3.9)) return
        super.lerpMotion(x,y,z)
    }
}
