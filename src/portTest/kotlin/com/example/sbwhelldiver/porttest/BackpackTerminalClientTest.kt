package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.client.TacticalTerminalDisplay
import com.example.sbwhelldiver.client.TacticalTerminalScreen
import com.example.sbwhelldiver.init.ModBlocks
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.stratagem.Stratagems
import com.example.sbwhelldiver.survival.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.client.gui.screens.TitleScreen
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.Difficulty
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.levelgen.WorldOptions
import net.minecraft.world.level.levelgen.presets.WorldPresets
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

/** Real client UI, network request and server inventory verification; excluded from release JARs. */
object BackpackTerminalClientTest {
    private var started=false
    private var ticks=0
    private val launched=System.currentTimeMillis()
    private val root=BlockPos(0,80,0)
    private val order=listOf("guard_dog","gas_guard_dog","flame_guard_dog","laser_guard_dog","stun_guard_dog")
    private val purchased=java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    @Volatile private var insufficientVerified=false
    fun tick(){
        val mc=Minecraft.getInstance()
        if(System.currentTimeMillis()-launched>240000){LOGGER.error("BACKPACK_TERMINAL_TIMEOUT");mc.stop();return}
        if(!started && mc.screen is TitleScreen && mc.overlay==null){
            started=true;mc.options.pauseOnLostFocus=false;mc.options.languageCode="zh_cn"
            mc.createWorldOpenFlows().createFreshLevel("port-client-test",LevelSettings("Backpack terminal verification",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,GameRules(),WorldDataConfiguration.DEFAULT),WorldOptions(1,false,false),{a->a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions()},mc.screen!!);return
        }
        val p=mc.player?:return;val server=mc.singleplayerServer?:return;ticks++
        if(ticks==1)server.execute{
            val level=server.overworld()
            level.gameRules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server)
            level.gameRules.getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.dayTime=6000
            for(x in -7..7)for(z in -7..7)level.setBlock(BlockPos(x,79,z),Blocks.SMOOTH_STONE.defaultBlockState(),3)
            level.setDefaultSpawnPos(BlockPos(1,80,-4),0f)
            val player=server.playerList.getPlayer(p.uuid)!!
            player.setGameMode(GameType.CREATIVE);player.teleportTo(level,1.0,80.0,-3.0,0f,8f);player.inventory.clearContent()
            val state=ModBlocks.TACTICAL_TERMINAL.get().defaultBlockState().setValue(TacticalTerminalBlock.FACING,Direction.NORTH)
            for(i in 0..3)level.setBlock(TacticalTerminalBlock.position(root,Direction.NORTH,i),state.setValue(TacticalTerminalBlock.PART,i),3)
            seed(player,order[0],true)
            LOGGER.info("BACKPACK_TERMINAL_FIXTURE_READY")
        }
        if(ticks==90)mc.gameMode!!.useItemOn(p,InteractionHand.MAIN_HAND,BlockHitResult(Vec3(1.0,80.95,0.0),Direction.NORTH,root,false))
        if(ticks==120){
            check(mc.screen is TacticalTerminalScreen)
            check(net.minecraft.network.chat.Component.translatable("gui.sbw_helldiver.category.backpack").string=="背包战备")
            check(net.minecraft.network.chat.Component.translatable("item.sbw_helldiver.gas_guard_dog_stratagem").string=="腐息")
            scrollDown();scrollDown()
        }
        if(ticks==130)capture("backpack-category.png")
        if(ticks==140)click(100.0,124.0)
        if(ticks==150)server.execute{server.playerList.getPlayer(p.uuid)!!.setGameMode(GameType.SURVIVAL)}
        if(ticks==155){check(TacticalTerminalOffers.ids(TerminalCategory.BACKPACK)==order);capture("backpack-prices.png")}
        if(ticks==170)click(100.0,50.0)
        if(ticks==230){
            (mc.screen as TacticalTerminalScreen).mouseReleased(0.0,0.0,0)
            server.execute{
                val player=server.playerList.getPlayer(p.uuid)!!;val cost=TacticalTerminalOffers.byId.getValue(order[0]).cost
                check(cardCount(player,order[0])==0)
                check(SampleStorage.total(player,0).toInt()==cost.common-1)
                check(SampleStorage.total(player,1).toInt()==cost.rare)
                check(SampleStorage.total(player,2).toInt()==cost.superSample)
                insufficientVerified=true;LOGGER.info("BACKPACK_TERMINAL_INSUFFICIENT_VERIFIED")
            }
            capture("backpack-insufficient.png")
        }
        for((index,id) in order.withIndex()){
            val start=270+index*100
            if(ticks==start-15)server.execute{seed(server.playerList.getPlayer(p.uuid)!!,id,false)}
            if(index==4 && ticks==start-5)scrollDown()
            if(ticks==start)click(100.0,50.0+minOf(index,3)*29)
            if(ticks==start+15)capture("backpack-hold-$id.png")
            if(ticks==start+60){
                (mc.screen as TacticalTerminalScreen).mouseReleased(0.0,0.0,0)
                server.execute{
                    val player=server.playerList.getPlayer(p.uuid)!!
                    check(cardCount(player,id)==1){"Wrong card or duplicate grant: $id"}
                    check((0..2).all{SampleStorage.total(player,it).signum()==0}){"Wrong sample cost for $id"}
                    purchased.add(id);LOGGER.info("BACKPACK_TERMINAL_PURCHASE_VERIFIED {}",id)
                }
                capture("backpack-purchased-$id.png")
            }
        }
        if(ticks==760){
            check(insufficientVerified && purchased.size==5){"Incomplete purchase verification: $purchased"}
            server.execute{val player=server.playerList.getPlayer(p.uuid)!!;player.setGameMode(GameType.CREATIVE);player.inventory.clearContent();player.containerMenu.broadcastChanges()}
        }
        if(ticks==780)click(100.0,50.0)
        if(ticks==840){
            (mc.screen as TacticalTerminalScreen).mouseReleased(0.0,0.0,0)
            server.execute{
                val player=server.playerList.getPlayer(p.uuid)!!
                check(cardCount(player,"gas_guard_dog")==1){"Creative empty-inventory UI purchase failed"}
                check((0..2).all{SampleStorage.total(player,it).signum()==0})
                LOGGER.info("BACKPACK_TERMINAL_CREATIVE_EMPTY_VERIFIED")
                val container=ItemStack(ModItems.SAMPLE_CONTAINER.get())
                for(i in 0..2)SampleStorage.add(container,i,java.math.BigInteger.ONE)
                player.inventory.setItem(8,container)
                val token=TacticalTerminalSessions.token(player)!!
                fun buy(id:String){
                    val time=System.nanoTime()
                    TacticalTerminalSessions.accept(player,root,token,0,id,time)
                    TacticalTerminalSessions.accept(player,root,token,2,id,time+TacticalTerminalHold.DURATION_TICKS*50_000_000L)
                }
                buy("guard_dog");check(cardCount(player,"guard_dog")==1)
                buy("destroyer_call_40");buy("destroyer_call_40")
                check(com.example.sbwhelldiver.stratagem.SuperDestroyers.state(player).calls(40)==1){"Creative destroyer limit failed"}
                repeat(6){buy("resupply")}
                check(com.example.sbwhelldiver.stratagem.StratagemCooldowns.state(player).entry("resupply").remaining==5){"Creative resupply limit failed"}
                check((0..2).all{SampleStorage.total(player,it)==java.math.BigInteger.ONE}){"Creative purchase consumed container samples"}
                LOGGER.info("BACKPACK_TERMINAL_CREATIVE_CONTAINER_AND_LIMITS_VERIFIED")
            }
            capture("backpack-creative-free.png")
        }
        if(ticks==880){mc.screen?.onClose();LOGGER.info("BACKPACK_TERMINAL_COMPLETED");mc.stop()}
    }
    private fun seed(player:ServerPlayer,id:String,short:Boolean){
        player.inventory.clearContent()
        val c=TacticalTerminalOffers.byId.getValue(id).cost
        for((index,total) in listOf(c.common-if(short)1 else 0,c.rare,c.superSample).withIndex()){
            var remaining=total
            while(remaining>0){val amount=minOf(remaining,64);check(player.inventory.add(ItemStack(SampleStorage.type(index),amount)));remaining-=amount}
        }
        player.inventory.setChanged();player.containerMenu.broadcastChanges()
    }
    private fun cardCount(player:ServerPlayer,id:String):Int {
        val item=BuiltInRegistries.ITEM.get(Stratagems.get(id)!!.itemId)
        return (0 until player.inventory.containerSize).sumOf{player.inventory.getItem(it).let{s->if(s.`is`(item))s.count else 0}}
    }
    private fun scrollDown(){(Minecraft.getInstance().screen as TacticalTerminalScreen).mouseScrolled(0.0,0.0,0.0,-1.0)}
    private fun click(x:Double,y:Double){
        val mc=Minecraft.getInstance();val screen=mc.screen as TacticalTerminalScreen
        val be=mc.level!!.getBlockEntity(root) as TacticalTerminalBlockEntity
        val point=TacticalTerminalDisplay.screenPoint(be,x,y)?:error("Terminal behind camera")
        val picked=TacticalTerminalDisplay.pointer(be,point.first,point.second)?:error("Screen picking failed")
        check(kotlin.math.abs(picked.first-x)<.1 && kotlin.math.abs(picked.second-y)<.1)
        screen.mouseClicked(point.first,point.second,0)
    }
    private fun capture(name:String){val mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.mainRenderTarget){LOGGER.info("BACKPACK_TERMINAL_SCREENSHOT {}",it.string)}}
}
