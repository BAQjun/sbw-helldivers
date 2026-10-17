package com.example.sbwhelldiver.item

import com.example.sbwhelldiver.entity.StratagemBeacon
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.menu.BeaconLoadoutMenu
import com.example.sbwhelldiver.stratagem.BeaconData
import com.example.sbwhelldiver.stratagem.BeaconInput
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResultHolder
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.level.Level
import net.minecraftforge.network.NetworkHooks
import net.minecraftforge.client.extensions.common.IClientItemExtensions
import java.util.UUID
import java.util.function.Consumer

/** Reusable beacon: equipped stratagem cards remain stored after a successful throw. */
class BeaconItem(properties: Properties) : Item(properties) {
    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slot: Int, selected: Boolean) {
        if (!level.isClientSide) {
            BeaconData.ensureIdentity(stack)
            if (entity is Player && entity.mainHandItem !== stack && entity.offhandItem !== stack && stack.tag?.contains(BeaconData.SELECTED) == true) {
                BeaconData.clear(stack)
                BeaconInput.reset(entity.uuid)
                entity.inventory.setChanged()
            }
        }
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResultHolder<ItemStack> {
        val stack = player.getItemInHand(hand)
        if (player.isPassenger) return InteractionResultHolder.pass(stack)
        if (player.isShiftKeyDown) {
            if (player is ServerPlayer) {
                BeaconData.ensureIdentity(stack)
                BeaconData.clear(stack)
                BeaconInput.reset(player.uuid)
                NetworkHooks.openScreen(player, SimpleMenuProvider({ id, inventory, _ ->
                    BeaconLoadoutMenu(id, inventory, hand)
                }, Component.translatable("screen.sbw_helldiver.loadout"))) { it.writeEnum(hand) }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide)
        }
        return InteractionResultHolder.fail(stack)
    }

    fun throwActivated(player: ServerPlayer, hand: InteractionHand, identity: UUID): Boolean {
        BeaconInput.observeHands(player)
        val stack = player.getItemInHand(hand)
        if (stack.item !== this || BeaconData.identity(stack) != identity || player.isSpectator || player.isPassenger ||
            player.containerMenu !== player.inventoryMenu || player.cooldowns.isOnCooldown(this)) return false
        if (BeaconData.selected(stack) == null) return false
        val level = player.level()
        val projectile = StratagemBeacon(ModEntities.STRATAGEM_BEACON.get(), player, level)
        projectile.setItem(stack.copyWithCount(1))
        projectile.shootFromRotation(player, player.xRot, player.yRot, 0f, 1.5f, 0.5f)
        if (!level.addFreshEntity(projectile)) return false
        BeaconData.clear(stack)
        BeaconInput.reset(player.uuid)
        player.cooldowns.addCooldown(this, 4)
        player.inventory.setChanged()
        player.containerMenu.broadcastChanges()
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this))
        return true
    }

    override fun appendHoverText(stack: ItemStack, level: Level?, tooltip: MutableList<Component>, flag: TooltipFlag) {
        tooltip.add(Component.translatable("tooltip.sbw_helldiver.beacon_controls"))
        for (def in BeaconData.loadout(stack).filterNotNull()) tooltip.add(Component.translatable(def.translation))
        BeaconData.selected(stack)?.let { tooltip.add(Component.translatable("tooltip.sbw_helldiver.beacon_ready", Component.translatable(it.translation))) }
    }

    override fun initializeClient(consumer: Consumer<IClientItemExtensions>) {
        consumer.accept(object : IClientItemExtensions {
            override fun getCustomRenderer() = com.example.sbwhelldiver.client.BeaconItemRenderer.INSTANCE
        })
    }
}
