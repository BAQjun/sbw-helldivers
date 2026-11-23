package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.network.WarpPackBurstPacket
import com.mojang.blaze3d.vertex.ByteBufferBuilder
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.world.phys.Vec3
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
internal object WarpPackBurstVisual {
    private const val LIFETIME = 14f
    private const val VIOLET = 0xC4A1FF
    private const val WHITE = 0xFFFFFF
    private val pulse = RenderType.entityTranslucentEmissive(warpTexture("warp_pulse"))
    private val flash = RenderType.entityTranslucentEmissive(warpTexture("warp_flash"))
    private val lightning = RenderType.entityTranslucentEmissive(BeaconModel.WHITE)
    private val buffers = MultiBufferSource.immediate(ByteBufferBuilder(4096))
    private var level: ClientLevel? = null
    private val active = ArrayList<Burst>()

    private data class Ray(val direction: Vec3, val side: Vec3, val distance: Double, val kink: Double)
    private data class Burst(val packet: WarpPackBurstPacket, val start: Long, val rays: List<Ray>)

    fun receive(packet: WarpPackBurstPacket) {
        val world = Minecraft.getInstance().level ?: return
        switchLevel(world)
        val random = Random(packet.origin.hashCode() xor world.gameTime.toInt())
        val rays = List(11) {
            val direction = Vec3(random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0), random.nextDouble(-1.0, 1.0)).normalize()
            val side = direction.cross(Vec3(0.0, 1.0, 0.0)).normalize()
            Ray(direction, side, random.nextDouble(.82, 1.62), random.nextDouble(-.16, .16))
        }
        active.removeAll { world.gameTime - it.start >= LIFETIME }
        if (active.size >= 32) active.removeAt(0)
        active.add(Burst(packet, world.gameTime, rays))
    }

    private fun switchLevel(world: ClientLevel?) {
        if (world === level) return
        active.clear()
        level = world
    }

    @SubscribeEvent
    fun render(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_PARTICLES || ShaderPackCompat.shadowPass()) return
        val minecraft = Minecraft.getInstance()
        val world = minecraft.level
        switchLevel(world)
        if (world == null || active.isEmpty()) return
        val partial = event.partialTick.getGameTimeDeltaPartialTick(false)
        active.removeAll { world.gameTime - it.start >= LIFETIME }
        if (active.isEmpty()) return

        val eye = event.camera.position
        val rotation = event.camera.rotation()
        val viewRight = rotation.transform(Vector3f(1f, 0f, 0f)).let { Vec3(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
        val viewUp = rotation.transform(Vector3f(0f, 1f, 0f)).let { Vec3(it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
        val pose = event.poseStack.last()

        fun billboard(type: RenderType, point: Vec3, size: Double, angle: Double, opacity: Float) {
            val right = viewRight.scale(cos(angle) * size).add(viewUp.scale(sin(angle) * size))
            val up = viewUp.scale(cos(angle) * size).subtract(viewRight.scale(sin(angle) * size))
            val vertices = buffers.getBuffer(type)
            warpQuad(vertices, pose, point, right, up, WHITE, opacity)
        }

        fun streak(from: Vec3, to: Vec3, width: Double, color: Int, opacity: Float) {
            val side = to.subtract(from).cross(from.add(to).scale(.5)).normalize().scale(width)
            val vertices = buffers.getBuffer(lightning)
            for (point in arrayOf(from.add(side), from.subtract(side), to.subtract(side), to.add(side))) {
                warpVertex(vertices, pose, point, .5f, .5f, color, opacity)
            }
        }

        // Flush each texture once so the bright flashes layer over the dark departure.
        for ((packet, start) in active) {
            val age = (world.gameTime - start + partial).coerceAtLeast(0f)
            val departure = packet.origin.subtract(eye)
            if (departure.lengthSqr() > .65) {
                val expand = (age / 6f).coerceAtMost(1f)
                val fade = (1f - (age - 3f).coerceAtLeast(0f) / 11f).coerceAtLeast(0f)
                billboard(pulse, departure, .36 + 1.02 * expand, age * .055, fade)
            }
        }
        buffers.endBatch(pulse)

        for ((packet, start) in active) {
            val age = (world.gameTime - start + partial).coerceAtLeast(0f)
            val departure = packet.origin.subtract(eye)
            val arrival = packet.destination.subtract(eye)
            val releaseAge = age - 2f
            val opening = if (releaseAge < 0f) 0f else (1f - releaseAge / 4f).coerceAtLeast(0f)
            val closing = (1f - age / 6f).coerceAtLeast(0f)
            if (opening > 0f && departure.lengthSqr() > .65) {
                billboard(flash, departure, .62 + releaseAge * .15, -.2, opening)
            }
            if (closing <= 0f) continue
            if (arrival.lengthSqr() > .65) {
                billboard(flash, arrival, .37 + age * .085, PI * .11, closing * .88f)
            } else if (minecraft.options.cameraType.isFirstPerson && event.camera.entity === minecraft.player) {
                // The world-space core can intersect the local camera immediately after arrival.
                val forward = packet.destination.subtract(packet.origin).normalize()
                val glint = forward.scale(.96).subtract(viewUp.scale(.20))
                billboard(flash, glint, .20 + age * .032, PI * .11, closing * .31f)
            }
        }
        buffers.endBatch(flash)

        for ((packet, start, rays) in active) {
            val age = (world.gameTime - start + partial).coerceAtLeast(0f)
            val departure = packet.origin.subtract(eye)
            val arrival = packet.destination.subtract(eye)
            for ((index, ray) in rays.withIndex()) {
                val endpoint = if (index < 8) departure else arrival
                if (endpoint.lengthSqr() <= .65) continue
                val lightningAge = age - if (index < 8) 1.4f else 0f
                if (lightningAge in 0f..<4f) {
                    val reach = ray.distance * (.57 + lightningAge * .185)
                    val inner = endpoint.add(ray.direction.scale(reach * .27))
                    val elbow = endpoint.add(ray.direction.scale(reach * .65)).add(ray.side.scale(ray.kink))
                    val tip = endpoint.add(ray.direction.scale(reach))
                    val alpha = 1f - lightningAge / 4f
                    streak(inner, elbow, .033, VIOLET, alpha * .85f)
                    streak(elbow, tip, .025, VIOLET, alpha * .85f)
                    streak(inner, elbow, .012, WHITE, alpha)
                    streak(elbow, tip, .009, WHITE, alpha)
                }
                val spark = endpoint.add(ray.direction.scale(ray.distance * (.65 + age * .065)))
                val tail = spark.subtract(ray.direction.scale(.08 + .055 * sin(index.toDouble())))
                streak(spark, tail, .017, VIOLET, (1f - age / LIFETIME).coerceAtLeast(0f))
            }
        }
        buffers.endBatch(lightning)
    }
}
