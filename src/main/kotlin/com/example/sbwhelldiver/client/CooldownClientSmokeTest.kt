package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.StratagemBeacon
import com.example.sbwhelldiver.entity.StratagemMarker
import com.example.sbwhelldiver.stratagem.*
import net.minecraft.client.Minecraft
import net.minecraft.client.Screenshot
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import java.util.UUID

/** Opt-in packet/GUI probe in the isolated IntegrationTest save. */
@Mod.EventBusSubscriber(modid=MOD_ID, value=[Dist.CLIENT])
object CooldownClientSmokeTest {
    private var started=false
    @Volatile private var prepared=false
    private var tick=0
    private val area=AABB(980.0,245.0,980.0,1040.0,1800.0,1040.0)
    private fun clean(server:net.minecraft.server.MinecraftServer) {
        server.overworld().getEntities(null,area){it is AbstractHellpodEntity || it is StratagemBeacon || it is StratagemMarker ||
            it is com.example.sbwhelldiver.entity.HellpodCapEntity}.forEach{it.discard()}
    }
    private fun capture(mc:Minecraft,name:String) {
        Screenshot.grab(mc.gameDirectory,"cooldown_$name.png",mc.mainRenderTarget){LOGGER.info("COOLDOWN_SCREENSHOT: {}",it.string)}
    }
    @SubscribeEvent fun tick(event:TickEvent.ClientTickEvent) {
        if(!java.lang.Boolean.getBoolean("sbw_helldiver.cooldownSmokeTest") || event.phase!=TickEvent.Phase.END)return
        val mc=Minecraft.getInstance();val player=mc.player?:return;val server=mc.singleplayerServer?:return
        if(!started) {
            started=true;mc.options.hideGui=false;mc.options.guiScale().set(2);mc.resizeDisplay()
            server.execute {
                val p=server.playerList.getPlayer(player.uuid)!!;val level=server.overworld()
                clean(server);CooldownSavedData.get(server).players.remove(p.uuid)
                level.dayTime=1000
                for(x in 995..1020)for(z in 995..1020)level.setBlock(BlockPos(x,256,z),Blocks.BEDROCK.defaultBlockState(),2)
                p.teleportTo(level,1004.5,257.0,1004.5,0f,45f)
                p.setGameMode(net.minecraft.world.level.GameType.CREATIVE)
                p.inventory.clearContent();p.inventory.selected=0
                val stack=ItemStack(ModItems.BEACON.get())
                BeaconData.ensureIdentity(stack)
                BeaconData.setItems(stack,listOf(ModItems.MACHINE_GUN_SENTRY,ModItems.EAGLE_AIRSTRIKE,ModItems.GATLING_SENTRY,ModItems.EAGLE_STRAFING_RUN).map{ItemStack(it.get())})
                p.inventory.setItem(0,stack);p.inventoryMenu.broadcastChanges()
                StratagemCooldowns.change(server,p.uuid){s,_->s.equip(StratagemCooldowns.equipped(p))}
                StratagemCooldowns.sync(p);prepared=true
            }
            return
        }
        if(!prepared)return
        tick++
        if(tick==10){StratagemClient.testHold=true;capture(mc,"ready")}
        if(tick in 20..32 && (tick-20)%3==0)StratagemClient.input(Stratagems.get("machine_gun_sentry")!!.code[(tick-20)/3])
        if(tick==38){
            check(BeaconData.selected(player.mainHandItem)?.id=="machine_gun_sentry"){"Real server input did not activate MG"}
            StratagemClient.testHold=false;check(StratagemClient.throwSelected())
        }
        if(tick==40)StratagemClient.testHold=true
        if(tick==80){check(StratagemCooldownClient.visual("machine_gun_sentry").phase==CallPhase.ARRIVING);capture(mc,"arrival")}
        if(tick==165){
            check(StratagemCooldownClient.visual("machine_gun_sentry").phase==CallPhase.COOLDOWN){"Real landed call never entered cooldown"}
            capture(mc,"cooldown")
            LOGGER.info("COOLDOWN_REAL_THROW_PASSED: validated input, real beacon landing, owner snapshot, arrival then cooldown")
            // Move only ledger visual fixtures into their midpoints to inspect the wipe direction.
            server.execute {
                val p=server.playerList.getPlayer(player.uuid)!!;val time=StratagemCooldowns.now(server);val s=StratagemCooldowns.state(p)
                s.entry("machine_gun_sentry").apply{phase=CallPhase.COOLDOWN;start=time-200;end=time+200}
                s.reserve("gatling_sentry",UUID.randomUUID(),time)
                s.entry("gatling_sentry").apply{phase=CallPhase.ARRIVING;start=time-200;end=time+200}
                StratagemCooldowns.sync(p)
            }
        }
        if(tick==180)capture(mc,"half_wipes")
        if(tick in 190..202 && (tick-190)%3==0)StratagemClient.input(Stratagems.eagleRearm.code[(tick-190)/3])
        if(tick==215){
            check(StratagemCooldownClient.visual("eagle_airstrike").rearming && StratagemCooldownClient.visual("eagle_strafing_run").rearming)
            check(BeaconData.selected(player.mainHandItem)==null){"Rearm activated a throwable beacon"}
            capture(mc,"rearm")
            LOGGER.info("COOLDOWN_REARM_HUD_PASSED: top command enters shared rearm through real input packets; independent uses shown")
        }
        if(tick==230){
            StratagemClient.testHold=false
            server.execute{clean(server);CooldownSavedData.get(server).players.remove(player.uuid);CooldownSavedData.get(server).setDirty()}
            LOGGER.info("COOLDOWN_CLIENT_PASSED: probe entities and ledger removed")
        }
        if(tick==245)mc.stop()
        if(tick>300)error("Cooldown client test timed out")
    }
}
