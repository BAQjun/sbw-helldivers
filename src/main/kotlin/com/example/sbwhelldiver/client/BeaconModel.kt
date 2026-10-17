package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.google.gson.JsonParser
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import org.joml.Matrix4f
import org.joml.Vector3f

/** Cached triangle poses preserve the extracted mesh and articulated hinge motion. */
object BeaconModel : ResourceManagerReloadListener {
    val TEXTURE = ResourceLocation(MOD_ID, "textures/mesh/stratagem_beacon.png")
    val WHITE = ResourceLocation(MOD_ID, "textures/mesh/white.png")
    private data class Triangle(val positions: Array<Vector3f>, val uv: Array<FloatArray>, val normal: Vector3f, val glow: Boolean)
    private var closed = emptyList<Triangle>()
    private var deploy = emptyList<List<Triangle>>()
    private var active = emptyList<Triangle>()
    var ready = false
        private set

    override fun onResourceManagerReload(resources: ResourceManager) {
        val json = resources.openAsReader(ResourceLocation(MOD_ID, "models/mesh/stratagem_beacon.mesh.json")).use { JsonParser.parseReader(it).asJsonObject }
        val vertices = json.getAsJsonArray("vertices").map { a -> a.asJsonArray.let { Vector3f(it[0].asFloat / 16, it[1].asFloat / 16, it[2].asFloat / 16) } }
        val joints = json.getAsJsonArray("joint").map { it.asInt }
        val uv = json.getAsJsonArray("uv").map { a -> a.asJsonArray.let { floatArrayOf(it[0].asFloat / 256, it[1].asFloat / 128) } }
        val faces = json.getAsJsonArray("faces")
        fun pose(frame: com.google.gson.JsonObject): List<Triangle> {
            val matrices = frame.getAsJsonObject("matrices").entrySet().associate { (id, raw) ->
                val rows = raw.asJsonArray
                val floats = FloatArray(16) { i -> rows[i % 4].asJsonArray[i / 4].asFloat }
                id.toInt() to Matrix4f().set(floats)
            }
            val points = vertices.mapIndexed { index, vector -> matrices.getValue(joints[index]).transformPosition(Vector3f(vector)) }
            return faces.mapNotNull { raw ->
                val face = raw.asJsonObject
                val indices = face.getAsJsonArray("vertices").map { it.asInt }
                val positions = indices.map { Vector3f(points[it]) }.toTypedArray()
                val normal = Vector3f(positions[1]).sub(positions[0]).cross(Vector3f(positions[2]).sub(positions[0]))
                if (normal.lengthSquared() < 1e-15f) return@mapNotNull null
                normal.normalize()
                val coordinates = if (face.has("uv")) face.getAsJsonArray("uv").map { a -> a.asJsonArray.let { floatArrayOf(it[0].asFloat / 256, it[1].asFloat / 128) } }.toTypedArray()
                    else indices.map { uv[it] }.toTypedArray()
                Triangle(positions, coordinates, normal, face["emissive"].asBoolean)
            }
        }
        val clips = json.getAsJsonObject("clips")
        closed = pose(clips.getAsJsonObject("closed").getAsJsonArray("frames")[0].asJsonObject)
        deploy = clips.getAsJsonObject("deploy").getAsJsonArray("frames").map { pose(it.asJsonObject) }
        active = pose(clips.getAsJsonObject("active").getAsJsonArray("frames")[0].asJsonObject)
        ready = true
    }

    fun render(pose: PoseStack, buffers: MultiBufferSource, light: Int, openingSeconds: Float?, activated: Boolean, color: Int) {
        val triangles = if (openingSeconds == null) closed else if (openingSeconds >= 1) active
            else deploy.getOrNull((openingSeconds.coerceAtLeast(0f) * 30).toInt()) ?: closed
        val transform = pose.last()
        for (glowPass in listOf(false, true)) {
          val type = if (glowPass && activated) RenderType.eyes(WHITE)
              else RenderType.entityCutoutNoCull(if (glowPass) WHITE else TEXTURE)
          val consumer = buffers.getBuffer(type)
          for (triangle in triangles) {
            if (triangle.glow != glowPass) continue
            val r = if (!triangle.glow) 1f else if (activated) ((color shr 16) and 255) / 255f else 0.11f
            val g = if (!triangle.glow) 1f else if (activated) ((color shr 8) and 255) / 255f else 0.15f
            val b = if (!triangle.glow) 1f else if (activated) (color and 255) / 255f else 0.16f
            for (index in intArrayOf(0, 1, 2, 2)) {
                val position = triangle.positions[index]
                val uv = triangle.uv[index]
                consumer.vertex(transform.pose(), position.x, position.y, position.z).color(r, g, b, 1f)
                    .uv(uv[0], uv[1]).overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(if (triangle.glow && activated) 0xF000F0 else light)
                    .normal(transform.normal(), triangle.normal.x, triangle.normal.y, triangle.normal.z).endVertex()
            }
          }
        }
    }
}
