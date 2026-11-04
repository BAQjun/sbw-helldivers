package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.survival.*
import com.example.sbwhelldiver.init.*
import net.minecraft.core.*
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.*
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.entity.ChestBlockEntity
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.gametest.*

@GameTestHolder("sbw_helldiver")
@PrefixGameTestTemplate(false)
object MachineGameTests {
    @JvmStatic @GameTest(template="port_test",timeoutTicks=30)
    fun allMannedEmplacementsPackWithEitherCrowbarHand(h:GameTestHelper) {
        // A local Player can ride without joining a fake network connection.
        val rider=object:net.minecraft.world.entity.player.Player(h.level,h.absolutePos(BlockPos(5,8,5)),0f,com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"RiderQA")) {
            override fun isSpectator()=false
            override fun isCreative()=false
        }
        for(type in listOf(ModEntities.HMG_EMPLACEMENT,ModEntities.ANTI_TANK_EMPLACEMENT,ModEntities.GRENADIER_BATTLEMENT))for(hand in InteractionHand.entries) {
            val p=player(h);val e=type.get().create(h.level)!!
            e.setPos(net.minecraft.world.phys.Vec3.atBottomCenterOf(h.absolutePos(BlockPos(5,8,5))))
            val saved=CompoundTag();e.saveWithoutId(saved);saved.putInt("HellpodPhase",com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase.ACTIVE.ordinal);saved.putInt("HmgAmmo",17);e.load(saved);h.level.addFreshEntity(e)
            p.setItemInHand(hand,ItemStack(com.atsuishio.superbwarfare.init.ModItems.CROWBAR.get()));p.isShiftKeyDown=true
            h.assertTrue(rider.startRiding(e,true),"Could not mount emplacement fixture")
            p.interactOn(e,hand);h.assertTrue(!e.isRemoved,"Occupied emplacement packed under its operator")
            e.ejectPassengers()
            h.assertTrue(p.interactOn(e,hand).consumesAction()&&e.isRemoved,"Sneaking crowbar failed to pack ${e.type} from $hand")
            val boxes=(0 until p.inventory.containerSize).map{p.inventory.getItem(it)}.filter{it.`is`(com.atsuishio.superbwarfare.init.ModBlocks.CONTAINER.get().asItem())}
            h.assertTrue(boxes.size==1,"Emplacement did not return exactly one container")
            val packed=boxes.single().get(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA)!!.copyTag().getCompound("Entity")
            val restored=type.get().create(h.level)!!;restored.load(packed)
            h.assertTrue(restored.ammoRemaining==17&&restored.phase==com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase.ACTIVE,"Packed emplacement lost ammunition or deployment state")
            restored.discard()
        }
        rider.discard();h.succeed()
    }
    private fun fixture(h:GameTestHelper):BlockPos {
        val root=h.absolutePos(BlockPos(5,4,5))
        for(x in -5..5)for(z in -5..5)for(y in -1..7)h.level.setBlock(root.offset(x,y,z),if(y==-1)Blocks.STONE.defaultBlockState()else Blocks.AIR.defaultBlockState(),3)
        return root
    }
    private fun player(h:GameTestHelper)=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.level,com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"MachineQA"))
    private fun energy(h:GameTestHelper,root:BlockPos,m:MachineDeployment)=h.level.getCapability(Capabilities.EnergyStorage.BLOCK,root.offset(MachineLayout.portOffset(m,MachineLayout.Port.ENERGY)),Direction.NORTH)!!
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun sneakingCrowbarPacksThroughRealBlockInteraction(h:GameTestHelper) {
        for(m in MachineDeployment.entries)for(hand in InteractionHand.entries) {
            val root=fixture(h)
            val be=MachineStructure.place(h.level,root,m,Direction.NORTH,CompoundTag(),null)!!;be.readyAt=0
            be.inventory.setStackInSlot(0,ItemStack(if(m==MachineDeployment.BIOPROCESSOR)Items.ROTTEN_FLESH else Items.RAW_IRON,64))
            energy(h,root,m).receiveEnergy(150000,false);repeat(13){be.serverTick()}
            be.inventory.setStackInSlot(9,ItemStack(ModItems.COMMON_SAMPLE.get(),7))
            val saved=be.contents();val parts=MachineLayout.shapes(m,Direction.NORTH).keys
            val hitPos=root.offset(parts.first{it!=BlockPos.ZERO})
            val hit=net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(hitPos),Direction.NORTH,hitPos,false)
            val p=player(h);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL)
            val crowbar=ItemStack(com.atsuishio.superbwarfare.init.ModItems.CROWBAR.get());p.setItemInHand(hand,crowbar)
            p.isShiftKeyDown=false
            h.assertTrue(!p.gameMode.useItemOn(p,h.level,crowbar,hand,hit).consumesAction() && !be.isRemoved,"Ordinary crowbar click packed machine")
            p.isShiftKeyDown=true
            h.assertTrue(p.gameMode.useItemOn(p,h.level,crowbar,hand,hit).consumesAction(),"Sneaking block interaction was skipped: $m $hand")
            h.assertTrue(parts.none{h.level.getBlockState(root.offset(it)).`is`(ModBlocks.SAMPLE_MACHINE.get())},"Packing left machine parts")
            val drop=h.level.getEntitiesOfClass(ItemEntity::class.java,be.bounds()).single()
            h.assertTrue(drop.item.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)!!.copyTag()==saved,"Packing lost inventory, energy, pending output or progress")
            val packed=drop.item.copy();drop.discard();p.isShiftKeyDown=false;p.setItemInHand(hand,packed)
            val ground=root.below();val placeHit=net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(ground),Direction.UP,ground,false)
            h.assertTrue(p.gameMode.useItemOn(p,h.level,packed,hand,placeHit).consumesAction()&&packed.isEmpty,"Packed container cannot be placed")
            val box=h.level.getEntitiesOfClass(MachineContainerEntity::class.java,be.bounds()).single()
            p.setItemInHand(hand,crowbar);box.interact(p,hand)
            val restored=h.level.getBlockEntity(root) as SampleMachineBlockEntity
            h.assertTrue(restored.contents()==saved,"Reopening packed machine changed its contents")
            box.discard();MachineStructure.remove(restored,false)
        }
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun portsAndFiveSecondEnergyBatch(h:GameTestHelper) {
        val root=fixture(h);val m=MachineDeployment.BIOPROCESSOR
        val be=MachineStructure.place(h.level,root,m,Direction.NORTH,CompoundTag(),null)!!;be.readyAt=0
        val inputPos=root.offset(MachineLayout.portOffset(m,MachineLayout.Port.INPUT));val outputPos=root.offset(MachineLayout.portOffset(m,MachineLayout.Port.OUTPUT))
        val input=h.level.getCapability(Capabilities.ItemHandler.BLOCK,inputPos,Direction.NORTH)!!
        val output=h.level.getCapability(Capabilities.ItemHandler.BLOCK,outputPos,Direction.NORTH)!!
        h.assertTrue(Direction.entries.all{h.level.getCapability(Capabilities.ItemHandler.BLOCK,inputPos,it)!=null},"Pipe cannot access input port from every side")
        h.assertTrue(input.insertItem(0,ItemStack(Items.DIRT),false).count==1,"Invalid input accepted")
        h.assertTrue(input.insertItem(0,ItemStack(Items.DRAGON_EGG),false).isEmpty,"Dragon egg rejected")
        h.assertTrue(input.extractItem(0,1,false).isEmpty,"Input allows extraction")
        val power=energy(h,root,m)
        h.assertTrue(power.maxEnergyStored==20_000_000,"Energy capacity differs from large battery")
        power.receiveEnergy(150000,false)
        repeat(99){be.serverTick()}
        h.assertTrue(be.progress==99&&output.getStackInSlot(0).isEmpty,"Batch completed early")
        h.assertTrue(power.energyStored==1500,"Wrong per-tick power cost")
        be.serverTick()
        h.assertTrue(output.getStackInSlot(0).`is`(ModItems.SUPER_SAMPLE.get())&&output.getStackInSlot(0).count==50,"Dragon egg output differs from 50 super samples")
        h.assertTrue(power.energyStored==0&&!output.insertItem(0,ItemStack(Items.DIRT),false).isEmpty,"Output insertion or energy debit wrong")
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun interruptedBatchPackingAndStaleCapabilities(h:GameTestHelper) {
        val root=fixture(h);val m=MachineDeployment.BIOPROCESSOR
        val be=MachineStructure.place(h.level,root,m,Direction.NORTH,CompoundTag(),null)!!;be.readyAt=0
        be.inventory.setStackInSlot(0,ItemStack(Items.DRAGON_EGG));val power=energy(h,root,m);power.receiveEnergy(15000,false)
        repeat(15){be.serverTick()}
        h.assertTrue(be.progress==10&&be.energy==0,"Unpowered processing did not pause")
        val saved=be.contents();MachineStructure.remove(be,true)
        h.assertTrue(power.receiveEnergy(5000,false)==0,"Removed capability still receives energy")
        val drops=h.level.getEntitiesOfClass(ItemEntity::class.java,MachineLayout.containerBounds(m).move(root.x+.5,root.y.toDouble(),root.z+.5))
        val stack=drops.single().item
        h.assertTrue(stack.`is`(ModItems.BIOPROCESSOR_CONTAINER.get()),"Packing did not drop one container")
        h.assertTrue(stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)!!.copyTag()==saved,"Packing lost machine contents")
        drops.forEach{it.discard()}
        val restored=MachineStructure.place(h.level,root,m,Direction.NORTH,saved,null)!!;restored.readyAt=0
        h.assertTrue(restored.progress==10&&restored.inventory.getStackInSlot(0).isEmpty,"Pending batch duplicated its input")
        energy(h,root,m).receiveEnergy(135000,false);repeat(90){restored.serverTick()}
        h.assertTrue(restored.inventory.getStackInSlot(9).count==50&&restored.energy==0,"Packed batch did not resume correctly")
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun savedPendingBatchResumesWithoutReroll(h:GameTestHelper) {
        val root=fixture(h);val m=MachineDeployment.BIOPROCESSOR
        val be=MachineStructure.place(h.level,root,m,Direction.NORTH,CompoundTag(),null)!!;be.readyAt=0
        be.inventory.setStackInSlot(0,ItemStack(Items.ROTTEN_FLESH,5));energy(h,root,m).receiveEnergy(150000,false)
        repeat(37){be.serverTick()}
        val saved=be.saveWithoutMetadata(h.level.registryAccess())
        val expected=ItemStack.parseOptional(h.level.registryAccess(),saved.getCompound("Pending"))
        h.assertTrue(!expected.isEmpty&&be.progress==37,"Batch was not saved in flight")
        val loaded=SampleMachineBlockEntity(root,be.blockState)
        loaded.loadWithComponents(saved,h.level.registryAccess());h.level.setBlockEntity(loaded)
        h.assertTrue(loaded.progress==37&&loaded.energy==94500&&loaded.inventory.getStackInSlot(0).isEmpty,"Reload lost progress, energy or reserved input")
        repeat(63){loaded.serverTick()}
        val result=loaded.inventory.getStackInSlot(9)
        h.assertTrue(ItemStack.isSameItemSameComponents(expected,result)&&expected.count==result.count&&loaded.energy==0,"Reload rerolled or duplicated the batch")
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun pullPushAndFullContainerRetention(h:GameTestHelper) {
        val root=fixture(h);val m=MachineDeployment.BIOPROCESSOR
        val be=MachineStructure.place(h.level,root,m,Direction.NORTH,CompoundTag(),null)!!;be.readyAt=0
        val from=root.offset(MachineLayout.portOffset(m,MachineLayout.Port.INPUT)).north();val to=root.offset(MachineLayout.portOffset(m,MachineLayout.Port.OUTPUT)).north()
        h.level.setBlock(from,Blocks.CHEST.defaultBlockState(),3);h.level.setBlock(to,Blocks.CHEST.defaultBlockState(),3)
        val source=h.level.getBlockEntity(from) as ChestBlockEntity;val target=h.level.getBlockEntity(to) as ChestBlockEntity
        source.setItem(0,ItemStack(Items.ROTTEN_FLESH,5));source.setItem(1,ItemStack(Items.DIRT,5))
        for(i in 0 until target.containerSize)target.setItem(i,ItemStack(Items.COBBLESTONE,64))
        be.inventory.setStackInSlot(9,ItemStack(ModItems.COMMON_SAMPLE.get(),5))
        h.runAtTickTime(6){
            h.assertTrue(source.getItem(0).isEmpty&&source.getItem(1).count==5,"Auto input pulled invalid ingredients or failed")
            h.assertTrue(be.inventory.getStackInSlot(9).count==5,"Full output container lost samples")
            target.setItem(0,ItemStack.EMPTY)
        }
        h.runAtTickTime(12){
            h.assertTrue(target.getItem(0).count==5&&be.inventory.getStackInSlot(9).isEmpty,"Auto output failed")
            h.succeed()
        }
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=90)
    fun containerWaitsForCrowbarAndShellFinishes(h:GameTestHelper) {
        val root=fixture(h);val m=MachineDeployment.BIOPROCESSOR
        val box=ModEntities.MACHINE_CONTAINER.get().create(h.level)!!;box.configure(m,root,Direction.NORTH,CompoundTag(),true,h.level.gameTime+5);h.level.addFreshEntity(box)
        h.assertTrue(MachineLayout.shapes(m,Direction.NORTH).keys.none{h.level.getBlockState(root.offset(it)).`is`(ModBlocks.SAMPLE_MACHINE.get())},"Machine generated during descent")
        h.runAtTickTime(8){
            h.assertTrue(box.phase==MachineContainerEntity.CLOSED&&h.level.getBlockEntity(root)==null,"Closed container generated a machine")
            val p=player(h);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(com.atsuishio.superbwarfare.init.ModItems.CROWBAR.get()))
            box.interact(p,InteractionHand.MAIN_HAND)
            h.assertTrue(box.phase==MachineContainerEntity.OPENING&&!box.isRemoved&&h.level.getBlockEntity(root) is SampleMachineBlockEntity,"Crowbar did not create machine/open shell")
        }
        h.runAtTickTime(46){h.assertTrue(!box.isRemoved,"Shell disappeared before the two-second opening completed")}
        h.runAtTickTime(51){h.assertTrue(box.isRemoved,"Shell remained after the two-second opening");h.succeed()}
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun orientationFallbackAndDestructiveRemoval(h:GameTestHelper) {
        val root=fixture(h);val m=MachineDeployment.BIOPROCESSOR
        h.level.setBlock(root.offset(-2,1,0),Blocks.STONE.defaultBlockState(),3)
        h.assertTrue(MachineStructure.choose(h.level,root,m,Direction.NORTH)==Direction.EAST,"Facing fallback did not try east first")
        val be=MachineStructure.place(h.level,root,m,Direction.EAST,CompoundTag(),null)!!
        val parts=MachineLayout.shapes(m,Direction.EAST).keys
        val target=root.offset(parts.first{it!=BlockPos.ZERO});h.level.destroyBlock(target,true)
        h.assertTrue(parts.none{h.level.getBlockState(root.offset(it)).`is`(ModBlocks.SAMPLE_MACHINE.get())},"Breaking a part left other machine blocks")
        h.assertTrue(h.level.getEntitiesOfClass(ItemEntity::class.java,be.bounds()).isEmpty(),"Mining produced a machine drop")
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun acceptedSampleTablesAndSteppedRefinery(h:GameTestHelper) {
        val root=fixture(h)
        val bio=SampleRecipe.forMachine(h.level,MachineDeployment.BIOPROCESSOR);val ref=SampleRecipe.forMachine(h.level,MachineDeployment.REFINERY)
        h.assertTrue(bio.size==23&&ref.size==13,"Processing recipe count differs from accepted table")
        h.assertTrue(bio.none{it.ingredient.test(ItemStack(Items.STRING))||it.ingredient.test(ItemStack(Items.EMERALD))||it.ingredient.test(ItemStack(Items.GLOWSTONE_DUST))},"Rejected extra drops have recipes")
        val echo=bio.single{it.ingredient.test(ItemStack(Items.ECHO_SHARD))};val head=bio.single{it.ingredient.test(ItemStack(Items.DRAGON_HEAD))}
        h.assertTrue(head.outcomes==echo.outcomes,"Dragon head differs from echo shard")
        val refinery=MachineStructure.place(h.level,root,MachineDeployment.REFINERY,Direction.NORTH,CompoundTag(),null)!!
        h.assertTrue(h.level.getBlockEntity(root.offset(2,3,2))==null&&h.level.getBlockEntity(root.offset(0,3,-1))!=null,"Refinery collision is not stepped")
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=110)
    fun adjacentInputsAndManualInsertionInEveryOrientation(h:GameTestHelper) {
        val cases=MachineDeployment.entries.flatMap{m->listOf(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST).map{m to it}}
        cases.forEachIndexed{i,(m,facing)->
            lateinit var be:SampleMachineBlockEntity
            lateinit var from:ChestBlockEntity
            lateinit var below:ChestBlockEntity
            lateinit var target:ChestBlockEntity
            val ingredient=if(m==MachineDeployment.BIOPROCESSOR)Items.ROTTEN_FLESH else Items.RAW_IRON
            h.runAtTickTime(1L+i*12){
                val root=fixture(h);be=MachineStructure.place(h.level,root,m,facing,CompoundTag(),null)!!;be.readyAt=0
                val input=root.offset(MachineLayout.rotate(MachineLayout.portOffset(m,MachineLayout.Port.INPUT),facing))
                val output=root.offset(MachineLayout.rotate(MachineLayout.portOffset(m,MachineLayout.Port.OUTPUT),facing))
                fun chest(p:BlockPos):ChestBlockEntity {h.level.setBlock(p,Blocks.CHEST.defaultBlockState(),3);return h.level.getBlockEntity(p) as ChestBlockEntity}
                from=chest(input.relative(facing));below=chest(input.below());target=chest(output.below())
                from.setItem(0,ItemStack(ingredient,5));from.setItem(1,ItemStack(Items.DIRT,7));below.setItem(0,ItemStack(ingredient,5))
                be.inventory.setStackInSlot(9,ItemStack(ModItems.COMMON_SAMPLE.get(),5))
                val p=player(h);p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack(ingredient,5))
                val port=h.level.getBlockEntity(input) as SampleMachineBlockEntity
                h.assertTrue(port.insertFromHand(p,InteractionHand.MAIN_HAND)&&p.mainHandItem.isEmpty,"Manual no-GUI input failed: $m $facing")
                h.assertTrue(Direction.entries.all{h.level.getCapability(Capabilities.ItemHandler.BLOCK,input,it)?.isItemValid(0,ItemStack(ingredient))==true},"Pipe side rejected valid ingredient")
            }
            h.runAtTickTime(8L+i*12){
                h.assertTrue(from.getItem(0).isEmpty&&below.getItem(0).isEmpty&&from.getItem(1).count==7,"Adjacent input failed or extracted dirt: $m $facing")
                h.assertTrue((0..8).sumOf{be.inventory.getStackInSlot(it).count}==15,"Input items lost/duplicated")
                h.assertTrue(target.getItem(0).count==5&&be.inventory.getStackInSlot(9).isEmpty,"Adjacent output failed: $m $facing")
                MachineStructure.remove(be,false)
            }
        }
        h.runAtTickTime(106){h.succeed()}
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun fullBlockCellsAndPanelPorts(h:GameTestHelper) {
        for(m in MachineDeployment.entries)for(facing in listOf(Direction.NORTH,Direction.EAST,Direction.SOUTH,Direction.WEST)) {
            val root=fixture(h)
            val be=MachineStructure.place(h.level,root,m,facing,CompoundTag(),null)!!
            val radius=if(m==MachineDeployment.BIOPROCESSOR)1 else 2
            // Panel positions are independent of the implementation's port lookup.
            val input=root.offset(MachineLayout.rotate(BlockPos(0,0,-radius),facing))
            val output=root.offset(MachineLayout.rotate(BlockPos(-radius,0,-radius),facing))
            val power=root.offset(MachineLayout.rotate(BlockPos(radius,0,-radius),facing))
            val ingredient=ItemStack(if(m==MachineDeployment.BIOPROCESSOR)Items.ROTTEN_FLESH else Items.RAW_IRON)
            val inputHandler=h.level.getCapability(Capabilities.ItemHandler.BLOCK,input,facing)!!
            val outputHandler=h.level.getCapability(Capabilities.ItemHandler.BLOCK,output,facing)!!
            h.assertTrue(inputHandler.insertItem(0,ingredient,false).isEmpty,"Centre input panel rejected ingredients: $m $facing")
            be.inventory.setStackInSlot(9,ItemStack(ModItems.COMMON_SAMPLE.get(),5))
            h.assertTrue(outputHandler.extractItem(0,5,false).count==5,"Left output panel cannot extract samples: $m $facing")
            h.assertTrue(h.level.getCapability(Capabilities.EnergyStorage.BLOCK,power,facing)!=null,"Energy panel moved")
            for(offset in MachineLayout.shapes(m,facing).keys) {
                val pos=root.offset(offset);val state=h.level.getBlockState(pos)
                val full=net.minecraft.world.phys.shapes.Shapes.block()
                val different=net.minecraft.world.phys.shapes.BooleanOp.NOT_SAME
                h.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(full,state.getShape(h.level,pos),different),"Selection is a partial block: $m $facing $offset")
                h.assertTrue(!net.minecraft.world.phys.shapes.Shapes.joinIsNotEmpty(full,state.getCollisionShape(h.level,pos),different),"Collision is a partial block: $m $facing $offset")
            }
            MachineStructure.remove(be,false)
        }
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun existingMachineCollisionUpgradesWithoutLosingInventory(h:GameTestHelper) {
        val root=fixture(h);val m=MachineDeployment.REFINERY
        val be=MachineStructure.place(h.level,root,m,Direction.NORTH,CompoundTag(),null)!!
        val offset=MachineLayout.shapes(m,Direction.NORTH).keys.first{it.y==2&&it.z==-2}
        val part=h.level.getBlockEntity(root.offset(offset)) as SampleMachineBlockEntity
        part.tearingDown=true;h.level.removeBlock(part.blockPos,false)
        be.readyAt=0;be.inventory.setStackInSlot(0,ItemStack(Items.RAW_IRON,64));energy(h,root,m).receiveEnergy(150000,false)
        repeat(13){be.serverTick()}
        be.inventory.setStackInSlot(9,ItemStack(ModItems.COMMON_SAMPLE.get(),5))
        val contents=be.contents()
        val input=root.offset(0,0,-2);val output=root.offset(-2,0,-2)
        h.level.setBlock(input,h.level.getBlockState(input).setValue(SampleMachineBlock.PORT,MachineLayout.Port.OUTPUT),3)
        h.level.setBlock(output,h.level.getBlockState(output).setValue(SampleMachineBlock.PORT,MachineLayout.Port.INPUT),3)
        val oldDeck=root.offset(2,1,2)
        val oldPart=be.blockState.setValue(SampleMachineBlock.MASTER,false)
        h.level.setBlock(oldDeck,oldPart,3)
        (h.level.getBlockEntity(oldDeck) as SampleMachineBlockEntity).root=root
        be.layoutVersion=5
        // Exercise the real ticking migration, while pausing processing for this tick.
        be.readyAt=h.level.gameTime+100;be.serverTick()
        h.assertTrue(be.layoutVersion==MachineLayout.VERSION,"Existing layout did not upgrade")
        h.assertTrue((h.level.getBlockEntity(root.offset(offset)) as? SampleMachineBlockEntity)?.root==root,"Missing model coverage was not restored")
        h.assertTrue(h.level.isEmptyBlock(oldDeck),"Upgrade retained an obsolete machine block")
        h.assertTrue(be.contents()==contents,"Upgrade changed inventory, energy or reserved batch/progress")
        h.assertTrue(h.level.getBlockEntity(root)===be,"Upgrade replaced the inventory owner")
        h.assertTrue(h.level.getCapability(Capabilities.ItemHandler.BLOCK,input,Direction.NORTH)!!.isItemValid(0,ItemStack(Items.RAW_IRON)),"Existing centre panel stayed output")
        h.assertTrue(h.level.getCapability(Capabilities.ItemHandler.BLOCK,output,Direction.NORTH)!!.extractItem(0,5,true).count==5,"Existing output panel stayed input")
        h.succeed()
    }
}
