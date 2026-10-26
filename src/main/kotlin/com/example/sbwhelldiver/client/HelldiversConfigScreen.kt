package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.config.ClientModelConfig
import com.example.sbwhelldiver.config.HelldiversConfig
import com.example.sbwhelldiver.network.InfiniteAmmoRequestPacket
import com.example.sbwhelldiver.network.AutoRetractRequestPacket
import com.example.sbwhelldiver.network.BeaconNetwork
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Tooltip
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.CommonComponents
import net.minecraft.network.chat.Component

class HelldiversConfigScreen(private val parent: Screen) : Screen(Component.translatable("config.sbw_helldiver.title")) {
    private var lowQuality = ClientModelConfig.lowQualityModels.get()
    private var autoRetract = if(HelldiversConfig.SPEC.isLoaded)HelldiversConfig.autoRetract.get() else true
    private var originalAutoRetract = autoRetract
    private var autoRetractEditable = false
    private var autoRetractButton:Button? = null
    private var infiniteAmmo = if (HelldiversConfig.SPEC.isLoaded) HelldiversConfig.infiniteTurretAmmo.get() else false
    private var originalInfiniteAmmo = infiniteAmmo
    private var infiniteAmmoButton: Button? = null
    private var infiniteAmmoEditable = false
    private var applying = false
    private var failed = false

    override fun init() {
        val left = width / 2 - 150
        addRenderableWidget(Button.builder(qualityLabel()) { button ->
            lowQuality = !lowQuality
            button.message = qualityLabel()
        }.bounds(left, height / 2 - 45, 300, 20)
            .tooltip(Tooltip.create(Component.translatable("config.sbw_helldiver.low_quality_models.tooltip"))).build())
        autoRetractButton=addRenderableWidget(Button.builder(autoRetractLabel()) { button ->
            autoRetract=!autoRetract;button.message=autoRetractLabel()
        }.bounds(left,height/2-15,300,20)
            .tooltip(Tooltip.create(Component.translatable(if(minecraft?.level==null) "config.sbw_helldiver.auto_retract.enter_world" else "config.sbw_helldiver.auto_retract.tooltip"))).build()).also{it.active=autoRetractEditable}
        infiniteAmmoButton = addRenderableWidget(Button.builder(infiniteAmmoLabel()) { button ->
            infiniteAmmo = !infiniteAmmo; button.message = infiniteAmmoLabel()
        }.bounds(left,height/2+15,300,20)
            .tooltip(Tooltip.create(Component.translatable(if(minecraft?.level==null) "config.sbw_helldiver.infinite_turret_ammo.enter_world" else "config.sbw_helldiver.infinite_turret_ammo.tooltip")))
            .build()).also { it.active = infiniteAmmoEditable }
        if (minecraft?.level != null) {
            BeaconNetwork.CHANNEL.sendToServer(AutoRetractRequestPacket(null))
            BeaconNetwork.CHANNEL.sendToServer(InfiniteAmmoRequestPacket(null))
        }
        addRenderableWidget(Button.builder(Component.translatable("config.sbw_helldiver.save")) { apply() }
            .bounds(left, height / 2 + 65, 145, 20).build())
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL) { onClose() }
            .bounds(left + 155, height / 2 + 65, 145, 20).build())
    }

    fun autoRetractReceived(enabled:Boolean,editable:Boolean) {
        originalAutoRetract=enabled;autoRetract=enabled;autoRetractEditable=editable
        autoRetractButton?.let{it.message=autoRetractLabel();it.active=editable && !applying
            it.setTooltip(Tooltip.create(Component.translatable(if(editable) "config.sbw_helldiver.auto_retract.tooltip" else "config.sbw_helldiver.auto_retract.permission")))}
    }
    fun infiniteTurretAmmoReceived(enabled: Boolean, editable: Boolean) {
        originalInfiniteAmmo = enabled; infiniteAmmo = enabled; infiniteAmmoEditable = editable
        infiniteAmmoButton?.let {
            it.message = infiniteAmmoLabel(); it.active = editable && !applying
            it.setTooltip(Tooltip.create(Component.translatable(if(editable) "config.sbw_helldiver.infinite_turret_ammo.tooltip" else "config.sbw_helldiver.auto_retract.permission")))
        }
    }
    private fun infiniteAmmoLabel() = Component.translatable("config.sbw_helldiver.infinite_turret_ammo.value",
        Component.translatable(if(infiniteAmmo) "options.on" else "options.off"))

    private fun autoRetractLabel()=Component.translatable("config.sbw_helldiver.auto_retract.value",
        Component.translatable(if(autoRetract)"options.on" else "options.off"))

    private fun qualityLabel() = Component.translatable("config.sbw_helldiver.low_quality_models.value",
        Component.translatable(if (lowQuality) "options.on" else "options.off"))

    private fun apply() {
        if (applying) return
        if(autoRetractEditable && autoRetract!=originalAutoRetract)
            BeaconNetwork.CHANNEL.sendToServer(AutoRetractRequestPacket(autoRetract))
        if (infiniteAmmoEditable && infiniteAmmo != originalInfiniteAmmo)
            BeaconNetwork.CHANNEL.sendToServer(InfiniteAmmoRequestPacket(infiniteAmmo))
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
        gui.drawCenteredString(font, title, width / 2, height / 2 - 90, 0xFFFFFF)
        val message = when {
            applying -> "config.sbw_helldiver.applying"
            failed -> "config.sbw_helldiver.failed"
            else -> "config.sbw_helldiver.hint"
        }
        gui.drawCenteredString(font, Component.translatable(message), width / 2, height / 2 + 48,
            if (failed) 0xFF7777 else 0xAAAAAA)
    }
}
