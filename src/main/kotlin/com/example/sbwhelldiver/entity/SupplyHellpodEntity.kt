package com.example.sbwhelldiver.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.Heightmap

/**
 * 独立空投仓：投下来开舱，弹出一个补给容器。
 *
 * 与哨戒炮台共用同一套下落 / 插入大地 / 收舱的状态机，差别只在展开动作 ——
 * 它没有炮塔，模型里只有 `open` / `close` / `closed` 三条 0.7 秒的舱盖动画，
 * 没有那套 3.2 秒的 `deploy`（开盖 → 升起 → 放平）。所以这里把时长与动画名都换掉。
 */
class SupplyHellpodEntity(type: EntityType<*>, level: Level) : AbstractHellpodEntity(type, level) {

    /** `animation.hellpod.open` 0.7 秒 = 14 tick。 */
    override val deployTicks get() = profile.deployTicks

    /** `animation.hellpod.close` 0.7 秒 = 14 tick。 */
    override val retractTicks get() = profile.retractTicks

    override val deployAnimation: String = "animation.hellpod.deploy"
    override val retractAnimation: String = "animation.hellpod.retract"

    /** 独立空投仓没有炮塔，收纳姿态就是「关着盖」这一条循环动画。 */
    override val packedAnimation: String = "animation.hellpod.packed"

    /** 补给只弹一次，重复进 ACTIVE 不该再刷一个箱子出来。 */
    private var supplyDropped = false

    override fun addAdditionalSaveData(tag: net.minecraft.nbt.CompoundTag) {
        super.addAdditionalSaveData(tag)
        tag.putBoolean("SupplyDropped", supplyDropped)
    }

    override fun readAdditionalSaveData(tag: net.minecraft.nbt.CompoundTag) {
        super.readAdditionalSaveData(tag)
        supplyDropped = tag.getBoolean("SupplyDropped")
    }

    override fun onActivated() {
        if (supplyDropped) return
        supplyDropped = true
        dropSupply()
    }

    /**
     * 在舱体旁边放一个卓越前线的补给容器。
     *
     * 不放正中间：舱体本身占两格多宽，放中间会被模型挡住，玩家既看不见也点不到。
     * 这里从舱体中心向外一圈圈找**下方有支撑、且自身可替换**的位置，
     * 实在放不下就退化成掉一个物品，至少不会凭空消失。
     */
    private fun dropSupply() {
        val lvl = level() as? ServerLevel ?: return

        val block = BuiltInRegistries.BLOCK.get(CONTAINER_ID)
        if (block == Blocks.AIR) {
            spawnItem(lvl)
            return
        }

        val surface = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ)
        for (radius in 1..3) {
            for (offset in RING_OFFSETS) {
                val pos = BlockPos(blockX + offset.first * radius, surface, blockZ + offset.second * radius)
                // 下方要结实，目标位置要能让出来，否则悬空或卡在树里。
                if (lvl.getBlockState(pos.below()).isSolidRender(lvl, pos.below()) &&
                    lvl.getBlockState(pos).canBeReplaced()
                ) {
                    lvl.setBlockAndUpdate(pos, block.defaultBlockState())
                    return
                }
            }
        }
        spawnItem(lvl)
    }

    /** 放不下方块时的兜底：把容器作为物品丢出来。 */
    private fun spawnItem(lvl: ServerLevel) {
        val stack = ItemStack(BuiltInRegistries.ITEM.get(CONTAINER_ID))
        if (stack.isEmpty) return
        val item = ItemEntity(lvl, x, y, z, stack)
        item.setDefaultPickUpDelay()
        lvl.addFreshEntity(item)
    }

    companion object {
        /** 卓越前线的普通补给箱，带随机战利品表。 */
        private val CONTAINER_ID = ResourceLocation("superbwarfare", "small_container")

        /** 环形搜索的四个方向。 */
        private val RING_OFFSETS = listOf(0 to 1, 1 to 0, 0 to -1, -1 to 0)
    }
}
