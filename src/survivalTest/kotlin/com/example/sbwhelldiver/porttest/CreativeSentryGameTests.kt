package com.example.sbwhelldiver.porttest

import com.example.sbwhelldiver.LOGGER
import com.example.sbwhelldiver.entity.AbstractHellpodEntity
import com.example.sbwhelldiver.entity.CombatHostility
import com.example.sbwhelldiver.init.ModEntities
import com.mojang.authlib.GameProfile
import java.util.UUID
import net.minecraft.core.BlockPos
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobSpawnType
import net.minecraft.world.level.GameType
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate

@GameTestHolder("sbw_helldiver")
@PrefixGameTestTemplate(false)
object CreativeSentryGameTests {
    @JvmStatic
    @GameTest(batch = "creative_sentry", template = "port_test", timeoutTicks = 120)
    fun creativeSentryTargetsAnUnawareWarden(h: GameTestHelper) {
        val center = h.absolutePos(BlockPos(3, 8, 3))
        for (floor in BlockPos.betweenClosed(center.offset(-2, -1, -2), center.offset(14, -1, 2))) {
            h.level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState())
        }
        val owner = FakePlayer(h.level, GameProfile(UUID.randomUUID(), "CreativeSentry"))
        owner.setPos(Vec3.atBottomCenterOf(center))
        val zombie = EntityType.ZOMBIE.create(h.level)!!
        val wolf = EntityType.WOLF.create(h.level)!!
        val warden = EntityType.WARDEN.create(h.level)!!
        warden.setPos(Vec3.atBottomCenterOf(center.offset(11, 0, 0)))
        warden.finalizeSpawn(h.level, h.level.getCurrentDifficultyAt(warden.blockPosition()), MobSpawnType.COMMAND, null, null)

        owner.setGameMode(GameType.SURVIVAL)
        h.assertTrue(warden.entityAngryAt.isEmpty && warden.target == null, "Warden already detected a target")
        h.assertTrue(CombatHostility.isHostileTo(warden, owner), "Sentry waited for the Warden to detect a survival player")

        owner.setGameMode(GameType.CREATIVE)
        h.assertTrue(owner.isCreative && !zombie.canAttack(owner), "Fixture must keep vanilla Creative immunity")
        zombie.isNoAi = true
        h.assertTrue(CombatHostility.isHostileTo(zombie, owner), "Legacy enemy classification was lost when the zombie had no AI")
        h.assertTrue(!CombatHostility.isHostileTo(wolf, owner), "Creative mode made a calm wolf hostile")
        wolf.remainingPersistentAngerTime = 100
        wolf.persistentAngerTarget = UUID.randomUUID()
        h.assertTrue(!CombatHostility.isHostileTo(wolf, owner), "Creative mode ignored whom the wolf was angry at")
        wolf.persistentAngerTarget = owner.uuid
        h.assertTrue(CombatHostility.isHostileTo(wolf, owner), "Sentry missed a wolf angry at its creative deployer")
        val team = h.level.scoreboard.addPlayerTeam("creative_sentry_test")
        try {
            h.level.scoreboard.addPlayerToTeam(owner.scoreboardName, team)
            h.level.scoreboard.addPlayerToTeam(zombie.scoreboardName, team)
            h.assertTrue(!CombatHostility.isHostileTo(zombie, owner), "Creative mode overrode team allegiance")
        } finally {
            h.level.scoreboard.removePlayerTeam(team)
        }

        val sentry = ModEntities.MACHINE_GUN_SENTRY.get().create(h.level)!!
        sentry.setPos(Vec3.atBottomCenterOf(center.offset(3, 0, 0)))
        val state = CompoundTag()
        sentry.saveWithoutId(state)
        state.putInt("HellpodPhase", AbstractHellpodEntity.Phase.DEPLOYING.ordinal)
        state.putInt("HellpodPhaseTicks", sentry.deployTicks - 1)
        state.putBoolean("HellpodCapReleased", true)
        state.putDouble("HellpodRestY", sentry.y)
        sentry.load(state)
        sentry.setOwnerUUID(owner.uuid)

        h.level.addNewPlayer(owner)
        h.level.addFreshEntity(sentry)
        h.level.addFreshEntity(warden)
        h.assertTrue(sentry.owner === owner, "Sentry could not resolve its creative deployer")
        h.runAfterDelay(110) { sentry.discard(); warden.discard(); owner.discard() }
        h.succeedWhen {
            h.assertTrue(sentry.shotSerial > 0, "Creative-owned sentry never opened fire on the unaware Warden")
            h.assertTrue(warden.target !== owner && owner.isCreative, "The probe changed Creative targeting immunity")
            LOGGER.info("CREATIVE_SENTRY_WARDEN_FIRED shots={}", sentry.shotSerial)
            sentry.discard()
            warden.discard()
            owner.discard()
        }
    }
}
