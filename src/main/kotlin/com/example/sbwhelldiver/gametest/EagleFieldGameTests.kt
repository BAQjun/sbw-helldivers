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
    @JvmStatic @GameTest(template="sentry_test",batch="eagle_fields_smoke",timeoutTicks=20)
    fun smokeBlocksNewTargetsButPreservesVanillaGoalsAndExistingAggro(h: GameTestHelper) {
        val at=origin(h);val mob=zombie(h,at.add(-20.0,0.0,0.0));val target=player(h,at.add(20.0,0.0,0.0))
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
        for((attack,count) in listOf(EagleAttack.NAPALM to 5,EagleAttack.GAS to 4,EagleAttack.SMOKE to 9)) {
            h.assertTrue(attack.perpendicular && attack.callTicks==82 && attack.defaultUses==3 && attack.count==count,"Wrong native field strike profile")
            val strike=ModEntities.EAGLE.get().create(h.level)!!;strike.configure(origin(h),Vec3(1.0,0.0,0.0),attack,null)
            val before=h.level.getEntities(null,AABB(origin(h),origin(h)).inflate(2000.0)).map{it.uuid}.toSet()
            val tag=CompoundTag();strike.saveWithoutId(tag);tag.putLong("StartedAt",h.level.gameTime-(attack.releaseAge(count-1).toLong()+1));strike.load(tag);h.level.addFreshEntity(strike);strike.tick()
            h.assertTrue(strike.fired==count,"Field strike withdrew before releasing all payloads")
            val shots=h.level.getEntities(null,AABB(origin(h),origin(h)).inflate(2000.0)).filterIsInstance<EagleFieldBombEntity>().filter{it.uuid !in before}.sortedBy{it.id}
            h.assertTrue(shots.size==count,"Carrier count differs from actual canisters: ${shots.size}/$count")
            if(attack==EagleAttack.GAS) {
                val expected=listOf(Vec3(-12.0,0.0,-4.0),Vec3.ZERO,Vec3(12.0,0.0,-4.0),Vec3.ZERO)
                for((i,shot) in shots.withIndex()) {
                    val t=EagleFlight.BOMB_FLIGHT.toDouble()
                    val landing=shot.position().add(shot.deltaMovement.scale(t)).add(0.0,-EagleFlight.BOMB_GRAVITY*t*(t-1)/2,0.0)
                    h.assertTrue(landing.distanceTo(strike.target.add(expected[i]))<.01,"Gas canister $i misses native landing point: $landing")
                    val turned=attack.landingOffset(i,Vec3(0.0,0.0,1.0))
                    h.assertTrue(turned.distanceTo(Vec3(-expected[i].z,0.0,expected[i].x))<.001,"Gas pattern fails to rotate with flight heading")
                }
            }
            shots.forEach{it.discard()};strike.discard()
        }
        h.level.getEntities(null,AABB(origin(h),origin(h)).inflate(2000.0)).filter{it is EagleFieldBombEntity}.forEach{it.discard()}
        h.succeed()
    }
}
