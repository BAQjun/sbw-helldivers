package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.asset.AssetCatalog
import com.example.sbwhelldiver.entity.*
import com.example.sbwhelldiver.init.*
import com.mojang.authlib.GameProfile
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
import java.util.UUID

@GameTestHolder("sbw_helldiver_tesla")
@PrefixGameTestTemplate(false)
object TeslaGameTests {
    @JvmStatic @GameTest(template="sentry_test",batch="tesla",timeoutTicks=10)
    fun fullSecondChargeCooldownAndPersistence(h: GameTestHelper) {
        val c=TeslaClock()
        repeat(80) { h.assertTrue(!c.advance(false),"Idle arcs must never discharge") }
        c.advance(true)
        repeat(19) { h.assertTrue(!c.advance(true),"Discharge before one second") }
        val tag=CompoundTag();c.save(tag);val loaded=TeslaClock();loaded.restore(tag)
        h.assertTrue(loaded.advance(true) && loaded.state==TeslaClock.State.COOLDOWN,"Charge/save lost its one-second boundary")
        repeat(19) { h.assertTrue(!loaded.advance(true) && loaded.state==TeslaClock.State.COOLDOWN,"Cooldown ended early") }
        loaded.advance(true);h.assertTrue(loaded.state==TeslaClock.State.CHARGING,"Next charge did not start after one-second cooldown")
        loaded.advance(false);h.assertTrue(loaded.state==TeslaClock.State.IDLE,"Lost target continued charging")
        val p=AssetCatalog.profiles.getValue("tesla_tower")
        h.assertTrue(p.isTesla && p.totalAmmo==0 && p.shellOffset==null,"Tesla acquired ammunition or shell ejection")
        h.succeed()
    }

    @JvmStatic @GameTest(template="sentry_test",batch="tesla",timeoutTicks=10)
    fun hostilePrimaryIndiscriminateUniqueChainExactDamageAndWall(h: GameTestHelper) {
        val bp=h.absolutePos(BlockPos(15,260,15));val origin=Vec3(bp.x+.5,bp.y.toDouble(),bp.z+.5)
        for(cx in ((bp.x-2) shr 4)..((bp.x+35) shr 4))for(cz in ((bp.z-2) shr 4)..((bp.z+4) shr 4)) {
            h.level.setChunkForced(cx,cz,true);h.level.getChunk(cx,cz)
        }
        val tower=ModEntities.TESLA_TOWER.get().create(h.level)!!;tower.setPos(origin)
        fun mob(x: Double,hostile: Boolean): net.minecraft.world.entity.Mob {
            val m=if(hostile)EntityType.HUSK.create(h.level)!! else EntityType.COW.create(h.level)!!
            m.isNoGravity=true;m.isNoAi=true;m.setPos(origin.add(x,0.0,0.0))
            m.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=200.0;m.health=200f
            m.getAttribute(Attributes.ARMOR)!!.baseValue=30.0;m.addEffect(MobEffectInstance(MobEffects.DAMAGE_RESISTANCE,1000,4))
            h.level.addFreshEntity(m);return m
        }
        val first=mob(5.0,true);val friendly=mob(7.0,false);val far=mob(34.0,true)
        val player=object : Player(h.level,BlockPos.ZERO,0f,GameProfile(UUID.randomUUID(),"TeslaJump")) {
            override fun isSpectator()=false
            override fun isCreative()=false
        }.also { it.getAttribute(Attributes.MAX_HEALTH)!!.baseValue=200.0;it.health=200f;it.isNoGravity=true;it.setPos(origin.add(9.0,0.0,0.0));h.level.addFreshEntity(it) }
        h.assertTrue(tower.health==150f && tower.validTarget(first) && !tower.validTarget(player) && !tower.validTarget(far),"Health or twenty-meter primary filtering is wrong")
        val path=TeslaArc.chain(tower,first)
        h.assertTrue(path==listOf(first,friendly,player),"Chain must include friendly/player nearest neighbors and never return to A")
        TeslaArc.discharge(tower,path+first)
        h.assertTrue(path.all { it.health==120f } && far.health==200f,"Each unique victim must take exactly eighty damage, at most three victims")
        h.assertTrue(path.all { it.getEffect(ModEffects.STUN.get())?.duration==2 && !it.hasEffect(ModEffects.BURNING.get()) },"Electric stun duration/burn exclusion is wrong")
        first.removeEffect(ModEffects.STUN.get());friendly.removeEffect(ModEffects.STUN.get());player.removeEffect(ModEffects.STUN.get())
        h.level.setBlock(bp.offset(6,0,0),Blocks.STONE.defaultBlockState(),2)
        h.level.setBlock(bp.offset(6,1,0),Blocks.STONE.defaultBlockState(),2)
        h.assertTrue(TeslaArc.chain(tower,first).size==1,"Electricity passed through a wall")
        h.level.setBlock(bp.offset(6,0,0),Blocks.AIR.defaultBlockState(),2);h.level.setBlock(bp.offset(6,1,0),Blocks.AIR.defaultBlockState(),2)
        (path+far).forEach { it.discard() };tower.discard();h.succeed()
    }
}
