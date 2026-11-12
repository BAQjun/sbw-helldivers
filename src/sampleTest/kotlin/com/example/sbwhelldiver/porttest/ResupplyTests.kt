package com.example.sbwhelldiver.porttest
import com.atsuishio.superbwarfare.data.gun.*
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import com.example.sbwhelldiver.item.GuardDogBackpack
import com.example.sbwhelldiver.stratagem.*
import com.example.sbwhelldiver.survival.*
import com.mojang.authlib.GameProfile
import net.minecraft.core.*
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.*
import net.minecraft.world.level.GameType
import net.minecraft.world.phys.*
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.*
import java.util.UUID
import kotlin.math.atan2
@GameTestHolder("sbw_helldiver_samples")
@PrefixGameTestTemplate(false)
object ResupplyTests {
 private fun player(h:GameTestHelper)=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"ResupplyQA")).also{it.inventory.clearContent();it.setGameMode(GameType.SURVIVAL)}
 private fun gun(id:String)=ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation("superbwarfare",id)))
 @JvmStatic @GameTest(template="port_test",timeoutTicks=30)
 fun ammunitionDedupOverflowAndBackpacks(h:GameTestHelper){
  val p=player(h);val guns=listOf(gun("ak_47"),gun("m_4"),gun("mk_14"));val expected=linkedMapOf<Ammo,Int>()
  guns.forEachIndexed{i,g->val d=g.toGunData()!!;val c=d.selectedAmmoConsumer();if(!c.initialized())c.init();check(c.type==AmmoConsumer.AmmoConsumeType.PLAYER_AMMO);val a=c.playerAmmoType!!;expected[a]=maxOf(expected[a]?:0,d.get(GunProp.MAGAZINE)*4/c.loadAmount.coerceAtLeast(1));p.inventory.setItem(i,g)}
  h.assertTrue(expected.size<guns.size,"Fixture guns do not share ammunition")
  for(a in expected.keys){val missing=a.limit-a.get(p);if(missing>0)check(a.add(p,missing))}
  val packs=listOf(ModItems.GUARD_DOG_BACKPACK.get(),ModItems.FLAME_GUARD_DOG_BACKPACK.get(),ModItems.GAS_GUARD_DOG_BACKPACK.get())
  packs.forEachIndexed{i,item->val s=ItemStack(item);GuardDogBackpack.updateAmmo(s,0);repeat(GuardDogBackpack.reserveCapacity(s)){GuardDogBackpack.loadSpare(s)};p.inventory.setItem(6+i,s)}
  ResupplyAmmo.refill(p)
  for((a,amount) in expected){val actual=p.inventory.items.filter{it.`is`(a.getItemStack().item)}.sumOf{it.count};h.assertTrue(actual==amount,"Ammo overflow duplicated or lost: $a $actual/$amount")}
  for(i in 6..8){val s=p.inventory.getItem(i);h.assertTrue(GuardDogBackpack.ammo(s)==GuardDogBackpack.capacity(s)&&GuardDogBackpack.spareMags(s)==GuardDogBackpack.reserveCapacity(s),"Dog not fully refilled")};h.succeed()
 }
 @JvmStatic @GameTest(template="port_test",timeoutTicks=30)
 fun paidChargesCannotBeDuplicated(h:GameTestHelper){
  val state=StratagemCooldownState();check(state.entry("resupply").remaining==0);repeat(5){check(state.purchaseResupply())};check(!state.purchaseResupply());state.restoreAllUses();state.setEnabled(false,0);check(state.entry("resupply").remaining==5)
  val token=UUID.randomUUID();check(state.reserve("resupply",token,1));check(!state.reserve("resupply",UUID.randomUUID(),1));check(state.beginArrival("resupply",token,2,197));check(state.beginArrival("resupply",token,3,197));check(state.entry("resupply").remaining==4);check(!state.beginArrival("resupply",UUID.randomUUID(),3,197));state.cancel("resupply",token);check(state.entry("resupply").remaining==5)
  check(state.reserve("resupply",token,5));check(state.beginArrival("resupply",token,6,201));state.advance(202);val loaded=StratagemCooldownState.load(state.save());loaded.restoreAllUses();check(loaded.entry("resupply").remaining==4)
  check(Stratagems.commands(emptyList(),false).take(3).map{it?.id}==listOf("call_super_destroyer","machine_deployment_beacon","resupply"));check(TacticalTerminalOffers.ids(TerminalCategory.BASIC)==TacticalTerminalOffers.refills.map{it.id}+"resupply");h.succeed()
 }
 @JvmStatic @GameTest(template="port_test",timeoutTicks=30)
 fun fourCasesPersistAndDropAsUsableEntities(h:GameTestHelper){
  val p=player(h);val pod=ModEntities.RESUPPLY_HELLPOD.get().create(h.level)!!;val at=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(5,5,5)));pod.moveTo(at.x,at.y,at.z,180f,0f)
  val n=pod.saveWithoutId(CompoundTag());n.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal);n.putDouble("HellpodRestY",at.y);pod.load(n);h.level.addFreshEntity(pod)
  p.setPos(at.add(0.0,0.0,-2.2));val target=pod.toWorld(ResupplyHellpodEntity.caseBounds(0).center);val d=target.subtract(p.eyePosition);p.yRot=Math.toDegrees(atan2(-d.x,d.z)).toFloat();p.xRot=Math.toDegrees(atan2(-d.y,d.horizontalDistance())).toFloat()
  check(pod.interact(p,InteractionHand.MAIN_HAND).consumesAction()) { "First case click rejected, phase=${pod.phase} eye=${p.eyePosition} target=$target view=${p.getViewVector(1f)}" };check(!pod.hasCase(0)&&(1..3).all(pod::hasCase)) { "Incorrect case removed" };check(!pod.interact(p,InteractionHand.MAIN_HAND).consumesAction()) { "Repeated ray selected another case" };val saved=pod.saveWithoutId(CompoundTag());pod.load(saved);check(!pod.hasCase(0)) { "Consumed case restored by save/load" }
  pod.destroy();pod.destroy();val boxes=h.level.getEntitiesOfClass(SupplyCaseEntity::class.java,pod.boundingBox.inflate(3.0));check(boxes.size==3) { "Dropped ${boxes.size} boxes instead of 3" };val box=boxes.first();check(box.interact(p,InteractionHand.MAIN_HAND).consumesAction()) { "Loose case rejected" };check(box.isRemoved) { "Loose case survived interaction" };check(!box.interact(p,InteractionHand.MAIN_HAND).consumesAction()) { "Loose case granted twice" };boxes.forEach{it.discard()};h.succeed()
 }
 @JvmStatic @GameTest(template="port_test",timeoutTicks=30)
 fun boostedRecipesKeepSuperAndInputCost(h:GameTestHelper){
  val recipes=SampleRecipe.forMachine(h.level,MachineDeployment.BIOPROCESSOR);check(recipes.size==21);val flesh=recipes.first{it.ingredient.test(ItemStack(Items.ROTTEN_FLESH))};check(flesh.count==5&&flesh.ticks==100);check(flesh.outcomes.map{it.chance}==listOf(70,29,1));check(flesh.outcomes.map{it.count}==listOf(7,1,1));check(flesh.outcomes.map{it.maxCount}==listOf(8,2,1))
  val echo=recipes.first{it.ingredient.test(ItemStack(Items.ECHO_SHARD))};check(echo.outcomes.map{it.count}==listOf(80,25,5));val rand=net.minecraft.util.RandomSource.create(50);val results=(1..1000).map{flesh.roll(rand)};check(results.filter{it.`is`(ModItems.COMMON_SAMPLE.get())}.map{it.count}.toSet()==setOf(7,8));h.succeed()
 }
 @JvmStatic @GameTest(template="port_test",timeoutTicks=30)
 fun taczBoxesPreferredAndSharedCalibreDeduplicated(h:GameTestHelper){
  val p=player(h)
  val id=ResourceLocation("tacz","m4a1")
  val g=com.tacz.guns.api.item.builder.GunItemBuilder.create().setId(id).build()
  check(!g.isEmpty)
  p.inventory.setItem(0,g);p.inventory.setItem(1,g.copy())
  val data=com.tacz.guns.api.TimelessAPI.getCommonGunIndex(id).orElseThrow().gunData
  val amount=com.tacz.guns.util.AttachmentDataUtils.getAmmoCountWithAttachment(g,data)*4
  val ammo=data.ammoId
  val boxStack=ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation("tacz","ammo_box")))
  val box=boxStack.item as com.tacz.guns.api.item.IAmmoBox
  box.setAmmoLevel(boxStack,0);box.setAmmoId(boxStack,ammo)
  val capacity=com.tacz.guns.api.TimelessAPI.getCommonAmmoIndex(ammo).orElseThrow().stackSize*com.tacz.guns.config.sync.SyncConfig.AMMO_BOX_STACK_SIZE.get()
  box.setAmmoCount(boxStack,capacity-7);p.inventory.setItem(2,boxStack)
  ResupplyAmmo.refill(p)
  check(box.getAmmoCount(boxStack)==capacity){"TaCZ box was not filled first"}
  val loose=p.inventory.items.filter{com.tacz.guns.api.item.IAmmo.getIAmmoOrNull(it)?.getAmmoId(it)==ammo}.sumOf{it.count}
  check(loose==amount-7){"TaCZ shared-calibre grant duplicated or lost: $loose/${amount-7}"}
  h.succeed()
 }
 @JvmStatic @GameTest(template="port_test",timeoutTicks=30)
 fun purchasesChargeSamplesOnceAndEnforceCap(h:GameTestHelper){
  val p=player(h);val root=h.absolutePos(BlockPos(5,4,5))
  val state=ModBlocks.TACTICAL_TERMINAL.get().defaultBlockState().setValue(TacticalTerminalBlock.FACING,Direction.NORTH)
  for(i in 0..3)h.level.setBlock(TacticalTerminalBlock.position(root,Direction.NORTH,i),state.setValue(TacticalTerminalBlock.PART,i),3)
  p.setPos(root.x+.5,root.y.toDouble(),root.z+1.5)
  val bag=ItemStack(ModItems.SAMPLE_CONTAINER.get());p.inventory.setItem(0,bag)
  for((tier,count)in listOf(1000,500,20).withIndex())SampleStorage.add(bag,tier,java.math.BigInteger.valueOf(count.toLong()))
  TacticalTerminalSessions.open(p,h.level.getBlockEntity(root) as TacticalTerminalBlockEntity);val token=TacticalTerminalSessions.token(p)!!
  var clock=10_000_000_000L
  fun buy(){TacticalTerminalSessions.accept(p,root,token,0,"resupply",clock);clock+=TacticalTerminalHold.DURATION_TICKS*50_000_000L;TacticalTerminalSessions.accept(p,root,token,2,"resupply",clock);TacticalTerminalSessions.accept(p,root,token,2,"resupply",clock+1);clock+=1_000_000_000L}
  repeat(6){buy()}
  check(StratagemCooldowns.state(p).entry("resupply").remaining==5)
  check((0..2).map{SampleStorage.count(bag,it).toInt()}==listOf(250,250,15)){"Resupply price or cap incorrect"}
  h.succeed()
 }

}
