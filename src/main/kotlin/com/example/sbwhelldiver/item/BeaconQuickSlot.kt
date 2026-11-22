package com.example.sbwhelldiver.item

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.stratagem.BeaconData
import com.example.sbwhelldiver.stratagem.BeaconInput
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.function.Supplier

/** Curios owns the stack. Only the player's active hotbar selection changes. */
object BeaconQuickSlot {
    private val attachments = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MOD_ID)
    private val activeSlot = attachments.register("beacon_quick_slot", Supplier {
        AttachmentType.builder(Supplier { -1 }).sync(ByteBufCodecs.VAR_INT).build()
    })

    fun register(bus: IEventBus) {
        attachments.register(bus)
        NeoForge.EVENT_BUS.addListener(::tick)
        NeoForge.EVENT_BUS.addListener<LivingSwapItemsEvent.Hands> { event ->
            val player = event.entity as? Player ?: return@addListener
            if (selected(player) != null) event.isCanceled = true
        }
    }

    fun select(player: ServerPlayer, normalSlot: Int) {
        if (normalSlot !in 0..8 || player.inventory.selected != normalSlot || !player.isAlive || player.isSpectator ||
            player.isPassenger || player.containerMenu !== player.inventoryMenu) return
        val beacon = UtilitySlot.BEACON.equipped(player) ?: return
        BeaconData.ensureIdentity(beacon)
        BeaconData.clear(beacon)
        BeaconInput.reset(player.uuid)
        player.setData(activeSlot.get(), normalSlot)
    }

    private fun anchor(player: Player): Int? =
        if (player.hasData(activeSlot.get())) player.getData(activeSlot.get()).takeIf { it >= 0 } else null

    fun selected(player: Player): ItemStack? {
        val anchor = anchor(player) ?: return null
        if ((!player.level().isClientSide || player.isLocalPlayer) && player.inventory.selected != anchor) return null
        return UtilitySlot.BEACON.equipped(player)
    }

    fun deselect(player: Player) {
        if (anchor(player) == null) return
        player.removeData(activeSlot.get())
        if (player is ServerPlayer) {
            UtilitySlot.BEACON.equipped(player)?.let(BeaconData::clear)
            BeaconInput.reset(player.uuid)
        }
    }

    fun removeSelected(player: Player): ItemStack? {
        val selected = selected(player) ?: return null
        val removed = UtilityCurios.takeBeacon(player, selected)
        deselect(player)
        return removed
    }

    fun replaceSelected(player: Player, replacement: ItemStack): Boolean {
        if (player.level().isClientSide && !player.isLocalPlayer) return false
        val beacon = selected(player) ?: return false
        if (!replacement.isEmpty && !UtilitySlot.BEACON.accepts(replacement)) {
            deselect(player)
            return false
        }
        if (beacon !== replacement) UtilityCurios.replaceBeacon(player, beacon, replacement)
        if (replacement.isEmpty) deselect(player)
        return true
    }

    private fun tick(event: PlayerTickEvent.Pre) {
        val player = event.entity as? ServerPlayer ?: return
        if (anchor(player) != null && (!player.isAlive || player.isSpectator || player.isPassenger || selected(player) == null))
            deselect(player)
    }
}
