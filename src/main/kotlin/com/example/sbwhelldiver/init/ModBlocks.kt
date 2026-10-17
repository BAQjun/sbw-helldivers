package com.example.sbwhelldiver.init
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.block.SentryFireBlock
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraftforge.registries.DeferredRegister
import net.minecraftforge.registries.ForgeRegistries
object ModBlocks {
    val BLOCKS=DeferredRegister.create(ForgeRegistries.BLOCKS,MOD_ID)
    val SENTRY_FIRE=BLOCKS.register("sentry_fire") { SentryFireBlock(BlockBehaviour.Properties.copy(Blocks.FIRE).lightLevel{12}) }
}
