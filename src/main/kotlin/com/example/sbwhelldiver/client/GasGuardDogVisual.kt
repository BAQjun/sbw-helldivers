package com.example.sbwhelldiver.client



import com.example.sbwhelldiver.MOD_ID

import com.example.sbwhelldiver.entity.GuardDogEntity
import com.example.sbwhelldiver.entity.GuardDogGas

import com.mojang.blaze3d.vertex.PoseStack

import net.minecraft.client.Minecraft

import net.minecraft.client.multiplayer.ClientLevel

import net.minecraft.client.renderer.MultiBufferSource

import net.minecraft.client.renderer.RenderType

import net.minecraft.client.renderer.culling.Frustum

import net.minecraft.client.renderer.texture.OverlayTexture

import net.minecraft.resources.ResourceLocation

import net.minecraft.util.Mth

import net.minecraft.world.phys.AABB

import net.minecraft.world.phys.Vec3

import java.util.WeakHashMap

import kotlin.math.sin



object GasGuardDogVisual {

    const val MIST_RADIUS = 2.8f

    const val MIST_HEIGHT = 1.6f

    const val MIST_DEPTH = 2.65f

    private const val SPRAY_EDGE = .85

    private const val SPRAY_DROP = .40

    private const val EMISSION_INTERVAL = GuardDogGas.CLOUD_INTERVAL_TICKS.toLong()

    private const val MIST_LIFETIME = GuardDogGas.CLOUD_LIFETIME_TICKS.toLong()

    private val clouds = WeakHashMap<GuardDogEntity, ArrayDeque<Cloud>>()



    private class Cloud(val center: Vec3, val flow: Vec3, val born: Long, var lastEmission: Long = born)



    data class MistVolume(val center: Vec3, val flow: Vec3, val terrainKey: Any, val density: Float, val age: Float)

    data class SprayVolume(val center: Vec3, val axis: Vec3, val length: Float, val bounds: AABB)



    private val jetType = RenderType.entityTranslucent(ResourceLocation(MOD_ID, "textures/effect/gas_jet.png"))



    private fun direction(dog: GuardDogEntity, partial: Float) = Vec3.directionFromRotation(

        Mth.lerp(partial, dog.xRotO, dog.xRot), Mth.rotLerp(partial, dog.yRotO, dog.yRot))



    fun render(dog: GuardDogEntity, partial: Float, pose: PoseStack, buffers: MultiBufferSource, light: Int) {

        if (!dog.gasSpraying || ShaderPackCompat.shadowPass()) return

        val start = GuardDogVisual.firingMuzzle(dog, partial)

        val axis = direction(dog, partial)

        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position

        val side = axis.cross(camera.subtract(start)).normalize()

        if (side.lengthSqr() < 1e-6) return



        val localStart = start.subtract(dog.getPosition(partial))

        val localEnd = localStart.add(axis.scale(dog.gasSprayLength.toDouble()))

        val age = dog.tickCount + partial

        val width = .082 + .012 * sin(age * 1.4f)

        val root = side.scale(.028)

        val tip = side.scale(width)

        val consumer = buffers.getBuffer(jetType)

        fun vertex(point: Vec3, u: Float, v: Float) {

            consumer.vertex(pose.last().pose(), point.x.toFloat(), point.y.toFloat(), point.z.toFloat())

                .color(107, 153, 71, 136).uv(u, v)

                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.last().normal(), 0f, 1f, 0f).endVertex()

        }

        vertex(localStart.subtract(root), 0f, 0f)

        vertex(localEnd.subtract(tip), 1f, 0f)

        vertex(localEnd.add(tip), 1f, 1f)

        vertex(localStart.add(root), 0f, 1f)

    }



    @Synchronized

    fun tick(dog: GuardDogEntity) {

        if (!dog.gasSpraying) return

        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position

        if (camera.distanceToSqr(dog.position()) > 64.0 * 64.0) return

        val now = dog.level().gameTime

        val trail = clouds.getOrPut(dog) { ArrayDeque(4) }

        trail.removeAll { now - it.lastEmission >= MIST_LIFETIME }

        val last = trail.maxOfOrNull { it.lastEmission }

        if (last != null && now - last < EMISSION_INTERVAL) return



        val axis = direction(dog, 1f)

        val offset = sin((now % 61) * 2.4) * .52

        val forward = Vec3(axis.x, 0.0, axis.z)

        val side = Vec3(-axis.z, 0.0, axis.x)

        val flow = forward.add(side.scale(offset))

        val center = GuardDogVisual.firingMuzzle(dog, 1f)

            .add(axis.scale(dog.gasSprayLength * (.91 + .08 * offset))).add(side.scale(offset * .36))

        val nearby = trail.lastOrNull { it.center.distanceToSqr(center) <= GuardDogGas.CLOUD_MERGE_DISTANCE_SQR }
        if (nearby != null) nearby.lastEmission = now
        else trail.addLast(Cloud(center, flow, now))

    }



    @Synchronized

    fun mistVolumes(level: ClientLevel, partial: Float, camera: Vec3, frustum: Frustum): List<MistVolume> {

        val now = level.gameTime

        val volumes = ArrayList<MistVolume>()

        val active = clouds.entries.iterator()

        while (active.hasNext()) {

            val (dog, trail) = active.next()

            trail.removeAll { now - it.lastEmission >= MIST_LIFETIME }

            if (dog.level() !== level || trail.isEmpty()) {

                active.remove()

                continue

            }

            for (cloud in trail) {

                val center = cloud.center

                if (center.distanceToSqr(camera) > 64.0 * 64.0) continue

                if (!frustum.isVisible(AABB(center.x - MIST_RADIUS, center.y - MIST_DEPTH, center.z - MIST_RADIUS,

                        center.x + MIST_RADIUS, center.y + MIST_HEIGHT, center.z + MIST_RADIUS))) continue

                val age = (now - cloud.born + partial).coerceAtLeast(0f)

                val fill = (age / 5f).coerceAtMost(1f)

                val tail = 1f - ((now - cloud.lastEmission + partial - 20f) / (MIST_LIFETIME - 20f)).coerceIn(0f, 1f)

                volumes += MistVolume(center, cloud.flow, cloud, fill * fill * (3f - 2f * fill) * tail, age / 20f)

            }

        }

        return volumes.sortedByDescending { it.center.distanceToSqr(camera) }

    }



    @Synchronized

    fun sprayVolumes(level: ClientLevel, partial: Float, camera: Vec3, frustum: Frustum): List<SprayVolume> {

        val volumes = ArrayList<SprayVolume>()

        for (dog in clouds.keys) {

            if (dog.level() !== level || !dog.gasSpraying || dog.distanceToSqr(camera) > 64.0 * 64.0) continue

            val length = dog.gasSprayLength

            val start = GuardDogVisual.firingMuzzle(dog, partial)

            val axis = direction(dog, partial)

            val end = start.add(axis.scale(length.toDouble()))

            val bounds = AABB(start, end).inflate(SPRAY_EDGE).expandTowards(0.0, -SPRAY_DROP, 0.0)

            if (frustum.isVisible(bounds)) volumes += SprayVolume(start.lerp(end, .5), axis, length, bounds)

        }

        return volumes

    }

}
