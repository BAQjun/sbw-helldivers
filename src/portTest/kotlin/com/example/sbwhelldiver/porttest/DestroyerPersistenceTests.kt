package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.stratagem.*
import com.mojang.authlib.GameProfile
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.common.util.FakePlayerFactory
import net.neoforged.neoforge.gametest.GameTestHolder
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate
import java.util.UUID

@GameTestHolder("sbw_helldiver_destroyer")
@PrefixGameTestTemplate(false)
object DestroyerPersistenceTests {
    private fun anchor()=DestroyerAnchor.at("minecraft:overworld",Vec3.ZERO,Stratagems.UP,240.0,180.0)

    @JvmStatic @GameTest(template="port_test",timeoutTicks=160)
    fun expiredFleetDepartsAndStops(h:GameTestHelper) {
        val player=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"DestroyerExpiry"))
        val ship=SuperDestroyers.state(player)
        val now=StratagemCooldowns.now(player.server)
        h.assertTrue(ship.schedule(anchor(),now-7000),"Expired fleet fixture did not start")
        ship.advance(now-6940)
        val calls=ship.callsRemaining
        h.assertTrue(ship.expired(now),"Fleet fixture must already be past its residence limit")
        // Exercise the real server tick handler, including its expiry/schedule/advance order.
        h.runAfterDelay(10){h.assertTrue(ship.transitionEnd!=0L && ship.destination==null,"Expired ship did not begin departure")}
        h.runAfterDelay(65){h.assertTrue(ship.anchor==null && ship.transitionEnd==0L,"Departure was rescheduled instead of completed")}
        h.runAfterDelay(130){
            h.assertTrue(ship.anchor==null && ship.destination==null && ship.transitionEnd==0L,"Departed ship started another warp")
            h.assertTrue(ship.callsRemaining==calls,"Automatic departure consumed another call")
            h.succeed()
        }
    }

    @JvmStatic @GameTest(template="port_test",timeoutTicks=10)
    fun legacyPendingDepartureCompletesOnReload(h:GameTestHelper) {
        // Pre-residence-limit save, or a beta save caught in the repeating departure.
        val legacy=CompoundTag().also {
            it.put("Anchor",anchor().save());it.putLong("AppearedAt",100L)
            it.putLong("TransitionEnd",7200L);it.putInt("CallsRemaining",2)
        }
        val ship=DestroyerState.load(legacy)
        h.assertTrue(!ship.expired(7200L),"A due departure must not schedule another expiry")
        h.assertTrue(!ship.schedule(anchor(),7200L),"A new command overwrote an uncommitted transition")
        h.assertTrue(ship.advance(7200L),"Saved departure did not complete at its deadline")
        h.assertTrue(!ship.advance(7201L) && ship.anchor==null && ship.transitionEnd==0L,"Saved departure completed more than once")
        h.assertTrue(ship.callsRemaining==2,"Loading or completing departure changed remaining calls")
        val restored=DestroyerState.load(ship.save())
        h.assertTrue(restored.anchor==null && !restored.expired(9000L),"Reload restarted a completed departure")
        h.succeed()
    }

    @JvmStatic @GameTest(template="port_test",timeoutTicks=10)
    fun residenceDeadlineSurvivesReloadAndReposition(h:GameTestHelper) {
        val ship=DestroyerState()
        ship.schedule(anchor(),100L);ship.advance(160L)
        h.assertTrue(!ship.expired(6159L) && ship.expired(6160L),"Residence must last exactly five minutes")
        ship.schedule(anchor().copy(direction=Stratagems.RIGHT),6100L)
        val restored=DestroyerState.load(ship.save())
        h.assertTrue(!restored.expired(6160L),"Due reposition was mistaken for an idle expired ship")
        h.assertTrue(restored.advance(6160L) && restored.residentSince==160L,"Reload or reposition extended residence")
        h.assertTrue(restored.expired(6160L),"Completed reposition suppressed expiry")
        h.assertTrue(restored.schedule(null,6160L),"Expired ship could not depart")
        h.assertTrue(!restored.schedule(anchor(),6220L),"Command replaced departure at its completion tick")
        restored.advance(6220L)
        h.assertTrue(restored.anchor==null && restored.residentSince==0L,"Expiry did not finish departure")
        h.succeed()
    }
}
