package com.example.sbwhelldiver.gametest

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.entity.AbstractHellpodEntity.Phase
import com.example.sbwhelldiver.entity.SentryHellpodEntity
import com.example.sbwhelldiver.entity.StratagemBeacon
import com.example.sbwhelldiver.entity.StratagemMarker
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.menu.BeaconLoadoutMenu
import com.example.sbwhelldiver.stratagem.BeaconData
import com.example.sbwhelldiver.stratagem.BeaconInput
import com.example.sbwhelldiver.stratagem.Stratagems
import com.mojang.authlib.GameProfile
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.gametest.framework.GameTest
import net.minecraft.gametest.framework.GameTestHelper
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.InteractionHand
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import net.minecraft.world.phys.AABB
import net.minecraft.server.level.ServerPlayer
import net.minecraft.network.chat.Component
import net.minecraftforge.common.util.FakePlayer
import net.minecraftforge.common.util.FakePlayerFactory
import net.minecraftforge.gametest.GameTestHolder
import net.minecraftforge.gametest.PrefixGameTestTemplate
import java.util.UUID

@GameTestHolder(MOD_ID)
@PrefixGameTestTemplate(false)
object BeaconGameTests {
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun delayedBeamActivationPlaysOnceAndSurvivesReload(helper: GameTestHelper) {
        val marker = ModEntities.STRATAGEM_MARKER.get().create(helper.level)!!
        val p = helper.absolutePos(BlockPos(15, 205, 15)); marker.setPos(p.x+.5, p.y.toDouble(), p.z+.5)
        marker.configure(Stratagems.get("machine_gun_sentry")!!.dropId, 200, null)
        val tag = CompoundTag(); marker.saveWithoutId(tag)
        tag.putLong("StartedAt", helper.level.gameTime-5); marker.load(tag)
        var sounds = 0
        val listener = java.util.function.Consumer<net.minecraftforge.event.PlayLevelSoundEvent.AtPosition> { event ->
            if (event.level === helper.level && event.position.distanceToSqr(marker.position()) < .0001 &&
                event.sound?.value() === com.example.sbwhelldiver.init.ModSounds.EVENTS.getValue("beacon_deploy").get()) {
                sounds++
                helper.assertTrue(event.source == net.minecraft.sounds.SoundSource.PLAYERS && event.sound!!.value().getRange(1f) == 64f,
                    "Beam activation used the block-volume slider or the short default range")
            }
        }
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener)
        try {
            marker.tick(); marker.playActivationSound(); marker.tick()
            helper.assertTrue(sounds == 1, "A delayed first tick silently lost or duplicated the beam activation cue")
            marker.saveWithoutId(tag)
            val restored = ModEntities.STRATAGEM_MARKER.get().create(helper.level)!!
            restored.load(tag); restored.tick(); restored.playActivationSound()
            helper.assertTrue(sounds == 1, "Loading an already deployed beacon replayed its activation cue")
        } finally { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener) }
        helper.succeed()
    }

    private fun cards() = listOf(ModItems.MACHINE_GUN_SENTRY, ModItems.GATLING_SENTRY, ModItems.ROCKET_SENTRY, ModItems.AUTOCANNON_SENTRY).map { ItemStack(it.get()) }
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun loadoutPreservesPhysicalItems(helper: GameTestHelper) {
        val player = FakePlayerFactory.get(helper.level, GameProfile(UUID.randomUUID(), "LoadoutTest"))
        val stack = ItemStack(ModItems.BEACON.get())
        player.setItemInHand(InteractionHand.MAIN_HAND, stack)
        cards().forEachIndexed { index, card -> player.inventory.setItem(9 + index, card.copyWithCount(2)) }
        val menu = BeaconLoadoutMenu(1, player.inventory, InteractionHand.MAIN_HAND)
        repeat(4) { menu.quickMoveStack(player, 4 + it) }
        helper.assertTrue(BeaconData.loadout(stack).filterNotNull().size == 4, "Four cards were not saved inside the beacon")
        helper.assertTrue((9..12).all { player.inventory.getItem(it).count == 1 }, "Loadout duplicated or lost card stacks")
        helper.assertTrue(!menu.slots[0].mayPlace(cards()[1]), "Duplicate card may be loaded")
        helper.assertTrue(!menu.slots[0].mayPlace(ItemStack(Items.DIAMOND)), "Non-stratagem may be loaded")
        helper.assertTrue(menu.quickMoveStack(player, 31).isEmpty && player.mainHandItem === stack, "Beacon may be moved out of its own menu")
        val restored = ItemStack.of(stack.save(CompoundTag()))
        helper.assertTrue(BeaconData.loadout(restored) == BeaconData.loadout(stack), "Loadout did not survive save/load")
        menu.quickMoveStack(player, 2)
        helper.assertTrue(BeaconData.loadout(stack).filterNotNull().size == 3, "Removing a card did not update NBT")
        val total = (0..35).sumOf { if (player.inventory.getItem(it).item == ModItems.ROCKET_SENTRY.get()) player.inventory.getItem(it).count else 0 }
        helper.assertTrue(total == 2, "Removing a card failed to return the physical item")
        helper.succeed()
    }
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun codesRequireLoadedCardAndIdentity(helper: GameTestHelper) {
        val player = FakePlayerFactory.get(helper.level, GameProfile(UUID.randomUUID(), "InputTest"))
        val stack = ItemStack(ModItems.BEACON.get())
        player.setItemInHand(InteractionHand.MAIN_HAND, stack)
        val identity = BeaconData.ensureIdentity(stack)
        BeaconData.setItems(stack, cards())
        helper.assertTrue(!ModItems.BEACON.get().use(helper.level, player, InteractionHand.MAIN_HAND).result.consumesAction(), "Uncharged beacon may be thrown")
        for (def in Stratagems.definitions) {
            val card = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(def.itemId)!!
            BeaconData.setItems(stack, listOf(ItemStack(card)))
            def.code.forEach { BeaconInput.accept(player, InteractionHand.MAIN_HAND, UUID.randomUUID(), it) }
            helper.assertTrue(BeaconData.selected(stack) == null, "Forged identity activated a beacon")
            BeaconInput.accept(player, InteractionHand.MAIN_HAND, identity, (def.code.first()+1)%4)
            helper.assertTrue(BeaconData.selected(stack) == null, "Invalid prefix activated a beacon")
            def.code.forEach { BeaconInput.accept(player, InteractionHand.MAIN_HAND, identity, it) }
            helper.assertTrue(BeaconData.selected(stack) == def, "Original ${def.id} code failed")
            BeaconData.clear(stack)
        }
        BeaconData.setItems(stack, listOf(cards()[0]))
        Stratagems.get("rocket_sentry")!!.code.forEach { BeaconInput.accept(player, InteractionHand.MAIN_HAND, identity, it) }
        helper.assertTrue(BeaconData.selected(stack) == null, "An unloaded card was called")
        BeaconInput.reset(player.uuid)
        helper.succeed()
    }
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun reselectSwitchAndLeftThrowAreServerValidated(helper: GameTestHelper) {
        val player = FakePlayerFactory.get(helper.level, GameProfile(UUID.randomUUID(), "ReselectTest"))
        val stack = ItemStack(ModItems.BEACON.get())
        val item = stack.item as com.example.sbwhelldiver.item.BeaconItem
        player.setItemInHand(InteractionHand.MAIN_HAND, stack)
        val id = BeaconData.ensureIdentity(stack)
        BeaconData.setItems(stack, cards())
        val machine = Stratagems.get("machine_gun_sentry")!!
        val gatling = Stratagems.get("gatling_sentry")!!
        fun activate(def: com.example.sbwhelldiver.stratagem.StratagemDefinition) {
            BeaconInput.accept(player, InteractionHand.MAIN_HAND, id, -2)
            def.code.forEach { BeaconInput.accept(player, InteractionHand.MAIN_HAND, id, it) }
            helper.assertTrue(BeaconData.selected(stack) == def, "Failed to select ${def.id}")
        }
        activate(machine)
        helper.assertTrue(!item.use(helper.level, player, InteractionHand.MAIN_HAND).result.consumesAction(), "Right click still throws")
        helper.assertTrue(BeaconData.selected(stack) == machine, "Ordinary right click cleared the selected card")
        BeaconInput.accept(player, InteractionHand.MAIN_HAND, UUID.randomUUID(), -2)
        helper.assertTrue(BeaconData.selected(stack) == machine, "Forged reselect cleared the beacon")
        activate(gatling)
        player.inventory.selected = 1
        BeaconInput.observeHands(player)
        helper.assertTrue(BeaconData.selected(stack) == null, "Switching main hand retained activation")
        helper.assertTrue(!item.throwActivated(player, InteractionHand.MAIN_HAND, id), "Stale hand threw the beacon")
        player.inventory.selected = 0
        activate(machine)
        helper.assertTrue(!item.throwActivated(player, InteractionHand.MAIN_HAND, UUID.randomUUID()), "Forged identity may throw")
        helper.assertTrue(item.throwActivated(player, InteractionHand.MAIN_HAND, id), "Validated left throw failed")
        helper.assertTrue(!item.throwActivated(player, InteractionHand.MAIN_HAND, id), "Repeated left packet duplicated a throw")
        helper.assertTrue(stack.count == 1 && BeaconData.selected(stack) == null && BeaconData.loadout(stack).filterNotNull().size == 4,
            "Throw lost the reusable beacon or its physical cards")
        helper.level.getEntitiesOfClass(com.example.sbwhelldiver.entity.StratagemBeacon::class.java, player.boundingBox.inflate(4.0)).forEach { it.discard() }
        // A main-hand switch must also cancel a charged beacon carried in the off hand.
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack(Items.STICK))
        player.setItemInHand(InteractionHand.OFF_HAND, stack)
        BeaconInput.observeHands(player)
        BeaconData.activate(stack, machine)
        player.inventory.selected = 2
        BeaconInput.observeHands(player)
        helper.assertTrue(BeaconData.selected(stack) == null, "Main-hand switch retained off-hand activation")
        BeaconInput.logout(player.uuid)
        helper.succeed()
    }
    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 180)
    fun countdownAndPodShareArrivalDeadline(helper: GameTestHelper) {
        for (x in 0..30) for (z in 0..30) helper.setBlock(BlockPos(x, 128, z), Blocks.BEDROCK)
        val p = helper.absolutePos(BlockPos(15, 129, 15))
        var marker = ModEntities.STRATAGEM_MARKER.get().create(helper.level)!!
        val owner = UUID.randomUUID()
        marker.moveTo(p.x + 0.5, p.y + 0.015, p.z + 0.5)
        marker.configure(Stratagems.get("machine_gun_sentry")!!.dropId, 80, owner, Direction.UP, p.y.toDouble())
        helper.level.addFreshEntity(marker)
        val deadline = helper.level.gameTime + 100
        var checkedSave = false
        var checkedWaitingSave = false
        var restoredPod = false
        var currentPod: SentryHellpodEntity? = null
        var lastFallY = Double.NaN
        helper.onEachTick {
            val pods = helper.level.getEntitiesOfClass(SentryHellpodEntity::class.java, net.minecraft.world.phys.AABB(p).inflate(4.0, 1400.0, 4.0))
                .filter { it.ownerUUID == owner }
            if (currentPod != null) helper.assertTrue(pods.size == 1, "Countdown spawned no pod or duplicate pods")
            if (pods.isNotEmpty()) currentPod = pods.single()
            if (!checkedWaitingSave && marker.elapsedTicks >= 3) {
                helper.assertTrue(currentPod == null && marker.podUUID == null, "Pod spawned too early and would drift through the countdown")
                val saved = CompoundTag(); marker.saveWithoutId(saved)
                marker.discard(); marker = ModEntities.STRATAGEM_MARKER.get().create(helper.level)!!
                marker.load(saved); helper.level.addFreshEntity(marker)
                checkedWaitingSave = true
            }
            if (!checkedSave && marker.elapsedTicks >= 60 && currentPod != null) {
                val remaining = marker.ticksLeft
                val saved = CompoundTag(); marker.saveWithoutId(saved)
                marker.discard()
                marker = ModEntities.STRATAGEM_MARKER.get().create(helper.level)!!
                marker.load(saved); helper.level.addFreshEntity(marker)
                helper.assertTrue(marker.podUUID == currentPod!!.uuid && marker.ticksLeft == remaining, "Marker save lost the spawned pod / clock")
                checkedSave = true
            }
            if (!restoredPod && marker.elapsedTicks >= 65 && currentPod != null) {
                val saved = CompoundTag(); currentPod!!.saveWithoutId(saved)
                currentPod!!.discard()
                val restored = ModEntities.MACHINE_GUN_SENTRY.get().create(helper.level)!!
                restored.load(saved); helper.level.addFreshEntity(restored); currentPod = restored
                restoredPod = true
            }
            helper.assertTrue(marker.duration == 80, "Visible four-second countdown was extended")
            if (marker.elapsedTicks in 80..99) {
                helper.assertTrue(marker.ticksLeft == 0 && !marker.isRemoved && currentPod?.phase == Phase.FALLING,
                    "The beam/pod did not remain airborne for one second after zero")
            }
            if (helper.level.gameTime < deadline && currentPod != null) {
                helper.assertTrue(currentPod!!.phase == Phase.FALLING, "Pod impacted before countdown plus one second")
                if (!lastFallY.isNaN()) helper.assertTrue(currentPod!!.y <= lastFallY + 0.001, "Pod rose during its descent")
                lastFallY = currentPod!!.y
            }
            if (helper.level.gameTime > deadline) {
                helper.assertTrue(marker.isRemoved && currentPod!!.phase == Phase.IMPACT, "Five-second arrival deadline did not match actual impact")
                val saved = CompoundTag(); currentPod!!.saveWithoutId(saved)
                helper.assertTrue(saved.getLong("ScheduledArrival") == deadline, "Saved descent lost its exact deadline")
                helper.assertTrue(saved.getLong("ScheduledArrival") - saved.getLong("ScheduledLaunch") == 95L, "Airborne descent did not last exactly 4.75 seconds")
                helper.assertTrue(saved.getDouble("ScheduledStartY") - p.y >= 720.0, "Spawn height did not reach the requested fast descent minimum")
                helper.assertTrue(kotlin.math.abs(currentPod!!.y - saved.getDouble("HellpodRestY")) < 0.001, "Scheduled pod missed its ground position")
                helper.assertTrue(lastFallY - currentPod!!.y >= 0.75, "Pod slowed to a soft landing instead of retaining impact speed")
                helper.assertTrue(checkedWaitingSave && checkedSave && restoredPod && currentPod!!.ownerUUID == owner, "Save/load lost call ownership")
                helper.assertBlockPresent(Blocks.BEDROCK, BlockPos(15, 128, 15))
                currentPod!!.discard(); helper.succeed()
            }
        }
    }

    private fun beaconFloor(helper: GameTestHelper) {
        for (x in 0..30) for (z in 0..30) helper.setBlock(BlockPos(x, 128, z), Blocks.BEDROCK)
    }
    private fun testBeacon(helper: GameTestHelper, x: Double, y: Double, z: Double, velocity: Vec3,
                           owner: ServerPlayer = FakePlayerFactory.get(helper.level, GameProfile(UUID.randomUUID(), "BeaconBounce"))): StratagemBeacon {
        val origin = helper.absolutePos(BlockPos(0, 0, 0))
        val ball = StratagemBeacon(ModEntities.STRATAGEM_BEACON.get(), owner, helper.level)
        ball.setPos(origin.x + x, origin.y + y, origin.z + z)
        val stack = ItemStack(ModItems.BEACON.get())
        BeaconData.setItems(stack, listOf(ItemStack(ModItems.MACHINE_GUN_SENTRY.get())))
        BeaconData.activate(stack, Stratagems.get("machine_gun_sentry")!!)
        ball.setItem(stack)
        ball.deltaMovement = velocity
        helper.level.addFreshEntity(ball)
        return ball
    }
    private fun markersNear(helper: GameTestHelper, pos: BlockPos) = helper.level.getEntitiesOfClass(
        StratagemMarker::class.java, AABB(helper.absolutePos(pos)).inflate(2.0))

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 15)
    fun beaconOnlyDeploysOnTopAndBouncesOffSidesAndCeilings(helper: GameTestHelper) {
        beaconFloor(helper)
        for (y in 129..135) helper.setBlock(BlockPos(10, y, 10), Blocks.BEDROCK)
        for (x in 18..22) for (z in 12..16) helper.setBlock(BlockPos(x, 132, z), Blocks.BEDROCK)
        val top = testBeacon(helper, 6.5, 129.8, 6.5, Vec3(0.0, -1.0, 0.0))
        val side = testBeacon(helper, 9.5, 130.5, 10.5, Vec3(0.8, 0.0, 0.0))
        val ceiling = testBeacon(helper, 20.5, 131.3, 14.5, Vec3(0.0, 1.0, 0.0))
        helper.runAfterDelay(2) {
            helper.assertTrue(top.isRemoved && markersNear(helper, BlockPos(6, 129, 6)).size == 1, "Exposed upper face did not deploy exactly once")
            helper.assertTrue(!side.isRemoved && side.deltaMovement.x < 0.0, "Side collision did not reflect the beacon")
            helper.assertTrue(side.x < helper.absolutePos(BlockPos(10, 0, 0)).x - 0.125, "Reflected beacon penetrated the wall")
            helper.assertTrue(!ceiling.isRemoved && ceiling.deltaMovement.y < 0.0, "Underside collision did not bounce downward")
            helper.assertTrue(markersNear(helper, BlockPos(10, 130, 10)).isEmpty() && markersNear(helper, BlockPos(20, 131, 14)).isEmpty(),
                "A side or underside collision called a pod")
            side.discard(); ceiling.discard()
            markersNear(helper, BlockPos(6, 129, 6)).forEach { it.discard() }
            helper.succeed()
        }
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 40)
    fun beaconChecksAllNineColumnsIncludingTransparentCornerRoof(helper: GameTestHelper) {
        beaconFloor(helper)
        // The central column is open. A glass roof in one corner must still reject deployment.
        helper.setBlock(BlockPos(19, 134, 19), Blocks.GLASS)
        val ball = testBeacon(helper, 20.5, 129.3, 20.5, Vec3(0.0, -0.4, 0.0))
        helper.runAfterDelay(8) {
            helper.assertTrue(!ball.isRemoved && !ball.deploymentFailed, "Beacon deployed with a roof over a corner of its 3x3 area")
            helper.assertTrue(markersNear(helper, BlockPos(20, 129, 20)).isEmpty(), "Covered beacon spawned a marker")
            helper.setBlock(BlockPos(19, 134, 19), Blocks.AIR)
        }
        helper.runAfterDelay(30) {
            val markers = markersNear(helper, BlockPos(20, 129, 20))
            helper.assertTrue(ball.isRemoved && markers.size == 1 && markers.single().face == Direction.UP,
                "Beacon did not retry and deploy after all nine columns became exposed")
            markers.forEach { it.discard() }
            helper.succeed()
        }
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 100)
    fun beaconRollsFromCoveredGroundIntoAnExposedLanding(helper: GameTestHelper) {
        beaconFloor(helper)
        for (x in 5..12) for (z in 5..12) helper.setBlock(BlockPos(x, 134, z), Blocks.BEDROCK)
        val ball = testBeacon(helper, 10.5, 129.2, 9.5, Vec3(0.45, -0.35, 0.0))
        val origin = helper.absolutePos(BlockPos(0, 0, 0))
        var sawRejectedGround = false
        helper.onEachTick {
            if (!ball.isRemoved && ball.x < origin.x + 14) sawRejectedGround = true
            val markers = helper.level.getEntitiesOfClass(StratagemMarker::class.java,
                AABB(helper.absolutePos(BlockPos(18, 129, 9))).inflate(10.0, 2.0, 2.0))
            if (markers.isNotEmpty()) {
                helper.assertTrue(sawRejectedGround && ball.isRemoved && !ball.deploymentFailed, "Covered beacon failed instead of rolling into the open")
                helper.assertTrue(markers.size == 1 && markers.single().x >= origin.x + 14 &&
                    kotlin.math.abs(markers.single().y - (origin.y + 129.015)) < 0.001,
                    "Beacon deployed before the entire 3x3 area cleared the roof or on the wrong surface")
                markers.forEach { it.discard() }
                helper.succeed()
            }
        }
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun beaconUpperHemisphereHitsRaisedWall(helper: GameTestHelper) {
        beaconFloor(helper)
        helper.setBlock(BlockPos(10, 130, 10), Blocks.BEDROCK)
        // The bottom ray runs BELOW the raised block; the upper hemisphere still contacts its side.
        val ball = testBeacon(helper, 9.45, 129.90, 10.5, Vec3(1.0, 0.0, 0.0))
        helper.runAfterDelay(1) {
            helper.assertTrue(!ball.isRemoved && ball.deltaMovement.x < 0, "Upper hemisphere passed through a raised wall")
            val wallX = helper.absolutePos(BlockPos(10, 130, 10)).x
            helper.assertTrue(ball.x + ball.ballRadius <= wallX + 0.001, "Whole sphere penetrated the wall")
            helper.assertTrue(kotlin.math.abs(ball.bbWidth - 0.32f) < 1e-6, "Collider does not match the rendered ball")
            ball.discard(); helper.succeed()
        }
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun beaconTopOfSphereHitsLowCeiling(helper: GameTestHelper) {
        beaconFloor(helper)
        for (x in 18..22) for (z in 12..16) helper.setBlock(BlockPos(x, 132, z), Blocks.BEDROCK)
        val ball = testBeacon(helper, 20.5, 131.5, 14.5, Vec3(0.0, 0.4, 0.0))
        helper.runAfterDelay(1) {
            helper.assertTrue(!ball.isRemoved && ball.deltaMovement.y < 0, "Ball only collided after its bottom reached the ceiling")
            helper.assertTrue(ball.boundingBox.maxY <= helper.absolutePos(BlockPos(20, 132, 14)).y + 0.001,
                "The upper half of the ball penetrated the ceiling")
            helper.assertTrue(markersNear(helper, BlockPos(20, 131, 14)).isEmpty(), "Ceiling contact deployed a beacon")
            ball.discard(); helper.succeed()
        }
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun beaconSphereHasRoundedCornersAndCannotTunnel(helper: GameTestHelper) {
        val box = AABB(10.0, 131.0, 11.0, 11.0, 132.0, 12.0)
        val movement = Vec3(12.0, 0.0, 0.0)
        val collision = com.example.sbwhelldiver.entity.BeaconCollision
        helper.assertTrue(collision.sweep(Vec3(9.0, 130.88, 10.88), movement, 0.16, box) == null,
            "A sphere-free corner was blocked by an inflated cube")
        val rounded = collision.sweep(Vec3(9.0, 130.92, 10.92), movement, 0.16, box)
        helper.assertTrue(rounded != null && rounded.normal.x < 0 && rounded.normal.y < 0 && rounded.normal.z < 0,
            "Fast sphere did not contact the rounded voxel corner")
        helper.assertTrue(kotlin.math.abs(rounded!!.center.distanceTo(rounded.point) - 0.16) < 1e-7,
            "Contact did not lie on the sphere surface")
        helper.assertTrue(collision.sweep(Vec3(9.0, 131.5, 11.5), movement, 0.16, box)!!.fraction < 0.1,
            "High-speed throw skipped a whole block")
        helper.succeed()
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 10)
    fun deploymentFailurePacketKeepsFourSlotPositions(helper: GameTestHelper) {
        val message = com.example.sbwhelldiver.network.BeaconFailurePacket("rocket_sentry",
            listOf("machine_gun_sentry", "", "rocket_sentry", "autocannon_sentry"))
        val buffer = net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer())
        try {
            message.encode(buffer)
            val decoded = com.example.sbwhelldiver.network.BeaconFailurePacket.decode(buffer)
            helper.assertTrue(decoded == message && decoded.loadoutIds[1].isEmpty(),
                "Failure packet moved a card or omitted its empty slot")
            helper.assertTrue(buffer.readableBytes() == 0, "Failure packet left unread data")
        } finally { buffer.release() }
        helper.succeed()
    }

    @JvmStatic @GameTest(template = "sentry_test", timeoutTicks = 140)
    fun beaconFailureUsesFirstBounceDeadlineAcrossSaveAndRepeatedHits(helper: GameTestHelper) {
        beaconFloor(helper)
        for (x in 9..13) for (z in 9..13) helper.setBlock(BlockPos(x, 132, z), Blocks.BEDROCK)
        val messages = mutableListOf<Component>()
        val player = object : FakePlayer(helper.level, GameProfile(UUID.randomUUID(), "BeaconFailure")) {
            override fun displayClientMessage(message: Component, actionBar: Boolean) {
                helper.assertTrue(actionBar, "Deployment failure must be shown to the thrower")
                messages.add(message)
            }
        }
        var ball = testBeacon(helper, 11.5, 129.3, 11.5, Vec3(0.0, -0.4, 0.0), player)
        var firstBounce = -1L
        var restored = false
        var observedFailure = false
        helper.onEachTick {
            val saved = CompoundTag(); ball.saveWithoutId(saved)
            val timestamp = saved.getLong("BeaconFirstBounceAt")
            if (timestamp >= 0 && firstBounce < 0) firstBounce = timestamp
            if (firstBounce >= 0) {
                helper.assertTrue(timestamp == firstBounce, "Subsequent ground bounces reset the retry timer")
                val age = helper.level.gameTime - firstBounce
                if (!restored && age >= 40) {
                    ball.discard()
                    ball = ModEntities.STRATAGEM_BEACON.get().create(helper.level)!!
                    ball.load(saved); ball.setOwner(player); helper.level.addFreshEntity(ball)
                    restored = true
                }
                if (age < 100) helper.assertTrue(!ball.deploymentFailed && messages.isEmpty(), "Deployment failed before five seconds after first bounce")
                if (age >= 100 && !observedFailure) {
                    helper.assertTrue(age == 100L && ball.deploymentFailed && restored, "Save/load changed the exact five-second retry deadline")
                    helper.assertTrue(messages.isEmpty(), "Deployment failure was duplicated in the centre/action bar")
                    helper.assertTrue(ball.deltaMovement == Vec3.ZERO && markersNear(helper, BlockPos(11, 129, 11)).isEmpty(),
                        "Failed beacon continued deploying or moving")
                    observedFailure = true
                }
                if (age >= 121) {
                    helper.assertTrue(ball.isRemoved && observedFailure && messages.isEmpty(), "Failed beacon lingered forever or added a centre message")
                    helper.succeed()
                }
            }
        }
    }

}
