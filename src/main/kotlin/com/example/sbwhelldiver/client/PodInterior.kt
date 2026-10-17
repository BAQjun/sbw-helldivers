package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.*
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.GameRenderer
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import kotlin.math.*

/** A depth portal inside the opened iris, retaining foreground occlusion and actual terrain. */
object PodInterior {
    fun render(pod: AbstractHellpodEntity, partial: Float, pose: PoseStack) {
        val openness = when (pod.phase) {
            AbstractHellpodEntity.Phase.DEPLOYING -> ((pod.phaseAge + partial - 3) / 7f).coerceIn(0f, 1f)
            AbstractHellpodEntity.Phase.ACTIVE -> 1f
            AbstractHellpodEntity.Phase.RETRACTING -> ((pod.retractTicks - pod.phaseAge - partial - 2) / 9f).coerceIn(0f, 1f)
            AbstractHellpodEntity.Phase.DESTROYED -> 1f
            AbstractHellpodEntity.Phase.SPENT -> (1f - (pod.phaseAge + partial) / AbstractHellpodEntity.DESTROY_CLOSE_TICKS).coerceIn(0f, 1f)
            else -> 0f
        }
        if (openness <= 0) return
        val target = Minecraft.getInstance().mainRenderTarget
        if (!target.isStencilEnabled) target.enableStencil()
        val bit = 0x80
        val stencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST)
        val cull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        val depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        val blend = GL11.glIsEnabled(GL11.GL_BLEND)
        val depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC)
        val depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        val range = BufferUtils.createDoubleBuffer(2).also { GL11.glGetDoublev(GL11.GL_DEPTH_RANGE, it) }
        val colors = BufferUtils.createByteBuffer(4).also { GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, it) }
        val sf = GL11.glGetInteger(GL11.GL_STENCIL_FUNC)
        val sr = GL11.glGetInteger(GL11.GL_STENCIL_REF)
        val sm = GL11.glGetInteger(GL11.GL_STENCIL_VALUE_MASK)
        val sw = GL11.glGetInteger(GL11.GL_STENCIL_WRITEMASK)
        val fail = GL11.glGetInteger(GL11.GL_STENCIL_FAIL)
        val zfail = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_FAIL)
        val zpass = GL11.glGetInteger(GL11.GL_STENCIL_PASS_DEPTH_PASS)
        val clear = GL11.glGetInteger(GL11.GL_STENCIL_CLEAR_VALUE)
        val shader = RenderSystem.getShader()
        try {
            RenderSystem.setShader(GameRenderer::getPositionColorShader)
            RenderSystem.disableCull()
            RenderSystem.disableBlend()
            RenderSystem.enableDepthTest()
            GL11.glEnable(GL11.GL_STENCIL_TEST)
            GL11.glStencilMask(bit)
            GL11.glClearStencil(0)
            GL11.glClear(GL11.GL_STENCIL_BUFFER_BIT)
            GL11.glStencilFunc(GL11.GL_ALWAYS, bit, bit)
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_REPLACE)
            RenderSystem.colorMask(false, false, false, false)
            RenderSystem.depthMask(false)
            RenderSystem.depthFunc(GL11.GL_LEQUAL)
            val radius = 0.79f * openness
            disk(pose, radius, 0.12f, 0, 0, 0)
            // Clear depth only in the visible aperture; walls/objects in front keep their depth.
            GL11.glStencilMask(0)
            GL11.glStencilFunc(GL11.GL_EQUAL, bit, bit)
            GL11.glStencilOp(GL11.GL_KEEP, GL11.GL_KEEP, GL11.GL_KEEP)
            RenderSystem.depthMask(true)
            RenderSystem.depthFunc(GL11.GL_ALWAYS)
            GL11.glDepthRange(1.0, 1.0)
            RenderSystem.colorMask(true, true, true, true)
            disk(pose, radius, 0.12f, 18, 23, 20)
            GL11.glDepthRange(range[0], range[1])
            RenderSystem.depthFunc(GL11.GL_LEQUAL)
            RenderSystem.colorMask(true, true, true, true)
            disk(pose, 1.05f, -3.7f, 18, 23, 20)
        } finally {
            GL11.glDepthRange(range[0], range[1])
            RenderSystem.depthMask(depthMask)
            RenderSystem.depthFunc(depthFunction)
            RenderSystem.colorMask(colors[0].toInt() != 0, colors[1].toInt() != 0, colors[2].toInt() != 0, colors[3].toInt() != 0)
            GL11.glStencilMask(sw)
            GL11.glStencilFunc(sf, sr, sm)
            GL11.glStencilOp(fail, zfail, zpass)
            GL11.glClearStencil(clear)
            if (!stencil) GL11.glDisable(GL11.GL_STENCIL_TEST)
            if (cull) RenderSystem.enableCull() else RenderSystem.disableCull()
            if (blend) RenderSystem.enableBlend() else RenderSystem.disableBlend()
            if (depth) RenderSystem.enableDepthTest() else RenderSystem.disableDepthTest()
            RenderSystem.setShader { shader }
        }
    }

    private fun disk(pose: PoseStack, radius: Float, height: Float, r: Int, g: Int, b: Int) {
        val builder = Tesselator.getInstance().builder
        val matrix = pose.last().pose()
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR)
        builder.vertex(matrix, 0f, height, 0f).color(r, g, b, 255).endVertex()
        for (i in 0..32) {
            val angle = i * Math.PI * 2 / 32
            builder.vertex(matrix, (sin(angle) * radius).toFloat(), height, (cos(angle) * radius).toFloat())
                .color(r, g, b, 255).endVertex()
        }
        BufferUploader.drawWithShader(builder.end())
    }
}
