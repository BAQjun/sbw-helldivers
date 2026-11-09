package com.example.sbwhelldiver.client.ponder

import com.atsuishio.superbwarfare.init.ModItems as SbwItems
import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.client.ArrowGlyph
import com.example.sbwhelldiver.init.ModEntities
import com.example.sbwhelldiver.init.ModItems
import com.example.sbwhelldiver.survival.EquipmentDeploymentBeaconEntity as Beacon
import com.example.sbwhelldiver.survival.MachineContainerEntity as Container
import com.example.sbwhelldiver.survival.MachineDeployment
import com.example.sbwhelldiver.survival.MachineLayout
import com.example.sbwhelldiver.survival.MachineLayout.Port
import com.example.sbwhelldiver.survival.SampleMachineBlockEntity
import com.example.sbwhelldiver.survival.TacticalTerminalBlock
import com.example.sbwhelldiver.survival.TacticalTerminalHold
import com.example.sbwhelldiver.survival.TacticalTerminalOffers
import net.createmod.catnip.math.Pointing
import net.createmod.ponder.api.PonderPalette
import net.createmod.ponder.api.element.PonderOverlayElement
import net.createmod.ponder.api.registration.PonderPlugin
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper
import net.createmod.ponder.api.scene.SceneBuilder
import net.createmod.ponder.api.scene.SceneBuildingUtil
import net.createmod.ponder.foundation.PonderIndex
import net.createmod.ponder.foundation.PonderScene
import net.createmod.ponder.foundation.ui.PonderUI
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.phys.Vec3
import java.util.function.Consumer

/** Kept separate from client setup so the mod can load without Ponder. */
object HelldiverPonder : PonderPlugin {
    private val root = BlockPos(4, 1, 4)
    private val facing = Direction.NORTH
    private val center = Vec3.atBottomCenterOf(root)

    fun register() = PonderIndex.addPlugin(this)

    override fun getModId() = MOD_ID

    override fun registerScenes(helper: PonderSceneRegistrationHelper<ResourceLocation>) {
        for (machine in MachineDeployment.entries) {
            val scene = { s: SceneBuilder, u: SceneBuildingUtil -> machineScene(s, u, machine) }
            helper.addStoryBoard(id("stratagem_beacon"), "survival/${machine.id}", scene)
            helper.addStoryBoard(id(machine.id + "_container"), "survival/${machine.id}", scene)
        }
        helper.addStoryBoard(id("tactical_terminal"), "survival/tactical_terminal", ::tacticalScene)
        helper.addStoryBoard(id("sample_container"), "survival/sample_container", ::sampleScene)
    }

    private fun sampleScene(scene: SceneBuilder, util: SceneBuildingUtil) {
        val world = scene.world()
        val bag = ItemStack(ModItems.SAMPLE_CONTAINER.get())
        val bagPos = Vec3(2.5, 2.0, 2.5)
        val machinePos = BlockPos(3, 1, 5)
        val terminalPos = BlockPos(6, 1, 4)
        scene.title("sample_container", Component.translatable("item.sbw_helldiver.sample_container").string)
        scene.configureBasePlate(0, 0, 9)
        scene.scaleSceneView(.8f)
        scene.showBasePlate()
        val displayed = world.createItemEntity(bagPos, Vec3.ZERO, bag)
        world.modifyEntity(displayed) { it.isNoGravity = true; place(it, bagPos) }
        scene.idle(15)

        caption(scene, bagPos, 100)
        scene.overlay().showControls(bagPos, Pointing.DOWN, 80).rightClick().withItem(bag)
        card(scene, 100) { g, x, y, age ->
            text(g, "sample_storage", x + 6, y + 3)
            for (i in 0..2) {
                val top = y + 13 + i * 14
                g.pose().pushPose(); g.pose().translate((x + 6).toFloat(), top.toFloat(), 0f)
                g.pose().scale(.75f, .75f, 1f)
                stack(g, ItemStack(when (i) { 0 -> ModItems.COMMON_SAMPLE.get(); 1 -> ModItems.RARE_SAMPLE.get(); else -> ModItems.SUPER_SAMPLE.get() }), 0, 0)
                g.pose().popPose()
                val amount = if (i == 0) { if (age < 35) 0 else if (age < 70) 64 else 63 } else 0
                label(g, Component.literal(amount.toString()), x + 22, top + 2)
                for ((j, key) in listOf("sample_deposit", "sample_take_one", "sample_take_stack").withIndex()) {
                    val left = x + 54 + j * 39
                    val active = i == 0 && ((j == 0 && age in 25f..40f) || (j == 1 && age in 60f..75f))
                    g.fill(left, top, left + 37, top + 12, if (active) 0xff927b29.toInt() else 0xff28414c.toInt())
                    text(g, key, left + 2, top + 2)
                }
            }
        }
        scene.idle(100)

        caption(scene, bagPos, 95)
        scene.overlay().showControls(bagPos, Pointing.DOWN, 65).rightClick().whileSneaking().withItem(bag)
        card(scene, 95) { g, x, y, age ->
            text(g, "sample_auto", x + 8, y + 8)
            stack(g, ItemStack(ModItems.COMMON_SAMPLE.get(), 5), x + 10, y + 28)
            label(g, Component.literal("→"), x + 42, y + 32)
            stack(g, bag, x + 62, y + 26)
            label(g, Component.literal(if (age < 45) "63" else "68"), x + 88, y + 32)
        }
        moveItem(scene, ItemStack(ModItems.COMMON_SAMPLE.get(), 5), BlockPos(1, 1, 2), BlockPos(2, 1, 2))
        scene.idle(74)

        val machine = util.select().position(machinePos)
        world.showSection(machine, Direction.DOWN)
        caption(scene, Vec3.atCenterOf(machinePos), 100)
        scene.overlay().showControls(Vec3.atCenterOf(machinePos).add(0.0, .5, -1.0), Pointing.DOWN, 80)
            .rightClick().whileSneaking().withItem(bag)
        scene.overlay().showOutline(PonderPalette.GREEN, "sample_binding", machine, 100)
        card(scene, 100) { g, x, y, age ->
            icon(g, MachineDeployment.BIOPROCESSOR, x + 8, y + 8)
            icon(g, MachineDeployment.REFINERY, x + 36, y + 8)
            label(g, Component.literal("→"), x + 70, y + 15)
            stack(g, bag, x + 92, y + 10)
            text(g, "sample_bound", x + 8, y + 39)
        }
        scene.idle(100)

        caption(scene, bagPos, 110)
        for (tick in 0..70) {
            world.modifyBlockEntityNBT(machine, SampleMachineBlockEntity::class.java) {
                it.putBoolean("Working", tick < 70); it.putLong("WorkTicks", tick.toLong())
            }
            scene.idle(1)
        }
        moveItem(scene, ItemStack(ModItems.COMMON_SAMPLE.get(), 5), machinePos, BlockPos(2, 1, 2))
        scene.idle(19)
        world.hideSection(machine, Direction.DOWN)

        for (part in 0..3) world.showSection(util.select().position(TacticalTerminalBlock.position(terminalPos, facing, part)), Direction.DOWN)
        caption(scene, Vec3.atCenterOf(terminalPos), 125)
        scene.overlay().showControls(Vec3.atCenterOf(terminalPos).add(.5, .5, -.5), Pointing.DOWN, 70).rightClick()
        card(scene, 125) { g, x, y, age ->
            val elapsed = (age - 30).toDouble()
            val progress = TacticalTerminalHold.maskProgress(elapsed)
            val done = elapsed >= TacticalTerminalHold.DURATION_TICKS
            g.fill(x + 5, y + 5, x + 169, y + 28, 0xff284654.toInt())
            g.fill(x + 5, y + 5, x + 5 + (164 * progress).toInt(), y + 28, 0xfff2f0dd.toInt())
            text(g, if (done) "purchase_complete" else "destroyer_refill", x + 10, y + 12,
                color = if (progress > .85) 0xff17262c.toInt() else 0xfff2f0dd.toInt())
            stack(g, bag, x + 8, y + 34)
            text(g, "sample_terminal", x + 32, y + 39)
        }
        scene.idle(125)
        scene.markAsFinished()
    }

    private fun machineScene(scene: SceneBuilder, util: SceneBuildingUtil, machine: MachineDeployment) {
        val world = scene.world()
        scene.title(machine.id + "_deployment", Component.translatable(machine.translation).string)
        scene.configureBasePlate(0, 0, 9)
        scene.scaleSceneView(if (machine == MachineDeployment.REFINERY) .61f else .75f)
        scene.showBasePlate()
        scene.idle(12)

        scene.overlay().showControls(center.add(0.0, .8, 0.0), Pointing.DOWN, 62)
            .rightClick().withItem(ItemStack(ModItems.BEACON.get()))
        caption(scene, center, 65)
        card(scene, 65) { g, x, y, age ->
            text(g, "call_command", x + 10, y + 9)
            drawArrows(g, MachineDeployment.callCode, x + 10, y + 23, age)
        }
        scene.idle(65)

        val beacon = world.createEntity { level ->
            checkNotNull(ModEntities.EQUIPMENT_BEACON.get().create(level)).also {
                place(it, center.add(0.0, 7.0, 0.0))
            }
        }
        scene.addKeyframe()
        for (tick in 1..20) {
            world.modifyEntity(beacon) { place(it, center.add(0.0, descent(tick, 20), 0.0)) }
            scene.idle(1)
        }
        for (tick in 0..43) {
            world.modifyEntity(beacon) {
                editEntity(it) {
                    putInt("Phase", Beacon.DEPLOYING)
                    putInt("Machine", -1)
                    putLong("Start", it.level().gameTime - tick)
                    putDouble("Ground", center.y)
                }
            }
            scene.idle(1)
        }
        world.modifyEntity(beacon) {
            editEntity(it) {
                putInt("Phase", Beacon.ACTIVE)
                putInt("Machine", -1)
                putLong("Start", it.level().gameTime)
                putDouble("Ground", center.y)
            }
        }
        caption(scene, center.add(0.0, 2.0, 0.0), 63)
        scene.overlay().showControls(center.add(0.0, 1.6, .65), Pointing.DOWN, 43).rightClick()
        scene.idle(48)
        card(scene, 89) { g, x, y, age ->
            for ((row, device) in MachineDeployment.entries.withIndex()) {
                val top = y + 5 + row * 26
                if (device == machine && age >= 13) g.fill(x + 4, top, x + 170, top + 25, 0xff234450.toInt())
                icon(g, device, x + 7, top + 2)
                label(g, Component.translatable(device.translation), x + 32, top + 3)
                drawArrows(g, device.code, x + 32, top + 13, if (device == machine) age - 18 else -1f)
            }
        }
        scene.idle(89)

        caption(scene, center, 88)
        card(scene, 111) { g, x, y, age ->
            icon(g, machine, x + 6, y + 5)
            label(g, Component.translatable(machine.translation), x + 32, y + 10)
            for ((i, material) in machine.materials.withIndex()) {
                stack(g, ItemStack(material.item, material.count), x + 8 + i * 26, y + 24)
            }
            drawArrows(g, List(6) { 1 }, x + 8, y + 44, age - 28)
        }
        scene.idle(111)

        caption(scene, center, 78)
        card(scene, 78) { g, x, y, age ->
            icon(g, machine, x + 8, y + 9)
            text(g, "arrival_time", x + 36, y + 16, (10 - age.toInt() * 10 / 78).coerceAtLeast(1))
        }
        scene.idle(54)
        for (tick in 0..23) {
            world.modifyEntity(beacon) {
                editEntity(it) {
                    putInt("Phase", Beacon.RETRACTING)
                    putInt("Machine", machine.ordinal)
                    putLong("Start", it.level().gameTime - (tick * 60L / 23))
                    putDouble("Ground", center.y)
                }
            }
            scene.idle(1)
        }
        world.modifyEntity(beacon, Entity::discard)
        val container = world.createEntity { level ->
            checkNotNull(ModEntities.MACHINE_CONTAINER.get().create(level)).also {
                it.configure(machine, root, facing, CompoundTag(), false, level.gameTime)
                place(it, center.add(0.0, 7.0, 0.0))
                it.sampleClientFlight()
            }
        }
        for (tick in 1..24) {
            world.modifyEntity(container) {
                place(it, center.add(0.0, descent(tick, 24), 0.0))
                (it as Container).sampleClientFlight()
            }
            scene.idle(1)
        }

        val crowbar = ItemStack(SbwItems.CROWBAR.get())
        caption(scene, center.add(0.0, 1.0, 0.0), 55)
        scene.overlay().showControls(center.add(0.0, 1.25, 0.0), Pointing.DOWN, 55)
            .rightClick().withItem(crowbar)
        scene.idle(55)

        val selection = util.select().position(root)
        world.showSection(selection, Direction.DOWN)
        for (tick in 0 until Container.OPENING_TICKS) {
            world.modifyEntity(container) {
                editEntity(it) {
                    putInt("Kind", machine.ordinal)
                    putInt("Facing", facing.get2DDataValue())
                    putInt("Phase", Container.OPENING)
                    putLong("Root", root.asLong())
                    putLong("Start", it.level().gameTime - tick)
                }
            }
            scene.idle(1)
        }
        world.modifyEntity(container, Entity::discard)

        val input = port(machine, Port.INPUT)
        val output = port(machine, Port.OUTPUT)
        val energy = port(machine, Port.ENERGY)
        val raw = ItemStack(if (machine == MachineDeployment.BIOPROCESSOR) Items.ROTTEN_FLESH else Items.RAW_IRON, 5)
        val common = ItemStack(ModItems.COMMON_SAMPLE.get(), if (machine == MachineDeployment.BIOPROCESSOR) 5 else 6)
        val supply = input.relative(facing)
        val receiver = output.relative(facing)
        scene.overlay().showOutline(PonderPalette.GREEN, Port.ENERGY, util.select().position(energy), 65)
        caption(scene, Vec3.atCenterOf(energy), 65)
        scene.idle(65)
        world.showSection(util.select().position(supply), Direction.DOWN)
        scene.overlay().showOutline(PonderPalette.INPUT, Port.INPUT, util.select().position(input), 61)
        caption(scene, Vec3.atCenterOf(input), 61)
        scene.idle(38)
        moveItem(scene, raw, supply, input)
        caption(scene, center, 100)
        for (tick in 0..100) {
            world.modifyBlockEntityNBT(selection, SampleMachineBlockEntity::class.java) {
                it.putLong("WorkTicks", tick.toLong())
                it.putBoolean("Working", tick < 100)
            }
            scene.idle(1)
        }
        world.hideSection(util.select().position(supply), Direction.UP)
        world.showSection(util.select().position(receiver), Direction.DOWN)
        scene.overlay().showOutline(PonderPalette.OUTPUT, Port.OUTPUT, util.select().position(output), 65)
        caption(scene, Vec3.atCenterOf(output), 65)
        moveItem(scene, common, output, receiver)
        scene.idle(43)

        caption(scene, center, 70)
        scene.overlay().showControls(center.add(0.0, 1.0, 1.0), Pointing.DOWN, 55)
            .rightClick().whileSneaking().withItem(crowbar)
        scene.idle(44)
        world.hideSection(selection, Direction.DOWN)
        val packed = if (machine == MachineDeployment.BIOPROCESSOR) ModItems.BIOPROCESSOR_CONTAINER else ModItems.REFINERY_CONTAINER
        world.createItemEntity(center.add(0.0, .25, 0.0), Vec3.ZERO, ItemStack(packed.get()))
        scene.idle(26)
        scene.markAsFinished()
    }

    private fun tacticalScene(scene: SceneBuilder, util: SceneBuildingUtil) {
        val world = scene.world()
        scene.title("tactical_terminal", localized("tactical_title").string)
        scene.configureBasePlate(0, 0, 9)
        scene.showBasePlate()
        for (part in 0..3) {
            val pos = TacticalTerminalBlock.position(root, facing, part)
            world.showSection(util.select().position(pos), Direction.DOWN)
        }
        scene.idle(20)
        caption(scene, center, 63)
        scene.overlay().showControls(center.add(.5, 1.25, -.45), Pointing.DOWN, 55).rightClick()
        scene.idle(63)

        val common = ModItems.COMMON_SAMPLE.get()
        val rare = ModItems.RARE_SAMPLE.get()
        val cost = TacticalTerminalOffers.destroyerCall
        caption(scene, center, 88)
        card(scene, 112) { g, x, y, age ->
            text(g, "tactical_title", x + 8, y + 6)
            for ((i, name) in listOf("orbital", "eagle", "sentry", "defense").withIndex()) {
                val col = i % 2
                val row = i / 2
                val left = x + 6 + col * 82
                val top = y + 20 + row * 17
                g.fill(left, top, left + 78, top + 15, if (i == ((age / 15).toInt() % 4)) 0xff326075.toInt() else 0xff182e39.toInt())
                text(g, name, left + 4, top + 4)
            }
        }
        scene.idle(112)
        caption(scene, center, 82)
        card(scene, 108) { g, x, y, age ->
            val elapsed = age.toDouble() - 25
            val progress = TacticalTerminalHold.maskProgress(elapsed)
            val done = elapsed >= TacticalTerminalHold.DURATION_TICKS
            g.fill(x + 5, y + 5, x + 169, y + 29, 0xff284654.toInt())
            g.fill(x + 5, y + 5, x + 5 + (164 * progress).toInt(), y + 29, 0xfff2f0dd.toInt())
            text(g, if (done) "purchase_complete" else "destroyer_refill", x + 11, y + 13,
                color = if (progress > .85) 0xff17262c.toInt() else 0xfff2f0dd.toInt())
            stack(g, ItemStack(common, cost.common), x + 10, y + 35)
            stack(g, ItemStack(rare, cost.rare), x + 36, y + 35)
        }
        scene.idle(108)
        scene.markAsFinished()
    }

    private fun moveItem(scene: SceneBuilder, stack: ItemStack, from: BlockPos, to: BlockPos) {
        val start = Vec3.atCenterOf(from).add(0.0, .65, 0.0)
        val end = Vec3.atCenterOf(to).add(0.0, .35, 0.0)
        val link = scene.world().createItemEntity(start, Vec3.ZERO, stack)
        for (tick in 0..20) {
            scene.world().modifyEntity(link) {
                it.isNoGravity = true
                it.deltaMovement = Vec3.ZERO
                place(it, start.lerp(end, tick / 20.0))
            }
            scene.idle(1)
        }
        scene.world().modifyEntity(link, Entity::discard)
    }

    private fun port(machine: MachineDeployment, port: Port) =
        root.offset(MachineLayout.rotate(MachineLayout.portOffset(machine, port), facing))

    private fun editEntity(entity: Entity, change: CompoundTag.() -> Unit) {
        val state = entity.saveWithoutId(CompoundTag())
        entity.load(state.apply(change))
    }

    private fun place(entity: Entity, pos: Vec3) {
        entity.setPos(pos.x, pos.y, pos.z)
        entity.xo = pos.x
        entity.yo = pos.y
        entity.zo = pos.z
    }

    private fun descent(tick: Int, duration: Int): Double {
        val fraction = 1.0 - tick.toDouble() / duration
        return 7.0 * fraction * fraction
    }

    private fun caption(scene: SceneBuilder, pos: Vec3, duration: Int) {
        scene.overlay().showText(duration).text("").pointAt(pos).attachKeyFrame()
    }

    private fun card(scene: SceneBuilder, duration: Int, paint: (GuiGraphics, Int, Int, Float) -> Unit) {
        scene.addInstruction(Consumer { current -> current.addElement(TerminalCard(current.currentTime, duration, paint)) })
    }

    private class TerminalCard(
        private val start: Int,
        private val duration: Int,
        private val paint: (GuiGraphics, Int, Int, Float) -> Unit
    ) : PonderOverlayElement {
        private var shown = true
        override fun isVisible() = shown
        override fun setVisible(visible: Boolean) { shown = visible }

        override fun render(scene: PonderScene, ui: PonderUI, g: GuiGraphics, partial: Float) {
            val age = scene.currentTime - start + partial
            if (age < 0 || age >= duration) return
            val x = 8
            val y = ui.height - 109 + (6 * (1 - age / 6).coerceAtLeast(0f)).toInt()
            g.pose().pushPose()
            g.pose().translate(0f, 0f, 250f)
            g.fill(x, y, x + 174, y + 58, 0xff09151c.toInt())
            g.renderOutline(x, y, 174, 58, 0xff78949a.toInt())
            paint(g, x, y, age)
            g.flush()
            g.pose().popPose()
        }
    }

    private fun drawArrows(g: GuiGraphics, code: List<Int>, x: Int, y: Int, age: Float) {
        for ((i, direction) in code.withIndex()) {
            val color = if (age >= 6 * (i + 1)) 0xffffdf71.toInt() else 0xffaec0c6.toInt()
            ArrowGlyph.forEachPixel(direction) { dx, dy ->
                val left = x + i * 13 + dx
                val top = y + dy
                g.fill(left, top, left + 1, top + 1, color)
            }
        }
    }

    private fun stack(g: GuiGraphics, stack: ItemStack, x: Int, y: Int) {
        g.renderItem(stack, x, y)
        g.renderItemDecorations(Minecraft.getInstance().font, stack, x, y)
    }

    private fun icon(g: GuiGraphics, machine: MachineDeployment, x: Int, y: Int) =
        g.blit(machine.icon, x, y, 20, 20, 0f, 0f, 128, 128, 128, 128)

    private fun text(g: GuiGraphics, key: String, x: Int, y: Int, vararg args: Any, color: Int = 0xfff2f0dd.toInt()) =
        label(g, localized(key, *args), x, y, color)

    private fun label(g: GuiGraphics, value: Component, x: Int, y: Int, color: Int = 0xfff2f0dd.toInt()) {
        g.drawString(Minecraft.getInstance().font, value, x, y, color, false)
    }

    private fun localized(key: String, vararg args: Any) = Component.translatable("tutorial.sbw_helldiver.$key", *args)
    private fun id(path: String) = ResourceLocation(MOD_ID, path)
}
