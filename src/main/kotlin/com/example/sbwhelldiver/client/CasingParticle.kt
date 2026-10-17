package com.example.sbwhelldiver.client

import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.Particle
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.particle.TextureSheetParticle
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.util.Mth
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.cos
import kotlin.math.sin

/** Small 3D spent cases: spin in flight, collide with blocks and rest sideways for five seconds. */
class CasingParticle(level: ClientLevel, x: Double, y: Double, z: Double,
    vx: Double, vy: Double, vz: Double, sprites: SpriteSet, private val kind: Kind) : TextureSheetParticle(level, x, y, z) {
    enum class Kind(val radius: Float, val length: Float) {
        MACHINE_GUN(0.013f, 0.105f), GATLING(0.017f, 0.14f), CANNON(0.033f, 0.29f)
    }
    private var pitch = if (kind == Kind.MACHINE_GUN) 90f else random.nextFloat() * 360
    private var previousPitch = pitch
    private var yaw = if (kind == Kind.MACHINE_GUN)
        Math.toDegrees(kotlin.math.atan2(-vz, vx)).toFloat() else random.nextFloat() * 360
    private var previousYaw = yaw
    private var landed = false
    private val launchX = x
    private val launchY = y
    private val launchZ = z

    init {
        xd = vx; yd = vy; zd = vz
        lifetime = 100
        gravity = 0.8f
        friction = 0.94f
        setSize(kind.radius * 2, kind.radius * 2)
        pickSprite(sprites)
        if (probe) created[kind] = (created[kind] ?: 0) + 1
    }

    override fun tick() {
        previousPitch = pitch; previousYaw = yaw
        super.tick()
        if (probe && kind == Kind.MACHINE_GUN && age == 2) {
            val horizontal = kotlin.math.hypot(x-launchX,z-launchZ)
            check(horizontal > 0.2) { "Machine gun casing failed to clear its receiver: $horizontal" }
            machineGunThrown++
        }
        if (probe && kind == Kind.CANNON && age == 6) {
            val horizontal = kotlin.math.hypot(x-launchX,z-launchZ)
            check(!landed && y-launchY > 0.12 && horizontal > 0.2) {
                "Cannon case failed to leave its port: rise=${y-launchY}, horizontal=$horizontal"
            }
            cannonThrown++
        }
        if (onGround) {
            pitch = 90f
            if (!landed) {
                landed = true
                if (probe) grounded[kind] = (grounded[kind] ?: 0) + 1
            }
        } else {
            pitch += 31f
            yaw += 13f
        }
    }

    override fun getRenderType(): ParticleRenderType = ParticleRenderType.PARTICLE_SHEET_OPAQUE

    override fun render(buffer: VertexConsumer, camera: Camera, partial: Float) {
        if (probe) rendered.add(kind)
        val rotation = Quaternionf().rotateY(Mth.lerp(partial, previousYaw, yaw) * Mth.DEG_TO_RAD)
            .rotateX(Mth.lerp(partial, previousPitch, pitch) * Mth.DEG_TO_RAD)
        val center = Vector3f((Mth.lerp(partial.toDouble(), xo, x) - camera.position.x).toFloat(),
            (Mth.lerp(partial.toDouble(), yo, y) - camera.position.y +
                if (kind == Kind.MACHINE_GUN && !landed) 0.0 else kind.radius + 0.002).toFloat(),
            (Mth.lerp(partial.toDouble(), zo, z) - camera.position.z).toFloat())
        val light = getLightColor(partial)
        val top = kind.length / 2
        val bottom = -top
        fun vertex(point: Vector3f, u: Float, v: Float, shade: Float) {
            point.rotate(rotation).add(center)
            buffer.vertex(point.x.toDouble(), point.y.toDouble(), point.z.toDouble()).uv(u, v)
                .color(shade, shade, shade, 1f).uv2(light).endVertex()
        }
        fun face(a: Vector3f, b: Vector3f, c: Vector3f, d: Vector3f, shade: Float) {
            vertex(a, u0, v1, shade); vertex(d, u0, v0, shade)
            vertex(c, u1, v0, shade); vertex(b, u1, v1, shade)
        }
        // An octagonal wall and a recessed dark mouth keep the silhouette recognizable at MC scale.
        for (side in 0..7) {
            val angle = side * Math.PI / 4
            val next = (side + 1) * Math.PI / 4
            val ax = cos(angle).toFloat() * kind.radius; val az = sin(angle).toFloat() * kind.radius
            val bx = cos(next).toFloat() * kind.radius; val bz = sin(next).toFloat() * kind.radius
            face(Vector3f(ax,bottom,az), Vector3f(bx,bottom,bz), Vector3f(bx,top,bz), Vector3f(ax,top,az),
                if (side % 2 == 0) 1f else 0.8f)
            face(Vector3f(ax,top,az), Vector3f(bx,top,bz), Vector3f(bx*.65f,top,bz*.65f), Vector3f(ax*.65f,top,az*.65f),1f)
            face(Vector3f(ax*.65f,top-kind.radius*.5f,az*.65f), Vector3f(bx*.65f,top-kind.radius*.5f,bz*.65f),
                Vector3f(0f,top-kind.radius*.5f,0f), Vector3f(0f,top-kind.radius*.5f,0f),0.25f)
            face(Vector3f(bx,bottom,bz), Vector3f(ax,bottom,az), Vector3f(0f,bottom,0f), Vector3f(0f,bottom,0f),0.7f)
        }
    }

    class Provider(private val sprites: SpriteSet, private val kind: Kind) : ParticleProvider<SimpleParticleType> {
        override fun createParticle(type: SimpleParticleType, level: ClientLevel, x: Double, y: Double, z: Double,
            vx: Double, vy: Double, vz: Double): Particle = CasingParticle(level,x,y,z,vx,vy,vz,sprites,kind)
    }

    companion object {
        private val probe = java.lang.Boolean.getBoolean("sbw_helldiver.clientSmokeTest")
        val created = mutableMapOf<Kind, Int>()
        val grounded = mutableMapOf<Kind, Int>()
        val rendered = mutableSetOf<Kind>()
        var machineGunThrown = 0
            private set
        var cannonThrown = 0
            private set
    }
}
