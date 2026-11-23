package com.example.sbwhelldiver.item

import com.example.sbwhelldiver.config.GuardDogConfigs

import com.example.sbwhelldiver.entity.GuardDogEntity

import com.example.sbwhelldiver.init.ModEntities


import net.minecraft.nbt.CompoundTag

import net.minecraft.network.chat.Component

import net.minecraft.server.level.ServerLevel

import net.minecraft.server.level.ServerPlayer

import net.minecraft.world.entity.Entity

import net.minecraft.world.entity.item.ItemEntity

import net.minecraft.world.entity.player.Player

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag


import net.minecraft.world.level.Level

import java.util.UUID



open class GuardDogBackpack(properties: Properties, val laser: Boolean = false, val stun: Boolean = false, val flame: Boolean = false, val gas: Boolean = false) : BackpackStratagemItem(properties) {

    override fun initializeClient(consumer: java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions>) {

        consumer.accept(object : net.minecraftforge.client.extensions.common.IClientItemExtensions {

            override fun getCustomRenderer() = com.example.sbwhelldiver.client.GuardDogVisual.itemRenderer

        })

    }



    override fun appendHoverText(stack: ItemStack, level: Level?, tooltip: MutableList<Component>, flag: TooltipFlag) {
        if (isEnergy(stack)) return
        tooltip.add(Component.translatable("tooltip.sbw_helldiver.guard_dog_reloads", spareMags(stack), reserveCapacity(stack)))
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

        private fun variant(stack: ItemStack) = GuardDogConfigs.Variant.of(isLaser(stack), isStun(stack), isFlame(stack), isGas(stack))

        fun capacity(stack: ItemStack) = GuardDogConfigs.ammoCapacity(variant(stack))

        fun reserveCapacity(stack: ItemStack) = GuardDogConfigs.reserveCapacity(variant(stack))

        fun isEnergy(stack: ItemStack) = isLaser(stack) || isStun(stack)



        fun equipped(player: Player): ItemStack? =

            StratagemBackpacks.equipped(player)?.takeIf { it.item is GuardDogBackpack }



        private fun data(stack: ItemStack) = stack.tag?.copy() ?: CompoundTag()

        private fun update(stack: ItemStack, edit: (CompoundTag) -> Unit) {

            val tag = data(stack)

            edit(tag)

            stack.tag = tag

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

            val maximum = GuardDogConfigs.get(variant(stack)).maxHealth.get().toFloat()
            if (it.contains(HEALTH)) it.getFloat(HEALTH).coerceIn(0f, maximum) else maximum

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

            val settings = GuardDogConfigs.get(variant(stack))
            if (settings.damageable.get() && tag.contains(HEALTH) && tag.getFloat(HEALTH) < settings.maxHealth.get()) {

                val repairTicks = settings.repairTicks.get()
                if (repairTicks == 0) tag.remove(HEALTH)
                else tag.putLong(READY_AT, now + repairTicks)

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
