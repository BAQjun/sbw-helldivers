package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block

/** A live, conservative solid volume. Geometry is retained and digging exposes it again. */
object TerrainMeshOcclusion {
    fun volume(pod: AbstractHellpodEntity): DoubleArray? {
        if (pod.phase == AbstractHellpodEntity.Phase.FALLING || ShaderPackCompat.shadowPass()) return null
        val level = pod.level()
        val origin = pod.blockPosition()
        var start = 0
        var count = 0
        var bestStart = 0
        var bestCount = 0
        val at = BlockPos.MutableBlockPos()
        // Read current blocks on each main render pass, even when game time is paused.
        // A small hole invalidates the entire layer instead of clipping exposed faces.
        for (y in origin.y - 7 until origin.y) {
            var full = true
            search@ for (x in origin.x - 2..origin.x + 2) {
                for (z in origin.z - 2..origin.z + 2) {
                    at.set(x, y, z)
                    if (!level.hasChunk(x shr 4, z shr 4)) {
                        full = false
                        break@search
                    }
                    val state = level.getBlockState(at)
                    if (!state.canOcclude() || !Block.isShapeFullBlock(state.getOcclusionShape(level, at))) {
                        full = false
                        break@search
                    }
                }
            }
            if (full) {
                if (count == 0) start = y
                count++
                if (count > bestCount) {
                    bestStart = start
                    bestCount = count
                }
            } else count = 0
        }
        if (bestCount == 0) return null
        val box = doubleArrayOf(
            (origin.x - 2).toDouble(), bestStart.toDouble(), (origin.z - 2).toDouble(),
            (origin.x + 3).toDouble(), (bestStart + bestCount).toDouble(), (origin.z + 3).toDouble()
        )
        val eye = Minecraft.getInstance().gameRenderer.mainCamera.position
        // Spectators inside solid terrain can inspect the complete retained hull.
        if (eye.x in box[0]..box[3] && eye.y in box[1]..box[4] && eye.z in box[2]..box[5]) return null
        // Since 1.21 entity poses are camera-relative world coordinates. Camera
        // rotation is applied separately through the render system's model-view
        // matrix; undoing it here would classify visible support parts as buried.
        return doubleArrayOf(
            box[0] - eye.x, box[1] - eye.y, box[2] - eye.z,
            box[3] - eye.x, box[4] - eye.y, box[5] - eye.z
        )
    }
}
