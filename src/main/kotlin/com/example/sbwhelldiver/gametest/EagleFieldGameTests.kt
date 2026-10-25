package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.effect.*
import com.example.sbwhelldiver.init.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.projectile.Snowball
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.*
import net.minecraftforge.event.entity.living.LivingEvent
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import kotlin.math.abs

@GameTestHolder("sbw_helldiver_eaglefields")
@PrefixGameTestTemplate(false)
object EagleFieldGameTests {
    private class FieldSpawnRecorder {
        val shots=mutableListOf<EagleFieldBombEntity>()
        @net.minecraftforge.eventbus.api.SubscribeEvent fun joined(event:net.minecraftforge.event.entity.EntityJoinLevelEvent) {
            (event.entity as? EagleFieldBombEntity)?.let{shots.add(it)}
        }
    }
    private fun origin(h: GameTestHelper)=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(15,20,15)))
    private fun floor(h: GameTestHelper,at: Vec3,r: Int=16) {
        val b=BlockPos.containing(at)
        for(x in -r..r)for(z in -r..r)h.level.setBlock(b.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3)
    }
    private fun cow(h: GameTestHelper,at: Vec3)=EntityType.COW.create(h.level)!!.also {
        it.setPos(at);it.isNoAi=true;it.isNoGravity=true;it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=1000.0;it.health=1000f;h.level.addFreshEntity(it)
    }
    private fun zombie(h: GameTestHelper,at: Vec3)=EntityType.ZOMBIE.create(h.level)!!.also {
        it.setPos(at);it.isNoAi=true;it.isNoGravity=true;h.level.addFreshEntity(it)
    }
    private fun player(h: GameTestHelper,at: Vec3):Player=h.makeMockPlayer().also {it.setPos(at)}
    private fun cleanup(h: GameTestHelper,at: Vec3) {
        h.level.getEntities(null,AABB(at,at).inflate(80.0)).filter {it is GroundFireEntity || it is MortarFieldEntity || it is EagleFieldBombEntity || it is net.minecraft.world.entity.animal.Cow || it is net.minecraft.world.entity.monster.Zombie}.forEach{it.discard()}
    }
    @JvmStatic @GameTest(template="sentry_test",batch="eagle_fields_damage",timeoutTicks=20)
    fun fieldPayloadsKeep240DirectWithoutGasOrSmokeBlast(h: GameTestHelper) {
        val at=origin(h);floor(h,at)
        for((i,type) in listOf(ModEntities.EAGLE_NAPALM_BOMB.get(),ModEntities.EAGLE_GAS_BOMB.get(),ModEntities.EAGLE_SMOKE_BOMB.get()).withIndex()) {
            val p=at.add(i*35.0,0.0,0.0);val victim=cow(h,p);val bystander=cow(h,p.add(1.0,0.0,0.0))
            val bomb=type.create(h.level)!!;bomb.setPos(p.add(-3.0,.5,0.0));bomb.shoot(1.0,0.0,0.0,6f,0f);h.level.addFreshEntity(bomb);bomb.tickCount=1
            bomb.onHitEntity(com.atsuishio.superbwarfare.world.phys.ExtendedEntityRayTraceResult(com.atsuishio.superbwarfare.world.phys.EntityResult(victim,p.add(0.0,.5,0.0),false,false)))
            h.assertTrue(abs(victim.health-(if(i==0)720f else 760f))<.01,"Direct/area damage wrong for $type: ${victim.health}")
            h.assertTrue(bystander.health==(if(i==0)960f else 1000f),"Unexpected gas/smoke blast or wrong napalm inner damage")
            victim.discard();bystander.discard();bomb.discard()
        }
        cleanup(h,at);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="eagle_fields_terrain",timeoutTicks=20)
    fun napalmFollowsTerrainAndSaves21SecondHeavyFire(h: GameTestHelper) {
        val at=origin(h);floor(h,at)
        h.level.setBlock(BlockPos.containing(at.add(2.0,0.0,0.0)),Blocks.STONE.defaultBlockState(),3)
        val field=GroundFireEntity.create(h.level,at,null,GroundFireEntity.NAPALM_RADIUS,true)
        val stepped=BlockPos.containing(at.add(2.0,1.0,0.0))
        h.assertTrue(field.remainingTicks==420 && field.coversCell(stepped) && h.level.getBlockState(stepped).`is`(ModBlocks.SENTRY_FIRE.get()),"Napalm did not follow a raised terrain cell")
        h.assertTrue(!field.coversCell(BlockPos.containing(at.add(11.0,0.0,0.0))),"Fire outside footprint")
        val tag=CompoundTag();field.saveWithoutId(tag);val restored=ModEntities.GROUND_FIRE.get().create(h.level)!!;restored.load(tag)
        h.assertTrue(restored.heavyBurning && restored.remainingTicks==420 && restored.coversCell(stepped),"Saved napalm lost lifetime/terrain/heavy effect")
        restored.discard();cleanup(h,at);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="eagle_fields_burn",timeoutTicks=20)
    fun napalmRefreshesThreeSecondNormalPlusOneSecondDoubleBurn(h: GameTestHelper) {
        val at=origin(h);floor(h,at);val field=GroundFireEntity.create(h.level,at,null,10.0,true);val victim=cow(h,at)
        field.igniteInside(victim)
        h.assertTrue(victim.getEffect(ModEffects.BURNING.get())?.duration==60 && victim.getEffect(ModEffects.HEAVY_BURNING.get())?.duration==20,"Burn durations")
        victim.tickCount=20
        ModEffects.BURNING.get().applyEffectTick(victim,0);ModEffects.HEAVY_BURNING.get().applyEffectTick(victim,0)
        h.assertTrue(abs(victim.health-952f)<.01,"Normal and double heavy burn must stack to 48; got ${1000-victim.health}")
        victim.removeEffect(ModEffects.HEAVY_BURNING.get());h.assertTrue(victim.hasEffect(ModEffects.BURNING.get()),"Heavy expiry removed normal burn")
        cleanup(h,at);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="eagle_fields_gas",timeoutTicks=20)
    fun gasRadiusLifetimeAndSixSecondTailSurviveSave(h: GameTestHelper) {
        val at=origin(h);val field=MortarFieldEntity.create(h.level,at,MortarFieldEntity.Kind.GAS,12.0,effectTicks=120)
        val inside=cow(h,at.add(11.5,.1,0.0));val outside=cow(h,at.add(12.8,.1,0.0));field.tick()
        h.assertTrue(inside.getEffect(ModEffects.GAS.get())?.duration==120 && !outside.hasEffect(ModEffects.GAS.get()),"Gas radius or effect tail")
        val tag=CompoundTag();field.saveWithoutId(tag);val loaded=ModEntities.MORTAR_FIELD.get().create(h.level)!!;loaded.load(tag)
        h.assertTrue(loaded.remainingTicks==299 && loaded.radius==12.0 && loaded.effectTicks==120,"Gas persistence")
        loaded.discard();cleanup(h,at);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="eagle_fields_gas_height",timeoutTicks=20)
    fun smallGasCloudsHaveLowCanopiesWithoutChangingEagleSmokeOrEms(h: GameTestHelper) {
        val at=origin(h).add(0.0,120.0,0.0)
        for((i,radius) in listOf(6.0,7.0,12.0).withIndex()) {
            val p=at.add(i*30.0,0.0,0.0)
            val field=MortarFieldEntity.create(h.level,p,MortarFieldEntity.Kind.GAS,radius,mineCloud=i==0)
            val expected=if(i==0)36.0/14 else if(i==1)3.5 else 10.0
            val inside=cow(h,p.add(0.0,expected-.4,0.0));val above=cow(h,p.add(0.0,expected+.5,0.0));field.tick()
            h.assertTrue(abs(field.fieldHeight-expected)<1e-6 && inside.hasEffect(ModEffects.GAS.get()) && !above.hasEffect(ModEffects.GAS.get()),"Gas height/effect coverage differs: radius=$radius height=${field.fieldHeight}")
            field.discard();inside.discard();above.discard()
        }
        val smoke=MortarFieldEntity.create(h.level,at,MortarFieldEntity.Kind.SMOKE)
        h.assertTrue(smoke.fieldHeight==10.0,"Smoke height changed with gas");smoke.discard()
        val ems=MortarFieldEntity.create(h.level,at,MortarFieldEntity.Kind.EMS)
        h.assertTrue(ems.fieldHeight==3.0,"EMS height changed with gas");ems.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="eagle_fields_center",timeoutTicks=20)
    fun everyBombingPatternCentersTheCompleteSalvoAtEveryHeading(h: GameTestHelper) {
        for(attack in EagleAttack.entries.filter{it.levelBombing})for(yaw in listOf(0.0,23.0,90.0,137.0)) {
            val a=Math.toRadians(yaw);val f=Vec3(kotlin.math.cos(a),0.0,kotlin.math.sin(a))
            val points=(0 until attack.count).map{attack.landingOffset(it,f)}
            h.assertTrue(points.fold(Vec3.ZERO){sum,p->sum.add(p)}.scale(1.0/attack.count).lengthSqr()<1e-10,"$attack salvo off-center at $yaw")
            h.assertTrue(points.distinct().size==attack.count,"$attack duplicate impact at $yaw")
        }
        h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="eagle_fields_no_generation",timeoutTicks=20)
    fun planningDistantFlightDoesNotSynchronouslyLoadTerrain(h: GameTestHelper) {
        val source=h.level.chunkSource;val before=source.loadedChunksCount
        val strike=ModEntities.EAGLE.get().create(h.level)!!
        try {
            strike.configure(Vec3(8_000_000.0,80.0,8_000_000.0),Vec3(1.0,0.0,0.0),EagleAttack.SMOKE,null)
            h.assertTrue(source.loadedChunksCount==before,"Planning loaded distant terrain synchronously: $before -> ${source.loadedChunksCount}")
        } finally {strike.discard()}
        h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="eagle_fields_real_impacts",timeoutTicks=260)
    fun boundedChunkLoadingStillDeliversAllGasAndSmokeImpacts(h: GameTestHelper) {
        val anchor=origin(h);val strikes=mutableListOf<EagleEntity>();val fields=mutableListOf<MortarFieldEntity>()
        val observer=object {
            @net.minecraftforge.eventbus.api.SubscribeEvent fun joined(e:net.minecraftforge.event.entity.EntityJoinLevelEvent) {
                val f=e.entity as? MortarFieldEntity ?: return
                if(f.position().distanceTo(Vec3(anchor.x,200.02,anchor.z))<150)fields.add(f)
            }
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(observer)
        for((i,attack) in listOf(EagleAttack.GAS,EagleAttack.SMOKE).withIndex()) {
            val at=Vec3(anchor.x+i*80,200.0,anchor.z);val f=Vec3(1.0,0.0,0.0)
            for(j in 0 until attack.count){val p=BlockPos.containing(at.add(attack.landingOffset(j,f))).below();for(dx in -2..2)for(dz in -2..2)h.level.setBlock(p.offset(dx,0,dz),Blocks.STONE.defaultBlockState(),2)}
            val strike=ModEntities.EAGLE.get().create(h.level)!!;strike.configure(at,f,attack,null);h.level.addFreshEntity(strike);strikes.add(strike)
        }
        h.succeedWhen {
            h.assertTrue(fields.count{it.kind==MortarFieldEntity.Kind.GAS}==4 && fields.count{it.kind==MortarFieldEntity.Kind.SMOKE}==9,"Canisters stalled before actual impact: gas=${fields.count{it.kind==MortarFieldEntity.Kind.GAS}}, smoke=${fields.count{it.kind==MortarFieldEntity.Kind.SMOKE}}")
            h.assertTrue(strikes.all{it.strikeComplete && it.isRemoved},"Carrier retired before ordnance resolved")
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(observer);fields.forEach{it.discard()};strikes.forEach{it.discard()}
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="eagle_fields_smoke",timeoutTicks=20)
    fun smokeBlocksNewTargetsButPreservesVanillaGoalsAndExistingAggro(h: GameTestHelper) {
        // Keep this sight fixture above adjacent test structures and their floors.
        val anchor=origin(h);val at=Vec3(anchor.x,200.0,anchor.z);val mob=zombie(h,at.add(-20.0,0.0,0.0));val target=player(h,at.add(20.0,0.0,0.0))
        h.assertTrue(mob.hasLineOfSight(target),"Baseline smoke sight fixture is obstructed")
        val goals=mob.goalSelector.availableGoals.map{it.goal};val targets=mob.targetSelector.availableGoals.map{it.goal}
        mob.target=target;SmokeOcclusion.tick(LivingEvent.LivingTickEvent(mob))
        val field=MortarFieldEntity.create(h.level,at,MortarFieldEntity.Kind.SMOKE)
        h.assertTrue(!mob.hasLineOfSight(target) && SmokeOcclusion.blocks(mob,target),"Smoke failed to occlude")
        SmokeOcclusion.tick(LivingEvent.LivingTickEvent(mob))
        h.assertTrue(mob.target===target,"Smoke replaced vanilla aggro")
        mob.target=null;mob.target=target
        h.assertTrue(mob.target==null,"A new target was acquired through smoke")
        h.assertTrue(goals==mob.goalSelector.availableGoals.map{it.goal} && targets==mob.targetSelector.availableGoals.map{it.goal},"Smoke replaced species AI goals")
        field.discard();mob.target=target;h.assertTrue(mob.target===target && mob.hasLineOfSight(target),"Smoke expiry still blocked targeting")
        cleanup(h,at);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="eagle_fields_path",timeoutTicks=20)
    fun existingPathUsesLastSeenPositionRatherThanUnseenMovement(h: GameTestHelper) {
        val at=origin(h);floor(h,at,35);val mob=zombie(h,at.add(-10.0,0.0,0.0));val target=player(h,at.add(10.0,0.0,0.0))
        mob.setOnGround(true);mob.target=target;SmokeOcclusion.tick(LivingEvent.LivingTickEvent(mob));val last=target.position()
        h.assertTrue(mob.navigation.createPath(target,0)!=null,"Baseline path fixture is not navigable")
        MortarFieldEntity.create(h.level,at,MortarFieldEntity.Kind.SMOKE)
        target.setPos(target.position().add(0.0,0.0,8.0));SmokeOcclusion.tick(LivingEvent.LivingTickEvent(mob))
        val path=mob.navigation.createPath(target,0)
        h.assertTrue(path!=null && path.target==BlockPos.containing(last),"Vanilla path leaked current hidden player location: ${path?.target} instead of $last")
        cleanup(h,at);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="eagle_fields_attack",timeoutTicks=20)
    fun insideSmokeOnlyCapsRangeAndAttacksRevealShotOrigin(h: GameTestHelper) {
        val at=origin(h);val mob=zombie(h,at.add(0.0,.1,0.0));val target=player(h,at.add(3.0,.1,0.0))
        MortarFieldEntity.create(h.level,at,MortarFieldEntity.Kind.SMOKE)
        h.assertTrue(mob.hasLineOfSight(target),"Close sight inside smoke was removed")
        target.setPos(at.add(8.0,.1,0.0));h.assertTrue(!mob.hasLineOfSight(target),"In-smoke perception range was not reduced")
        val launchedAt=target.position();val shot=Snowball(h.level,target);h.level.addFreshEntity(shot)
        target.setPos(at.add(10.0,.1,2.0))
        mob.hurt(h.level.damageSources().thrown(shot,target),1f)
        h.assertTrue(SmokeOcclusion.knownPosition(mob,target)==launchedAt,"Through-smoke hit revealed current position instead of launch origin")
        shot.discard();cleanup(h,at);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="eagle_fields_flight",timeoutTicks=20)
    fun nativeProfilesReleaseAllCanistersAndUseSharedRearm(h: GameTestHelper) {
        val record=FieldSpawnRecorder();net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(record)
        try { for((attack,count) in listOf(EagleAttack.NAPALM to 5,EagleAttack.GAS to 4,EagleAttack.SMOKE to 9)) {
            record.shots.clear()
            h.assertTrue(attack.perpendicular && attack.callTicks==82 && attack.defaultUses==3 && attack.count==count,"Wrong native field strike profile")
            val strike=ModEntities.EAGLE.get().create(h.level)!!;strike.configure(origin(h),Vec3(1.0,0.0,0.0),attack,null)
            val tag=CompoundTag();strike.saveWithoutId(tag);tag.putLong("StartedAt",h.level.gameTime-(attack.releaseAge(count-1).toLong()+1));strike.load(tag);h.level.addFreshEntity(strike);strike.tick()
            h.assertTrue(strike.fired==count,"Field strike withdrew before releasing all payloads")
            // Observe actual accepted spawns, including entities awaiting a remote chunk's
            // entity-ticking transition; a same-tick spatial query can omit those entities.
            val shots=record.shots.toList()
            h.assertTrue(shots.size==count,"Carrier count differs from actual canisters: ${shots.size}/$count")
            if(attack==EagleAttack.NAPALM || attack==EagleAttack.GAS || attack==EagleAttack.SMOKE) {
                val expected=if(attack==EagleAttack.NAPALM) listOf(Vec3(-28.0,0.0,1.0),Vec3(-14.0,0.0,-1.0),Vec3.ZERO,Vec3(14.0,0.0,1.0),Vec3(28.0,0.0,-1.0))
                else if(attack==EagleAttack.GAS)
                    listOf(Vec3(-18.0,0.0,-2.0),Vec3(-6.0,0.0,2.0),Vec3(6.0,0.0,-2.0),Vec3(18.0,0.0,2.0))
                else listOf(Vec3(-20.0,0.0,5.0),Vec3(-15.0,0.0,-5.0),Vec3(-10.0,0.0,5.0),Vec3(-5.0,0.0,-5.0),Vec3.ZERO,Vec3(5.0,0.0,5.0),Vec3(10.0,0.0,-5.0),Vec3(15.0,0.0,5.0),Vec3(20.0,0.0,-5.0))
                h.assertTrue(expected.distinct().size==count,"Upgraded field canisters must not repeat a landing point")
                h.assertTrue(expected.fold(Vec3.ZERO){a,p->a.add(p)}.lengthSqr()<1e-10,"Full upgraded pattern is not centered on the beacon")
                h.assertTrue(expected.zipWithNext().all{(a,b)->b.x>a.x},"$attack bombing order returns to an earlier point")
                for((i,shot) in shots.withIndex()) {
                    val t=EagleFlight.BOMB_FLIGHT.toDouble()
                    val landing=shot.position().add(shot.deltaMovement.scale(t)).add(0.0,-EagleFlight.BOMB_GRAVITY*t*(t-1)/2,0.0)
                    h.assertTrue(landing.distanceTo(strike.target.add(expected[i]))<.01,"$attack canister $i misses the centered landing pattern: $landing")
                    h.assertTrue(shot.deltaMovement.x>=1.999,"$attack canister $i was launched backwards or stalled")
                    h.assertTrue(shot.deltaMovement.y<0,"$attack canister $i was launched upwards")
                    h.assertTrue(attack.releaseAge(i)<EagleFlight.attackEnd(attack),"Aircraft pulled up before canister $i")
                    val turned=attack.landingOffset(i,Vec3(0.0,0.0,1.0))
                    h.assertTrue(turned.distanceTo(Vec3(-expected[i].z,0.0,expected[i].x))<.001,"$attack pattern fails to rotate with flight heading")
                }
            }
            shots.forEach{it.discard()};strike.discard()
        }
        } finally {record.shots.forEach{it.discard()};net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(record)}
        h.level.getEntities(null,AABB(origin(h),origin(h)).inflate(2000.0)).filter{it is EagleFieldBombEntity || it is EagleEntity}.forEach{it.discard()}
        h.succeed()
    }
}
