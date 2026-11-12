package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.survival.ResupplyAmmo
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level

/** Loose cases are physical supplies, never inventory items or automatic pickups. */
class SupplyCaseEntity(type: EntityType<*>, level: Level): Entity(type, level) {
    override fun defineSynchedData() = Unit
    override fun readAdditionalSaveData(tag: CompoundTag) = Unit
    override fun addAdditionalSaveData(tag: CompoundTag) = Unit
    override fun isPickable() = !isRemoved
    override fun interact(player: Player, hand: InteractionHand): InteractionResult {
        if (isRemoved || player.isSpectator) return InteractionResult.PASS
        if (player is ServerPlayer) {
            discard(); ResupplyAmmo.refill(player)
            ResupplyAmmo.playPickupSound(level(), x, y, z)
        }
        return InteractionResult.sidedSuccess(level().isClientSide)
    }
    override fun tick() {
        super.tick()
        if (!isNoGravity) deltaMovement = deltaMovement.add(0.0,-.04,0.0)
        move(MoverType.SELF,deltaMovement)
        deltaMovement = deltaMovement.multiply(if(onGround()).6 else .98,.98,if(onGround()).6 else .98)
        if(onGround()) deltaMovement = deltaMovement.multiply(1.0,0.0,1.0)
    }
}
