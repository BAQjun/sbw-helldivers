package com.example.sbwhelldiver.gametest
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.asset.AssetCatalog
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import com.atsuishio.superbwarfare.world.phys.EntityResult
import com.atsuishio.superbwarfare.world.phys.ExtendedEntityRayTraceResult
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.effect.*
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.phys.*
import net.minecraftforge.gametest.*
import kotlin.math.abs

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
object StatusMortarGameTests {
    private fun origin(h: GameTestHelper): Vec3 {
        val p=h.absolutePos(BlockPos(15,200,15));return Vec3(p.x+.5,p.y.toDouble(),p.z+.5)
    }
    private fun mob(h: GameTestHelper,p: Vec3,health: Float=200f)=EntityType.ZOMBIE.create(h.level)!!.also {
        it.isNoAi=true;it.isNoGravity=true;it.setPos(p)
        it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=health.toDouble();it.health=health
        it.getAttribute(Attributes.ARMOR)!!.baseValue=0.0
        h.level.addFreshEntity(it)
    }
    private fun player(h: GameTestHelper,p: Vec3)=object : net.minecraft.world.entity.player.Player(
        h.level,BlockPos.ZERO,0f,com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"StatusTest")) {
        override fun isSpectator()=false
        override fun isCreative()=false
    }.also { it.isNoGravity=true;it.setPos(p);h.level.addFreshEntity(it) }

    @JvmStatic @GameTest(template="sentry_test",timeoutTicks=10)
    fun singleShotRatesAmmoAndFiveBlockScatter(h: GameTestHelper) {
        for ((id,rpm) in listOf("gas_mortar_sentry" to 15,"ems_mortar_sentry" to 10)) {
            val p=AssetCatalog.profiles.getValue(id)
            h.assertTrue(p.isMortar && p.burstSize==1 && p.totalAmmo==23 && p.mortarScatterRadius==5.0,"Wrong special mortar profile")
            val c=BurstClock();val ticks=mutableListOf<Int>()
            repeat(3600) { if(c.advance(1,p.burstInterval,p.burstPause,it.toLong())>0)ticks.add(it) }
            h.assertTrue(ticks.size==rpm*3,"Wrong average RPM: "+id)
            h.assertTrue(ticks.zipWithNext().all { it.second-it.first in if(rpm==15)80..80 else 120..120 },"Single mortar became a burst")
            val r=java.util.Random(18)
            repeat(1000) { h.assertTrue(MortarBallistics.scatterPoint(Vec3.ZERO,r.nextDouble(),r.nextDouble(),p.mortarScatterRadius).horizontalDistance()<=5.0,"Scatter exceeded five blocks") }
        }
        h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",timeoutTicks=10)
    fun gasDirectHasNoBlastAndOnlyOneField(h: GameTestHelper) {
        val p=origin(h);val hit=mob(h,p);val nearby=mob(h,p.add(1.0,0.0,0.0))
        val shell=ModEntities.GAS_MORTAR_SHELL.get().create(h.level)!!
        shell.setPos(p);shell.tickCount=1;h.level.addFreshEntity(shell)
        shell.onHitEntity(ExtendedEntityRayTraceResult(EntityResult(hit,p.add(0.0,.7,0.0),false,false)))
        h.assertTrue(hit.health==198f && nearby.health==200f,"Gas shell caused blast damage or lost direct 2 damage")
        shell.causeExplode(p)
        val fields=h.level.getEntitiesOfClass(MortarFieldEntity::class.java,AABB(p,p).inflate(2.0))
        h.assertTrue(fields.size==1 && fields[0].kind==MortarFieldEntity.Kind.GAS && fields[0].radius==7.0 && fields[0].remainingTicks==300,"Gas impact area duplicated or incorrect")
        fields.forEach { it.discard() };hit.discard();nearby.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",timeoutTicks=10)
    fun emsDirectAndDualRadiusBlastAndSingleField(h: GameTestHelper) {
        val p=origin(h)
        val hit=mob(h,p);val inner=mob(h,p.add(1.0+hit.bbWidth/2,0.0,0.0))
        val half=mob(h,p.add(0.0,0.0,1.5+hit.bbWidth/2))
        val beyond=mob(h,p.add(2.1+hit.bbWidth/2,0.0,0.0))
        val shell=ModEntities.EMS_MORTAR_SHELL.get().create(h.level)!!;shell.setPos(p);shell.tickCount=1
        h.level.addFreshEntity(shell)
        shell.onHitEntity(ExtendedEntityRayTraceResult(EntityResult(hit,p.add(0.0,.7,0.0),false,false)))
        h.assertTrue(abs(hit.health-183f)<.01 && abs(inner.health-185f)<.01 && abs(half.health-193f)<.01 && beyond.health==200f,
            "EMS damage incorrect: "+listOf(hit.health,inner.health,half.health,beyond.health))
        shell.causeExplode(p)
        val fields=h.level.getEntitiesOfClass(MortarFieldEntity::class.java,AABB(p,p).inflate(2.0))
        h.assertTrue(fields.size==1 && fields[0].kind==MortarFieldEntity.Kind.EMS && fields[0].radius==10.0 && fields[0].remainingTicks==140,"EMS field duplicated or wrong")
        fields.forEach { it.discard() };listOf(hit,inner,half,beyond).forEach { it.discard() };h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",timeoutTicks=10)
    fun fieldBoundariesDurationsAndLifetimePersistence(h: GameTestHelper) {
        val p=origin(h)
        val inside=mob(h,p.add(6.99,0.0,0.0));val outside=mob(h,p.add(7.01,0.0,0.0))
        val user=player(h,p.add(0.0,0.0,2.0))
        val gas=MortarFieldEntity.create(h.level,p,MortarFieldEntity.Kind.GAS);gas.tick()
        h.assertTrue(inside.getEffect(ModEffects.GAS.get())?.duration==100 && inside.getEffect(ModEffects.CONFUSION.get())?.duration==100,"Gas did not refresh five-second effects")
        h.assertTrue(!outside.hasEffect(ModEffects.GAS.get()) && user.hasEffect(ModEffects.GAS.get()) && !user.hasEffect(ModEffects.CONFUSION.get()),"Gas boundary or player immunity incorrect")
        inside.setPos(p.add(20.0,0.0,0.0));inside.removeEffect(ModEffects.GAS.get());inside.removeEffect(ModEffects.CONFUSION.get())
        gas.tick();h.assertTrue(!inside.hasEffect(ModEffects.GAS.get()),"Leaving area still refreshed gas")
        val saved=CompoundTag();gas.saveWithoutId(saved);val restored=ModEntities.MORTAR_FIELD.get().create(h.level)!!;restored.load(saved)
        h.assertTrue(restored.remainingTicks==298 && restored.kind==MortarFieldEntity.Kind.GAS,"Reload reset field lifetime")
        repeat(298) { gas.tick() };gas.tick();h.assertTrue(gas.isRemoved,"Gas outlived fifteen seconds")
        val ems=MortarFieldEntity.create(h.level,p,MortarFieldEntity.Kind.EMS)
        outside.setPos(p.add(9.99,0.0,0.0));ems.tick()
        h.assertTrue(outside.getEffect(ModEffects.STUN.get())?.duration==60 && user.getEffect(ModEffects.STUN.get())?.duration==60,"EMS did not refresh three-second stun")
        repeat(139) { ems.tick() };ems.tick();h.assertTrue(ems.isRemoved,"EMS outlived seven seconds")
        listOf(inside,outside,user,restored).forEach { it.discard() };h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",timeoutTicks=10)
    fun poisonCadenceSlowAndLethality(h: GameTestHelper) {
        val v=mob(h,origin(h),12f)
        v.getAttribute(Attributes.ARMOR)!!.baseValue=30.0
        v.addEffect(MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,1000,4))
        val original=v.getAttributeValue(Attributes.MOVEMENT_SPEED)
        v.addEffect(MobEffectInstance(ModEffects.GAS.get(),100))
        h.assertTrue(abs(v.getAttributeValue(Attributes.MOVEMENT_SPEED)-original*.75)<1e-6,"Gas slow is not 25 percent")
        val effect=ModEffects.GAS.get()
        repeat(19) { v.tickCount=it+1;effect.applyEffectTick(v,0) }
        h.assertTrue(v.health==12f,"Poison ran every tick")
        for(t in listOf(20,40,60)) { v.tickCount=t;effect.applyEffectTick(v,0) }
        h.assertTrue(v.health<=0f,"Gas must deal four HP per second through armor/resistance and kill")
        v.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",timeoutTicks=10)
    fun stunPlayerSlowMobFreezeAttackAndAiRestoration(h: GameTestHelper) {
        val p=origin(h);val z=mob(h,p).also { it.isNoAi=false }
        val target=mob(h,p.add(1.0,0.0,0.0));val user=player(h,p.add(3.0,0.0,0.0))
        val speed=user.getAttributeValue(Attributes.MOVEMENT_SPEED)
        z.target=target;z.addEffect(MobEffectInstance(ModEffects.STUN.get(),60))
        h.assertTrue(z.isNoAi && z.target==null,"Stun did not disable AI/target")
        z.deltaMovement=Vec3(1.0,.2,1.0)
        com.example.sbwhelldiver.effect.StatusEffectEvents.tick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(z))
        h.assertTrue(z.deltaMovement.horizontalDistanceSqr()==0.0,"Stunned mob moved")
        val hp=target.health;z.doHurtTarget(target);h.assertTrue(target.health==hp,"Stunned mob attacked")
        user.addEffect(MobEffectInstance(ModEffects.STUN.get(),60))
        h.assertTrue(abs(user.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed*.3)<1e-6,"Player slow is not 70 percent")
        target.hurt(h.level.damageSources().playerAttack(user),3f)
        h.assertTrue(target.health<hp,"Stun blocked player attacks")
        z.removeEffect(ModEffects.STUN.get());h.assertTrue(!z.isNoAi,"Stun permanently disabled AI")
        target.addEffect(MobEffectInstance(ModEffects.STUN.get(),60));target.removeEffect(ModEffects.STUN.get())
        h.assertTrue(target.isNoAi,"Stun overwrote preexisting NoAI")
        user.removeEffect(ModEffects.STUN.get());h.assertTrue(abs(user.getAttributeValue(Attributes.MOVEMENT_SPEED)-speed)<1e-6,"Player speed did not restore")
        listOf(z,target,user).forEach { it.discard() };h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",timeoutTicks=10)
    fun confusionSuppressesTargetsAndExemptsPlayers(h: GameTestHelper) {
        val p=origin(h);val z=mob(h,p).also { it.isNoAi=false };val other=mob(h,p.add(1.0,0.0,0.0));val user=player(h,p)
        z.addEffect(MobEffectInstance(ModEffects.CONFUSION.get(),100));z.target=other
        h.assertTrue(z.target==null,"Confusion retained normal targeting")
        user.addEffect(MobEffectInstance(ModEffects.CONFUSION.get(),100))
        h.assertTrue(!user.hasEffect(ModEffects.CONFUSION.get()),"Players received confusion")
        z.removeEffect(ModEffects.CONFUSION.get());z.target=other
        h.assertTrue(z.target===other,"Normal target acquisition did not restore")
        listOf(z,other,user).forEach { it.discard() };h.succeed()
    }

    @JvmStatic @GameTest(template="sentry_test",timeoutTicks=130)
    fun confusedMobWandersAndAttacksWithoutNormalTarget(h: GameTestHelper) {
        for(x in 8..23)for(z in 8..23)h.setBlock(BlockPos(x,199,z),net.minecraft.world.level.block.Blocks.BEDROCK)
        val p=origin(h);val z=mob(h,p).also { it.isNoAi=false;it.isNoGravity=false }
        val victim=mob(h,p.add(.8,0.0,0.0))
        z.addEffect(MobEffectInstance(ModEffects.CONFUSION.get(),100))
        var moved=false;var attacked=false
        h.onEachTick {
            z.clearFire()
            if(z.position().subtract(p).horizontalDistance()>.05)moved=true
            if(victim.health<200f)attacked=true
            if(z.hasEffect(ModEffects.CONFUSION.get()))h.assertTrue(z.target==null,"Confused mob regained normal target")
        }
        h.runAtTickTime(110) {
            h.assertTrue(moved && attacked,"Confused mob did not wander and randomly attack")
            h.assertTrue(!z.hasEffect(ModEffects.CONFUSION.get()),"Confusion tail did not expire")
            z.target=victim;h.assertTrue(z.target===victim,"Confusion blocked targeting after expiry")
            z.discard();victim.discard();h.succeed()
        }
    }
    @JvmStatic @GameTest(template="sentry_test",timeoutTicks=90)
    fun stunTailExpiresAndRestoresAiAfterLeavingField(h: GameTestHelper) {
        val p=origin(h);val z=mob(h,p).also { it.isNoAi=false }
        val field=MortarFieldEntity.create(h.level,p,MortarFieldEntity.Kind.EMS)
        field.tick();field.discard()
        h.assertTrue(z.isNoAi && z.hasEffect(ModEffects.STUN.get()),"EMS stun did not start")
        h.runAtTickTime(65) {
            h.assertTrue(!z.hasEffect(ModEffects.STUN.get()) && !z.isNoAi,"Three-second stun did not restore AI after expiry")
            z.discard();h.succeed()
        }
    }

    @JvmStatic @GameTest(template="sentry_test",timeoutTicks=10)
    fun gasExcludesPoisonedEnemiesButOtherMortarsDoNot(h: GameTestHelper) {
        val p=origin(h);val enemy=EntityType.HUSK.create(h.level)!!.also { it.isNoAi=true;it.isNoGravity=true;it.setPos(p.add(0.0,0.0,20.0));h.level.addFreshEntity(it) }
        val gas=ModEntities.GAS_MORTAR_SENTRY.get().create(h.level)!!.also { it.setPos(p) }
        val ems=ModEntities.EMS_MORTAR_SENTRY.get().create(h.level)!!.also { it.setPos(p) }
        val normal=ModEntities.MORTAR_SENTRY.get().create(h.level)!!.also { it.setPos(p) }
        h.assertTrue(gas.validTarget(enemy),"Gas mortar rejected an unpoisoned enemy")
        enemy.addEffect(MobEffectInstance(ModEffects.GAS.get(),100))
        h.assertTrue(!gas.validTarget(enemy),"Gas mortar reacquired an already poisoned enemy")
        h.assertTrue(ems.validTarget(enemy) && normal.validTarget(enemy),"Gas target exclusion leaked into other mortars")
        enemy.removeEffect(ModEffects.GAS.get())
        h.assertTrue(gas.validTarget(enemy),"Enemy remained excluded after poison expired")
        enemy.discard();h.succeed()
    }
}
