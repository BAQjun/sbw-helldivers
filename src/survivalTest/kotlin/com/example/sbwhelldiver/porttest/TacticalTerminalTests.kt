package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.init.ModBlocks
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.survival.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.GameType
import net.minecraftforge.common.capabilities.ForgeCapabilities
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.util.UUID

@GameTestHolder("sbw_helldiver")
@PrefixGameTestTemplate(false)
object TacticalTerminalTests {
    @JvmStatic @GameTest(template="port_test",timeoutTicks=100,batch="tactical_terminal")
    fun screenAndHoldPurchase(h:GameTestHelper){
        val root=h.absolutePos(BlockPos(5,4,5));val level=h.level
        val state=ModBlocks.TACTICAL_TERMINAL.get().defaultBlockState().setValue(TacticalTerminalBlock.FACING,Direction.NORTH)
        for(i in 0..3)level.setBlock(TacticalTerminalBlock.position(root,Direction.NORTH,i),state.setValue(TacticalTerminalBlock.PART,i),3)
        val be=level.getBlockEntity(root) as TacticalTerminalBlockEntity
        val upper=TacticalTerminalBlock.position(root,Direction.NORTH,3)
        h.assertTrue(be.screenOn,"Terminal should light without a power supply")
        h.assertTrue(state.setValue(TacticalTerminalBlock.PART,3).getCollisionShape(level,upper).bounds().maxY==.5,"Upper terminal collision must be half a block")
        val p=net.minecraftforge.common.util.FakePlayerFactory.get(level,com.mojang.authlib.GameProfile(UUID.randomUUID(),"TerminalQA"))
        p.setGameMode(GameType.SURVIVAL);p.setPos(root.x+.5,root.y.toDouble(),root.z+1.5)
        p.inventory.setItem(0,ItemStack(ModItems.COMMON_SAMPLE.get(),50));p.inventory.setItem(1,ItemStack(ModItems.RARE_SAMPLE.get(),15))
        TacticalTerminalSessions.open(p,be);val token=TacticalTerminalSessions.token(p)!!;val id="orbital_precision_strike"
        TacticalTerminalSessions.accept(p,root,token,0,id,10_000_000_000L)
        for(t in 5 until TacticalTerminalHold.DURATION_TICKS.toInt() step 5)h.runAfterDelay(t.toLong()){TacticalTerminalSessions.accept(p,root,token,1,id)}
        h.runAfterDelay(TacticalTerminalHold.DURATION_TICKS-1){
            TacticalTerminalSessions.accept(p,root,token,2,id,10_000_000_000L)
            h.assertTrue(p.inventory.getItem(0).count==50,"Premature hold spent samples")
        }
        h.runAfterDelay(TacticalTerminalHold.DURATION_TICKS+1){
            TacticalTerminalSessions.accept(p,root,token,2,id,10_000_000_000L)
            h.assertTrue((0 until p.inventory.containerSize).none{p.inventory.getItem(it).`is`(ModItems.COMMON_SAMPLE.get()) || p.inventory.getItem(it).`is`(ModItems.RARE_SAMPLE.get())},"Completed purchase did not spend samples")
            h.assertTrue((0 until p.inventory.containerSize).any{p.inventory.getItem(it).`is`(ModItems.ORBITAL_PRECISION_STRIKE.get())},"Completed purchase did not grant its card")
            TacticalTerminalSessions.accept(p,root,token,2,id,10_000_000_000L)
            h.assertTrue((0 until p.inventory.containerSize).sumOf{if(p.inventory.getItem(it).`is`(ModItems.ORBITAL_PRECISION_STRIKE.get()))p.inventory.getItem(it).count else 0}==1,"Repeated completion granted another card")
            TacticalTerminalSessions.accept(p,root,token,TacticalTerminalSessions.SCREEN_OFF,"")
            h.assertTrue(!be.screenOn && TacticalTerminalSessions.token(p)==null,"Switching off the screen must close the session")
            val pick=ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE)
            h.assertTrue(pick.isCorrectToolForDrops(state),"Terminal must be mineable with an iron pickaxe")
            p.mainHandItem.let{p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,pick)}
            level.destroyBlock(upper,true,p)
            val drops=level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity::class.java,net.minecraft.world.phys.AABB(root).inflate(4.0))
            h.assertTrue(drops.sumOf{if(it.item.`is`(ModItems.TACTICAL_TERMINAL.get()))it.item.count else 0}==1,"Mining a terminal part must drop exactly one terminal")
            h.assertTrue((0..3).none{level.getBlockState(TacticalTerminalBlock.position(root,Direction.NORTH,it)).`is`(ModBlocks.TACTICAL_TERMINAL.get())},"Breaking one part left the other terminal blocks")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=40,batch="tactical_terminal")
    fun lowTpsPurchaseCompletesOnce(h:GameTestHelper) {
        val root=h.absolutePos(BlockPos(5,4,5)); val level=h.level
        val state=ModBlocks.TACTICAL_TERMINAL.get().defaultBlockState().setValue(TacticalTerminalBlock.FACING,Direction.NORTH)
        for(i in 0..3)level.setBlock(TacticalTerminalBlock.position(root,Direction.NORTH,i),state.setValue(TacticalTerminalBlock.PART,i),3)
        val be=level.getBlockEntity(root) as TacticalTerminalBlockEntity
        val player=net.minecraftforge.common.util.FakePlayerFactory.get(level,com.mojang.authlib.GameProfile(UUID.randomUUID(),"SlowTerminalQA"))
        player.setGameMode(GameType.SURVIVAL);player.setPos(root.x+.5,root.y.toDouble(),root.z+1.5)
        player.inventory.setItem(0,ItemStack(ModItems.COMMON_SAMPLE.get(),50));player.inventory.setItem(1,ItemStack(ModItems.RARE_SAMPLE.get(),15))
        TacticalTerminalSessions.open(player,be);val token=TacticalTerminalSessions.token(player)!!;val id="orbital_precision_strike"
        val start=10_000_000_000L;val duration=TacticalTerminalHold.DURATION_TICKS*50_000_000L
        TacticalTerminalSessions.accept(player,root,token,0,id,start)
        TacticalTerminalSessions.accept(player,root,token,2,id,start+duration-1)
        h.assertTrue(player.inventory.getItem(0).count==50,"Early completion spent samples")
        TacticalTerminalSessions.accept(player,root,token,2,id,start+duration)
        TacticalTerminalSessions.accept(player,root,token,2,id,start+duration+1)
        h.assertTrue((0 until player.inventory.containerSize).sumOf{if(player.inventory.getItem(it).`is`(ModItems.ORBITAL_PRECISION_STRIKE.get()))player.inventory.getItem(it).count else 0}==1,"Slow server did not grant exactly one card")
        h.assertTrue((0 until player.inventory.containerSize).none{player.inventory.getItem(it).`is`(ModItems.COMMON_SAMPLE.get()) || player.inventory.getItem(it).`is`(ModItems.RARE_SAMPLE.get())},"Slow server did not spend the samples")
        h.succeed()
    }

}
