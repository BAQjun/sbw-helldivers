package com.example.sbwhelldiver.gametest
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.ModEntities
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MoverType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.util.UUID
import kotlin.math.*
import com.atsuishio.superbwarfare.world.phys.ExtendedEntityRayTraceResult
import com.atsuishio.superbwarfare.world.phys.EntityResult

@GameTestHolder("sbw_helldiver_grenadier")
@PrefixGameTestTemplate(false)
object GrenadierGameTests {
    private fun setup(h:GameTestHelper,yaw:Float=0f):GrenadierBattlementEntity {
        val pos=h.absolutePos(BlockPos(8,240,8));h.level.getChunk(pos)
        for(x in -5..5)for(z in -5..5)h.level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2)
        val testOrigin=Vec3(pos.x+.5,pos.y.toDouble(),pos.z+.5)
        val stale=h.level.getEntitiesOfClass(GrenadierBattlementEntity::class.java,net.minecraft.world.phys.AABB(testOrigin.subtract(5.0,2.0,5.0),testOrigin.add(5.0,3.0,5.0))).filter {it.position().distanceTo(testOrigin)<.01}.toSet()
        h.level.getEntitiesOfClass(GrenadierWallPanelEntity::class.java,net.minecraft.world.phys.AABB(testOrigin.subtract(5.0,2.0,5.0),testOrigin.add(5.0,3.0,5.0))).filter {it.battlement in stale}.forEach {it.discard()}
        stale.forEach {it.discard()}
        return ModEntities.GRENADIER_BATTLEMENT.get().create(h.level)!!.also {d ->
            d.setPos(pos.x+.5,pos.y.toDouble(),pos.z+.5);d.yRot=yaw
            val tag=CompoundTag();d.saveWithoutId(tag);tag.putInt("HellpodPhase",AbstractHellpodEntity.Phase.ACTIVE.ordinal)
            tag.putDouble("HellpodRestY",d.y);tag.putFloat("HmgYaw",yaw);d.load(tag);h.level.addFreshEntity(d)
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="grenadier_rate",timeoutTicks=510)
    fun fullMagazineAt160RpmLeavesOccupiedAndEmptyCover(h:GameTestHelper) {
        val d=setup(h);val p=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"GrenadierRate"))
        p.setPos(d.position());h.level.addFreshEntity(p);p.startRiding(d,true)
        h.assertTrue(d.ammoRemaining==60&&abs(d.health-500f)<.01,"Magazine or health disagrees with requested values")
        var serial=0;val times=mutableListOf<Long>();var emptyAt=-1L;var dismounted=false
        h.onEachTick {
            if(d.ammoRemaining>0)d.acceptInput(p,0f,-35f,true)
            if(d.shotSerial!=serial) {times.add(h.level.gameTime);serial=d.shotSerial}
            if(serial==60) {
                if(emptyAt<0) {
                    emptyAt=h.level.gameTime
                    h.assertTrue(times.size==60&&times.zipWithNext().all {it.second-it.first in 7L..8L},"160 RPM cadence has a gap or extra shot")
                    h.assertTrue(times.last()-times.first()==443L,"Average firing interval is not 7.5 ticks")
                }
                h.assertTrue(d.phase==AbstractHellpodEntity.Phase.ACTIVE&&!d.isWreck,"Empty wall folded or was destroyed")
                if(!dismounted)h.assertTrue(p.vehicle===d,"Last grenade forced the player off")
                if(h.level.gameTime-emptyAt>=8&&!dismounted) {
                    p.stopRiding();dismounted=true
                    val tag=CompoundTag();d.saveWithoutId(tag);d.load(tag)
                    h.assertTrue(d.ammoRemaining==0,"Saving refilled the launcher")
                }
                if(h.level.gameTime-emptyAt>=18) {p.discard();d.discard();h.succeed()}
            }
        }
    }
    @JvmStatic @GameTest(template="sentry_test",batch="grenadier_collision",timeoutTicks=30)
    fun angledWallBlocksMobsAndBulletsAndSharesOneHealthPool(h:GameTestHelper) {
        val d=setup(h,37f);d.tick()
        val angle=Math.toRadians(37.0)
        fun point(x:Double,z:Double)=d.position().add(x*cos(angle)-z*sin(angle),.2,x*sin(angle)+z*cos(angle))
        val panels=h.level.getEntitiesOfClass(GrenadierWallPanelEntity::class.java,d.boundingBox.inflate(5.0)){it.battlement===d}
        h.assertTrue(panels.size==3,"Deployed wall is missing collision surfaces")
        h.assertTrue(d.boundingBox.size<.001&&!d.canBeCollidedWith()&&!d.isPickable,"Parent retained a large hit/collision box")
        val heights=panels.map {it.boundingBox.ysize}.sorted()
        h.assertTrue(heights[2]>heights[1]&&abs(heights[0]-heights[1])<.001,"Wall is not two equal low wings and one high centre")
        val mob=EntityType.ZOMBIE.create(h.level)!!;mob.isNoAi=true;mob.isNoGravity=true;mob.setPos(point(2.0,-2.0));h.level.addFreshEntity(mob)
        repeat(40) {mob.move(MoverType.SELF,Vec3(-sin(angle)*.1,0.0,cos(angle)*.1))}
        val dz=mob.position().subtract(d.position());val localZ=-dz.x*sin(angle)+dz.z*cos(angle)
        h.assertTrue(localZ<.1,"Creature walked through an outer wall panel at a non-cardinal yaw")
        mob.discard() // Remove the creature obstacle before testing the independent bullet path.
        val p=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"GrenadierCollision"));p.setPos(point(2.9,-3.0));h.level.addFreshEntity(p)
        val before=d.health;val bullet=ModEntities.SENTRY_BULLET.get().create(h.level)!!
        bullet.owner=p;bullet.setDamage(17f);bullet.setPos(point(2.7,-1.0));bullet.shoot(-sin(angle),0.0,cos(angle),2f,0f);h.level.addFreshEntity(bullet)
        bullet.tick()
        h.assertTrue(d.health<before,"SBW bullet passed through the outer wall without transferring damage")
        val baseline=d.health;val source=h.level.damageSources().explosion(d,p)
        d.hurt(source,20f);panels.forEach {it.hurt(source,80f)}
        h.assertTrue(abs(d.health-(baseline-40f))<.01,"One explosion was multiplied by the number of collision cells, or lacks 50% resistance")
        p.startRiding(d,true);val anchor=d.operatorPosition();d.acceptInput(p,60f,0f,false);d.tick()
        h.assertTrue(abs(d.weaponYaw-60f)<.01,"Launcher retained mechanical drag on a large aim change")
        h.assertTrue(d.operatorPosition().distanceTo(anchor)<.001,"Operator anchor turned with launcher instead of the fixed wall")
        p.stopRiding();p.discard();mob.discard();bullet.discard();d.discard();panels.forEach {it.discard()};h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="grenadier_blast",timeoutTicks=10)
    fun grenadeAppliesFiveDirectAndDualRadiusSixtyBlast(h:GameTestHelper) {
        // Spawn chunks keep fresh entities available to the immediate blast query.
        val pos=BlockPos(8,300,8);h.level.getChunk(pos);val origin=Vec3(pos.x+.5,pos.y.toDouble(),pos.z+.5)
        val shell=ModEntities.GRENADIER_GRENADE.get().create(h.level)!!;shell.setPos(origin);shell.tickCount=1;shell.setExplosionDestroy(false);h.level.addFreshEntity(shell)
        fun target(distance:Double)=EntityType.RAVAGER.create(h.level)!!.also {
            it.isNoAi=true;it.isNoGravity=true;it.getAttribute(Attributes.ARMOR)!!.baseValue=0.0;it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=1000.0;it.health=1000f
            it.setPos(origin.x+distance+it.bbWidth/2,origin.y-.5,origin.z);h.level.getChunk(it.blockPosition());h.level.addFreshEntity(it)
        }
        val mobs=listOf(target(0.0),target(3.5),target(5.5),target(7.6));val before=mobs.map{it.health}
        shell.onHitEntity(ExtendedEntityRayTraceResult(EntityResult(mobs[0],origin,false,false)))
        val damages=listOf(65f,60f,29.5f,0f)
        mobs.forEachIndexed {i,m ->h.assertTrue(abs(before[i]-m.health-damages[i])<.03,"Actual grenade damage at target $i differs: ${before[i]-m.health}");m.discard()}
        h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="grenadier_bearing",timeoutTicks=10)
    fun wallBearingUsesOriginalThrowPointAfterMotionAndReload(h:GameTestHelper) {
        val start=Vec3(17.25,230.0,-11.5)
        val ball=StratagemBeacon(ModEntities.STRATAGEM_BEACON.get(),start.x,start.y,start.z,h.level)
        ball.shoot(.8,.2,.6,1.5f,0f)
        ball.setPos(start.add(30.0,-2.0,15.0));ball.yRot=151f
        val tag=CompoundTag();ball.saveWithoutId(tag)
        val restored=ModEntities.STRATAGEM_BEACON.get().create(h.level)!!;restored.load(tag)
        for (delta in listOf(Vec3(4.0,-6.0,9.0),Vec3(-11.0,-2.0,3.0),Vec3(.01,-3.0,-20.0))) {
            val yaw=Math.toRadians(restored.wallDeploymentYaw(start.add(delta)).toDouble())
            val alongWall=Vec3(cos(yaw),0.0,sin(yaw))
            h.assertTrue(abs(alongWall.dot(delta.normalize()))<1e-6,"Wall is not perpendicular to original throw-to-impact line")
        }
        ball.discard();restored.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="grenadier_bearing",timeoutTicks=10)
    fun beaconImpactTransfersPerpendicularPreciseHeadingToPod(h:GameTestHelper) {
        val near=h.absolutePos(BlockPos(8,180,8));val pos=BlockPos(near.x,minOf(near.y,h.level.maxBuildHeight-8),near.z);h.level.getChunk(pos);val landing=Vec3(pos.x+.37,pos.y.toDouble(),pos.z+.61)
        // Reused acceptance worlds can retain an old, higher fixture platform in this column.
        for(x in -2..2)for(z in -2..2)for(y in pos.y until h.level.maxBuildHeight)h.level.setBlock(BlockPos(pos.x+x,y,pos.z+z),Blocks.AIR.defaultBlockState(),2)
        h.level.getEntitiesOfClass(StratagemMarker::class.java,net.minecraft.world.phys.AABB(pos.x-2.0,h.level.minBuildHeight.toDouble(),pos.z-2.0,pos.x+3.0,h.level.maxBuildHeight+1000.0,pos.z+3.0)).forEach {it.discard()}

        for(x in -2..2)for(z in -2..2)h.level.setBlock(pos.offset(x,-1,z),Blocks.STONE.defaultBlockState(),2)
        val start=landing.add(-4.0,2.0,-9.0)
        val owner=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"WallThrowBearing"));owner.setPos(start)
        val ball=StratagemBeacon(ModEntities.STRATAGEM_BEACON.get(),owner,h.level)
        val stack=net.minecraft.world.item.ItemStack(com.example.sbwhelldiver.init.ModItems.BEACON.get())
        val definition=com.example.sbwhelldiver.stratagem.Stratagems.get("grenadier_battlement")!!
        com.example.sbwhelldiver.stratagem.BeaconData.setItems(stack,listOf(net.minecraft.world.item.ItemStack(com.example.sbwhelldiver.init.ModItems.GRENADIER_BATTLEMENT.get())))
        com.example.sbwhelldiver.stratagem.BeaconData.activate(stack,definition);ball.item=stack
        ball.shoot(0.0,0.0,1.0,1.5f,0f);ball.yRot=123f;owner.setPos(start.add(20.0,0.0,-18.0))
        ball.setPos(landing.add(0.0,.15,0.0));ball.deltaMovement=Vec3(0.0,-.4,0.0);h.level.addFreshEntity(ball);ball.tick()
        val markers=h.level.getEntitiesOfClass(StratagemMarker::class.java,net.minecraft.world.phys.AABB(landing.subtract(1.0,1.0,1.0),landing.add(1.0,1.0,1.0)))
        h.assertTrue(markers.size==1,"Valid thrown beacon did not create exactly one marker: count=${markers.size}, pos=${ball.position()}, removed=${ball.isRemoved}, selected=${com.example.sbwhelldiver.stratagem.BeaconData.selected(ball.item)?.id}")
        val marker=markers.single();val expected=Math.toDegrees(atan2(-4.0,9.0)).toFloat()
        h.assertTrue(abs(marker.yRot-expected)<1e-5,"Impact used ball attitude or current owner position")
        val tag=CompoundTag();marker.saveWithoutId(tag);tag.putInt("Duration",20);tag.putInt("LandingGraceTicks",0);tag.putLong("StartedAt",(h.level.gameTime-20).coerceAtLeast(0));marker.load(tag);marker.tick()
        val podId=requireNotNull(marker.podUUID){"Restored marker did not spawn a pod; removed=${marker.isRemoved}, deadline=${marker.arrivalDeadline}, now=${h.level.gameTime}"}
        val pod=requireNotNull(h.level.getEntity(podId)){"Spawned pod is not in the loaded fixture chunk"} as GrenadierBattlementEntity
        h.assertTrue(abs(pod.yRot-expected)<1e-5,"Drop lost deployment bearing")
        val saved=CompoundTag();pod.saveWithoutId(saved)
        h.assertTrue(abs(saved.getFloat("GrenadierDeploymentHeading")-expected)<1e-5,"Full-precision heading was not saved")
        pod.lerpTo(pod.x,pod.y,pod.z,expected-1f,0f,3,true);pod.tick()
        h.assertTrue(abs(pod.yRot-expected)<1e-5,"Quantized movement rotation replaced deployment heading")
        marker.discard();pod.discard();ball.discard();owner.discard();h.succeed()
    }
    @JvmStatic @GameTest(template="sentry_test",batch="grenadier_geometry",timeoutTicks=10)
    fun muzzleAndOpticShareRotatedOffsetPivot(h:GameTestHelper) {
        for(heading in listOf(37f,-120f,179f,0f)) {
            val d=setup(h,heading)
            val angle=Math.toRadians(heading.toDouble())
            val atZero=d.opticalCameraPosition(1f).subtract(d.position())
            val expected=Vec3(-.00031694,1.6299705025,-.1749576225)
            val rotated=Vec3(expected.x*cos(angle)-expected.z*sin(angle),expected.y,expected.x*sin(angle)+expected.z*cos(angle))
            h.assertTrue(atZero.distanceTo(rotated)<1e-6,"Optic omitted rotation of the off-centre deployment pivot")
            d.discard()
        }
        h.succeed()
    }

}


