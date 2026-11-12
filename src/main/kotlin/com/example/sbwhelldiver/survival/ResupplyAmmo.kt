package com.example.sbwhelldiver.survival

import com.atsuishio.superbwarfare.data.gun.Ammo
import com.atsuishio.superbwarfare.data.gun.AmmoConsumer
import com.atsuishio.superbwarfare.data.gun.GunProp
import com.atsuishio.superbwarfare.data.gun.toGunData
import com.example.sbwhelldiver.item.GuardDogBackpack
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/** One grant per ammunition type, using the largest carried magazine of that type. */
object ResupplyAmmo {
    const val MAGAZINES_PER_GUN = 6
    /** Both attached and loose cases use the verified native supply-box interaction event. */
    fun playPickupSound(level: net.minecraft.world.level.Level, x: Double, y: Double, z: Double) {
        if (!level.isClientSide) level.playSound(null, x, y, z,
            com.example.sbwhelldiver.init.ModSounds.EVENTS.getValue("sfx_supply_pickup").get(),
            net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f)
    }

    fun refill(player: ServerPlayer) {
        val stacks = (0 until player.inventory.containerSize).map { player.inventory.getItem(it) }
        val reserves = linkedMapOf<Ammo, Int>()
        val items = mutableListOf<Pair<ItemStack, Int>>()
        for (stack in stacks) {
            val gun = stack.toGunData() ?: continue
            val consumer = gun.selectedAmmoConsumer()
            if (!consumer.initialized()) consumer.init()
            val magazine = gun.get(GunProp.MAGAZINE).coerceAtLeast(0)
            val loadAmount = consumer.loadAmount.coerceAtLeast(1)
            val amount = ((magazine.toLong() * MAGAZINES_PER_GUN + loadAmount - 1) / loadAmount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            when (consumer.type) {
                AmmoConsumer.AmmoConsumeType.PLAYER_AMMO -> consumer.playerAmmoType?.let { reserves[it] = maxOf(reserves[it] ?: 0, amount) }
                AmmoConsumer.AmmoConsumeType.ITEM -> {
                    val ammo = consumer.stack()
                    if (ammo.isEmpty) continue
                    val index = items.indexOfFirst { ItemStack.isSameItemSameComponents(it.first, ammo) }
                    if (index < 0) items.add(ammo.copyWithCount(1) to amount)
                    else items[index] = items[index].first to maxOf(items[index].second, amount)
                }
                else -> Unit
            }
        }
        for ((ammo, amount) in reserves) {
            val before = ammo.get(player)
            val stored = minOf(amount, (ammo.limit - before).coerceAtLeast(0))
            if (stored > 0) ammo.add(player, stored)
            // Some capability providers report success without accepting ammunition.
            val accepted = (ammo.get(player) - before).coerceIn(0, stored)
            give(player, ammo.getItemStack(), amount - accepted)
        }
        for ((ammo, amount) in items) give(player, ammo, amount)
        if (net.neoforged.fml.ModList.get().isLoaded("tacz")) com.example.sbwhelldiver.compat.TaczResupply.refill(player, stacks)
        for (stack in stacks) if (stack.item is GuardDogBackpack) GuardDogBackpack.refill(stack)
        GuardDogBackpack.equipped(player)?.let(GuardDogBackpack::refill)
        player.inventory.setChanged()
        player.containerMenu.broadcastChanges()
    }

    fun give(player: ServerPlayer, template: ItemStack, amount: Int) {
        if (template.isEmpty) return
        var left = amount
        while (left > 0) {
            val stack = template.copyWithCount(minOf(left, template.maxStackSize.coerceAtLeast(1)))
            left -= stack.count
            player.inventory.add(stack)
            if (!stack.isEmpty) player.drop(stack, false)
        }
    }
}
