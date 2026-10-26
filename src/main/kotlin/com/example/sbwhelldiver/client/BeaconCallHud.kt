package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.StratagemMarker
import com.example.sbwhelldiver.stratagem.Stratagems
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.RenderLevelStageEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.joml.Quaternionf
import org.joml.Matrix4f
import org.joml.Vector4f
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/** Screen-space label at the beam's intersection with the crosshair's horizontal row. */
@Mod.EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object BeaconCallHud : ResourceManagerReloadListener {
    const val ICON_SIZE = 12
    const val AIM_MARGIN = 32
    const val TITLE_COLOR = 0xFFDB4A
    data class Label(val marker: StratagemMarker, val x: Int, val y: Int, val iconSize: Int, val distance: Double)
    var label: Label? = null
        private set
    private var projectionDiagnostics = 0
    private val whiteIcons = mutableMapOf<String, ResourceLocation>()
    var monochromeIconCount = 0
        private set

    override fun onResourceManagerReload(manager: ResourceManager) {
        val mc = Minecraft.getInstance()
        whiteIcons.values.forEach { mc.textureManager.release(it) }
        whiteIcons.clear(); monochromeIconCount = 0
        // Derive a white silhouette only for this label; preserve coloured inventory/loadout cards.
        Stratagems.definitions.forEach { def ->
            manager.getResource(def.icon).orElseThrow().open().use { stream ->
                NativeImage.read(stream).use { source ->
                    val mask = NativeImage(source.width, source.height, false)
                    for (y in 0 until source.height) for (x in 0 until source.width) {
                        val pixel = source.getPixelRGBA(x, y)
                        val alpha = (((pixel ushr 24) and 255) * .65f).toInt()
                        val lit = max(max(pixel and 255, (pixel ushr 8) and 255), (pixel ushr 16) and 255) >= 80
                        mask.setPixelRGBA(x, y, if (lit) (alpha shl 24) or 0xDADADA else 0)
                    }
                    val location = ResourceLocation(MOD_ID, "dynamic/call_icon/${def.id}")
                    mc.textureManager.register(location, DynamicTexture(mask))
                    whiteIcons[def.id] = location
                    monochromeIconCount++
                }
            }
        }
    }

    @SubscribeEvent fun project(event: RenderLevelStageEvent) {
        if (event.stage != RenderLevelStageEvent.Stage.AFTER_LEVEL) return
        val mc = Minecraft.getInstance()
        label = null
        val level = mc.level ?: return
        if (mc.options.hideGui || mc.screen != null) return
        val camera = event.camera.position
        // Camera rotation is independent of the temporary world-render pose stack.
        val view = Matrix4f().rotationY(Math.PI.toFloat()).rotate(Quaternionf(event.camera.rotation()).conjugate())
        val matrix = Matrix4f(event.projectionMatrix).mul(view)
        val width = mc.window.guiScaledWidth
        val height = mc.window.guiScaledHeight
        val beamHeight = AbstractHellpodEntity.descentSpawnHeight().toFloat()
        var closest = Double.POSITIVE_INFINITY
        level.entitiesForRendering().filterIsInstance<StratagemMarker>().forEach { marker ->
            if (marker.isRemoved || marker.stratagem == null || marker.elapsedTicks == 0) return@forEach
            val x = (marker.x - camera.x).toFloat()
            val z = (marker.z - camera.z).toFloat()
            val bottom = matrix.transform(Vector4f(x, (marker.y + .2 - camera.y).toFloat(), z, 1f))
            val top = matrix.transform(Vector4f(x, (marker.y + beamHeight - camera.y).toFloat(), z, 1f))
            // At screen-centre Y, clip-space Y is zero. Intersect the entire visible beam, not its base.
            if (java.lang.Boolean.getBoolean("sbw_helldiver.beaconSmokeTest") && marker.x > 590 && projectionDiagnostics++ < 3) {
                com.example.sbwhelldiver.LOGGER.info("CALL_HUD_PROJECTION: camera={} marker={} rotation={} bottom={} top={}", camera, marker.position(), event.camera.rotation(), bottom, top)
            }
            val dy = top.y - bottom.y
            if (abs(dy) < 1e-6) return@forEach
            val fraction = -bottom.y / dy
            if (fraction !in 0f..1f) return@forEach
            val point = bottom.lerp(top, fraction)
            if (point.w <= 1e-5f || point.z < -point.w || point.z > point.w) return@forEach
            val screenX = (point.x / point.w * .5f + .5f) * width
            val miss = abs(screenX - width / 2.0)
            if (miss > AIM_MARGIN || miss >= closest) return@forEach
            closest = miss
            label = Label(marker, screenX.roundToInt(), height / 2, ICON_SIZE, marker.position().distanceTo(mc.player?.position() ?: camera))
        }
    }

    fun draw(gui: GuiGraphics, partial: Float) {
        val mc = Minecraft.getInstance()
        val current = label ?: return
        if (mc.options.hideGui || mc.screen != null || current.marker.isRemoved) return
        val def = current.marker.stratagem ?: return
        val icon = whiteIcons[def.id] ?: return
        val x = current.x + 10
        val y = current.y - ICON_SIZE / 2
        gui.fill(x - 1, y - 1, x + ICON_SIZE + 1, y + ICON_SIZE + 1, 0x50000000)
        gui.flush()
        RenderSystem.enableBlend()
        RenderSystem.defaultBlendFunc()
        gui.blit(icon, x, y, ICON_SIZE, ICON_SIZE, 0f, 0f, 64, 64, 64, 64)
        RenderSystem.disableBlend()
        val distance = "${current.distance.roundToInt()}m"
        gui.drawString(mc.font, distance, x + ICON_SIZE / 2 - mc.font.width(distance) / 2, y + ICON_SIZE + 3, 0xFFFFFF, true)
        val textX = x + ICON_SIZE + 5
        val name = Component.translatable(def.translation)
        val countingTicks=if(current.marker.isSustainedOrbital && current.marker.orbitalFiredAt>=0)current.marker.orbitalActivityLeft else current.marker.ticksLeft
        val remaining = ceil((countingTicks - partial).coerceAtLeast(0f) / 20f).toInt()
        val time = "%02d:%02d".format(java.util.Locale.ROOT, remaining / 60, remaining % 60)
        gui.drawString(mc.font, name, textX, current.y - 10, TITLE_COLOR, true)
        val status=if(current.marker.hasArrived)Component.translatable("hud.$MOD_ID.arrived") else if(current.marker.isSustainedOrbital && current.marker.orbitalFiredAt>=0)Component.translatable("hud.$MOD_ID.in_progress",time) else Component.translatable("hud.$MOD_ID.arrival_countdown", time)
        gui.drawString(mc.font, status, textX, current.y + 2, 0xDDE1E5, true)
    }

}
