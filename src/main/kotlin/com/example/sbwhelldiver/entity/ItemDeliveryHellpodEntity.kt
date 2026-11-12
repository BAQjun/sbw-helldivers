package com.example.sbwhelldiver.entity

import com.example.sbwhelldiver.init.ModSounds
import net.minecraft.tags.DamageTypeTags
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.syncher.EntityDataAccessor
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.network.syncher.SynchedEntityData
import net.minecraft.sounds.SoundSource
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

/** The shared hellpod carries one independently removable item on each side of its lift. */
open class ItemDeliveryHellpodEntity(type: EntityType<*>, level: Level) : AbstractHellpodEntity(type, level) {
    enum class Side(
        val sign: Int,
        val shield: String,
        val stackKey: String,
        val stackData: EntityDataAccessor<ItemStack>,
        val closedAt: EntityDataAccessor<Long>,
    ) {
        FRONT(-1, "front_shield", "FrontItem", FRONT_ITEM, FRONT_CLOSED),
        REAR(1, "rear_shield", "RearItem", REAR_ITEM, REAR_CLOSED);

        val mount get() = Vec3(0.0, 1.4, sign * .405)
        val pickArea get() = AABB(-.28, .83, .33 * sign, .28, 1.97, .60 * sign)
    }

    override fun defineSynchedData(builder: SynchedEntityData.Builder) {
        super.defineSynchedData(builder)
        for (side in Side.entries) {
            builder.define(side.stackData, ItemStack.EMPTY)
            builder.define(side.closedAt, -1L)
        }
    }

    override val deployAnimation = "animation.hellpod.item_delivery_deploy"
    override val retractAnimation = "animation.hellpod.item_delivery_retract"
    override val deployTicks get() = LIFT_END
    override val retractTicks get() = 25

    fun setPayload(front: ItemStack, rear: ItemStack) {
        entityData.set(Side.FRONT.stackData, front.copy())
        entityData.set(Side.REAR.stackData, rear.copy())
    }

    protected fun closeSide(side: Side) {
        entityData.set(side.closedAt, level().gameTime)
        playRackSound("shutter", toWorld(side.mount))
    }
    fun item(side: Side): ItemStack = entityData.get(side.stackData)
    open fun shieldAge(side: Side, sampleTime: Double): Float {
        val start = entityData.get(side.closedAt)
        if (start < 0) return 0f
        return (sampleTime - start).toFloat().coerceIn(0f, SHUTTER_TICKS.toFloat())
    }

    fun toWorld(local: Vec3): Vec3 = position().add(local.yRot(modelRotation()))
    protected fun toLocal(world: Vec3): Vec3 = world.subtract(position()).yRot(-modelRotation())
    private fun modelRotation() = Math.toRadians(180.0 - yRot).toFloat()

    override fun interactAt(player: Player, hit: Vec3, hand: InteractionHand) = interact(player, hand)

    override fun interact(player: Player, hand: InteractionHand): InteractionResult {
        if (phase != Phase.ACTIVE || player.isSpectator) return InteractionResult.PASS
        val eye = player.eyePosition
        val end = eye.add(player.getViewVector(1f).scale(player.entityInteractionRange()))
        val localEye = toLocal(eye)
        val side = if (localEye.z < 0) Side.FRONT else Side.REAR
        val stack = item(side)
        if (stack.isEmpty) return InteractionResult.PASS
        val hit = side.pickArea.clip(localEye, toLocal(end)).orElse(null) ?: return InteractionResult.PASS
        if (level().clip(ClipContext(eye, toWorld(hit), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player))
                .type != HitResult.Type.MISS) return InteractionResult.PASS

        if (!level().isClientSide) {
            val received = stack.copy()
            if (!player.addItem(received)) return InteractionResult.FAIL
            entityData.set(side.stackData, ItemStack.EMPTY)
            entityData.set(side.closedAt, level().gameTime)
            playRackSound("shutter", toWorld(side.mount))
            if (shouldRetract()) enterPhase(Phase.RETRACTING)
        }
        return InteractionResult.sidedSuccess(level().isClientSide)
    }

    override fun tick() {
        super.tick()
        if (level().isClientSide) return
        if (phase == Phase.DEPLOYING && phaseAge == LIFT_START) playRackSound("deploy", position())
    }

    override fun onRetracting() {
        playRackSound("retract", position())
    }

    protected fun playRackSound(action: String, pos: Vec3) {
        // Each weapon-rack event plays two independently randomized layers.
        for (layer in listOf("a", "b")) {
            val sound = ModSounds.EVENTS.getValue("sfx_item_delivery_" + action + "_" + layer).get()
            level().playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.BLOCKS, 1f, 1f)
        }
    }

    override fun automaticRetractionEnabled() = true
    override fun shouldRetract() = Side.entries.all { item(it).isEmpty }

    override fun hurt(source: DamageSource, amount: Float): Boolean {
        if (level().isClientSide || isRemoved || !amount.isFinite() || amount <= 0 || isInvulnerableTo(source)) return false
        val damage = if (source.`is`(DamageTypeTags.IS_EXPLOSION)) amount * SentryHellpodEntity.EXPLOSION_MULTIPLIER
            else getDamageModifier().compute(this, source, amount)
        val before = health
        onHurt(damage, source.entity, true)
        if (health <= 0) destroy()
        return health < before || isRemoved
    }

    override fun destroy() {
        if (level().isClientSide || isRemoved) return
        dropPayload()
        discard()
    }

    override fun kill() {
        if (!level().isClientSide && !isRemoved) dropPayload()
        super.kill()
    }

    private fun dropPayload() {
        for (side in Side.entries) {
            val stack = item(side)
            if (stack.isEmpty) continue
            entityData.set(side.stackData, ItemStack.EMPTY)
            val pos = toWorld(side.mount)
            val drop = ItemEntity(level(), pos.x, pos.y, pos.z, stack)
            drop.setDefaultPickUpDelay()
            level().addFreshEntity(drop)
        }
    }

    override fun addAdditionalSaveData(tag: CompoundTag) {
        super.addAdditionalSaveData(tag)
        for (side in Side.entries) {
            val stack = item(side)
            if (!stack.isEmpty) tag.put(side.stackKey, stack.save(registryAccess()))
            tag.putLong(side.stackKey + "ClosedAt", entityData.get(side.closedAt))
        }
    }

    override fun readAdditionalSaveData(tag: CompoundTag) {
        super.readAdditionalSaveData(tag)
        for (side in Side.entries) {
            val stack = ItemStack.parseOptional(registryAccess(), tag.getCompound(side.stackKey))
            entityData.set(side.stackData, if (stack.isEmpty) stack else stack.copyWithCount(1))
            val closedKey = side.stackKey + "ClosedAt"
            entityData.set(side.closedAt, if (tag.contains(closedKey)) tag.getLong(closedKey) else -1L)
        }
    }

    companion object {
        // The extracted 60 Hz rack moves up in frames 0-15; the shutters move in frames 14-42.
        const val SHUTTER_TICKS = 14
        const val SHUTTER_START = 14f / 3f
        const val LIFT_START = 9
        const val LIFT_END = 14
        // Preserve the fast end of the original drop, with a short moving lead-in so
        // the last shutter can visibly rise while the rack is already retracting.
        const val LOWER_MAIN_START = 9
        const val LOWER_TICKS = 16
        private val FRONT_ITEM = SynchedEntityData.defineId(ItemDeliveryHellpodEntity::class.java, EntityDataSerializers.ITEM_STACK)
        private val REAR_ITEM = SynchedEntityData.defineId(ItemDeliveryHellpodEntity::class.java, EntityDataSerializers.ITEM_STACK)
        private val FRONT_CLOSED = SynchedEntityData.defineId(ItemDeliveryHellpodEntity::class.java, EntityDataSerializers.LONG)
        private val REAR_CLOSED = SynchedEntityData.defineId(ItemDeliveryHellpodEntity::class.java, EntityDataSerializers.LONG)
    }
}
