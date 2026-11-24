package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.item.GuardDogBackpack
import com.example.sbwhelldiver.item.HoverPack
import com.example.sbwhelldiver.item.HoverPackFlight
import com.example.sbwhelldiver.item.JumpPack
import com.example.sbwhelldiver.item.JumpPackFlight
import com.example.sbwhelldiver.item.StratagemBackpacks
import com.example.sbwhelldiver.item.SupplyBackpack
import com.example.sbwhelldiver.item.WarpPack
import com.example.sbwhelldiver.item.WarpPackFlight
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemCooldowns
import net.neoforged.api.distmarker.Dist
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.neoforge.client.event.RenderGuiEvent
import kotlin.math.roundToInt

@EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object BackpackStatusHud {
    private const val ICON_SIZE = 18
    private const val MARGIN = 8
    private const val BAR_WIDTH = 3
    private const val LOW_COLOR = 0xFF4949
    private const val READY_COLOR = 0xFFFFFF

    private data class Meter(val remaining: Float, val tint: Int = if (remaining < .5f) LOW_COLOR else READY_COLOR)

    @SubscribeEvent
    fun draw(event: RenderGuiEvent.Post) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        if (mc.options.hideGui || mc.screen != null || !player.isAlive || player.isSpectator) return
        val stack = StratagemBackpacks.equipped(player) ?: return
        val item = stack.item
        val gameTime = player.level().gameTime
        val partial = event.partialTick.getGameTimeDeltaPartialTick(false)
        val meter = when (item) {
            is GuardDogBackpack -> {
                val maximum = GuardDogBackpack.reserveCapacity(stack)
                val remaining = when {
                    GuardDogBackpack.isEnergy(stack) -> 1f
                    maximum == 0 -> 0f
                    else -> GuardDogBackpack.spareMags(stack).toFloat() / maximum
                }
                Meter(remaining)
            }
            is SupplyBackpack -> Meter(SupplyBackpack.cases(stack).toFloat() / SupplyBackpack.CAPACITY)
            is WarpPack -> {
                val energy = WarpPackVisual.displayEnergy(stack)
                val tint = when (WarpPackFlight.phase(energy)) {
                    WarpPackFlight.Phase.SAFE -> READY_COLOR
                    WarpPackFlight.Phase.RISK -> WarpPackFlight.Phase.RISK.color
                    WarpPackFlight.Phase.LETHAL -> LOW_COLOR
                }
                Meter(energy.toFloat() / WarpPackFlight.FULL, tint)
            }
            is JumpPack -> charge(item, player.cooldowns, JumpPackFlight.readyAt(stack), JumpPackFlight.RECHARGE_TICKS, gameTime, partial)
            is HoverPack -> charge(item, player.cooldowns, HoverPackFlight.readyAt(stack), HoverPackFlight.COOLDOWN_TICKS, gameTime, partial)
            else -> return
        }

        val itemId = BuiltInRegistries.ITEM.getKey(item).path
        val iconId = if (item is GuardDogBackpack) itemId.removeSuffix("_backpack") else itemId
        val gui = event.guiGraphics
        val top = gui.guiHeight() - MARGIN - ICON_SIZE
        val bottom = top + ICON_SIZE
        val tint = meter.tint
        if (!BeaconCallHud.drawWhiteIcon(gui, iconId, MARGIN, top, ICON_SIZE, tint)) return

        val left = MARGIN + ICON_SIZE + 3
        val right = left + BAR_WIDTH
        gui.fill(left - 1, top - 1, right + 1, bottom + 1, 0x80000000.toInt())
        gui.fill(left, top, right, bottom, 0x60000000 or tint)
        val filled = (ICON_SIZE * meter.remaining).roundToInt()
        if (filled > 0) gui.fill(left, bottom - filled, right, bottom, 0xFF000000.toInt() or tint)
    }

    private fun charge(item: Item, cooldowns: ItemCooldowns, readyAt: Long, duration: Int, gameTime: Long, partial: Float): Meter {
        val stackCooldown = ((readyAt - gameTime - partial) / duration).coerceIn(0f, 1f)
        val playerCooldown = cooldowns.getCooldownPercent(item, partial)
        val tint = if (readyAt > gameTime || cooldowns.isOnCooldown(item)) LOW_COLOR else READY_COLOR
        return Meter(1f - maxOf(stackCooldown, playerCooldown), tint)
    }
}
