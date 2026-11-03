package com.example.sbwhelldiver.init
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.block.SentryFireBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
object ModBlocks {
    val BLOCKS=DeferredRegister.create(net.minecraftforge.registries.ForgeRegistries.BLOCKS,MOD_ID)
    val SENTRY_FIRE=BLOCKS.register("sentry_fire") { SentryFireBlock(BlockBehaviour.Properties.copy(Blocks.FIRE)) }
    val TACTICAL_TERMINAL=BLOCKS.register("tactical_terminal"){com.example.sbwhelldiver.survival.TacticalTerminalBlock(BlockBehaviour.Properties.copy(Blocks.IRON_BLOCK).noOcclusion())}
    val SAMPLE_MACHINE=BLOCKS.register("sample_machine"){com.example.sbwhelldiver.survival.SampleMachineBlock(BlockBehaviour.Properties.copy(Blocks.NETHERITE_BLOCK).noOcclusion().noLootTable())}
}
