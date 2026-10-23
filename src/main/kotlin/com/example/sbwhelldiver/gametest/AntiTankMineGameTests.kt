package com.example.sbwhelldiver.gametest
import com.example.sbwhelldiver.asset.AssetCatalog
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.projectile.Arrow
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.*
import kotlin.math.abs
@GameTestHolder("sbw_helldiver_antitank")
@PrefixGameTestTemplate(false)
object AntiTankMineGameTests {
    private fun cleanup(h:GameTestHelper,p:Vec3) {
        h.level.getEntities(null,AABB(p,p).inflate(25.0)) { it is MineDispenserEntity || it is AntiPersonnelMineEntity || it is net.minecraft.world.entity.animal.Cow || it is ItemEntity || it is Arrow || it is GroundFireEntity || it is MortarFieldEntity }.forEach { it.discard() }
    }
    private fun point(h:GameTestHelper):Vec3 {
        val b=h.absolutePos(BlockPos(15,260,15));val p=Vec3(b.x+.5,b.y.toDouble(),b.z+.5)
        for(x in ((b.x-24) shr 4)..((b.x+24) shr 4))for(z in ((b.z-24) shr 4)..((b.z+24) shr 4)) {h.level.setChunkForced(x,z,true);h.level.getChunk(x,z)}
        cleanup(h,p)
        for(x in -23..23)for(z in -23..23)h.level.setBlock(BlockPos.containing(p.add(x.toDouble(),-1.0,z.toDouble())),Blocks.STONE.defaultBlockState(),2)
        return p
    }
    private fun cow(h:GameTestHelper,p:Vec3,max:Double=500.0)=EntityType.COW.create(h.level)!!.also {
        it.isNoAi=true;it.isNoGravity=true;it.setPos(p);it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=max;it.health=max.toFloat();h.level.addFreshEntity(it)
    }
    private fun mine(h:GameTestHelper,p:Vec3,armed:Boolean=true):AntiPersonnelMineEntity {
        val m=ModEntities.ANTI_TANK_MINE.get().create(h.level)!!;m.setPos(p)
        if(armed){val t=CompoundTag();m.saveWithoutId(t);t.putInt("MineAge",40);t.putBoolean("MineArmed",true);t.putBoolean("MineSettled",true);m.load(t)}
        h.level.addFreshEntity(m);return m
    }
    private fun dispenser(h:GameTestHelper,p:Vec3):MineDispenserEntity {
        val d=ModEntities.ANTI_TANK_MINEFIELD.get().create(h.level)!!;d.setPos(p);val t=CompoundTag();d.saveWithoutId(t)
        t.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal);t.putInt("HellpodActiveTicks",0);t.putInt("MineSpreadAge",0);t.putDouble("HellpodRestY",p.y);d.load(t);h.level.addFreshEntity(d);return d
    }
    private fun payload(h:GameTestHelper,d:MineDispenserEntity)=h.level.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(d.position(),d.position()).inflate(24.0)){it.dispenserId==d.uuid}
    @JvmStatic @GameTest(template="sentry_test",batch="at_pressure",timeoutTicks=10)
    fun thresholdUsesMaximumHealthNotCurrentHealth(h:GameTestHelper) {
        val p=point(h);val m=mine(h,p);val light=cow(h,p,99.0);m.tick()
        h.assertTrue(!m.isRemoved,"99 maximum HP triggered pressure switch");light.discard()
        val item=ItemEntity(h.level,p.x,p.y,p.z,ItemStack(Items.STONE));h.level.addFreshEntity(item);m.tick()
        h.assertTrue(!m.isRemoved,"Non-living item triggered pressure switch");item.discard()
        val heavy=cow(h,p,100.0);heavy.health=1f;m.tick()
        h.assertTrue(m.isRemoved,"Maximum HP=100 with current HP=1 did not trigger pressure switch");cleanup(h,p);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_vehicle",timeoutTicks=10)
    fun vehiclePressureUsesItsOwnMaximumHealth(h:GameTestHelper) {
        val p=point(h);val m=mine(h,p)
        val light=ModEntities.ANTI_PERSONNEL_MINEFIELD.get().create(h.level)!!;light.setPos(p);light.noPhysics=false;h.level.addFreshEntity(light);m.tick()
        h.assertTrue(!m.isRemoved&&light.getMaxHealth()==40f,"40-HP dispenser triggered anti-tank mine");light.discard()
        val heavy=ModEntities.TESLA_TOWER.get().create(h.level)!!;heavy.setPos(p);heavy.health=1f;heavy.noPhysics=false;h.level.addFreshEntity(heavy);m.tick()
        h.assertTrue(heavy.getMaxHealth()==150f&&m.isRemoved,"High-max-HP vehicle with low current health did not trigger");heavy.discard();cleanup(h,p);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_unarmed",timeoutTicks=10)
    fun heavyContactWaitsUntilArmed(h:GameTestHelper) {
        val p=point(h);val m=mine(h,p,false);cow(h,p,100.0);m.tick()
        h.assertTrue(!m.isRemoved&&!m.armed,"Unarmed anti-tank mine detonated on pressure");cleanup(h,p);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_attack",timeoutTicks=10)
    fun lowHealthAttackerAndActualArrowStillDetonate(h:GameTestHelper) {
        val p=point(h);val attacker=cow(h,p.add(9.0,0.0,0.0),20.0);val first=mine(h,p,false)
        h.assertTrue(first.hurt(h.level.damageSources().mobAttack(attacker),1f)&&first.isRemoved,"Low-HP melee attacker was rejected")
        val second=mine(h,p,false);val arrow=Arrow(h.level,p.x-1,p.y+.09,p.z);arrow.shoot(1.0,0.0,0.0,1f,0f);h.level.addFreshEntity(arrow);arrow.tick();arrow.tick()
        h.assertTrue(second.isRemoved,"Actual arrow did not detonate unarmed anti-tank mine");cleanup(h,p);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_explosion",timeoutTicks=15)
    fun externalBlastIgnoresPressureThreshold(h:GameTestHelper) {
        val p=point(h);val attacker=cow(h,p.add(9.0,0.0,0.0),20.0);val m=mine(h,p,false)
        h.assertTrue(m.hurt(h.level.damageSources().explosion(attacker,attacker),1f)&&!m.isRemoved,"External blast must schedule detonation independently of HP")
        h.runAfterDelay(3){h.assertTrue(m.isRemoved,"Scheduled external blast did not detonate");cleanup(h,p);h.succeed()}
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_damage",timeoutTicks=10)
    fun innerThreeMetresFullDamageAndSevenMetreCutoff(h:GameTestHelper) {
        val p=point(h);val m=mine(h,p);val inner=cow(h,p.add(2.9,0.0,0.0));val edge=cow(h,p.add(5.0,0.0,0.0));val outside=cow(h,p.add(7.6,0.0,0.0));m.detonate()
        h.assertTrue(abs(inner.health-180f)<.01,"Inner 3-m damage is not 320: ${inner.health}")
        h.assertTrue(edge.health in 180.1f..499.9f&&outside.health==500f,"Outer falloff or 7-m cutoff is incorrect: edge=${edge.health} outside=${outside.health} at=$p")
        h.assertTrue(abs(SentryExplosion.damageAt(3.0,320f,3.0,7.0)-320f)<.01 && SentryExplosion.damageAt(7.0,320f,3.0,7.0)==0f,"Exact blast boundaries changed")
        cleanup(h,p);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_fields",timeoutTicks=10)
    fun environmentalFieldsDoNotTrigger(h:GameTestHelper) {
        val p=point(h);val m=mine(h,p);val fire=GroundFireEntity.create(h.level,p,null,4.0);val gas=MortarFieldEntity.create(h.level,p,MortarFieldEntity.Kind.GAS);val ems=MortarFieldEntity.create(h.level,p,MortarFieldEntity.Kind.EMS)
        fire.tick();gas.tick();ems.tick();m.tick()
        h.assertTrue(!m.isRemoved&&!m.hurt(h.level.damageSources().onFire(),20f),"Ground fire, gas or EMS detonated anti-tank mine");cleanup(h,p);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_count",timeoutTicks=10)
    fun exactlyEighteenAndDestroyedLauncherStops(h:GameTestHelper) {
        val p=point(h);val d=dispenser(h,p)
        h.assertTrue(d.minesPerPanel==3&&d.mineCount==18&&d.health==40f&&AssetCatalog.profile(d.type).totalAmmo==18,"Wrong dispenser configuration")
        repeat(30){d.tick()};val mines=payload(h,d)
        h.assertTrue(mines.size==18&&d.releasedWaves==3&&mines.all{it.kind==AntiPersonnelMineEntity.Kind.ANTI_TANK},"Wrong mine count/type");cleanup(h,p)
        val mid=dispenser(h,p);mid.tick();mid.hurt(h.level.damageSources().generic(),10000f);repeat(40){mid.tick()}
        h.assertTrue(payload(h,mid).size==6&&mid.phase==AbstractHellpodEntity.Phase.SPENT,"Destroyed launcher continued releasing");cleanup(h,p);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_save",timeoutTicks=10)
    fun restoreDoesNotRepeatOrLoseWaves(h:GameTestHelper) {
        val p=point(h);val d=dispenser(h,p);d.tick();val tag=CompoundTag();d.saveWithoutId(tag);d.discard()
        val loaded=ModEntities.ANTI_TANK_MINEFIELD.get().create(h.level)!!;loaded.load(tag);h.level.addFreshEntity(loaded);repeat(30){loaded.tick()}
        h.assertTrue(loaded.releasedWaves==3&&payload(h,loaded).size==18,"Save/reload repeated or lost a release wave");cleanup(h,p);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="at_landing",timeoutTicks=180)
    fun eighteenSettleWithoutTriggeringEachOther(h:GameTestHelper) {
        val p=point(h);val d=dispenser(h,p)
        h.runAfterDelay(135){val mines=payload(h,d);h.assertTrue(mines.size==18&&mines.all{it.settled&&it.armed},"Anti-tank field detonated during scatter or failed to settle: ${mines.size}");cleanup(h,p);h.succeed()}
    }
}
