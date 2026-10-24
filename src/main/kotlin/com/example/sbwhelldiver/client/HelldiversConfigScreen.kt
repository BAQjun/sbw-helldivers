package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.config.ClientModelConfig
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class HelldiversConfigScreen(private val parent: Screen) : Screen(Component.translatable("config.sbw_helldiver.title")) {
    private var lowQuality = ClientModelConfig.lowQualityModels.get()
    private var applying = false
    private var failed = false

    override fun init() {
        val left = width / 2 - 150
        addRenderableWidget(Button.builder(qualityLabel()) { button ->
            lowQuality = !lowQuality
            button.message = qualityLabel()
        }.bounds(left, height / 2 - 25, 300, 20)
            .tooltip(Tooltip.create(Component.translatable("config.sbw_helldiver.low_quality_models.tooltip"))).build())
        addRenderableWidget(Button.builder(Component.translatable("config.sbw_helldiver.save")) { apply() }
            .bounds(left, height / 2 + 45, 145, 20).build())
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { onClose() }
            .bounds(left + 155, height / 2 + 45, 145, 20).build())
    }

    private fun qualityLabel() = Component.translatable("config.sbw_helldiver.low_quality_models.value",
        Component.translatable(if (lowQuality) "options.on" else "options.off"))

    private fun apply() {
        if (applying) return
        val previous = ClientModelConfig.lowQualityModels.get()
        if (previous == lowQuality) { onClose(); return }
        applying = true
        failed = false
        children().filterIsInstance<Button>().forEach { it.active = false }
        ClientModelConfig.lowQualityModels.set(lowQuality)
        ClientModelConfig.SPEC.save()
        val client = requireNotNull(minecraft)
        client.reloadResourcePacks().whenComplete { _, error ->
            client.execute {
                applying = false
                if (error == null) client.setScreen(parent)
                else {
                    LOGGER.error("Could not apply model quality setting", error)
                    ClientModelConfig.lowQualityModels.set(previous)
                    ClientModelConfig.SPEC.save()
                    lowQuality = previous
                    failed = true
                    rebuildWidgets()
                }
            }
        }
    }

    override fun onClose() { if (!applying) minecraft?.setScreen(parent) }

    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partial: Float) {
        renderBackground(gui)
        super.render(gui, mouseX, mouseY, partial)
        gui.drawCenteredString(font, title, width / 2, height / 2 - 70, 0xFFFFFF)
        val message = when {
            applying -> "config.sbw_helldiver.applying"
            failed -> "config.sbw_helldiver.failed"
            else -> "config.sbw_helldiver.low_quality_models.hint"
        }
        gui.drawCenteredString(font, Component.translatable(message), width / 2, height / 2 + 8,
            if (failed) 0xFF7777 else 0xAAAAAA)
    }
}
