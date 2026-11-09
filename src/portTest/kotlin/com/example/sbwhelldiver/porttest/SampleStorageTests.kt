package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.menu.SampleContainerMenu
import com.example.sbwhelldiver.stratagem.*
import com.example.sbwhelldiver.survival.*
import com.mojang.authlib.GameProfile
import net.minecraft.core.*
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.*
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.*
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.gametest.*
import java.math.BigInteger
import java.util.UUID

@GameTestHolder("sbw_helldiver_samples")
@PrefixGameTestTemplate(false)
object SampleStorageTests {
    private fun player(h:GameTestHelper)=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"SampleQA")).also{it.inventory.clearContent();it.setGameMode(GameType.SURVIVAL)}
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun unboundedStorageAndInventorySafety(h:GameTestHelper){
        val p=player(h);val bag=ItemStack(ModItems.SAMPLE_CONTAINER.get());p.inventory.setItem(0,bag)
        val huge=BigInteger.TEN.pow(45);SampleStorage.add(bag,0,huge)
        val restored=ItemStack.parseOptional(h.level.registryAccess(),bag.save(h.level.registryAccess()) as CompoundTag)
        h.assertTrue(SampleStorage.count(restored,0)==huge,"Sample count overflowed or failed to save")
        p.inventory.setItem(1,ItemStack(ModItems.COMMON_SAMPLE.get(),5));p.inventory.setItem(2,ItemStack(ModItems.RARE_SAMPLE.get(),7))
        SampleStorage.collect(p,bag)
        h.assertTrue(SampleStorage.count(bag,0)==huge+BigInteger.valueOf(5)&&SampleStorage.count(bag,1)==BigInteger.valueOf(7),"Collection changed sample types or amounts")
        for(i in 1..35)p.inventory.setItem(i,ItemStack(Items.STONE,64))
        SampleStorage.withdraw(p,bag,0,2304)
        h.assertTrue(SampleStorage.count(bag,0)==huge+BigInteger.valueOf(5),"Full inventory lost samples")
        p.inventory.setItem(1,ItemStack(ModItems.COMMON_SAMPLE.get(),63));p.inventory.setItem(2,ItemStack.EMPTY)
        SampleStorage.withdraw(p,bag,0,2304)
        h.assertTrue(p.inventory.getItem(1).count==64&&p.inventory.getItem(2).count==64&&SampleStorage.count(bag,0)==huge-BigInteger.valueOf(60),"Withdrawal did not respect actual free capacity")
        val menu=SampleContainerMenu(1,p.inventory,InteractionHand.MAIN_HAND)
        h.assertTrue(!menu.clickMenuButton(p,12)&&!menu.clickMenuButton(p,-1),"Invalid action accepted")
        h.assertTrue(!menu.slots.first{it.containerSlot==0}.mayPickup(p),"Opened container can be removed through its menu")
        p.inventory.setItem(0,ItemStack(ModItems.SAMPLE_CONTAINER.get()))
        h.assertTrue(!menu.stillValid(p),"Replacing the opened container retained access")
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun containerPurchasesAndIndependentCaps(h:GameTestHelper){
        val p=player(h);val root=h.absolutePos(BlockPos(5,4,5))
        val state=ModBlocks.TACTICAL_TERMINAL.get().defaultBlockState().setValue(TacticalTerminalBlock.FACING,Direction.NORTH)
        for(i in 0..3)h.level.setBlock(TacticalTerminalBlock.position(root,Direction.NORTH,i),state.setValue(TacticalTerminalBlock.PART,i),3)
        p.setPos(root.x+.5,root.y.toDouble(),root.z+1.5)
        val bag=ItemStack(ModItems.SAMPLE_CONTAINER.get());p.inventory.setItem(0,bag)
        SampleStorage.add(bag,0,BigInteger.valueOf(2000));SampleStorage.add(bag,1,BigInteger.valueOf(1000));SampleStorage.add(bag,2,BigInteger.valueOf(20))
        TacticalTerminalSessions.open(p,h.level.getBlockEntity(root) as TacticalTerminalBlockEntity);val token=TacticalTerminalSessions.token(p)!!
        var clock=10_000_000_000L
        fun buy(id:String){TacticalTerminalSessions.accept(p,root,token,0,id,clock);clock+=TacticalTerminalHold.DURATION_TICKS*50_000_000L;TacticalTerminalSessions.accept(p,root,token,2,id,clock);TacticalTerminalSessions.accept(p,root,token,2,id,clock+1);clock+=1_000_000_000L}
        buy("destroyer_call_40")
        h.assertTrue(SuperDestroyers.state(p).calls(40)==1&&SampleStorage.count(bag,0)==BigInteger.valueOf(1600)&&SampleStorage.count(bag,1)==BigInteger.valueOf(880)&&SampleStorage.count(bag,2)==BigInteger.valueOf(15),"40-minute purchase failed or repeated its payment")
        val before=bag.copy();buy("destroyer_call_40")
        h.assertTrue(ItemStack.isSameItemSameComponents(before,bag),"Capped purchase charged samples")
        repeat(3){buy("destroyer_call_20")};buy("destroyer_call")
        h.assertTrue(SuperDestroyers.state(p).calls(20)==3&&SuperDestroyers.state(p).calls(5)==1,"Duration caps were not independent")
        buy("orbital_precision_strike")
        h.assertTrue(p.inventory.items.count{it.`is`(ModItems.ORBITAL_PRECISION_STRIKE.get())}==1,"Container samples were not accepted for stratagem purchase")
        SampleStorage.take(bag,2,Int.MAX_VALUE);val unavailable=bag.copy();buy("orbital_laser")
        h.assertTrue(ItemStack.isSameItemSameComponents(unavailable,bag),"Insufficient currency partially consumed other samples")
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun bindingOwnershipAndPersistence(h:GameTestHelper){
        val p=player(h);val other=player(h);val root=h.absolutePos(BlockPos(5,4,5))
        for(x in -5..5)for(z in -5..5)for(y in -1..7)h.level.setBlock(root.offset(x,y,z),if(y<0)Blocks.STONE.defaultBlockState()else Blocks.AIR.defaultBlockState(),3)
        val data=CompoundTag().also{it.putUUID("MachineOwner",p.uuid)}
        val machine=MachineStructure.place(h.level,root,MachineDeployment.BIOPROCESSOR,Direction.NORTH,data,null)!!
        val bag=ItemStack(ModItems.SAMPLE_CONTAINER.get());p.setItemInHand(InteractionHand.MAIN_HAND,bag);p.isShiftKeyDown=true
        val hit=BlockHitResult(Vec3.atCenterOf(root),Direction.NORTH,root,false)
        (bag.item as SampleContainerItem).useOn(UseOnContext(p,InteractionHand.MAIN_HAND,hit))
        val id=SampleStorage.identity(bag)
        h.assertTrue(machine.boundContainer==id&&!SampleStorage.automatic(bag),"Machine binding toggled automatic collection instead")
        other.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(ModItems.SAMPLE_CONTAINER.get()));other.isShiftKeyDown=true
        (bag.item as SampleContainerItem).useOn(UseOnContext(other,InteractionHand.MAIN_HAND,hit))
        h.assertTrue(machine.boundContainer==id,"Another player replaced machine binding")
        val saved=machine.contents();machine.restoreContents(saved)
        h.assertTrue(machine.owner==p.uuid&&machine.boundContainer==id,"Packing data lost machine owner or destination")
        h.assertTrue(SampleStorage.receiver(machine)==null,"Offline owner was treated as an online receiver")
        (bag.item as SampleContainerItem).useOn(UseOnContext(p,InteractionHand.MAIN_HAND,hit))
        h.assertTrue(machine.boundContainer==null,"Second use did not unbind")
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun durationMigrationAndCommandOrder(h:GameTestHelper){
        val legacy=DestroyerState.load(CompoundTag().also{it.putInt("CallsRemaining",3)})
        h.assertTrue(legacy.calls(5)==3&&legacy.calls(20)==0&&legacy.calls(40)==0&&legacy.residenceTicks==6000L,"Old save migrated incorrectly")
        for((minutes,cap)in listOf(5 to 5,20 to 3,40 to 1)){
            val state=DestroyerState();repeat(cap){h.assertTrue(state.addCall(minutes),"Call refill rejected before cap")}
            h.assertTrue(!state.addCall(minutes+1)&&!state.addCall(minutes),"Invalid duration or over-cap refill accepted")
            h.assertTrue(state.selectDuration(minutes)&&state.spendCall(minutes),"Selected call could not be spent")
            val anchor=DestroyerAnchor.at("minecraft:overworld",Vec3.ZERO,0,240.0,180.0)
            state.schedule(anchor,100);state.advance(160)
            val loaded=DestroyerState.load(state.save());val deadline=160+minutes*1200L
            h.assertTrue(!loaded.expired(deadline-1)&&loaded.expired(deadline),"Selected residence deadline changed on reload")
            loaded.schedule(anchor.copy(direction=1),200);loaded.advance(260)
            h.assertTrue(loaded.residentSince==160L&&loaded.anchor!!.center==Vec3.ZERO&&loaded.residenceTicks==minutes*1200L,"Reposition reset duration or coverage center")
        }
        val p=player(h);SuperDestroyers.state(p).addCall(20)
        val beacon=ItemStack(ModItems.BEACON.get());BeaconData.ensureIdentity(beacon);p.setItemInHand(InteractionHand.MAIN_HAND,beacon)
        val identity=BeaconData.identity(beacon)!!
        for(arrow in Stratagems.destroyerCall.code)BeaconInput.accept(p,InteractionHand.MAIN_HAND,identity,arrow)
        h.assertTrue(BeaconInput.menuStage(p.uuid)==3,"Call skipped duration selection")
        BeaconInput.accept(p,InteractionHand.MAIN_HAND,identity,Stratagems.UP)
        h.assertTrue(BeaconInput.menuStage(p.uuid)==3,"Unavailable duration advanced the menu")
        BeaconInput.accept(p,InteractionHand.MAIN_HAND,identity,Stratagems.LEFT)
        h.assertTrue(BeaconInput.menuStage(p.uuid)==1,"Duration did not lead to direction selection")
        BeaconInput.accept(p,InteractionHand.MAIN_HAND,identity,Stratagems.UP)
        h.assertTrue(SuperDestroyers.state(p).calls(20)==0&&SuperDestroyers.state(p).residenceTicks==24000L&&BeaconInput.menuStage(p.uuid)==0,"Bearing did not launch exactly the selected duration")
        h.succeed()
    }
}
