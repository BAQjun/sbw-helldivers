package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.config.*
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.network.InfiniteAmmoSettings
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.*
import java.util.UUID

@GameTestHolder("sbw_helldiver_config")
@PrefixGameTestTemplate(false)
object InfiniteAmmoGameTests {
    private fun active(pod:AbstractHellpodEntity,at:Vec3,ammo:Int?=null) {
        pod.setPos(at);val tag=CompoundTag();pod.saveWithoutId(tag)
        tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal)
        tag.putInt("HellpodActiveTicks",5000);tag.putDouble("HellpodRestY",at.y)
        tag.putBoolean("HmgHasBeenMounted",true)
        ammo?.let{tag.putInt("HmgAmmo",it)};pod.load(tag)
    }
    @JvmStatic @GameTest(templateNamespace="sbw_helldiver_config",template="sentry_test",timeoutTicks=20)
    fun manualWeaponsIgnoreLifetimeAndOnlyConsumeAmmoWhenFinite(h:GameTestHelper) {
        val snapshot=ConfigTestSnapshot()
        val original=HelldiversConfig.infiniteTurretAmmo.get();val retract=HelldiversConfig.autoRetract.get()
        val actors=mutableListOf<Entity>();val values=mutableListOf<Pair<net.minecraftforge.common.ForgeConfigSpec.IntValue,Int>>()
        try {
            HelldiversConfig.autoRetract.set(true)
            for((i,type) in listOf(ModEntities.HMG_EMPLACEMENT.get(),ModEntities.ANTI_TANK_EMPLACEMENT.get(),ModEntities.GRENADIER_BATTLEMENT.get()).withIndex()) {
                val c=TurretConfigs.forType(type)!!;values.add(c.lifetimeTicks to c.lifetimeTicks.get());c.lifetimeTicks.set(1)
                val pod=type.create(h.level) as HmgEmplacementEntity;actors.add(pod)
                val at=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(8+i*8,180,8)));h.level.getChunk(BlockPos.containing(at))
                active(pod,at,2);h.level.addFreshEntity(pod);pod.tick()
                h.assertTrue(pod.phase==AbstractHellpodEntity.Phase.ACTIVE,"Manned weapon retracted with ammo after its lifetime: $type")
                val player=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"AmmoOperator$i"));actors.add(player);player.setPos(at);h.level.addFreshEntity(player);player.startRiding(pod,true);pod.tick()
                val next=HmgEmplacementEntity::class.java.getDeclaredField("nextShot").apply{isAccessible=true}
                HelldiversConfig.infiniteTurretAmmo.set(true)
                repeat(4){next.setLong(pod,0);pod.acceptInput(player,0f,-10f,true);pod.tick()}
                h.assertTrue(pod.shotSerial==4 && pod.ammoRemaining==2,"Unlimited manned weapon stopped firing or consumed ammo: $type")
                val save=CompoundTag();pod.saveWithoutId(save);pod.load(save)
                h.assertTrue(pod.ammoRemaining==2,"Save/load changed unlimited ammunition")
                pod.tick();HelldiversConfig.infiniteTurretAmmo.set(false)
                repeat(2){next.setLong(pod,0);pod.acceptInput(player,0f,-10f,true);pod.tick()}
                h.assertTrue(pod.ammoRemaining==0 && pod.phase==AbstractHellpodEntity.Phase.ACTIVE && player.vehicle===pod,"Last round ejected the operator or finite ammo failed: $type")
                player.stopRiding();pod.tick()
                h.assertTrue(pod.phase==AbstractHellpodEntity.Phase.RETRACTING,"Empty manned weapon did not retract after dismount: $type")
                pod.discard();player.discard()
            }
        } finally {actors.forEach{it.discard()};values.forEach{it.first.set(it.second)};HelldiversConfig.infiniteTurretAmmo.set(original);HelldiversConfig.autoRetract.set(retract);snapshot.close()}
        h.succeed()
    }
    @JvmStatic @GameTest(templateNamespace="sbw_helldiver_config",template="sentry_test",timeoutTicks=10,batch="ammo_permissions")
    fun nonOperatorsCannotEnableInfiniteAmmunition(h:GameTestHelper) {
        val player=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"AmmoPermission"))
        val original=HelldiversConfig.infiniteTurretAmmo.get()
        h.assertTrue(!InfiniteAmmoSettings.update(player,!original)&&HelldiversConfig.infiniteTurretAmmo.get()==original,"Ordinary client enabled unlimited ammunition")
        player.discard();h.succeed()
    }
    @JvmStatic @GameTest(templateNamespace="sbw_helldiver_config",template="sentry_test",timeoutTicks=500,batch="ammo_combat")
    fun autonomousWeaponsKeepFiringAndRocketStillReloads(h:GameTestHelper) {
        val snapshot=ConfigTestSnapshot()
        val original=HelldiversConfig.infiniteTurretAmmo.get();val auto=HelldiversConfig.autoRetract.get()
        val types=listOf(ModEntities.MACHINE_GUN_SENTRY.get(),ModEntities.GATLING_SENTRY.get(),ModEntities.AUTOCANNON_SENTRY.get(),ModEntities.ROCKET_SENTRY.get(),ModEntities.MORTAR_SENTRY.get(),ModEntities.GAS_MORTAR_SENTRY.get(),ModEntities.EMS_MORTAR_SENTRY.get(),ModEntities.FLAME_SENTRY.get())
        val actors=mutableListOf<Entity>();val pods=mutableListOf<SentryHellpodEntity>()
        val restore=mutableListOf<()->Unit>();val forced=mutableListOf<Pair<Int,Int>>()
        fun change(v:net.minecraftforge.common.ForgeConfigSpec.IntValue,n:Int){val old=v.get();restore.add{v.set(old)};v.set(n)}
        var done=false
        fun cleanup(){if(done)return;done=true;actors.forEach{it.discard()};restore.asReversed().forEach{it()};forced.forEach{h.level.setChunkForced(it.first,it.second,false)};HelldiversConfig.infiniteTurretAmmo.set(original);HelldiversConfig.autoRetract.set(auto);snapshot.close();TurretConfigs.changed()}
        try {
            HelldiversConfig.infiniteTurretAmmo.set(true);HelldiversConfig.autoRetract.set(false)
            types.forEachIndexed {i,type ->
                val c=TurretConfigs.forType(type)!!;change(c.ammunition!!,if(i==7)8 else 2);c.magazineSize?.let{change(it,2)}
                change(c.lifetimeTicks,0)
                val at=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(8+i*80,280,8)))
                val chunk=net.minecraft.world.level.ChunkPos(BlockPos.containing(at))
                for(x in chunk.x-1..chunk.x+1)for(z in chunk.z-1..chunk.z+3)if(h.level.setChunkForced(x,z,true))forced.add(x to z)
                val pod=type.create(h.level)!!;actors.add(pod);active(pod,at)
                SentryHellpodEntity::class.java.getDeclaredMethod("onActivated").apply{isAccessible=true}.invoke(pod)
                h.level.addFreshEntity(pod);pods.add(pod)
                val target=EntityType.HUSK.create(h.level)!!;actors.add(target);target.isNoAi=true;target.isNoGravity=true;target.isInvulnerable=true;target.setPos(at.add(0.0,0.0,if(i==7)10.0 else 22.0));h.level.addFreshEntity(target)
            };TurretConfigs.changed()
            var age=0;var rocketReloaded=false;var rocketWasReloading=false
            h.onEachTick {
                try {
                    age++;val rocket=pods[3]
                    if(age==100 || age==350)pods.forEach{p->com.example.sbwhelldiver.LOGGER.info("AMMO_TEST_STATE {} shots={} clip={} target={} rotor={}",p.profile.id,p.shotSerial,p.clipRemaining,p.selectedTarget?.position(),p.rotorClock.speed)}
                    if(rocket.reloading)rocketWasReloading=true
                    if(rocketWasReloading&&!rocket.reloading&&rocket.clipRemaining>0)rocketReloaded=true
                    if(age==360){
                        pods.forEachIndexed{i,p->h.assertTrue(p.shotSerial>2,"Unlimited weapon did not continue firing: ${p.profile.id} shots=${p.shotSerial}")
                            if(i!=3)h.assertTrue(p.clipRemaining==(if(i==7)8 else 2),"Unlimited weapon consumed ammunition: ${p.profile.id}")}
                        h.assertTrue(rocketReloaded&&rocket.reserveRemaining==0,"Rocket bypassed reload or created finite reserve")
                        // Turning the option off resumes normal consumption without changing remaining stock.
                        HelldiversConfig.infiniteTurretAmmo.set(false)
                    }
                    if(age==460){
                        h.assertTrue(pods[0].clipRemaining==0&&pods[7].clipRemaining==0,"Disabling unlimited ammo did not restore consumption")
                        HelldiversConfig.infiniteTurretAmmo.set(true)
                    }
                    if(age==480){h.assertTrue(pods[0].clipRemaining>0&&pods[7].clipRemaining>0,"Enabling unlimited ammo did not revive deployed empty weapons");cleanup();h.succeed()}
                }catch(t:Throwable){cleanup();throw t}
            }
        }catch(t:Throwable){cleanup();throw t}
    }
}
