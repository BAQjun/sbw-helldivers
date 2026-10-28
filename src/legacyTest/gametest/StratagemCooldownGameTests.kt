package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.config.StratagemCooldownConfig as Config
import com.example.sbwhelldiver.config.HelldiversConfig
import com.example.sbwhelldiver.stratagem.*
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.network.CooldownSnapshotPacket
import com.mojang.authlib.GameProfile
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.util.UUID

@GameTestHolder("sbw_helldiver_cooldowns")
@PrefixGameTestTemplate(false)
object StratagemCooldownGameTests {
    private const val AIR = "eagle_airstrike"
    private const val STRAFE = "eagle_strafing_run"
    private const val MG = "machine_gun_sentry"
    @JvmStatic @GameTest(template="sentry_test", timeoutTicks=5)
    fun orbitalActivityPersistsWithoutChangingCooldown(h: GameTestHelper) {
        val id="orbital_120mm_he_barrage"; val token=UUID.randomUUID(); val state=StratagemCooldownState()
        state.reserve(id,token,0); state.beginArrival(id,token,0,100); state.advance(100)
        val cooldownEnd=state.entry(id).end
        state.beginActivity(id,token,100,440)
        h.assertTrue(state.entry(id).activity(100.0)==CallPhase.ACTIVE && state.entry(id).end==cooldownEnd,"Activity changed cooldown or skipped active state")
        var saved=StratagemCooldownState.load(state.save())
        h.assertTrue(saved.entry(id).activity(500.0)==CallPhase.ACTIVE,"An overdue real projectile was falsely marked arrived")
        saved.finishActivity(id,UUID.randomUUID(),441)
        h.assertTrue(saved.entry(id).activity(441.0)==CallPhase.ACTIVE,"A stale call completed the active call")
        saved.finishActivity(id,token,441); saved=StratagemCooldownState.load(saved.save())
        h.assertTrue(saved.entry(id).activity(441.0)==CallPhase.ARRIVED && saved.entry(id).activity(500.99)==CallPhase.ARRIVED && saved.entry(id).activity(501.0)==null,"Arrived notice hold or persistence wrong")
        h.assertTrue(saved.entry(id).end==cooldownEnd,"Completing activity restarted cooldown")
        val old=saved.save();old.getList("Entries",10).forEach{raw->val n=raw as net.minecraft.nbt.CompoundTag;listOf("ActivityStart","ActivityEnd","ActivityFinished","ActivityToken").forEach(n::remove)}
        h.assertTrue(StratagemCooldownState.load(old).entry(id).activity(500.0)==null,"Old saves created phantom ongoing calls")
        saved.setEnabled(false,600); val next=UUID.randomUUID(); saved.beginActivity(id,next,600,940)
        saved.finishActivity(id,token,601)
        h.assertTrue(saved.available(id,601) && saved.entry(id).activity(601.0)==CallPhase.ACTIVE,"Disabled cooldown blocked testing or old completion hid newer call")
        saved.cancel(id,next)
        h.assertTrue(saved.entry(id).activity(602.0)==null,"Cancelled activity stayed visible")
        h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test", timeoutTicks=5, batch="cooldown_custom_config")
    fun configuredZeroAndCustomDurations(h: GameTestHelper) {
        val values=listOf(Config.cooldowns.getValue(MG),Config.cooldowns.getValue(AIR),Config.autoRearm,Config.manualRearm)
        val previous=values.map{it.get()}; val uses=Config.uses.getValue(AIR);val previousUses=uses.get()
        try {
            values.forEach{it.set(0.0)};uses.set(1)
            val s=StratagemCooldownState();s.equip(listOf(AIR));val token=UUID.randomUUID()
            s.reserve(AIR,token,0);s.beginArrival(AIR,token,0,0);s.advance(0)
            h.assertTrue(s.available(AIR,0)&&s.entry(AIR).remaining==1,"Zero-time rearm failed or recursed")
            val gun=UUID.randomUUID();s.reserve(MG,gun,1);s.beginArrival(MG,gun,1,10)
            h.assertTrue(s.available(MG,10),"Zero cooldown blocked an arrived stratagem")
            Config.cooldowns.getValue(MG).set(1.25)
            s.reserve(MG,gun,11);s.beginArrival(MG,gun,11,20);s.advance(20)
            h.assertTrue(s.entry(MG).end==45L&&!s.available(MG,44)&&s.available(MG,45),"Custom cooldown ignored")
            h.assertTrue(s.rearm(50)&&!s.rearming(50),"Zero manual rearm left a stuck busy state")
        } finally {values.forEachIndexed{i,v->v.set(previous[i])};uses.set(previousUses)}
        h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test", timeoutTicks=5)
    fun fullyUpgradedDefaultsAndArrivalClock(h: GameTestHelper) {
        h.assertTrue(Config.baseSeconds.keys == Stratagems.definitions.map { it.id }.toSet(), "A stratagem is missing a cooldown setting")
        h.assertTrue(Config.ticks(MG)==1539 && Config.ticks("flame_sentry")==1710 && Config.ticks("mortar_sentry")==3078 &&
            Config.ticks(AIR)==143 && Config.maxUses(AIR)==3 && Config.maxUses(STRAFE)==5 && Config.rearmTicks(false)==2280 && Config.rearmTicks(true)==2052,
            "Fully upgraded defaults differ from native settings/module multipliers")
        val s = StratagemCooldownState(); val token = UUID.randomUUID()
        h.assertTrue(s.reserve(MG,token,100),"Initial call refused")
        h.assertTrue(!s.reserve(MG,UUID.randomUUID(),101),"Duplicate airborne call allowed")
        s.beginArrival(MG,token,110,200); s.advance(199)
        h.assertTrue(s.entry(MG).phase==CallPhase.ARRIVING,"Cooldown began before arrival")
        s.advance(200)
        h.assertTrue(s.entry(MG).start==200L && s.entry(MG).end==1739L && !s.available(MG,1738),"Cooldown does not start exactly on arrival")
        h.assertTrue(s.available(MG,1739),"Cooldown did not finish on its deadline")
        h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test", timeoutTicks=5)
    fun independentEagleChargesAndSharedAutomaticRearm(h: GameTestHelper) {
        val s=StratagemCooldownState(); s.equip(listOf(AIR,STRAFE)); var now=100L
        fun use(id:String) {
            val token=UUID.randomUUID()
            h.assertTrue(s.reserve(id,token,now),"Eagle cannot dispatch $id at $now")
            h.assertTrue(!s.available(if(id==AIR)STRAFE else AIR,now),"Another Eagle dispatched simultaneously")
            s.beginArrival(id,token,now+1,now+50); now+=50; s.advance(now)
            if(!s.rearming(now)) h.assertTrue(!s.available(STRAFE,now+142),"Shared Eagle turnaround bypassed")
            now+=143; s.advance(now)
        }
        repeat(3){use(AIR)}
        h.assertTrue(!s.rearming(now)&&s.entry(AIR).remaining==0&&s.entry(STRAFE).remaining==5,"One depleted Eagle incorrectly auto-rearmed all Eagles")
        h.assertTrue(!s.available(AIR,now)&&s.available(STRAFE,now),"Independent uses were not respected")
        repeat(5){use(STRAFE)}
        h.assertTrue(s.rearming(now)&&!s.available(AIR,now),"All depleted Eagles did not automatically rearm")
        val end=s.rearmEnd
        val restored=StratagemCooldownState.load(s.save()); restored.advance(end)
        h.assertTrue(restored.available(AIR,end)&&restored.entry(AIR).remaining==3&&restored.entry(STRAFE).remaining==5,"Rearm failed to restore all independent pools after reload")
        h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test", timeoutTicks=5)
    fun manualRearmRejectsStaleCallsAndSurvivesOfflineTime(h: GameTestHelper) {
        val s=StratagemCooldownState();s.equip(listOf(AIR,STRAFE));val token=UUID.randomUUID()
        s.reserve(AIR,token,100);s.beginArrival(AIR,token,101,150)
        h.assertTrue(s.entry(AIR).remaining==2,"Call did not consume exactly one use")
        s.beginArrival(AIR,token,102,151)
        h.assertTrue(s.entry(AIR).remaining==2,"Rescheduled arrival double-charged the use")
        h.assertTrue(s.rearm(110)&&s.rearmEnd==2162L,"Manual early-rearm upgrade not applied")
        h.assertTrue(!s.available(STRAFE,111)&&!s.beginArrival(AIR,token,112,300),"Old call can overwrite shared rearm")
        val saved=CooldownSavedData().also{it.players[UUID.randomUUID()]=s}.save(CompoundTag())
        h.assertTrue(saved.getList("Players",10).size==1,"Player ledger was not saved")
        val restored=StratagemCooldownState.load(s.save());restored.advance(5000)
        h.assertTrue(restored.available(AIR,5000)&&restored.entry(AIR).remaining==3,"Offline rearm did not finish")
        val s2=StratagemCooldownState();s2.equip(listOf(AIR));var t=0L
        repeat(3){val id=UUID.randomUUID();s2.reserve(AIR,id,t);s2.beginArrival(AIR,id,t+1,t+5);t+=200;s2.advance(t)}
        val offline=StratagemCooldownState.load(s2.save());offline.advance(10000)
        h.assertTrue(offline.available(AIR,10000),"Offline arrival and automatic rearm did not catch up")
        h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test", timeoutTicks=5)
    fun failureCancellationAndBypass(h: GameTestHelper) {
        val s=StratagemCooldownState();s.equip(listOf(AIR));val token=UUID.randomUUID()
        s.reserve(AIR,token,100);s.beginArrival(AIR,token,101,150);s.cancel(AIR,UUID.randomUUID())
        h.assertTrue(s.entry(AIR).remaining==2,"Wrong token modified an accepted call")
        s.cancel(AIR,token);s.cancel(AIR,token)
        h.assertTrue(s.entry(AIR).remaining==3&&s.available(AIR,102),"Failed drop was charged or refunded twice")
        s.reserve(MG,UUID.randomUUID(),103);s.advance(1303)
        h.assertTrue(s.available(MG,1303),"Lost beacon reservation never expires")
        s.reserve(AIR,token,1400);s.beginArrival(AIR,token,1401,1450);s.advance(1450);s.setEnabled(false,1451)
        h.assertTrue(s.available(AIR,1451)&&s.entry(AIR).remaining==3,"Bypass does not clear wait/use limits")
        val restored=StratagemCooldownState.load(s.save());h.assertTrue(!restored.enabled,"Bypass setting lost on reload")
        restored.setEnabled(true,1500);h.assertTrue(restored.available(AIR,1500),"Re-enabling left stale cooldowns")
        h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test", timeoutTicks=5)
    fun validatedInputThrowCommandAndOwnerIsolation(h: GameTestHelper) {
        val p=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"CooldownInput"))
        val hand=InteractionHand.MAIN_HAND;val stack=ItemStack(ModItems.BEACON.get())
        p.inventory.clearContent();p.setItemInHand(hand,stack)
        BeaconData.setItems(stack,listOf(ItemStack(ModItems.MACHINE_GUN_SENTRY.get()),ItemStack(ModItems.EAGLE_AIRSTRIKE.get())))
        val identity=BeaconData.ensureIdentity(stack);val s=StratagemCooldowns.state(p)
        try {
            val token=UUID.randomUUID();val now=StratagemCooldowns.now(p.server)
            s.reserve(MG,token,now);s.beginArrival(MG,token,now,now+100)
            Stratagems.get(MG)!!.code.forEach{BeaconInput.accept(p,hand,identity,it)}
            h.assertTrue(BeaconData.selected(stack)==null,"Server input accepts a cooling stratagem")
            BeaconData.activate(stack,Stratagems.get(MG)!!)
            h.assertTrue(!(ModItems.BEACON.get() as com.example.sbwhelldiver.item.BeaconItem).throwActivated(p,hand,identity),"Forged/stale activation bypasses throw check")
            BeaconData.clear(stack);BeaconInput.reset(p.uuid)
            Stratagems.eagleRearm.code.forEach{BeaconInput.accept(p,hand,identity,it)}
            h.assertTrue(s.rearming(now)&&BeaconData.selected(stack)==null,"Rearm code did not start immediate shared rearm")
            val other=StratagemCooldowns.state(p.server,UUID.randomUUID())
            h.assertTrue(other.available(MG,now),"Players share cooldowns")
            p.server.commands.performPrefixedCommand(p.createCommandSourceStack().withPermission(2),"helldivers cooldown")
            h.assertTrue(!s.enabled,"Self toggle command failed")
            p.server.commands.performPrefixedCommand(p.createCommandSourceStack().withPermission(2),"helldivers cooldown set true")
            h.assertTrue(s.enabled,"Explicit enable command failed")
            p.server.commands.performPrefixedCommand(p.createCommandSourceStack().withPermission(2),"helldivers cooldown @s")
            h.assertTrue(!s.enabled,"Target player toggle command failed")
            p.server.commands.performPrefixedCommand(p.createCommandSourceStack().withPermission(2),"helldivers cooldown set true @s")
            h.assertTrue(s.enabled,"Target player explicit enable command failed")
            val packet=CooldownSnapshotPacket(now,s.save());val buffer=FriendlyByteBuf(io.netty.buffer.Unpooled.buffer())
            try {packet.encode(buffer);val decoded=CooldownSnapshotPacket.decode(buffer);h.assertTrue(decoded==packet,"Owner snapshot changed during serialization")}finally{buffer.release()}
        } finally {
            BeaconInput.logout(p.uuid);p.inventory.clearContent();CooldownSavedData.get(p.server).players.remove(p.uuid)
        }
        h.succeed()
    }
}
