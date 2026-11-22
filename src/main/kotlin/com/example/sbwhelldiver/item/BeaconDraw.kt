package com.example.sbwhelldiver.item

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.menu.BeaconLoadoutMenu
import com.example.sbwhelldiver.stratagem.BeaconData
import net.minecraft.nbt.CompoundTag
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.PlayerEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.UUID
import java.util.function.Supplier

/** A beacon drawn from Curios borrows the selected slot until it is put away. */
object BeaconDraw {
    private val attachments = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MOD_ID)
    private val stowed = attachments.register("drawn_beacon", Supplier {
        AttachmentType.builder(Supplier(::CompoundTag)).serialize(CompoundTag.CODEC).build()
    })
    private const val SLOT = "HotbarSlot"
    private const val BEACON = "Beacon"
    private const val ORIGINAL_ITEM = "OriginalItem"

    fun register(bus: IEventBus) {
        attachments.register(bus)
        NeoForge.EVENT_BUS.addListener(::tick)
        NeoForge.EVENT_BUS.addListener<LivingDeathEvent> { event ->
            (event.entity as? ServerPlayer)?.let(::restore)
        }
        NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedOutEvent> { event ->
            (event.entity as? ServerPlayer)?.let(::restore)
        }
        NeoForge.EVENT_BUS.addListener<PlayerEvent.PlayerLoggedInEvent> { event ->
            (event.entity as? ServerPlayer)?.let(::restore)
        }
    }

    private fun backpackSlot(player: Player) =
        (9..35).firstOrNull { player.inventory.getItem(it).item is BeaconItem }

    fun hasInBackpack(player: Player) = backpackSlot(player) != null

    fun draw(player: ServerPlayer, slot: Int) {
        val inventory = player.inventory
        if (slot !in 0..8 || inventory.selected != slot || !player.isAlive || player.isSpectator ||
            player.isPassenger || player.containerMenu !== player.inventoryMenu || player.mainHandItem.item is BeaconItem) return

        restore(player)
        val previous = inventory.getItem(slot)
        val equipped = if (StratagemBackpacks.usesCurios) UtilityCurios.takeBeacon(player) else null
        val beacon = if (equipped != null) {
            // Save with the player so a full inventory or interrupted session cannot lose this stack.
            player.setData(stowed.get(), CompoundTag().apply {
                putInt(SLOT, slot)
                putUUID(BEACON, BeaconData.ensureIdentity(equipped))
                put(ORIGINAL_ITEM, previous.saveOptional(player.registryAccess()))
            })
            equipped
        } else {
            val source = backpackSlot(player) ?: return
            val inBackpack = inventory.getItem(source)
            inventory.setItem(source, previous)
            inBackpack
        }
        BeaconData.ensureIdentity(beacon)
        BeaconData.clear(beacon)
        inventory.setItem(slot, beacon)
        syncInventory(player)
    }

    fun afterThrow(player: ServerPlayer, identity: UUID) {
        val state = state(player) ?: return
        if (state.hasUUID(BEACON) && state.getUUID(BEACON) == identity) restore(player)
    }

    private fun state(player: ServerPlayer) =
        if (player.hasData(stowed.get())) player.getData(stowed.get()) else null

    private fun tick(event: PlayerTickEvent.Pre) {
        val player = event.entity as? ServerPlayer ?: return
        val state = state(player) ?: return
        val slot = state.getInt(SLOT)
        val menu = player.containerMenu
        if (!player.isAlive || player.isSpectator || player.isPassenger || player.inventory.selected != slot ||
            !matches(player.inventory.getItem(slot), state) ||
            (menu !== player.inventoryMenu && menu !is BeaconLoadoutMenu)) restore(player)
    }

    private fun matches(stack: ItemStack, state: CompoundTag) =
        stack.item is BeaconItem && state.hasUUID(BEACON) && BeaconData.identity(stack) == state.getUUID(BEACON)

    private fun restore(player: ServerPlayer) {
        val state = state(player) ?: return
        val inventory = player.inventory
        val slot = state.getInt(SLOT)
        val beacon = inventory.getItem(slot)
        if (matches(beacon, state) && StratagemBackpacks.usesCurios && UtilityCurios.returnBeacon(player, beacon)) {
            BeaconData.clear(beacon)
            inventory.setItem(slot, ItemStack.EMPTY)
        }
        val previous = ItemStack.parseOptional(player.registryAccess(), state.getCompound(ORIGINAL_ITEM))
        player.removeData(stowed.get())
        if (!previous.isEmpty) {
            if (inventory.getItem(slot).isEmpty) inventory.setItem(slot, previous)
            else inventory.placeItemBackInInventory(previous)
        }
        syncInventory(player)
    }

    private fun syncInventory(player: ServerPlayer) {
        player.inventory.setChanged()
        player.inventoryMenu.broadcastChanges()
        if (player.containerMenu !== player.inventoryMenu) player.containerMenu.broadcastChanges()
    }
}
