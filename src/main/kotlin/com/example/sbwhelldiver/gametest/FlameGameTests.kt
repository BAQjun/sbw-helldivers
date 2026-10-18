package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.asset.AssetCatalog
import com.example.sbwhelldiver.effect.FlameDamage
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.*
import kotlin.math.abs

@GameTestHolder("sbw_helldiver_flame")
@PrefixGameTestTemplate(false)
object FlameGameTests {
    private fun origin(h: GameTestHelper): Vec3 {
        val p=h.absolutePos(BlockPos(15,300,15))
        // Range/deployment checks extend beyond the tiny template. Keep their entities ticking
        // even when this batch runs without the neighbouring regression-test structures.
        for(cx in ((p.x-4) shr 4)..((p.x+4) shr 4))for(cz in ((p.z-4) shr 4)..((p.z+38) shr 4)) {
            h.level.setChunkForced(cx,cz,true);h.level.getChunk(cx,cz)
        }
        for(dx in -2..2)for(dz in -1..37)for(y in 0..3)
            h.level.setBlock(p.offset(dx,y,dz),Blocks.AIR.defaultBlockState(),2)
        return Vec3(p.x+.5,p.y.toDouble(),p.z+.5)
    }
    private fun mob(h: GameTestHelper,p: Vec3)=EntityType.HUSK.create(h.level)!!.also {
        it.isNoAi=true;it.isNoGravity=true;it.setPos(p)
        it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=200.0;it.health=200f
        it.getAttribute(Attributes.ARMOR)!!.baseValue=0.0;h.level.addFreshEntity(it)
    }
    private fun player(h: GameTestHelper,p: Vec3)=object: Player(h.level,BlockPos.ZERO,0f,com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"FlameTest")) {
        override fun isSpectator()=false
        override fun isCreative()=false
    }.also { it.isNoGravity=true;it.setPos(p);h.level.addFreshEntity(it) }

    private fun deployed(h:GameTestHelper,p:Vec3):SentryHellpodEntity {
        val pod=ModEntities.FLAME_SENTRY.get().create(h.level)!!;pod.setPos(p)
        val tag=CompoundTag();pod.saveWithoutId(tag)
        tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal)
        tag.putDouble("HellpodRestY",p.y);tag.putBoolean("HellpodCapReleased",true)
        pod.load(tag);h.level.addFreshEntity(pod);return pod
    }

    @JvmStatic @GameTest(template="sentry_test",batch="flame_destruction_blast",timeoutTicks=20)
    fun destructionBlastRadiiCooldownAndSingleDetonation(h:GameTestHelper) {
        val p=origin(h);val pod=deployed(h,p)
        val distances=listOf(0.0,.4,.41,2.2,4.0,4.1)
        val victims=distances.map { d ->
            mob(h,p).also { it.setPos(p.x+d+it.bbWidth/2.0,p.y,p.z) }
        }
        val protected=mob(h,p.add(0.0,0.0,.1)).also{it.isInvulnerable=true}
        victims[1].hurt(h.level.damageSources().generic(),1f)
        val before=victims.map{it.health}
        pod.hurt(com.atsuishio.superbwarfare.init.ModDamageTypes.causeGunFireDamage(h.level.registryAccess(),null,EntityType.HUSK.create(h.level)!!),10000f)
        h.assertTrue(pod.phase==AbstractHellpodEntity.Phase.SPENT && !pod.isWreck,"Destroyed flame sentry did not stop at its empty pod")
        val expected=listOf(60f,60f,(59.0*(4.0-.41)/3.6).toFloat(),29.5f,0f,0f)
        victims.forEachIndexed { i,v ->
            h.assertTrue(abs(before[i]-v.health-expected[i])<.02f,"Fuel blast at ${distances[i]} m: expected ${expected[i]}, got ${before[i]-v.health}")
        }
        h.assertTrue(protected.health==200f,"Fuel blast bypassed entity invulnerability")
        val after=victims.map{it.health}
        pod.destroy();repeat(10){pod.tick()}
        h.assertTrue(victims.map{it.health}==after,"Fuel blast repeated or native wreck damage stacked on top")
        val saved=CompoundTag();pod.saveWithoutId(saved);pod.discard()
        val restored=ModEntities.FLAME_SENTRY.get().create(h.level)!!;restored.load(saved);h.level.addFreshEntity(restored)
        restored.destroy();restored.tick()
        h.assertTrue(restored.phase==AbstractHellpodEntity.Phase.SPENT && victims.map{it.health}==after,"Reloading an empty pod detonated fuel again")
        (victims+listOf(protected,restored)).forEach{it.discard()};h.succeed()
    }

    @JvmStatic @GameTest(template="sentry_test",batch="flame_destruction_fire",timeoutTicks=20)
    fun destructionFireMatchesJetAndNormalRecoveryDoesNotIgnite(h:GameTestHelper) {
        val p=origin(h)
        for(dx in -5..5)for(dz in -5..5)h.level.setBlock(BlockPos.containing(p.add(dx.toDouble(),-1.0,dz.toDouble())),Blocks.STONE.defaultBlockState(),2)
        val pod=deployed(h,p);val inside=mob(h,p.add(2.0,0.0,0.0));val outside=mob(h,p.add(4.6,0.0,0.0))
        pod.hurt(com.atsuishio.superbwarfare.init.ModDamageTypes.causeGunFireDamage(h.level.registryAccess(),null,EntityType.HUSK.create(h.level)!!),10000f)
        h.assertTrue(pod.phase==AbstractHellpodEntity.Phase.SPENT,"Lethal gun damage failed to destroy flame sentry: health=${pod.health}, phase=${pod.phase}")
        fun fields()=h.level.getEntitiesOfClass(GroundFireEntity::class.java,net.minecraft.world.phys.AABB(p,p).inflate(6.0))
        val field=fields().single()
        h.assertTrue(field.radius in 3.0..4.0 && field.remainingTicks==300,"Fuel fire must have a 3-4 m radius and the jet's 15-second lifetime")
        field.tick()
        h.assertTrue(h.level.getBlockState(inside.blockPosition()).lightEmission==Blocks.FIRE.defaultBlockState().lightEmission,"Ground fire must emit the same block light as vanilla fire")
        h.assertTrue(inside.getEffect(ModEffects.BURNING.get())?.duration==60 && !inside.hasEffect(ModEffects.PANIC.get()),"Fuel fire did not use the same burn as jet ground fire")
        h.assertTrue(!outside.hasEffect(ModEffects.BURNING.get()) && outside.health==200f,"Fuel fire/blast crossed its outer radius")
        val hp=inside.health;inside.invulnerableTime=0;FlameDamage.burn(inside)
        h.assertTrue(abs(inside.health-(hp-16f))<.01f,"Fuel ground fire did not deal the jet ground fire's 16 HP per second")
        pod.destroy();h.assertTrue(fields().size==1,"Destroyed fuel tank created duplicate fire fields")
        val recovering=deployed(h,p.add(12.0,0.0,0.0));val rt=CompoundTag();recovering.saveWithoutId(rt)
        rt.putInt("HellpodPhase",AbstractHellpodEntity.Phase.RETRACTING.ordinal);recovering.load(rt)
        repeat(recovering.retractTicks+1){recovering.tick()}
        h.assertTrue(recovering.phase==AbstractHellpodEntity.Phase.DONE && fields().size==1,"Normal recovery detonated fuel")
        val saved=CompoundTag();field.saveWithoutId(saved);val restored=ModEntities.GROUND_FIRE.get().create(h.level)!!;restored.load(saved)
        h.assertTrue(restored.radius==field.radius && restored.remainingTicks==299,"Reload reset fuel-fire radius or lifetime")
        repeat(299){field.tick()};field.tick();h.assertTrue(field.isRemoved,"Fuel ground fire outlived jet ground fire")
        listOf(pod,inside,outside,recovering,restored).forEach{it.discard()}
        for(dx in -5..5)for(dz in -5..5){val at=BlockPos.containing(p.add(dx.toDouble(),0.0,dz.toDouble()));if(h.level.getBlockState(at).`is`(ModBlocks.SENTRY_FIRE.get()))h.level.removeBlock(at,false)}
        h.succeed()
    }

    @JvmStatic @GameTest(template="sentry_test",batch="flame",timeoutTicks=10)
    fun ignitionCadenceIdleResumeAndPersistence(h: GameTestHelper) {
        val c=FlameClock();val shots=mutableListOf<Long>()
        for(t in 0L..119L)if(c.advance(t,true,true,900))shots.add(t)
        h.assertTrue(shots.first()==20L && shots.zipWithNext().all { it.second-it.first==5L },"Ignition/cadence incorrect: $shots")
        val saved=CompoundTag();c.save(saved,120);val restored=FlameClock();restored.restore(saved,1000)
        h.assertTrue(!restored.advance(1000,false,false,900) && restored.state==FlameClock.State.READY,"Idle loses ignition")
        h.assertTrue(restored.advance(1001,true,true,900),"Resuming during idle incurred a second ignition")
        for(t in 1002L..1240L)restored.advance(t,false,false,900)
        h.assertTrue(restored.state==FlameClock.State.READY,"Idle extinguished too early")
        restored.advance(1241,false,false,900);h.assertTrue(restored.state==FlameClock.State.OFF,"Twelve second shutdown missing")
        h.assertTrue(!restored.advance(1242,true,true,900) && restored.state==FlameClock.State.IGNITING,"Reacquisition did not ignite")
        val warming=CompoundTag();restored.save(warming,1247);val warmReload=FlameClock();warmReload.restore(warming,2000)
        for(t in 2000L..2014L)h.assertTrue(!warmReload.advance(t,true,true,900),"Save bypassed ignition")
        h.assertTrue(warmReload.advance(2015,true,true,900),"Warmup remainder not preserved")
        h.assertTrue(!warmReload.advance(2016,true,true,1) && warmReload.state==FlameClock.State.OFF,"Odd remaining ammo sprayed")
        val p=AssetCatalog.profiles.getValue("flame_sentry")
        h.assertTrue(p.totalAmmo==900 && p.isFlame && p.shellOffset==null,"Wrong flame profile")
        h.succeed()
    }

    @JvmStatic @GameTest(template="sentry_test",batch="flame",timeoutTicks=10)
    fun exactCustomDamagePanicAndVanillaFireExclusion(h: GameTestHelper) {
        val p=origin(h);val v=mob(h,p);val sentry=ModEntities.FLAME_SENTRY.get().create(h.level)!!
        val speed=v.getAttributeValue(Attributes.MOVEMENT_SPEED)
        v.getAttribute(Attributes.ARMOR)!!.baseValue=30.0;v.addEffect(MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,1000,4))
        val motion=Vec3(-.07,.05,.12);v.deltaMovement=motion;v.hasImpulse=false
        repeat(4) { FlameDamage.hit(v,sentry) }
        h.assertTrue(v.deltaMovement==motion && !v.hasImpulse,"Flame jets added a physical hit impulse")
        h.assertTrue(v.health==196f,"One-damage jets were lost to the native hit cooldown/armor: ${v.health}")
        h.assertTrue(v.getEffect(ModEffects.BURNING.get())?.duration==60 && v.getEffect(ModEffects.PANIC.get())?.duration==20,"Wrong burn/panic durations")
        h.assertTrue(abs(v.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed*.75)<1e-6,"Panic is not 25 percent slow")
        val other=mob(h,p.add(2.0,0.0,0.0));v.target=other
        h.assertTrue(v.target==null,"Panic retained a target")
        val hp=v.health;v.hurt(h.level.damageSources().onFire(),1f);v.hurt(h.level.damageSources().inFire(),1f)
        h.assertTrue(v.health==hp,"Vanilla fire stacked with custom burning")
        for(t in 1..19) { v.tickCount=t;ModEffects.BURNING.get().applyEffectTick(v,0) }
        h.assertTrue(v.health==hp,"Burn damage ran faster than one second")
        v.tickCount=20;ModEffects.BURNING.get().applyEffectTick(v,0)
        h.assertTrue(v.health==hp-16f,"Burn is not exactly sixteen HP per second")
        val user=player(h,p.add(4.0,0.0,0.0));FlameDamage.hit(user,sentry)
        h.assertTrue(user.hasEffect(ModEffects.BURNING.get()) && !user.hasEffect(ModEffects.PANIC.get()),"Player flame/panic rules incorrect")
        v.removeEffect(ModEffects.PANIC.get());v.target=other
        h.assertTrue(v.target===other && abs(v.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed)<1e-6,"Panic failed to restore speed/targeting")
        v.removeEffect(ModEffects.BURNING.get());h.assertTrue(!v.isOnFire,"Custom burn left vanilla fire ticking")
        v.health=16f;FlameDamage.ignite(v,sentry);v.tickCount=40;ModEffects.BURNING.get().applyEffectTick(v,0)
        h.assertTrue(v.health<=0,"Custom burn cannot kill")
        listOf(v,other,user,sentry).forEach { it.discard() };h.succeed()
    }

    @JvmStatic @GameTest(template="sentry_test",batch="flame",timeoutTicks=20)
    fun streamRangeWallsAndGroundFireLifetime(h: GameTestHelper) {
        val p=origin(h);val sentry=ModEntities.FLAME_SENTRY.get().create(h.level)!!
        val near=mob(h,p.add(0.0,0.0,8.0));val far=mob(h,p.add(0.0,0.0,36.0))
        val spread=mob(h,p.add(.8,0.0,20.0));val outside=mob(h,p.add(2.0,0.0,20.0))
        val ray=h.level.clip(net.minecraft.world.level.ClipContext(p.add(0.0,1.0,0.0),p.add(0.0,1.0,35.0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,sentry))
        val query=h.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity::class.java,net.minecraft.world.phys.AABB(p.add(0.0,1.0,0.0),ray.location).inflate(.3))
        FlameJet.spray(h.level,sentry,p.add(0.0,1.0,0.0),Vec3(0.0,0.0,1.0))
        h.assertTrue(near.health==199f && far.health==200f,"Jet range is not 35 blocks: ${near.health}/${far.health}, p=$p ray=${ray.type}/${ray.location}, query=${query.map { it.position() }}, nearClip=${near.boundingBox.inflate(.3).clip(p.add(0.0,1.0,0.0),ray.location)}")
        h.assertTrue(spread.health==199f && outside.health==200f,"Spread damage does not match the visible expanding jet")
        for(dx in -1..1)for(y in 0..2)h.level.setBlock(BlockPos.containing(p.add(dx.toDouble(),y.toDouble(),4.0)),Blocks.BEDROCK.defaultBlockState(),2)
        FlameJet.spray(h.level,sentry,p.add(0.0,1.0,0.0),Vec3(0.0,0.0,1.0))
        h.assertTrue(near.health==199f,"Flames burned through a wall")
        val ground=p.add(5.0,0.0,0.0);val stepping=mob(h,ground)
        val support=BlockPos.containing(ground).below();val previousSupport=h.level.getBlockState(support)
        h.level.setBlock(support,Blocks.STONE.defaultBlockState(),2)
        val field=GroundFireEntity.create(h.level,ground,sentry);field.tick()
        h.assertTrue(stepping.getEffect(ModEffects.BURNING.get())?.duration==60 && !stepping.hasEffect(ModEffects.PANIC.get()),"Ground flame does not refresh three seconds burn")
        val saved=CompoundTag();field.saveWithoutId(saved);val restored=ModEntities.GROUND_FIRE.get().create(h.level)!!;restored.load(saved)
        h.assertTrue(restored.remainingTicks==299,"Save reset ground flame lifetime")
        repeat(299) { field.tick() };field.tick();h.assertTrue(field.isRemoved,"Ground flame outlived fifteen seconds")
        for(dx in -1..1)for(y in 0..2)h.level.setBlock(BlockPos.containing(p.add(dx.toDouble(),y.toDouble(),4.0)),Blocks.AIR.defaultBlockState(),2)
        h.level.setBlock(support,previousSupport,2)
        listOf(near,far,spread,outside,stepping,sentry,restored).forEach { it.discard() };h.succeed()
    }

    @JvmStatic @GameTest(template="sentry_test",batch="flame",timeoutTicks=700)
    fun actualDeploymentAmmoIgnitionResumeAndShutdown(h: GameTestHelper) {
        val p=origin(h)
        for(dx in -3..3)for(dz in -3..18)h.level.setBlock(BlockPos.containing(p.add(dx.toDouble(),-1.0,dz.toDouble())),Blocks.BEDROCK.defaultBlockState(),2)
        val pod=ModEntities.FLAME_SENTRY.get().create(h.level)!!
        pod.isInvulnerable=true;pod.moveTo(p.x,p.y+30+pod.profile.podHeight,p.z,0f,0f);h.level.addFreshEntity(pod)
        var enemy=mob(h,p.add(0.0,0.0,12.0)).also { it.isInvulnerable=true }
        var ignitedAt=-1L;var stoppedAt=-1L;var resumedAt=-1L;var finalGapAt=-1L;var serial=0
        val shots=mutableListOf<Long>()
        h.runAfterDelay(699) { h.fail("Flame deployment incomplete: phase=${pod.phase} age=${pod.phaseAge} entityTicks=${pod.tickCount} position=${pod.position()} ignition=$ignitedAt serial=$serial stopped=$stoppedAt resumed=$resumedAt finalGap=$finalGapAt target=${enemy.position()} valid=${pod.validTarget(enemy)}") }
        h.onEachTick {
            val now=h.level.gameTime
            if(pod.flameState==FlameClock.State.IGNITING && ignitedAt<0) { ignitedAt=now;h.assertTrue(pod.clipRemaining==900,"Fuel spent while igniting") }
            if(pod.shotSerial!=serial) {
                shots.add(now);serial=pod.shotSerial
                h.assertTrue(pod.clipRemaining==900-2*serial,"Jet ammo cost incorrect")
                if(serial==1)h.assertTrue(now-ignitedAt in 20L..21L,"First flame ignored one second ignition: ${now-ignitedAt}")
            }
            if(serial>=8 && stoppedAt<0) { enemy.discard();stoppedAt=now }
            if(stoppedAt>=0 && resumedAt<0 && now-stoppedAt>=40) {
                h.assertTrue(pod.flameState==FlameClock.State.READY,"Idle flame lost readiness")
                enemy=mob(h,p.add(0.0,0.0,12.0)).also { it.isInvulnerable=true };resumedAt=now
            }
            if(resumedAt>=0 && finalGapAt<0 && serial>=12) { enemy.discard();finalGapAt=now }
            if(finalGapAt>=0 && now-finalGapAt>=242) {
                h.assertTrue(pod.flameState==FlameClock.State.OFF && pod.phase==AbstractHellpodEntity.Phase.ACTIVE,"Flame did not extinguish after idle")
                h.assertTrue(shots.zipWithNext().all { it.second-it.first>=5L },"Jets exceeded five-tick cadence")
                h.assertTrue(shots.first { it>=resumedAt }-resumedAt<=6,"Idle resumption ignited a second time")
                h.assertTrue(abs(pod.data().compute().seekInfo!!.maxSeekRange-35)<1e-6 && pod.getMaxHealth()==120f,"Range/health mismatch")
                pod.discard();h.succeed()
            }
        }
    }
}
