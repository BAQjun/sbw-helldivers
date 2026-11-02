package com.example.sbwhelldiver.init
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.block.SentryFireBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.neoforged.neoforge.registries.DeferredRegister
import net.minecraft.core.registries.BuiltInRegistries
object ModBlocks {
    val BLOCKS=DeferredRegister.create(BuiltInRegistries.BLOCK,MOD_ID)
    val SENTRY_FIRE=BLOCKS.register("sentry_fire") { _ -> SentryFireBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.FIRE)) }
    val TACTICAL_TERMINAL=BLOCKS.register("tactical_terminal"){_->com.example.sbwhelldiver.survival.TacticalTerminalBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion())}
    val SAMPLE_MACHINE=BLOCKS.register("sample_machine"){_->com.example.sbwhelldiver.survival.SampleMachineBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.NETHERITE_BLOCK).noOcclusion().noLootTable())}
}
