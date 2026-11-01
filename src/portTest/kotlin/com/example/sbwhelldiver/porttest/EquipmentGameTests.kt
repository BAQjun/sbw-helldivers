package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.stratagem.*
import com.example.sbwhelldiver.survival.*
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.*
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.gametest.*

@GameTestHolder("sbw_helldiver")
@PrefixGameTestTemplate(false)
object EquipmentGameTests {
    private fun terminalFixture(h:GameTestHelper,ready:(EquipmentDeploymentBeaconEntity,net.minecraft.server.level.ServerPlayer,java.util.UUID,()->Unit)->Unit) {
        val level=h.level;val at=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(5,4,5)))
        for(x in -4..4)for(z in -4..4)for(y in -1..7)level.setBlock(BlockPos.containing(at.add(x.toDouble(),y.toDouble(),z.toDouble())),if(y==-1)Blocks.STONE.defaultBlockState()else Blocks.AIR.defaultBlockState(),3)
        val player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"TerminalRegression"))
        player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);player.inventory.clearContent()
        val field=net.minecraft.server.players.PlayerList::class.java.getDeclaredField("playersByUUID").also{it.isAccessible=true}
        @Suppress("UNCHECKED_CAST") val players=field.get(level.server.playerList) as MutableMap<java.util.UUID,net.minecraft.server.level.ServerPlayer>
        players[player.uuid]=player
        val e=ModEntities.EQUIPMENT_BEACON.get().create(level)!!;e.setPos(at);e.configure(at.y,level.gameTime+1,player.uuid);level.addFreshEntity(e)
        h.runAfterDelay(60){
            val eye=e.screenCenter().add(e.screenNormal().scale(1.1));val look=e.screenCenter().subtract(eye)
            player.moveTo(eye.x,eye.y-player.eyeHeight,eye.z,Math.toDegrees(kotlin.math.atan2(-look.x,look.z)).toFloat(),(-Math.toDegrees(kotlin.math.atan2(look.y,look.horizontalDistance()))).toFloat())
            h.assertTrue(e.openTerminal(player),"Cannot open test terminal")
            val token=e.javaClass.getDeclaredField("nonce").also{it.isAccessible=true}.get(e) as java.util.UUID
            ready(e,player,token,{players.remove(player.uuid)})
        }
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=160)
    fun sixLeftInputsRecallBeaconWithoutContainer(h:GameTestHelper)=terminalFixture(h){e,p,token,cleanup->
        repeat(6){i->h.runAfterDelay((i+1).toLong()){h.assertTrue(e.acceptInput(p,token,3),"Recall arrow rejected")}}
        h.runAfterDelay(5){h.assertTrue(e.phase==EquipmentDeploymentBeaconEntity.ACTIVE&&e.prefix.size==5,"Recall happened before sixth left")}
        h.runAfterDelay(8){h.assertTrue(e.phase==EquipmentDeploymentBeaconEntity.RETRACTING,"Six left inputs did not retract")}
        h.runAfterDelay(70){h.assertTrue(e.isRemoved,"Recall left hellpod behind");h.assertTrue(h.level.getEntitiesOfClass(MachineContainerEntity::class.java,e.boundingBox.inflate(20.0)).isEmpty(),"Recall spawned unwanted container");cleanup();h.succeed()}
    }
    private fun creativeDeployment(h:GameTestHelper,m:MachineDeployment)=terminalFixture(h){e,p,token,cleanup->
        val code=m.code+List(6){1}
        code.forEachIndexed{i,d->h.runAfterDelay((i+1).toLong()){h.assertTrue(e.acceptInput(p,token,d),"Creative command rejected")}}
        h.runAfterDelay(15){h.assertTrue(e.phase==EquipmentDeploymentBeaconEntity.COUNTDOWN&&e.error==0&&p.inventory.isEmpty,"Creative deployment required materials")}
        h.runAfterDelay(220){
            h.assertTrue(e.isRemoved,"Completed deployment left its beacon/pod behind")
            val boxes=h.level.getEntitiesOfClass(MachineContainerEntity::class.java,e.boundingBox.inflate(20.0))
            h.assertTrue(boxes.size==1&&boxes.single().machine==m&&boxes.single().phase==MachineContainerEntity.CLOSED,"Creative deployment did not deliver correct machine")
            cleanup();h.succeed()
        }
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=310)
    fun creativeBioprocessorNeedsNoMaterials(h:GameTestHelper)=creativeDeployment(h,MachineDeployment.BIOPROCESSOR)
    @JvmStatic @GameTest(template="port_test",timeoutTicks=310)
    fun creativeRefineryNeedsNoMaterials(h:GameTestHelper)=creativeDeployment(h,MachineDeployment.REFINERY)
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun recipeTransfersBatteryEnergy(h:GameTestHelper) {
        val battery=ItemStack(com.atsuishio.superbwarfare.init.ModItems.SMALL_BATTERY_PACK.get())
        battery.set(com.atsuishio.superbwarfare.init.ModDataComponents.ENERGY,123456)
        val input=CraftingInput.of(3,3,listOf(ItemStack.EMPTY,ItemStack(Items.AMETHYST_SHARD),ItemStack.EMPTY,
            ItemStack(Items.IRON_INGOT),ItemStack(com.atsuishio.superbwarfare.init.ModItems.ANCIENT_CPU.get()),ItemStack(Items.IRON_INGOT),
            ItemStack.EMPTY,battery,ItemStack.EMPTY))
        val recipe=h.level.recipeManager.byKey(ResourceLocation.fromNamespaceAndPath("sbw_helldiver","stratagem_beacon")).orElseThrow().value as BeaconCraftingRecipe
        h.assertTrue(recipe.matches(input,h.level),"Beacon recipe rejected the specified ingredients")
        val output=recipe.assemble(input,h.level.registryAccess())
        h.assertTrue(output.`is`(ModItems.BEACON.get())&&BeaconEnergy.stored(output)==123456,"Battery energy was not inherited")
        h.assertTrue((output.item as com.atsuishio.superbwarfare.item.EnergyStorageItem).getMaxEnergy(output)==BeaconEnergy.capacity(),"Beacon capacity differs from the battery")
        h.assertTrue(battery.getOrDefault(com.atsuishio.superbwarfare.init.ModDataComponents.ENERGY,0)==123456,"Craft preview consumed battery energy")
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun builtinCallDoesNotUseLoadoutSlots(h:GameTestHelper) {
        MachineDeployment.checkCodes()
        val stack=ItemStack(ModItems.BEACON.get());BeaconData.ensureIdentity(stack)
        h.assertTrue(BeaconData.loadout(stack).all{it==null},"Fresh beacon contains a loadout card")
        h.assertTrue(Stratagems.commands(BeaconData.loadout(stack)).contains(Stratagems.equipmentDeployment),"Call unavailable without loadout")
        BeaconData.activate(stack,Stratagems.equipmentDeployment)
        h.assertTrue(BeaconData.selected(stack)==Stratagems.equipmentDeployment,"Built-in call cannot be selected")
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun throwsSpendExactlyOneKilorfOnlyOnSuccess(h:GameTestHelper) {
        val player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.level,com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"BeaconEnergyQA"))
        player.moveTo(Vec3.atBottomCenterOf(h.absolutePos(BlockPos(2,12,2))))
        val stack=ItemStack(ModItems.BEACON.get());val id=BeaconData.ensureIdentity(stack);player.inventory.selected=0;player.inventory.setItem(0,stack)
        BeaconInput.observeHands(player);BeaconData.activate(stack,Stratagems.equipmentDeployment);BeaconEnergy.set(stack,999)
        val item=stack.item as com.example.sbwhelldiver.item.BeaconItem
        h.assertTrue(!item.throwActivated(player,net.minecraft.world.InteractionHand.MAIN_HAND,id)&&BeaconEnergy.stored(stack)==999,"Uncharged throw succeeded or lost charge")
        BeaconEnergy.set(stack,2500)
        h.assertTrue(item.throwActivated(player,net.minecraft.world.InteractionHand.MAIN_HAND,id)&&BeaconEnergy.stored(stack)==1500,"Throw did not cost exactly 1,000 RF")
        h.assertTrue(!item.throwActivated(player,net.minecraft.world.InteractionHand.MAIN_HAND,id)&&BeaconEnergy.stored(stack)==1500,"Duplicate throw consumed energy")
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun deploymentFootprintsRespectBlockGridAndObstructions(h:GameTestHelper) {
        val root=h.absolutePos(BlockPos(5,12,5))
        for(machine in MachineDeployment.entries)for(facing in net.minecraft.core.Direction.Plane.HORIZONTAL) {
            for(x in -4..4)for(z in -4..4)for(y in -1..7)h.level.setBlock(root.offset(x,y,z),if(y==-1)Blocks.STONE.defaultBlockState()else Blocks.AIR.defaultBlockState(),3)
            h.assertTrue(MachineStructure.space(h.level,root,machine,facing),"Empty supported area rejected")
            val port=root.offset(MachineLayout.rotate(MachineLayout.portOffset(machine,MachineLayout.Port.INPUT),facing))
            h.level.setBlock(port,Blocks.STONE.defaultBlockState(),3)
            h.assertTrue(!MachineStructure.space(h.level,root,machine,facing),"Blocked deployment accepted")
        }
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun screenFacesNearestCardinalTowardsThrowPoint(h:GameTestHelper) {
        for(degrees in listOf(-179.0,-135.1,-134.9,-90.0,-45.1,-44.9,0.0,44.9,45.1,89.0,134.9,135.1,179.0)) {
            val a=Math.toRadians(degrees);val origin=Vec3(-kotlin.math.sin(a)*10,7.0,kotlin.math.cos(a)*10)
            val yaw=EquipmentBeaconGeometry.deploymentYaw(origin,Vec3.ZERO,0f)
            val n=EquipmentBeaconGeometry.normal.yRot(-yaw*(Math.PI/180).toFloat()).multiply(1.0,0.0,1.0).normalize()
            val expected=listOf(Vec3(1.0,0.0,0.0),Vec3(-1.0,0.0,0.0),Vec3(0.0,0.0,1.0),Vec3(0.0,0.0,-1.0)).maxBy{it.dot(origin)}
            h.assertTrue(n.dot(expected)>.99999,"Screen faces wrong cardinal at $degrees: $n")
        }
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=20)
    fun materialBillsAreCompleteAndAtomic(h:GameTestHelper) {
        val player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(h.level,com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"EquipmentCostsQA"))
        for(machine in MachineDeployment.entries) {
            player.inventory.clearContent()
            h.assertTrue(machine.materials.map{it.count}==if(machine==MachineDeployment.REFINERY)listOf(5,10,5,15,1,2) else listOf(2,12,2,20,1,2),"Incorrect machine bill")
            machine.materials.forEachIndexed{i,m->player.inventory.setItem(i,ItemStack(m.item,if(i==5)1 else m.count))}
            h.assertTrue(!machine.consumeMaterials(player.inventory),"Partial bill was charged")
            h.assertTrue(player.inventory.getItem(0).count==machine.materials[0].count,"Failed payment consumed other materials")
            player.inventory.setItem(40,ItemStack(machine.materials[5].item))
            val iron=machine.materials[1];player.inventory.setItem(1,ItemStack(iron.item,iron.count-3));player.inventory.setItem(9,ItemStack(iron.item,3))
            h.assertTrue(machine.consumeMaterials(player.inventory),"Complete bill across stacks/offhand rejected")
            h.assertTrue((0 until player.inventory.containerSize).all{player.inventory.getItem(it).isEmpty},"Payment left or overcharged materials")
        }
        h.succeed()
    }
    @JvmStatic @GameTest(template="port_test",timeoutTicks=400)
    fun terminalPaymentAndRetractionAreServerAuthoritative(h:GameTestHelper) {
        val level=h.level
        val at=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(5,12,5)))
        for(x in -4..4)for(z in -4..4)for(y in -1..7){level.setBlock(BlockPos.containing(at.add(x.toDouble(),y.toDouble(),z.toDouble())),if(y==-1)Blocks.STONE.defaultBlockState()else Blocks.AIR.defaultBlockState(),3)}
        val e=ModEntities.EQUIPMENT_BEACON.get().create(level)!!;e.setPos(at);e.configure(at.y,level.gameTime,null);level.addFreshEntity(e)
        val player=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"TerminalQA"))
        // Keep the mock operator discoverable through the same player-list lookup as a real session.
        val playersField=net.minecraft.server.players.PlayerList::class.java.getDeclaredField("playersByUUID").also{it.isAccessible=true}
        @Suppress("UNCHECKED_CAST") val players=playersField.get(level.server.playerList) as MutableMap<java.util.UUID,net.minecraft.server.level.ServerPlayer>
        players[player.uuid]=player
        h.runAfterDelay(60) {
            h.assertTrue(e.capReleased&&level.getEntitiesOfClass(com.example.sbwhelldiver.entity.HellpodCapEntity::class.java,e.boundingBox.inflate(20.0)){it.sourcePodUUID==e.uuid}.size==1,"Deployment did not eject exactly one independent cap")
            val eye=e.screenCenter().add(e.screenNormal().scale(1.1));val look=e.screenCenter().subtract(eye)
            player.moveTo(eye.x,eye.y-player.eyeHeight,eye.z,Math.toDegrees(kotlin.math.atan2(-look.x,look.z)).toFloat(),(-Math.toDegrees(kotlin.math.atan2(look.y,kotlin.math.sqrt(look.x*look.x+look.z*look.z)))).toFloat())
            h.assertTrue(e.openTerminal(player),"Screen ray could not open deployed terminal")
            val field=e.javaClass.getDeclaredField("nonce").also{it.isAccessible=true};val token=field.get(e) as java.util.UUID
            h.assertTrue(!e.acceptInput(player,java.util.UUID.randomUUID(),0),"Stale session accepted")
            fun sequence(code:List<Int>,then:()->Unit) {code.forEachIndexed{i,d->h.runAfterDelay((i+1).toLong()){e.acceptInput(player,token,d)}};h.runAfterDelay((code.size+2).toLong()){then()}}
            sequence(MachineDeployment.BIOPROCESSOR.code) {
                h.assertTrue(e.selectedMachine==MachineDeployment.BIOPROCESSOR,"Machine code not accepted")
                sequence(List(6){1}) {
                    h.assertTrue(e.error==2&&!e.paid,"Missing material was accepted")
                    val machine=MachineDeployment.BIOPROCESSOR
                    machine.materials.forEachIndexed{i,m->player.inventory.setItem(i,ItemStack(m.item,m.count))}
                    sequence(List(6){1}) {
                        h.assertTrue(e.phase==EquipmentDeploymentBeaconEntity.COUNTDOWN&&machine.materials.all{it.available(player.inventory)==0},"Payment/countdown failed")
                        h.assertTrue(!e.acceptInput(player,token,1),"Replayed input accepted")
                        val tag=net.minecraft.nbt.CompoundTag();e.saveWithoutId(tag)
                        val restored=ModEntities.EQUIPMENT_BEACON.get().create(level)!!;restored.load(tag)
                        h.assertTrue(restored.paid&&restored.phase==e.phase&&restored.deploymentEnd==e.deploymentEnd,"Paid state/deadline lost on reload");restored.discard()
                        h.runAfterDelay(2){
                            val status=StratagemCooldowns.state(player).entry(Stratagems.equipmentDeployment.id)
                            h.assertTrue(status.phase==CallPhase.ARRIVING&&status.end-status.start==200L,"Left HUD countdown does not use the normal arrival clock")
                            h.assertTrue(status.equipmentMachine==MachineDeployment.BIOPROCESSOR.id,"HUD still describes the beacon instead of the selected machine")
                            val saved=StratagemCooldownState.load(StratagemCooldowns.state(player).save()).entry(Stratagems.equipmentDeployment.id)
                            h.assertTrue(saved.equipmentMachine==status.equipmentMachine,"Selected HUD machine lost on reload")
                        }
                        h.runAfterDelay(130){h.assertTrue(e.phase==EquipmentDeploymentBeaconEntity.COUNTDOWN,"Beacon retracted before the final three seconds")}
                        h.runAfterDelay(145){h.assertTrue(e.phase==EquipmentDeploymentBeaconEntity.RETRACTING&&e.phaseStart==e.deploymentEnd-60,"Beacon did not begin retraction exactly three seconds before arrival")}
                        h.runAfterDelay(190){h.assertTrue(e.phase==EquipmentDeploymentBeaconEntity.RETRACTING,"Beacon ended the countdown early")}
                        h.runAfterDelay(205){h.assertTrue(e.phase==EquipmentDeploymentBeaconEntity.FINISHED,"Deployment reservation did not complete");e.discard();players.remove(player.uuid);h.succeed()}
                    }
                }
            }
        }
    }
}
