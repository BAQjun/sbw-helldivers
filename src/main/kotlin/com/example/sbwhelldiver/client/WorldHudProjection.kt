package com.example.sbwhelldiver.client

import net.neoforged.neoforge.client.event.RenderLevelStageEvent
import org.joml.Matrix4f

/** Project camera-relative world positions with the same matrices as this frame.
 * NeoForge 1.21 already includes the OpenGL camera convention in modelViewMatrix;
 * adding a half turn hides forward marks and reverses emplacement aim offsets.
 * Using the event also preserves camera roll, zoom and shader/mod adjustments.
 */
object WorldHudProjection {
    fun matrix(event: RenderLevelStageEvent): Matrix4f =
        Matrix4f(event.projectionMatrix).mul(event.modelViewMatrix)
}
