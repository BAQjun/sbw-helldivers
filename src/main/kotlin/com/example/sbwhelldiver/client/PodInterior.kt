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
    fun render(pod: AbstractHellpodEntity, partial: Float, pose: PoseStack,
        buffers: net.minecraft.client.renderer.MultiBufferSource, light: Int) {
        // A depth portal is not a shadow caster and must never clear pack depth.
        if (ShaderPackCompat.shadowPass()) return
        val openness = when (pod.phase) {
            AbstractHellpodEntity.Phase.DEPLOYING -> ((pod.phaseAge + partial - 3) / 7f).coerceIn(0f, 1f)
            AbstractHellpodEntity.Phase.ACTIVE -> 1f
            AbstractHellpodEntity.Phase.RETRACTING -> ((pod.retractTicks - pod.phaseAge - partial - 2) / 9f).coerceIn(0f, 1f)
            AbstractHellpodEntity.Phase.DESTROYED -> 1f
            AbstractHellpodEntity.Phase.SPENT -> (1f - (pod.phaseAge + partial) / AbstractHellpodEntity.DESTROY_CLOSE_TICKS).coerceIn(0f, 1f)
            else -> 0f
        }
        if (openness <= 0) return
        if (ShaderPackCompat.active()) {
            // Shader packs replace the immediate position/color program. The
            // stencil/depth-range portal can then cover unrelated world pixels.
            // Keep the aperture dark using the pack's normal entity material,
            // without changing its framebuffer, stencil, or depth range.
            aperture(pose, openness, buffers, light)
            return
        }
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

    private val apertureTexture = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("sbw_helldiver", "textures/mesh/white.png")
    private fun aperture(pose: PoseStack, openness: Float,
        buffers: net.minecraft.client.renderer.MultiBufferSource, light: Int) {
        val out = buffers.getBuffer(net.minecraft.client.renderer.RenderType.entityCutoutNoCull(apertureTexture))
        val matrix = pose.last().pose()
        val normal = pose.last().normal()
        val radius = .79f * openness
        fun vertex(x: Float, z: Float) {
            out.vertex(matrix, x, .12f, z).color(18, 23, 20, 255)
                .uv(.5f + x / 2, .5f + z / 2)
                .overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                .uv2(light).normal(normal, 0f, 1f, 0f).endVertex()
        }
        for (i in 0 until 32) {
            val a = i * Math.PI * 2 / 32
            val b = (i + 1) * Math.PI * 2 / 32
            vertex(0f, 0f)
            vertex((sin(a) * radius).toFloat(), (cos(a) * radius).toFloat())
            vertex((sin(b) * radius).toFloat(), (cos(b) * radius).toFloat())
            vertex(0f, 0f)
        }
    }

    private fun disk(pose: PoseStack, radius: Float, height: Float, r: Int, g: Int, b: Int) {
        val builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR)
        val matrix = pose.last().pose()
        builder.vertex(matrix, 0f, height, 0f).color(r, g, b, 255).endVertex()
        for (i in 0..32) {
            val angle = i * Math.PI * 2 / 32
            builder.vertex(matrix, (sin(angle) * radius).toFloat(), height, (cos(angle) * radius).toFloat())
                .color(r, g, b, 255).endVertex()
        }
        BufferUploader.drawWithShader(builder.buildOrThrow())
    }
}
