package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.menu.BeaconLoadoutMenu
import com.example.sbwhelldiver.stratagem.Stratagems
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class BeaconLoadoutScreen(menu: BeaconLoadoutMenu, inventory: Inventory, title: Component) : AbstractContainerScreen<BeaconLoadoutMenu>(menu, inventory, title) {
    init { imageWidth = 250; imageHeight = 190; inventoryLabelX = 44; inventoryLabelY = 88; titleLabelX = 14; titleLabelY = 12 }
    override fun renderBg(gui: GuiGraphics, partial: Float, mouseX: Int, mouseY: Int) {
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xEF172522.toInt())
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + 3, 0xFF76CEA5.toInt())
        for (i in 0..3) {
            val x = leftPos + 14 + 56 * i
            gui.fill(x, topPos + 31, x + 50, topPos + 81, 0xFF263C33.toInt())
            gui.fill(x + 12, topPos + 8 + 31, x + 30, topPos + 26 + 31, 0xFF121C18.toInt())
            val def = Stratagems.fromItem(menu.cards.getItem(i))
            if (def == null) gui.drawCenteredString(font, "${i + 1}", x + 21, topPos + 44, 0x7A9A89)
            else def.code.forEachIndexed { index, arrow -> ArrowGlyph.draw(gui, x + 2 + index * 8, topPos + 65, arrow, 0xFFD2F1DF.toInt(), 1) }
        }
        for (slot in menu.slots.drop(4)) {
            gui.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF34443C.toInt())
            gui.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF1F3028.toInt())
        }
        gui.drawString(font, Component.translatable("screen.sbw_helldiver.loadout_hint"), leftPos + 14, topPos + 177, 0xA4B8AD, false)
    }
    override fun render(gui: GuiGraphics, mouseX: Int, mouseY: Int, partial: Float) {
        renderBackground(gui)
        super.render(gui, mouseX, mouseY, partial)
        renderTooltip(gui, mouseX, mouseY)
    }
    override fun renderLabels(gui: GuiGraphics, mouseX: Int, mouseY: Int) {
        gui.drawString(font, title, titleLabelX, titleLabelY, 0xD4E9D9, false)
        gui.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xADC2B5, false)
    }
}

object ArrowGlyph {
    private val pixels = listOf("...#...", "..###..", ".#####.", "#######", "..###..", "..###..", "..###..")
    fun draw(gui: GuiGraphics, x: Int, y: Int, direction: Int, color: Int, scale: Int = 1) {
        for (row in 0..6) for (col in 0..6) if (pixels[row][col] == '#') {
            val (dx, dy) = when (direction) { 1 -> 6 - row to col; 2 -> 6 - col to 6 - row; 3 -> row to 6 - col; else -> col to row }
            gui.fill(x + dx * scale, y + dy * scale, x + (dx + 1) * scale, y + (dy + 1) * scale, color)
        }
    }
}
