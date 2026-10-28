package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.config.HelldiversConfig
import com.example.sbwhelldiver.entity.SentryExplosion
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.network.*
import com.atsuishio.superbwarfare.entity.projectile.FastThrowableProjectile
import com.atsuishio.superbwarfare.config.server.ExplosionConfig
import com.mojang.authlib.GameProfile
import io.netty.buffer.Unpooled
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.*
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.*
import java.util.UUID

@GameTestHolder("sbw_helldiver_explosionconfig")
@PrefixGameTestTemplate(false)
object ExplosionBlockDamageGameTests {
    @JvmStatic @GameTest(templateNamespace="sbw_helldiver_config",template="sentry_test",timeoutTicks=30)
    fun blockProtectionKeepsBlastDamageAndRestoresDestruction(h:GameTestHelper) {
        h.assertTrue(!HelldiversConfig.stratagemExplosionBlockDamage.getDefault(),"Block damage must default to OFF")
        val snapshot=ConfigTestSnapshot();val original=ExplosionConfig.EXPLOSION_DESTROY.get()
        try {
            ExplosionConfig.EXPLOSION_DESTROY.set(true)
            val at=Vec3.atBottomCenterOf(h.absolutePos(BlockPos(8,180,8)))
            h.level.getChunk(BlockPos.containing(at))
            val block=BlockPos.containing(at)
            val types=listOf(ModEntities.SENTRY_CANNON_SHELL.get(),ModEntities.SENTRY_ROCKET.get(),ModEntities.MORTAR_SHELL.get(),ModEntities.EAGLE_BOMB.get(),ModEntities.EAGLE_CLUSTER_BOMBLET.get(),ModEntities.EAGLE_110MM_ROCKET.get(),ModEntities.ORBITAL_SHELL.get(),ModEntities.ORBITAL_120_SHELL.get(),ModEntities.ORBITAL_RAILCANNON_SHELL.get())
            for(type in types){
                h.level.setBlock(block,Blocks.DIRT.defaultBlockState(),3)
                val victim=EntityType.RAVAGER.create(h.level)!!;victim.setPos(at.add(1.5,0.0,0.0));victim.isNoAi=true;victim.getAttribute(Attributes.ARMOR)!!.baseValue=0.0;h.level.addFreshEntity(victim)
                val shell=type.create(h.level) as FastThrowableProjectile;shell.setPos(at);shell.setExplosionDamage(20f);shell.setExplosionRadius(3f)
                try{
                    HelldiversConfig.stratagemExplosionBlockDamage.set(false)
                    val before=victim.health;SentryExplosion.explode(shell,at,2.0,true)
                    h.assertTrue(h.level.getBlockState(block).`is`(Blocks.DIRT),"Protected block destroyed by $type")
                    h.assertTrue(victim.health<before,"Block protection removed entity damage for $type")
                }finally{shell.discard();victim.discard()}
            }
            HelldiversConfig.stratagemExplosionBlockDamage.set(true)
            val victim=EntityType.RAVAGER.create(h.level)!!;victim.setPos(at.add(1.5,0.0,0.0));victim.isNoAi=true;victim.getAttribute(Attributes.ARMOR)!!.baseValue=0.0;h.level.addFreshEntity(victim)
            val before=victim.health
            val shell=ModEntities.ORBITAL_SHELL.get().create(h.level)!!;shell.setPos(at);shell.setExplosionDamage(60f);shell.setExplosionRadius(3f)
            try {
                SentryExplosion.explode(shell,at,2.0,true)
                h.assertTrue(kotlin.math.abs(before-victim.health-60f)<.001f,"Enabling terrain damage changed entity damage or applied a second damage pass")
            } finally {shell.discard();victim.discard()}
            h.assertTrue(h.level.getBlockState(block).isAir,"Enabling block damage did not restore destruction")
            h.level.setBlock(block,Blocks.DIRT.defaultBlockState(),3)
            val safe=ModEntities.SENTRY_CANNON_SHELL.get().create(h.level)!!;safe.setPos(at);safe.setExplosionDamage(60f);safe.setExplosionRadius(3f)
            SentryExplosion.explode(safe,at,2.0,false);safe.discard()
            h.assertTrue(h.level.getBlockState(block).`is`(Blocks.DIRT),"Enabling changed an inherently non-destructive blast")
            ExplosionConfig.EXPLOSION_DESTROY.set(false)
            val disabled=ModEntities.ORBITAL_SHELL.get().create(h.level)!!;disabled.setPos(at);disabled.setExplosionDamage(60f);disabled.setExplosionRadius(3f)
            try {SentryExplosion.explode(disabled,at,2.0,true)} finally {disabled.discard()}
            h.assertTrue(h.level.getBlockState(block).isAir,"SBW disabled our independent stratagem option")
            h.level.setBlock(block,Blocks.DIRT.defaultBlockState(),3)
            val unrelated=EntityType.SNOWBALL.create(h.level)!!;unrelated.setPos(at)
            try {com.atsuishio.superbwarfare.tools.CustomExplosion.Builder(unrelated).position(at).damage(60f).radius(3f).destroyBlock(true).explode()} finally {unrelated.discard()}
            h.assertTrue(h.level.getBlockState(block).`is`(Blocks.DIRT),"Our independent option enabled unrelated explosions")
            HelldiversConfig.stratagemExplosionBlockDamage.set(false)
            val protected=ModEntities.ORBITAL_SHELL.get().create(h.level)!!;protected.setPos(at);protected.setExplosionDamage(60f);protected.setExplosionRadius(3f)
            try {SentryExplosion.explode(protected,at,2.0,true)} finally {protected.discard()}
            h.assertTrue(h.level.getBlockState(block).`is`(Blocks.DIRT),"Both switches off failed to protect terrain")
            ExplosionConfig.EXPLOSION_DESTROY.set(true)
            val independent=EntityType.SNOWBALL.create(h.level)!!;independent.setPos(at)
            try {com.atsuishio.superbwarfare.tools.CustomExplosion.Builder(independent).position(at).damage(60f).radius(3f).destroyBlock(true).explode()} finally {independent.discard()}
            h.assertTrue(h.level.getBlockState(block).isAir,"Our OFF setting disabled unrelated SBW explosions")
            h.level.setBlock(block,Blocks.AIR.defaultBlockState(),3)
        }finally{ExplosionConfig.EXPLOSION_DESTROY.set(original);snapshot.close()}
        h.succeed()
    }
    @JvmStatic @GameTest(templateNamespace="sbw_helldiver_config",template="sentry_test",timeoutTicks=10,batch="block_damage_permissions")
    fun onlyOperatorsCanChangeBlockDamageAndPacketsPreserveQueries(h:GameTestHelper){
        val player=FakePlayerFactory.get(h.level,GameProfile(UUID.randomUUID(),"BlockDamagePermission"))
        val original=HelldiversConfig.stratagemExplosionBlockDamage.get()
        h.assertTrue(!ExplosionBlockDamageSettings.update(player,!original)&&HelldiversConfig.stratagemExplosionBlockDamage.get()==original,"Ordinary player changed world protection")
        for(value in listOf(null,false,true)){
            val buffer=FriendlyByteBuf(Unpooled.buffer())
            try{ExplosionBlockDamageRequestPacket(value).encode(buffer);h.assertTrue(ExplosionBlockDamageRequestPacket.decode(buffer).enabled==value,"Request packet lost query or boolean")}finally{buffer.release()}
        }
        player.discard();h.succeed()
    }
}
