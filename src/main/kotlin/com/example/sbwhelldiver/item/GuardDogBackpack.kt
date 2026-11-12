package com.example.sbwhelldiver.item

import com.example.sbwhelldiver.entity.GuardDogEntity
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.core.component.DataComponents
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomData
import net.minecraft.world.level.Level
import java.util.UUID

open class GuardDogBackpack(properties: Properties, val laser: Boolean = false, val stun: Boolean = false, val flame: Boolean = false, val gas: Boolean = false) : BackpackStratagemItem(properties) {
    override fun initializeClient(consumer: java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions>) {
        consumer.accept(object : net.neoforged.neoforge.client.extensions.common.IClientItemExtensions {
            override fun getCustomRenderer() = com.example.sbwhelldiver.client.GuardDogVisual.itemRenderer
        })
    }

    override fun inventoryTick(stack: ItemStack, level: Level, entity: Entity, slot: Int, selected: Boolean) {
        if (level is ServerLevel && entity is ServerPlayer) service(stack, level, entity)
    }

    override fun onEntityItemUpdate(stack: ItemStack, entity: ItemEntity): Boolean {
        val level = entity.level() as? ServerLevel ?: return false
        if (active(stack) != null) {
            val dropped = stack.copy()
            dock(dropped, level.gameTime, emptyFold(dropped))
            entity.item = dropped
        }
        return false
    }

    internal fun service(stack: ItemStack, level: ServerLevel, player: ServerPlayer) {
        if (level.gameTime % 10 != 0L) return
        val active = active(stack)
        if (active != null) {
            val drone = level.getEntity(active) as? GuardDogEntity
            if (equipped(player) !== stack || drone == null || !drone.belongsTo(player)) dock(stack, level.gameTime)
        }
        val ready = data(stack).getLong(READY_AT)
        if (ready in 1..level.gameTime) update(stack) {
            it.remove(READY_AT)
            it.remove(HEALTH)
        }
    }

    companion object {
        private const val ACTIVE = "GuardDogActive"
        private const val AMMO = "GuardDogAmmo"
        private const val SPARE_MAGS = "GuardDogSpareMags"
        private const val EMPTY_FOLD = "GuardDogEmptyFold"
        private const val HEALTH = "GuardDogHealth"
        private const val READY_AT = "GuardDogReadyAt"

        fun isLaser(stack: ItemStack) = (stack.item as? GuardDogBackpack)?.laser == true
        fun isStun(stack: ItemStack) = (stack.item as? GuardDogBackpack)?.stun == true
        fun isFlame(stack: ItemStack) = (stack.item as? GuardDogBackpack)?.flame == true
        fun isGas(stack: ItemStack) = (stack.item as? GuardDogBackpack)?.gas == true
        fun capacity(stack: ItemStack) = if (isGas(stack)) GuardDogEntity.GAS_MAGAZINE else if (isFlame(stack)) GuardDogEntity.FLAME_MAGAZINE else GuardDogEntity.MAGAZINE
        fun reserveCapacity(stack: ItemStack) = if (isGas(stack)) GuardDogEntity.GAS_RESERVES else if (isFlame(stack)) GuardDogEntity.FLAME_RESERVES else GuardDogEntity.SPARE_MAGAZINES
        fun isEnergy(stack: ItemStack) = isLaser(stack) || isStun(stack)

        fun equipped(player: Player): ItemStack? =
            StratagemBackpacks.equipped(player)?.takeIf { it.item is GuardDogBackpack }

        private fun data(stack: ItemStack) = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag()
        private fun update(stack: ItemStack, edit: (CompoundTag) -> Unit) {
            val tag = data(stack)
            edit(tag)
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag))
        }

        fun active(stack: ItemStack): UUID? = data(stack).takeIf { it.hasUUID(ACTIVE) }?.getUUID(ACTIVE)
        fun ammo(stack: ItemStack): Int = data(stack).let {
            if (it.contains(AMMO)) it.getInt(AMMO).coerceIn(0, capacity(stack)) else capacity(stack)
        }
        fun spareMags(stack: ItemStack): Int = data(stack).let {
            if (it.contains(SPARE_MAGS)) it.getInt(SPARE_MAGS).coerceIn(0, reserveCapacity(stack))
            else reserveCapacity(stack)
        }

        fun loadSpare(stack: ItemStack): Boolean {
            val spares = spareMags(stack)
            if (spares <= 0) return false
            update(stack) {
                it.putInt(SPARE_MAGS, spares - 1)
                it.remove(AMMO)
            }
            return true
        }

        fun health(stack: ItemStack): Float = data(stack).let {
            if (it.contains(HEALTH)) it.getFloat(HEALTH).coerceIn(0f, GuardDogEntity.MAX_HEALTH) else GuardDogEntity.MAX_HEALTH
        }

        fun refill(stack: ItemStack) = update(stack) {
            it.putInt(AMMO, capacity(stack)); it.putInt(SPARE_MAGS, reserveCapacity(stack)); it.remove(EMPTY_FOLD)
        }

        fun updateAmmo(stack: ItemStack, ammo: Int) = update(stack) { it.putInt(AMMO, ammo) }
        fun updateHealth(stack: ItemStack, health: Float) = update(stack) { it.putFloat(HEALTH, health) }

        fun emptyFold(stack: ItemStack): Boolean = data(stack).getBoolean(EMPTY_FOLD)

        fun dock(stack: ItemStack, now: Long, emptyFold: Boolean = false) = update(stack) { tag ->
            tag.remove(ACTIVE)
            if (emptyFold) tag.putBoolean(EMPTY_FOLD, true) else tag.remove(EMPTY_FOLD)
            if (!isEnergy(stack) && tag.contains(HEALTH) && tag.getFloat(HEALTH) < GuardDogEntity.MAX_HEALTH) {
                tag.putLong(READY_AT, now + GuardDogEntity.REARM_TICKS)
            }
        }

        fun toggle(player: ServerPlayer) {
            if (!player.isAlive || player.isSpectator) return
            val stack = equipped(player) ?: return
            val level = player.serverLevel()
            val current = active(stack)
            if (current != null) {
                val drone = level.getEntity(current) as? GuardDogEntity
                if (drone != null && drone.belongsTo(player)) {
                    drone.recall()
                    return
                }
                dock(stack, level.gameTime)
            }
            val now = level.gameTime
            val ready = data(stack).getLong(READY_AT)
            if (ready > now) {
                player.displayClientMessage(Component.translatable("message.sbw_helldiver.guard_dog_servicing"), true)
                return
            }
            val minimumAmmo = if (isFlame(stack)) com.example.sbwhelldiver.entity.FlameClock.AMMO_PER_JET else 1
            if (!isEnergy(stack) && ammo(stack) < minimumAmmo) {
                if (spareMags(stack) == 0) {
                    player.displayClientMessage(Component.translatable("message.sbw_helldiver.guard_dog_no_ammo"), true)
                    return
                }
                // A lost drone may have been removed after ejecting its magazine, before insertion.
                loadSpare(stack)
            }
            val drone = ModEntities.GUARD_DOG.get().create(level) ?: return
            if (!drone.attach(player, stack) || !level.addFreshEntity(drone)) return
            update(stack) {
                if (ready > 0) {
                    it.remove(READY_AT)
                    it.remove(HEALTH)
                }
                it.putUUID(ACTIVE, drone.uuid)
            }
        }
    }
}
