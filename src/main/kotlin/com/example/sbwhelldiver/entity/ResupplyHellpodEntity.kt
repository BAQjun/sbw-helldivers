package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.survival.ResupplyAmmo
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** Four independently selectable cases, sharing the established item-rack hellpod lifecycle. */
class ResupplyHellpodEntity(type: EntityType<*>, level: Level) : ItemDeliveryHellpodEntity(type, level) {
    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder); builder.define(CASES, 15)
    }
    fun hasCase(index: Int) = index in 0..3 && entityData.get(CASES) and (1 shl index) != 0
    private fun removeCase(index: Int) { entityData.set(CASES, entityData.get(CASES) and (1 shl index).inv()) }
    override fun shouldRetract() = entityData.get(CASES) == 0
    override fun interact(player: Player, hand: InteractionHand): InteractionResult {
        if (phase != Phase.ACTIVE || player.isSpectator) return InteractionResult.PASS
        val eye = player.eyePosition
        val end = eye.add(player.getViewVector(1f).scale(player.entityInteractionRange()))
        val localEye = toLocal(eye)
        val sideIndex = if(localEye.z < 0) 0 else 1
        val target = (0..3).filter { it % 2 == sideIndex && hasCase(it) }.mapNotNull { index ->
            caseBounds(index).clip(localEye, toLocal(end)).orElse(null)?.let { Triple(index, it, localEye.distanceToSqr(it)) }
        }.minByOrNull { it.third } ?: return InteractionResult.PASS
        if (level().clip(ClipContext(eye, toWorld(target.second), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player)).type != HitResult.Type.MISS) return InteractionResult.PASS
        if (player is ServerPlayer) {
            // Consume before granting: two interactions in the same tick cannot duplicate a case.
            removeCase(target.first)
            ResupplyAmmo.refill(player)
            ResupplyAmmo.playPickupSound(level(), x, y + 1, z)
            val side = if (target.first % 2 == 0) Side.FRONT else Side.REAR
            if ((0..3).none { it % 2 == target.first % 2 && hasCase(it) }) closeSide(side)
            if (shouldRetract()) enterPhase(Phase.RETRACTING)
        }
        return InteractionResult.sidedSuccess(level().isClientSide)
    }
    private fun dropCases() {
        for (index in 0..3) if (hasCase(index)) {
            removeCase(index)
            val drop = ModEntities.SUPPLY_CASE.get().create(level()) ?: continue
            val pos = toWorld(caseBounds(index).center.add(0.0, -.17, 0.0))
            drop.moveTo(pos.x, pos.y, pos.z, yRot + if(index%2==0)0f else 180f, 0f)
            drop.deltaMovement = Vec3((random.nextDouble()-.5)*.15,.15,(random.nextDouble()-.5)*.15)
            level().addFreshEntity(drop)
        }
    }
    override fun destroy() { if (!level().isClientSide && !isRemoved) dropCases(); super.destroy() }
    override fun kill() { if (!level().isClientSide && !isRemoved) dropCases(); super.kill() }
    override fun addAdditionalSaveData(tag: CompoundTag) { super.addAdditionalSaveData(tag); tag.putInt("SupplyCases", entityData.get(CASES)) }
    override fun readAdditionalSaveData(tag: CompoundTag) { super.readAdditionalSaveData(tag); entityData.set(CASES, if(tag.contains("SupplyCases"))tag.getInt("SupplyCases") and 15 else 15) }
    companion object {
        private val CASES = SynchedEntityData.defineId(ResupplyHellpodEntity::class.java, EntityDataSerializers.INT)
        fun caseBounds(index: Int): AABB {
            val y = if(index < 2)21.64/16 else 10.77/16
            return if(index%2==0) AABB(-.324,y,-.427,.324,y+.34,-.195)
                else AABB(-.324,y,.195,.324,y+.34,.427)
        }
    }
}
