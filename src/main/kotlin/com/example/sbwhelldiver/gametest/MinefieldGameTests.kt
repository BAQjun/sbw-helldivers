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
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.*
import kotlin.math.abs

@GameTestHolder("sbw_helldiver_minefield")
@PrefixGameTestTemplate(false)
object MinefieldGameTests {
    private fun origin(h: GameTestHelper): Vec3 {
        val p=h.absolutePos(BlockPos(15,260,15))
        for(x in ((p.x-10) shr 4)..((p.x+25) shr 4))for(z in ((p.z-10) shr 4)..((p.z+25) shr 4)) {
            h.level.setChunkForced(x,z,true);h.level.getChunk(x,z)
        }
        val at=Vec3(p.x+.5,p.y.toDouble(),p.z+.5)
        // Failed test fixtures above the template are outside its normal cleanup volume.
        h.level.getEntities(null,AABB(at,at).inflate(30.0)) { it is MineDispenserEntity || it is AntiPersonnelMineEntity || it is HellpodCapEntity }.forEach { it.discard() }
        return at
    }
    private fun mine(h: GameTestHelper,p: Vec3,armed: Boolean=true): AntiPersonnelMineEntity {
        val m=ModEntities.ANTI_PERSONNEL_MINE.get().create(h.level)!!;m.setPos(p)
        if(armed) {
            val tag=CompoundTag();m.saveWithoutId(tag);tag.putInt("MineAge",40);tag.putBoolean("MineArmed",true);tag.putBoolean("MineSettled",true);m.load(tag)
        }
        h.level.addFreshEntity(m);return m
    }
    private fun dispenser(h: GameTestHelper,p: Vec3): MineDispenserEntity {
        val d=ModEntities.ANTI_PERSONNEL_MINEFIELD.get().create(h.level)!!;d.setPos(p)
        val tag=CompoundTag();d.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal);tag.putInt("HellpodActiveTicks",0);tag.putInt("MineSpreadAge",0);tag.putDouble("HellpodRestY",p.y);d.load(tag)
        h.level.addFreshEntity(d);return d
    }
    @JvmStatic @GameTest(template="sentry_test",batch="mine_clock",timeoutTicks=10)
    fun nativeParametersAndCompleteUniformSockets(h: GameTestHelper) {
        h.assertTrue(MinefieldClock.COUNT==48 && MinefieldClock.releasedWaves(-1)==0 && MinefieldClock.releasedWaves(0)==1 && MinefieldClock.releasedWaves(29)==8,"Native mine count or throw duration changed")
        h.assertTrue(MinefieldClock.speedAt(0.0)==120.0 && MinefieldClock.speedAt(45.0)==60.0 && MinefieldClock.speedAt(60.0)==0.0 && MinefieldClock.angleAt(60.0)==5400.0,"400 rpm and 1.5-second spindown mismatch")
        h.assertTrue(MineDispenserEntity.launchDirections.size==48 && MineDispenserEntity.launchDirections.all { abs(it.length()-1)<.001 && it.y>.6 && it.y<.8 } && MineDispenserEntity.slots.size==48 && MineDispenserEntity.slots.all { it.length()>0 && it.y>0 },"Original mounted slots were lost")
        val visible=ModEntities.ANTI_PERSONNEL_MINE.get().create(h.level)!!
        h.assertTrue(visible.shouldRenderAtSqrDistance(80.0*80.0) && visible.shouldRenderAtSqrDistance(127.0*127.0) && !visible.shouldRenderAtSqrDistance(129.0*129.0),"Mine rendering does not cover 128 m")
        h.assertTrue(visible.bbWidth==.28f && visible.bbHeight==.18f,"Display fix changed mine collision dimensions")
        val d=ModEntities.ANTI_PERSONNEL_MINEFIELD.get().create(h.level)!!
        h.assertTrue(d.health==40f && d.getMaxHealth()==40f && AssetCatalog.profile(d.type).deployTicks==49,"Dispenser health/deployment is wrong")
        val tag=CompoundTag();d.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal);tag.putInt("MineSpreadAge",0);d.load(tag)
        h.assertTrue(!d.spinning,"Motor sound gate opened before any rotation")
        tag.putInt("MineSpreadAge",1);d.load(tag);h.assertTrue(d.spinning,"Rotating motor sound gate never opened")
        val audio=MineDispenserEntity::class.java.classLoader.getResourceAsStream("assets/sbw_helldiver/helldivers/audio.json")!!.bufferedReader().use { com.google.gson.JsonParser.parseReader(it).asJsonObject }
        val model=audio["models"].asJsonObject["ap_mine_hellpod_v1"].asJsonObject
        val deploying=model["animation.hellpod.deploy"].asJsonObject["events"].asJsonArray.map { it.asJsonObject }
        h.assertTrue(deploying.count { it["effect"].asString.startsWith("sfx_ap_mine_deploy_") }==3 && deploying.none { it["effect"].asString.startsWith("sfx_ap_mine_spin_") },"Opening sounds are missing or rotation sound begins during opening")
        val leafCue=deploying.single { it["effect"].asString=="sfx_ap_mine_deploy_3" }
        h.assertTrue(kotlin.math.abs(leafCue["time"].asDouble-1.4666666675)<.001,"Leaf-opening cue is detached from the original deployment marker")
        h.assertTrue(model["animation.minefield.spin"].asJsonObject["events"].asJsonArray.all { audio["effects"].asJsonObject[it.asJsonObject["effect"].asString].asJsonObject["requires_mine_rotation"].asBoolean },"A payload mechanical layer bypasses the actual-rotation gate")
        d.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="mine_spread",timeoutTicks=10)
    fun releaseFortyEightAndStopOnDestruction(h: GameTestHelper) {
        val p=origin(h);val d=dispenser(h,p)
        repeat(30) { d.tick() }
        val released=h.level.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(p,p).inflate(20.0)) { it.dispenserId==d.uuid }
        h.assertTrue(released.size==48 && d.releasedWaves==8,"Launcher did not release exactly 48 mines")
        h.assertTrue(released.all { it.deltaMovement.length() in .2499.. .6251 },"Launch speed outside native 5-12.5 m/s")
        h.assertTrue(released.map { Math.toDegrees(kotlin.math.atan2(it.deltaMovement.x,it.deltaMovement.z)).toInt()/30 }.toSet().size>=9,"Mines collapsed into a few fixed directions")
        released.forEach { it.discard() };d.discard()
        val mid=dispenser(h,p);mid.tick()
        val first=h.level.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(p,p).inflate(20.0)) { it.dispenserId==mid.uuid }
        h.assertTrue(first.size==6,"First wave is not six mines")
        mid.hurt(h.level.damageSources().generic(),10000f);repeat(40) { mid.tick() }
        val after=h.level.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(p,p).inflate(20.0)) { it.dispenserId==mid.uuid }
        h.assertTrue(mid.phase==AbstractHellpodEntity.Phase.SPENT && after.size==6,"Destroyed dispenser continued launching or removed released mines")
        after.forEach { it.discard() };mid.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="mine_damage",timeoutTicks=10)
    fun innerDamageOuterFalloffAndDeferredChain(h: GameTestHelper) {
        val p=origin(h);val m=mine(h,p);val next=mine(h,p.add(2.5,0.0,0.0),false);val weak=mine(h,p.add(0.0,0.0,4.5),false);val outside=mine(h,p.add(0.0,0.0,6.0),false)
        val victim=EntityType.COW.create(h.level)!!;victim.isNoAi=true;victim.isNoGravity=true;victim.setPos(p.add(.4,0.0,0.0));victim.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=500.0;victim.health=500f;h.level.addFreshEntity(victim)
        h.assertTrue(AntiPersonnelMineEntity.OUTER_RADIUS==5.0 && SentryExplosion.damageAt(1.2,100f,1.2,5.0)==100f && abs(SentryExplosion.damageAt(3.1,100f,1.2,5.0)-49.5)<.01 && SentryExplosion.damageAt(5.1,100f,1.2,5.0)==0f,"Explosion radii/falloff incorrect")
        m.hurt(h.level.damageSources().generic(),1f)
        h.assertTrue(m.isRemoved && !next.isRemoved && !weak.isRemoved && !outside.isRemoved && abs(victim.health-400f)<.01,"Initial blast was not immediate or chained recursively in the same tick")
        val saved=CompoundTag();next.saveWithoutId(saved)
        h.assertTrue(saved.getLong("MineChainDue")==h.level.gameTime+1,"Pending external explosion was not persisted")
        h.runAfterDelay(2) {
            h.assertTrue(next.isRemoved && !weak.isRemoved && !outside.isRemoved && victim.health<400f,"Next-tick chain blast or 5-metre range was incorrect")
            outside.discard();weak.discard();victim.discard();h.succeed()
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="mine_trigger",timeoutTicks=10)
    fun itemStepFieldsExcludedAndFlameJetIncluded(h: GameTestHelper) {
        val p=origin(h);h.level.setBlock(BlockPos.containing(p.add(0.0,-1.0,0.0)),Blocks.STONE.defaultBlockState(),2)
        val m=mine(h,p);MortarFieldEntity.create(h.level,p,MortarFieldEntity.Kind.GAS);MortarFieldEntity.create(h.level,p,MortarFieldEntity.Kind.EMS)
        val flame=ModEntities.FLAME_SENTRY.get().create(h.level)!!;flame.setPos(p.add(-12.0,0.0,0.0));GroundFireEntity.create(h.level,p,flame)
        m.tick();h.assertTrue(!m.isRemoved && !m.hurt(h.level.damageSources().onFire(),10f),"A gas/EMS/ground-fire area triggered the mine")
        val item=ItemEntity(h.level,p.x,p.y+.02,p.z,ItemStack(Items.STICK));h.level.addFreshEntity(item);m.tick()
        h.assertTrue(m.isRemoved,"Nonliving item contact did not trigger the armed mine")
        val jetMine=mine(h,p.add(0.0,0.0,12.0),false)
        FlameJet.spray(h.level,flame,p.add(-4.0,.1,12.0),Vec3(1.0,0.0,0.0))
        h.assertTrue(jetMine.isRemoved,"Flame stream did not trigger an unarmed mine")
        h.level.getEntities(null,AABB(p,p).inflate(20.0)) { it is GroundFireEntity || it is MortarFieldEntity || it is ItemEntity }.forEach { it.discard() }
        flame.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="mine_ranged",timeoutTicks=10)
    fun arrowCollisionAndLaserRayTrigger(h: GameTestHelper) {
        val p=origin(h);val m=mine(h,p,false)
        val arrow=net.minecraft.world.entity.projectile.Arrow(h.level,p.x-1.0,p.y+.09,p.z)
        arrow.shoot(1.0,0.0,0.0,1f,0f);h.level.addFreshEntity(arrow);arrow.tick();arrow.tick()
        h.assertTrue(m.isRemoved,"Arrow passed through a shootable mine")
        val laser=ModEntities.LASER_SENTRY.get().create(h.level)!!;laser.setPos(p.add(0.0,0.0,15.0))
        val target=mine(h,p.add(0.0,0.0,12.0),false)
        val hit=LaserBeam.trace(laser,p.add(-2.0,.09,12.0),Vec3(1.0,0.0,0.0))
        h.assertTrue(hit.mine==target,"Laser ray did not intersect the mine")
        LaserBeam.fire(laser,hit);h.assertTrue(target.isRemoved,"Laser hit did not detonate mine")
        arrow.discard();laser.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="mine_landing",timeoutTicks=200)
    fun fullFlightAndLandingDoNotSelfDetonate(h: GameTestHelper) {
        val p=origin(h)
        for(x in -22..22)for(z in -22..22)h.level.setBlock(BlockPos.containing(p.add(x.toDouble(),-1.0,z.toDouble())),Blocks.STONE.defaultBlockState(),2)
        val d=dispenser(h,p)
        val released=mutableListOf<AntiPersonnelMineEntity>()
        repeat(120) {
            d.tick()
            h.level.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(p,p).inflate(30.0)) { it.dispenserId==d.uuid }.forEach { m ->
                if(m !in released)released.add(m)
            }
            released.filter { !it.isRemoved }.forEach { it.tick() }
        }
        h.assertTrue(released.size==48 && released.none { it.isRemoved },"Empty-field deployment self-detonated: spawned=${released.size}, remaining=${released.count { !it.isRemoved }}, launcher=${d.phase}")
        h.assertTrue(released.all { it.armed && it.settled },"Mines did not finish landing and arming")
        val distances=released.map { it.position().subtract(p).horizontalDistance() }
        com.example.sbwhelldiver.LOGGER.info("MINE_LANDING_RANGE min={} mean={} max={}",distances.min(),distances.average(),distances.max())
        h.assertTrue(distances.min()>6.3 && distances.average()>11.0 && distances.max()>16.0,"Minefield is still concentrated near the pod: $distances")
        val spacing=released.indices.minOf { i -> released.indices.filter { it!=i }.minOf { j -> released[i].position().distanceTo(released[j].position()) } }
        h.assertTrue(spacing>1.8,"Mines overlapped after landing: nearest distance=$spacing")
        com.example.sbwhelldiver.LOGGER.info("MINE_LANDING_SPACING nearest={}",spacing)
        released.forEach { it.discard() };d.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="mine_hardware",timeoutTicks=10)
    fun passiveMineAndOwnHardwareCannotTrigger(h: GameTestHelper) {
        val p=origin(h);h.level.setBlock(BlockPos.containing(p.add(0.0,-1.0,0.0)),Blocks.STONE.defaultBlockState(),2)
        val d=dispenser(h,p);val a=mine(h,p);val b=mine(h,p)
        a.launch(p,Vec3.ZERO,null,d.uuid);b.launch(p,Vec3.ZERO,null,d.uuid)
        val cap=ModEntities.HELLPOD_CAP.get().create(h.level)!!;cap.ejectFrom(d);cap.setPos(p);h.level.addFreshEntity(cap)
        repeat(3) { a.tick();b.tick() }
        h.assertTrue(!a.isRemoved && !b.isRemoved && !a.canCollideWith(b) && !a.canCollideWith(d) && !a.canCollideWith(cap),"Passive contact with mine or own payload hardware triggered a mine")
        h.assertTrue(!a.hurt(h.level.damageSources().thrown(b,null),1f),"A mine's passive collision was treated as an attack")
        val attacker=EntityType.COW.create(h.level)!!
        h.assertTrue(a.hurt(h.level.damageSources().mobAttack(attacker),1f) && a.isRemoved && !b.isRemoved,"Real attack was suppressed or chained before the next tick")
        h.runAfterDelay(2) {
            h.assertTrue(b.isRemoved,"A real neighboring explosion did not trigger the other mine")
            cap.discard();d.discard();attacker.discard();h.succeed()
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="mine_live",timeoutTicks=240)
    fun completePodDeploymentSurvivesLiveWorldTicks(h: GameTestHelper) {
        val p=origin(h)
        for(x in -22..22)for(z in -22..22)h.level.setBlock(BlockPos.containing(p.add(x.toDouble(),-1.0,z.toDouble())),Blocks.STONE.defaultBlockState(),2)
        val d=ModEntities.ANTI_PERSONNEL_MINEFIELD.get().create(h.level)!!;d.setPos(p)
        val tag=CompoundTag();d.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.DEPLOYING.ordinal)
        tag.putDouble("HellpodRestY",p.y);tag.putBoolean("HellpodCapReleased",false);d.load(tag);h.level.addFreshEntity(d)
        h.runAfterDelay(180) {
            val mines=h.level.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(p,p).inflate(30.0)) { it.dispenserId==d.uuid }
            h.assertTrue(d.releasedWaves==8 && mines.size==48 && mines.all { it.armed && it.settled },"Live deployment lost mines: count=${mines.size}, waves=${d.releasedWaves}, phase=${d.phase}")
            h.assertTrue(h.level.getEntitiesOfClass(HellpodCapEntity::class.java,AABB(p,p).inflate(30.0)) { it.sourcePodUUID==d.uuid }.isNotEmpty(),"Test did not include the actual ejected cap")
            mines.forEach { it.discard() };h.level.getEntitiesOfClass(HellpodCapEntity::class.java,AABB(p,p).inflate(30.0)) { it.sourcePodUUID==d.uuid }.forEach { it.discard() };d.discard();h.succeed()
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="mine_all_origins",timeoutTicks=650)
    fun everyMineInCompleteFieldCanDetonateWithoutClearingTheField(h: GameTestHelper) {
        val p=origin(h)
        for(x in -23..23)for(z in -23..23)h.level.setBlock(BlockPos.containing(p.add(x.toDouble(),-1.0,z.toDouble())),Blocks.STONE.defaultBlockState(),2)
        val d=dispenser(h,p)
        val released=mutableListOf<AntiPersonnelMineEntity>()
        repeat(120) {
            d.tick()
            h.level.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(p,p).inflate(30.0)) { it.dispenserId==d.uuid }.forEach { if(it !in released)released.add(it) }
            released.filter { !it.isRemoved }.forEach { it.tick() }
        }
        h.assertTrue(released.size==48 && released.all { !it.isRemoved && it.armed && it.settled },"The reference field did not deploy intact")
        val positions=released.map { it.position() };released.forEach { it.discard() };d.discard()
        val detonated=mutableListOf<Int>()
        fun trial(index: Int) {
            val field=positions.map { mine(h,it) }
            field[index].hurt(h.level.damageSources().generic(),1f)
            h.runAfterDelay(10) {
                val dead=field.count { it.isRemoved };detonated.add(dead)
                h.assertTrue(dead in 1..12,"One mine cleared too much of the normal field: origin=$index detonated=$dead/48")
                h.assertTrue(field.filter { !it.isRemoved }.all { val t=CompoundTag();it.saveWithoutId(t);t.getLong("MineChainDue")<0 },"A pending chain would continue after the result was counted")
                field.forEach { it.discard() }
                if(index+1<positions.size)trial(index+1) else {
                    com.example.sbwhelldiver.LOGGER.info("MINE_ALL_ORIGINS_PASSED origins=48 detonatedMin={} detonatedMax={} survivorsMin={}",detonated.min(),detonated.max(),48-detonated.max())
                    h.succeed()
                }
            }
        }
        trial(0)
    }
    @JvmStatic @GameTest(template="sentry_test",batch="mine_save",timeoutTicks=10)
    fun savingCannotDuplicateReleasedMines(h: GameTestHelper) {
        val p=origin(h);val d=dispenser(h,p);repeat(9) { d.tick() }
        val tag=CompoundTag();d.saveWithoutId(tag);val wave=d.releasedWaves;val originalId=d.uuid;d.discard()
        // Let normal entity removal finish before reloading the same persistent UUID.
        h.runAfterDelay(1) {
            val loaded=ModEntities.ANTI_PERSONNEL_MINEFIELD.get().create(h.level)!!;loaded.load(tag)
            h.assertTrue(h.level.addFreshEntity(loaded),"Saved dispenser UUID was not available after removal")
            repeat(30) { loaded.tick() }
            val all=h.level.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(p,p).inflate(20.0)) { it.dispenserId==originalId }
            h.assertTrue(wave>0 && loaded.releasedWaves==8 && all.size==48,"Reload wave=$wave loaded=${loaded.releasedWaves} phase=${loaded.phase} count=${all.size} expected=48")
            all.forEach { it.discard() };loaded.discard();h.succeed()
        }
    }
}
