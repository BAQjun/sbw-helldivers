package com.example.sbwhelldiver.compat

import com.example.sbwhelldiver.survival.ResupplyAmmo
import com.tacz.guns.api.TimelessAPI
import com.tacz.guns.api.item.IGun
import com.tacz.guns.api.item.IAmmoBox
import com.tacz.guns.api.item.builder.AmmoItemBuilder
import com.tacz.guns.config.sync.SyncConfig
import com.tacz.guns.util.AttachmentDataUtils
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

/** Loaded only when TaCZ is present; no TaCZ types escape this adapter. */
object TaczResupply {
    fun refill(player: ServerPlayer, stacks: List<ItemStack>) {
        val amounts = linkedMapOf<ResourceLocation, Int>()
        for (stack in stacks) {
            val gun = IGun.getIGunOrNull(stack) ?: continue
            val data = TimelessAPI.getCommonGunIndex(gun.getGunId(stack)).orElse(null)?.gunData ?: continue
            val amount = (AttachmentDataUtils.getAmmoCountWithAttachment(stack, data).coerceAtLeast(0).toLong() * com.example.sbwhelldiver.survival.ResupplyAmmo.MAGAZINES_PER_GUN).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            amounts[data.ammoId] = maxOf(amounts[data.ammoId] ?: 0, amount)
        }
        for ((ammo, amount) in amounts) {
            val index = TimelessAPI.getCommonAmmoIndex(ammo).orElse(null) ?: continue
            var remaining = amount
            // Fill matching boxes first, so empty universal boxes stay useful for other calibres.
            for (matching in listOf(true, false)) for (stack in stacks) {
                val box = stack.item as? IAmmoBox ?: continue
                if (remaining <= 0 || box.isCreative(stack) || box.isAllTypeCreative(stack)) continue
                val current = box.getAmmoCount(stack).coerceAtLeast(0)
                val same = box.getAmmoId(stack) == ammo
                if (same != matching || !same && current > 0) continue
                val capacity = (index.stackSize.toLong() * SyncConfig.AMMO_BOX_STACK_SIZE.get() * (box.getAmmoLevel(stack) + 1)).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                val inserted = minOf(remaining, (capacity - current).coerceAtLeast(0))
                if (inserted > 0) {
                    box.setAmmoId(stack, ammo); box.setAmmoCount(stack, current + inserted); remaining -= inserted
                }
            }
            ResupplyAmmo.give(player, AmmoItemBuilder.create().setId(ammo).build(), remaining)
        }
    }
}
