package com.example.sbwhelldiver.gametest
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.effect.FlameDamage
import com.example.sbwhelldiver.init.*
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import net.minecraftforge.gametest.*
import kotlin.math.abs
@GameTestHolder("sbw_helldiver_elemental")
@PrefixGameTestTemplate(false)
object ElementalMineGameTests {
    private fun point(h:GameTestHelper):Vec3 {
        val p=h.absolutePos(BlockPos(15,260,15))
        for(x in ((p.x-22) shr 4)..((p.x+22) shr 4))for(z in ((p.z-22) shr 4)..((p.z+22) shr 4)){h.level.setChunkForced(x,z,true);h.level.getChunk(x,z)}
        return Vec3(p.x+.5,p.y.toDouble(),p.z+.5)
    }
    private fun cow(h:GameTestHelper,p:Vec3)=EntityType.COW.create(h.level)!!.also {
        it.isNoAi=true;it.isNoGravity=true;it.setPos(p);it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=500.0;it.health=500f;h.level.addFreshEntity(it)
    }
    private fun cleanup(h:GameTestHelper,p:Vec3){h.level.getEntities(null,AABB(p,p).inflate(24.0)){it is AntiPersonnelMineEntity||it is GroundFireEntity||it is MortarFieldEntity||it is MineDispenserEntity||it is net.minecraft.world.entity.animal.Cow}.forEach{it.discard()}}
    @JvmStatic @GameTest(template="sentry_test",batch="elemental_fire",timeoutTicks=10)
    fun incendiaryBlastAndCustomBurn(h:GameTestHelper) {
        val p=point(h);for(x in -5..5)for(z in -5..5)h.level.setBlock(BlockPos.containing(p.add(x.toDouble(),-1.0,z.toDouble())),Blocks.STONE.defaultBlockState(),2);val m=ModEntities.INCENDIARY_MINE.get().create(h.level)!!;m.setPos(p);h.level.addFreshEntity(m)
        val inner=cow(h,p.add(.5,0.0,0.0));val edge=cow(h,p.add(3.5,0.0,0.0));val outside=cow(h,p.add(5.0,0.0,0.0))
        m.detonate();h.assertTrue(m.isRemoved&&abs(inner.health-450f)<.01&&edge.health in 450.1f..499.9f&&outside.health==500f,"Incendiary blast must peak at 50 with falloff and stop at 4 m")
        val field=h.level.getEntitiesOfClass(GroundFireEntity::class.java,AABB(p,p).inflate(5.0)).single();h.assertTrue(field.radius==4.0&&field.remainingTicks==GroundFireEntity.LIFETIME,"Wrong incendiary field dimensions/lifetime")
        field.tick();h.assertTrue(inner.hasEffect(ModEffects.BURNING.get())&&edge.hasEffect(ModEffects.BURNING.get())&&!outside.hasEffect(ModEffects.BURNING.get())&&!inner.hasEffect(ModEffects.PANIC.get()),"Ground fire must refresh 3-s burn inside 4 m without panic")
        val before=inner.health;inner.invulnerableTime=0;FlameDamage.burn(inner);h.assertTrue(abs(inner.health-(before-10))<.01&&inner.getEffect(ModEffects.BURNING.get())!!.duration==60,"Custom burning must deal 10 per half-second, not vanilla damage")
        cleanup(h,p);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="elemental_gas",timeoutTicks=10)
    fun gasHasNoBlastAndReusesMortarEffects(h:GameTestHelper) {
        val p=point(h);val m=ModEntities.GAS_MINE.get().create(h.level)!!;m.setPos(p);h.level.addFreshEntity(m)
        val inside=cow(h,p.add(5.8,0.0,0.0));val outside=cow(h,p.add(6.3,0.0,0.0));val close=cow(h,p.add(.5,0.0,0.0))
        m.detonate();h.assertTrue(listOf(inside,outside,close).all{it.health==500f},"Gas mine caused immediate explosion damage")
        val field=h.level.getEntitiesOfClass(MortarFieldEntity::class.java,AABB(p,p).inflate(7.0)).single();h.assertTrue(field.kind==MortarFieldEntity.Kind.GAS&&field.radius==6.0&&field.remainingTicks==300,"Wrong gas radius/lifetime")
        field.tick();h.assertTrue(inside.hasEffect(ModEffects.GAS.get())&&inside.hasEffect(ModEffects.CONFUSION.get())&&close.hasEffect(ModEffects.GAS.get())&&!outside.hasEffect(ModEffects.GAS.get()),"Gas mine did not share mortar poison/confusion or crossed 6-m boundary: inside=${inside.activeEffects} close=${close.activeEffects} outside=${outside.activeEffects} at=$p")
        val mortar=MortarFieldEntity.create(h.level,p.add(0.0,0.0,12.0),MortarFieldEntity.Kind.GAS);h.assertTrue(mortar.radius==7.0,"Mine radius changed the existing mortar radius")
        cleanup(h,p);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="elemental_save",timeoutTicks=10)
    fun savedFieldsKeepRadiusAndExpire(h:GameTestHelper) {
        val p=point(h);val fire=GroundFireEntity.create(h.level,p,null,4.0);val gas=MortarFieldEntity.create(h.level,p.add(12.0,0.0,0.0),MortarFieldEntity.Kind.GAS,6.0,true)
        repeat(120){fire.tick();gas.tick()};val ft=CompoundTag();val gt=CompoundTag();fire.saveWithoutId(ft);gas.saveWithoutId(gt)
        val f=ModEntities.GROUND_FIRE.get().create(h.level)!!;f.load(ft);val g=ModEntities.MORTAR_FIELD.get().create(h.level)!!;g.load(gt)
        h.assertTrue(f.radius==4.0&&g.radius==6.0&&g.mineCloud&&f.remainingTicks==GroundFireEntity.LIFETIME-120&&g.remainingTicks==180,"Field save lost radius or remaining lifespan")
        repeat(180){fire.tick();gas.tick()};h.assertTrue(fire.remainingTicks==0&&gas.remainingTicks==0,"Field did not expire at its own maximum lifetime")
        fire.tick();gas.tick();h.assertTrue(fire.isRemoved&&gas.isRemoved,"Expired areas remained active");f.discard();g.discard();cleanup(h,p);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="elemental_trigger",timeoutTicks=10)
    fun fieldsDoNotDetonateMinesButProjectilesDo(h:GameTestHelper) {
        val p=point(h);h.level.setBlock(BlockPos.containing(p.add(0.0,-1.0,0.0)),Blocks.STONE.defaultBlockState(),2)
        for((i,type) in listOf(ModEntities.INCENDIARY_MINE,ModEntities.GAS_MINE).withIndex()) {
            val at=p.add(i*8.0,0.0,0.0);h.level.setBlock(BlockPos.containing(at.add(0.0,-1.0,0.0)),Blocks.STONE.defaultBlockState(),2)
            val m=type.get().create(h.level)!!;m.setPos(at);val t=CompoundTag();m.saveWithoutId(t);t.putInt("MineAge",40);t.putBoolean("MineArmed",true);t.putBoolean("MineSettled",true);m.load(t);h.level.addFreshEntity(m)
            val f=GroundFireEntity.create(h.level,at,null,4.0);val g=MortarFieldEntity.create(h.level,at,MortarFieldEntity.Kind.GAS,6.0);val ems=MortarFieldEntity.create(h.level,at,MortarFieldEntity.Kind.EMS)
            f.tick();g.tick();ems.tick();m.tick();h.assertTrue(!m.isRemoved&&!m.hurt(h.level.damageSources().onFire(),20f),"Ground fire/gas/EMS triggered mine")
            val arrow=net.minecraft.world.entity.projectile.Arrow(h.level,at.x-1,at.y+.09,at.z);arrow.shoot(1.0,0.0,0.0,1f,0f);h.level.addFreshEntity(arrow);arrow.tick();arrow.tick();h.assertTrue(m.isRemoved,"Projectile did not detonate elemental mine");arrow.discard()
        };cleanup(h,p);h.succeed()
    }
    private fun dispenser(h:GameTestHelper,p:Vec3,type:net.minecraftforge.registries.RegistryObject<EntityType<MineDispenserEntity>>):MineDispenserEntity {
        val d=type.get().create(h.level)!!;d.setPos(p);val tag=CompoundTag();d.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal);tag.putInt("HellpodActiveTicks",0);tag.putInt("MineSpreadAge",0);tag.putDouble("HellpodRestY",p.y);d.load(tag);h.level.addFreshEntity(d);return d
    }
    @JvmStatic @GameTest(template="sentry_test",batch="elemental_dispenser",timeoutTicks=10)
    fun correctPayloadFortyEightAndDestructionStopsRelease(h:GameTestHelper) {
        val p=point(h)
        for((type,kind) in listOf(ModEntities.INCENDIARY_MINEFIELD to AntiPersonnelMineEntity.Kind.INCENDIARY,ModEntities.GAS_MINEFIELD to AntiPersonnelMineEntity.Kind.GAS)) {
            val d=dispenser(h,p,type);h.assertTrue(d.getMaxHealth()==40f,"Elemental dispenser health is not 40")
            repeat(30){d.tick()};val mines=h.level.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(p,p).inflate(22.0)){it.dispenserId==d.uuid}
            h.assertTrue(mines.size==48&&mines.all{it.kind==kind},"Wrong payload kind/count: kinds=${mines.groupingBy{it.kind}.eachCount()} expected=$kind at=$p worldCount=${h.level.allEntities.count{it is AntiPersonnelMineEntity&&it.dispenserId==d.uuid}}");mines.forEach{it.discard()};d.discard()
            val mid=dispenser(h,p,type);mid.tick();mid.hurt(h.level.damageSources().generic(),10000f);repeat(40){mid.tick()}
            val partial=h.level.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(p,p).inflate(22.0)){it.dispenserId==mid.uuid};h.assertTrue(partial.size==6&&mid.phase==AbstractHellpodEntity.Phase.SPENT,"Destroyed launcher kept releasing mines");partial.forEach{it.discard()};mid.discard()
        };cleanup(h,p);h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="elemental_landing",timeoutTicks=350)
    fun bothMinefieldsSettleWithoutSelfDetonation(h:GameTestHelper) {
        val p=point(h);for(x in -22..22)for(z in -22..22)h.level.setBlock(BlockPos.containing(p.add(x.toDouble(),-1.0,z.toDouble())),Blocks.STONE.defaultBlockState(),2)
        val first=dispenser(h,p,ModEntities.INCENDIARY_MINEFIELD)
        h.runAfterDelay(135) {
            val mines=h.level.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(p,p).inflate(22.0)){it.dispenserId==first.uuid}
            h.assertTrue(mines.size==48&&mines.all{it.settled&&it.armed},"Incendiary field exploded during deployment or did not arm");cleanup(h,p)
            val second=dispenser(h,p,ModEntities.GAS_MINEFIELD)
            h.runAfterDelay(135) {
                val gasMines=h.level.getEntitiesOfClass(AntiPersonnelMineEntity::class.java,AABB(p,p).inflate(22.0)){it.dispenserId==second.uuid}
                h.assertTrue(gasMines.size==48&&gasMines.all{it.settled&&it.armed},"Gas field exploded during release");cleanup(h,p);h.succeed()
            }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="elemental_pod_save",timeoutTicks=10)
    fun savedDispenserResumesWithoutDuplication(h:GameTestHelper) = MinefieldGameTests.savingCannotDuplicateReleasedMines(h)
    @JvmStatic @GameTest(template="sentry_test",batch="effect_fire_block",timeoutTicks=450)
    fun realFireBlocksRefreshAndExpireWithoutPanic(h:GameTestHelper) {
        val p=point(h);val pos=BlockPos.containing(p);h.level.setBlock(pos.below(),Blocks.STONE.defaultBlockState(),2)
        val field=GroundFireEntity.create(h.level,p,null,.75)
        val state=h.level.getBlockState(pos);h.assertTrue(state.`is`(ModBlocks.SENTRY_FIRE.get()) && !state.`is`(Blocks.FIRE),"Ground fire must be a real mod block")
        val victim=cow(h,p);val before=victim.health
        ModBlocks.SENTRY_FIRE.get().entityInside(state,h.level,pos,victim)
        h.assertTrue(victim.health==before && victim.getEffect(ModEffects.BURNING.get())?.duration==60 && !victim.hasEffect(ModEffects.PANIC.get()),"Ground fire contact: hp=${victim.health}/$before effects=${victim.activeEffects} pos=$p field=${field.position()} life=${field.remainingTicks} covers=${field.coversCell(pos)} owners=${h.level.getEntitiesOfClass(GroundFireEntity::class.java,AABB(pos).inflate(16.0)).map{it.position()}}")
        val mine=ModEntities.INCENDIARY_MINE.get().create(h.level)!!;mine.setPos(p);h.level.addFreshEntity(mine)
        ModBlocks.SENTRY_FIRE.get().entityInside(state,h.level,pos,mine);h.assertTrue(!mine.isRemoved,"Fire block triggered mine")
        mine.discard();victim.discard()
        h.runAfterDelay(100) {
            val refreshed=GroundFireEntity.create(h.level,p,null,.75);h.assertTrue(refreshed===field,"Same fire cell duplicated its lifetime tracker")
            h.runAfterDelay((GroundFireEntity.LIFETIME-2).toLong()) { h.assertTrue(h.level.getBlockState(pos).`is`(ModBlocks.SENTRY_FIRE.get()),"Refreshed flame expired early") }
            h.runAfterDelay((GroundFireEntity.LIFETIME+2).toLong()) { h.assertTrue(h.level.getBlockState(pos).isAir,"Real fire block exceeded refreshed maximum life");cleanup(h,p);h.succeed() }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="effect_gas_height",timeoutTicks=10)
    fun gasHasThreeBlockHeightAndOnlyJetCausesPanic(h:GameTestHelper) {
        val p=point(h);val gas=MortarFieldEntity.create(h.level,p,MortarFieldEntity.Kind.GAS,6.0,true)
        val inside=cow(h,p.add(1.0,2.9,0.0));val above=cow(h,p.add(1.0,3.1,0.0));gas.tick()
        h.assertTrue(inside.hasEffect(ModEffects.GAS.get()) && !above.hasEffect(ModEffects.GAS.get()),"Gas volume must reach three metres and stop above it")
        val attacker=cow(h,p.add(10.0,0.0,0.0));val target=cow(h,p.add(12.0,0.0,0.0))
        FlameDamage.ignite(target,attacker);h.assertTrue(!target.hasEffect(ModEffects.PANIC.get()),"Plain burning caused fear")
        FlameDamage.hit(target,attacker);h.assertTrue(target.getEffect(ModEffects.PANIC.get())?.duration==20,"Flame stream did not apply 1-second panic")
        cleanup(h,p);h.succeed()
    }
}
