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
}
