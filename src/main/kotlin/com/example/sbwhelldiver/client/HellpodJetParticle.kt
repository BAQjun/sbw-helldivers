package com.example.sbwhelldiver.client

import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Camera
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.ParticleProvider
import net.minecraft.client.particle.ParticleRenderType
import net.minecraft.client.particle.SpriteSet
import net.minecraft.client.particle.TextureSheetParticle
import net.minecraft.core.particles.SimpleParticleType
import net.minecraft.util.Mth
import org.joml.Vector3f
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import net.minecraft.world.phys.Vec3

/** Full-bright exhaust following each nozzle's outward/downward axis. */
class HellpodJetParticle(level: ClientLevel, x: Double, y: Double, z: Double,
    vx: Double, vy: Double, vz: Double, sprites: SpriteSet,
    private val anchor: AbstractHellpodEntity? = null, private val offset: Vec3 = Vec3.ZERO,
    private val sample: Int = 0, private val direction: Vec3 = Vec3(0.0, -1.0, 0.0)) : TextureSheetParticle(level, x, y, z) {
    init {
        xd = vx; yd = vy; zd = vz
        lifetime = 4 + random.nextInt(3)
        friction = 0.98f; gravity = 0f
        setSize(0.03f, 0.03f)
        pickSprite(sprites)
        quadSize = 0.15f + random.nextFloat() * 0.025f
        if (probe) created++
    }
    override fun tick() {
        if (anchor != null) {
            if (anchor.isRemoved || anchor.phase != AbstractHellpodEntity.Phase.FALLING || age++ >= lifetime) { remove(); return }
            val local = offset.yRot(-anchor.yRot * Mth.DEG_TO_RAD)
            // Keep the flame root attached to the outlet; only the outer plume travels.
            val travel = if (sample == 0) 0.005 else sample * 0.25 + age * 0.10
            val displaced = local.add(direction.yRot(-anchor.yRot * Mth.DEG_TO_RAD).scale(travel))
            xo = anchor.xo + displaced.x; yo = anchor.yo + displaced.y; zo = anchor.zo + displaced.z
            setPos(anchor.x + displaced.x, anchor.y + displaced.y, anchor.z + displaced.z)
            alpha = (1f - age.toFloat() / lifetime).coerceAtLeast(0f)
            return
        }
        super.tick()
        alpha = (1f - age.toFloat() / lifetime).coerceAtLeast(0f)
        if (onGround) remove()
    }
    override fun getLightColor(partial: Float) = 0xF000F0
    override fun getRenderType() = ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT
    override fun render(buffer: VertexConsumer, camera: Camera, partial: Float) {
        if (probe) rendered++
        val axis = if (anchor != null) direction.yRot(-anchor.yRot * Mth.DEG_TO_RAD) else direction
        val right = Vector3f(axis.x.toFloat(), axis.y.toFloat(), axis.z.toFloat())
            .cross(Vector3f(0f, 0f, -1f).rotate(camera.rotation()))
        if (right.lengthSquared() < 1e-5f) right.set(1f, 0f, 0f) else right.normalize()
        right.mul(quadSize)
        val px = Mth.lerp(partial.toDouble(), xo, x) - camera.position.x
        val py = Mth.lerp(partial.toDouble(), yo, y) - camera.position.y
        val pz = Mth.lerp(partial.toDouble(), zo, z) - camera.position.z
        val length = (1.35 + alpha * 0.95) * if (sample == 0) 1.0 else 0.75
        fun point(side: Int, lower: Boolean, u: Float, v: Float) {
            val distance = if (lower) length else 0.0
            buffer.vertex(px + right.x * side + axis.x * distance, py + right.y * side + axis.y * distance, pz + right.z * side + axis.z * distance)
                .uv(u,v).color(1f, 0.95f, 0.72f, alpha).uv2(0xF000F0).endVertex()
        }
        // The sprite's solid base sits at the nozzle; its tapered tip points away.
        point(-1,true,u0,v0);point(-1,false,u0,v1);point(1,false,u1,v1);point(1,true,u1,v0)
    }
    class Provider(private val sprites: SpriteSet) : ParticleProvider<SimpleParticleType> {
        init { registeredSprites = sprites }
        override fun createParticle(type: SimpleParticleType, level: ClientLevel, x: Double, y: Double, z: Double,
            vx: Double, vy: Double, vz: Double) = HellpodJetParticle(level,x,y,z,vx,vy,vz,sprites)
    }
    companion object {
        private var registeredSprites: SpriteSet? = null
        fun emit(pod: AbstractHellpodEntity, offset: Vec3, sample: Int, direction: Vec3) {
            val sprites = registeredSprites ?: return
            val level = pod.level() as? ClientLevel ?: return
            val p = pod.position().add(offset.yRot(-pod.yRot * Mth.DEG_TO_RAD))
                .add(direction.yRot(-pod.yRot * Mth.DEG_TO_RAD).scale(if (sample == 0) 0.005 else sample * 0.25))
            val particle = HellpodJetParticle(level,p.x,p.y,p.z,0.0,-0.35,0.0,sprites,pod,offset,sample,direction)
            particle.xo = pod.xo + p.x - pod.x
            particle.yo = pod.yo + p.y - pod.y
            particle.zo = pod.zo + p.z - pod.z
            net.minecraft.client.Minecraft.getInstance().particleEngine.add(particle)
        }
        fun emitSmoke(pod: AbstractHellpodEntity, offset: Vec3, direction: Vec3) {
            val axis = direction.yRot(-pod.yRot * Mth.DEG_TO_RAD)
            val base = pod.position().add(offset.yRot(-pod.yRot * Mth.DEG_TO_RAD))
            val drift = axis.scale(0.28).add(0.0, (pod.y - pod.yo).coerceAtMost(0.0) * 0.9, 0.0)
            val engine = net.minecraft.client.Minecraft.getInstance().particleEngine
            val whiteOrigin = base.add(axis.scale(0.8))
            val white = engine.createParticle(net.minecraft.core.particles.ParticleTypes.CLOUD,
                whiteOrigin.x, whiteOrigin.y, whiteOrigin.z, drift.x, drift.y, drift.z)
            white?.let {
                it.setColor(0.96f, 0.96f, 0.94f)
                it.scale(1.25f)
                it.lifetime = 20 + pod.level().random.nextInt(7)
                if (probe) whiteSmoke++
            }
            val blackOrigin = base.add(axis.scale(1.25))
            val black = engine.createParticle(net.minecraft.core.particles.ParticleTypes.LARGE_SMOKE,
                blackOrigin.x, blackOrigin.y, blackOrigin.z, drift.x * 1.1, drift.y, drift.z * 1.1)
            black?.let {
                it.setColor(0.10f, 0.11f, 0.10f)
                it.scale(1.15f)
                it.lifetime = 22 + pod.level().random.nextInt(7)
                if (probe) blackSmoke++
            }
        }
        private val probe get() = java.lang.Boolean.getBoolean("sbw_helldiver.beaconSmokeTest")
        var created = 0
        var rendered = 0
        var whiteSmoke = 0
        var blackSmoke = 0
    }
}
