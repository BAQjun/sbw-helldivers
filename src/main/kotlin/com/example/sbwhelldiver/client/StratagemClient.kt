package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.item.BeaconItem
import com.example.sbwhelldiver.network.BeaconKeyPacket
import com.example.sbwhelldiver.network.BeaconThrowPacket
import com.example.sbwhelldiver.network.BeaconNetwork
import com.example.sbwhelldiver.stratagem.BeaconData
import com.example.sbwhelldiver.stratagem.Stratagems
import com.example.sbwhelldiver.stratagem.StratagemDefinition
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket
import net.minecraft.world.InteractionHand
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.InputEvent
import net.minecraftforge.client.event.MovementInputUpdateEvent
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent
import net.minecraftforge.client.event.RegisterKeyMappingsEvent
import net.minecraftforge.client.settings.IKeyConflictContext
import net.minecraftforge.client.settings.KeyConflictContext
import net.minecraftforge.event.TickEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import org.lwjgl.glfw.GLFW
import java.util.UUID

@Mod.EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object StratagemClient {
    private val onFootContext = object : IKeyConflictContext {
        override fun isActive() = KeyConflictContext.IN_GAME.isActive && Minecraft.getInstance().player?.isPassenger != true
        override fun conflicts(other: IKeyConflictContext) = other === this || other === KeyConflictContext.IN_GAME
    }
    val ALT = KeyMapping("key.sbw_helldiver.stratagem", onFootContext, com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, "key.categories.sbw_helldiver")
    val prefix = mutableListOf<Int>()
    private var identity: UUID? = null
    private var previousHand: InteractionHand? = null
    private var lastInput = 0L
    private var errorUntil = 0L
    private var altWasDown = false
    private var waitForAltRelease = false
    private var previousSlot = -1
    var testHold = false
    private var failureLoadout = List<String>(4) { "" }
    private val failures = mutableMapOf<String, Long>()
    private data class HudContents(val loadout: List<StratagemDefinition?>, val selected: StratagemDefinition?,
                                   val prefix: List<Int>, val failureRows: Set<Int>, val inputError: Boolean)
    private var hudContents: HudContents? = null
    private var hudTransitionAt = 0L
    private var hudTransitionFrom = 0.0
    private var hudTargetVisible = false

    private data class AutomaticRow(var definition:StratagemDefinition,var index:Int,var selected:Boolean,val slide:HudSlide=HudSlide())
    private val automaticRows=linkedMapOf<String,AutomaticRow>()
    private var automaticLoadout = emptyList<StratagemDefinition?>()
    private var automaticSelected: StratagemDefinition? = null
    internal var lastAutomaticRows = emptyMap<String, HudLayout>()
        private set

    private fun observeAutomaticLoadout() {
        val player = Minecraft.getInstance().player ?: return
        val stack = listOf(player.mainHandItem, player.offhandItem).firstOrNull { it.item is BeaconItem }
            ?: (0 until player.inventory.containerSize).asSequence().map(player.inventory::getItem).firstOrNull { it.item is BeaconItem }
        automaticSelected = stack?.let(BeaconData::selected)
        // Keep pending calls visible when the player switches away or drops the beacon.
        if (stack != null) automaticLoadout = StratagemCooldownClient.commands(BeaconData.loadout(stack))
    }

    private fun hudVisibility(nanos: Long): Double {
        val progress = ((nanos - hudTransitionAt) / 180_000_000.0).coerceIn(0.0, 1.0)
        val eased = 1.0 - (1.0 - progress) * (1.0 - progress) * (1.0 - progress)
        val target = if (hudTargetVisible) 1.0 else 0.0
        return hudTransitionFrom + (target - hudTransitionFrom) * eased
    }

    private fun clearHud() {
        hudContents = null; hudTransitionFrom = 0.0; hudTransitionAt = 0L; hudTargetVisible = false
    }
    private var previousLevel: net.minecraft.client.multiplayer.ClientLevel? = null
    internal var lastFailureRows = emptySet<Int>()
        private set

    fun deploymentFailed(stratagemId: String, loadoutIds: List<String>) {
        val mc = Minecraft.getInstance()
        val now = mc.level?.gameTime ?: return
        if (Stratagems.get(stratagemId) == null || loadoutIds.none { it == stratagemId }) return
        failureLoadout = List(4) { loadoutIds.getOrNull(it)?.takeIf { id -> Stratagems.get(id) != null } ?: "" }
        failures[stratagemId] = now + 60 // Three seconds, without changing the held item or input session.
    }
    private fun rawAltDown(): Boolean {
        val mc = Minecraft.getInstance()
        return ALT.isDown || GLFW.glfwGetKey(mc.window.window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS ||
            ((java.lang.Boolean.getBoolean("sbw_helldiver.beaconSmokeTest") || java.lang.Boolean.getBoolean("sbw_helldiver.cooldownSmokeTest")) && testHold)
    }
    private fun beaconBlocked(): Boolean {
        if (Minecraft.getInstance().player?.isPassenger == true) {
            waitForAltRelease = true
            return true
        }
        if (waitForAltRelease) {
            if (rawAltDown()) return true
            waitForAltRelease = false
        }
        return false
    }
    fun held(): InteractionHand? {
        if (beaconBlocked()) return null
        val player = Minecraft.getInstance().player ?: return null
        return InteractionHand.values().firstOrNull { player.getItemInHand(it).item is BeaconItem }
    }
    fun holdingAlt(): Boolean = !beaconBlocked() && rawAltDown()
    @SubscribeEvent fun attack(event: InputEvent.InteractionKeyMappingTriggered) {
        val mc = Minecraft.getInstance()
        if (!event.isAttack || mc.screen != null || held() == null) return
        event.isCanceled = true
        event.setSwingHand(false)
        if (!holdingAlt()) throwSelected()
    }
    fun throwSelected(): Boolean {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return false
        val hand = held() ?: return false
        val stack = player.getItemInHand(hand)
        val token = BeaconData.identity(stack) ?: return false
        if (BeaconData.selected(stack) == null || player.isSpectator || holdingAlt()) return false
        BeaconNetwork.CHANNEL.sendToServer(BeaconThrowPacket(hand, token))
        BeaconData.clear(stack)
        prefix.clear()
        return true
    }
    /** Send the vanilla slot change before any beacon packets, then start a fresh Alt session. */
    private fun updateAlt() {
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (beaconBlocked()) {
            prefix.clear(); identity = null; previousHand = null; errorUntil = 0L
            altWasDown = false; destroyerStage=0;clearHud()
            return
        }
        val down = holdingAlt()
        if (down && !altWasDown && mc.screen == null && player != null && !player.isSpectator) {
            if (player.mainHandItem.item !is BeaconItem) {
                val slot = (0..8).firstOrNull { player.inventory.getItem(it).item is BeaconItem }
                if (slot != null) {
                    player.inventory.selected = slot
                    mc.connection?.send(ServerboundSetCarriedItemPacket(slot))
                    clearInventorySelections()
                    previousSlot = slot
                }
            }
            held()?.let { hand ->
                val stack = player.getItemInHand(hand)
                BeaconData.identity(stack)?.let {
                    BeaconNetwork.CHANNEL.sendToServer(BeaconKeyPacket(hand, it, -2))
                    BeaconData.clear(stack)
                    prefix.clear()
                    identity = it; previousHand = hand
                }
            }
        }
        altWasDown = down
    }

    private fun clearInventorySelections() {
        Minecraft.getInstance().player?.let { player ->
            for (i in 0 until player.inventory.containerSize) {
                val stack = player.inventory.getItem(i)
                if (stack.item is BeaconItem) BeaconData.clear(stack)
            }
        }
        prefix.clear()
    }
    @SubscribeEvent fun key(event: InputEvent.Key) {
        updateAlt()
        if (event.action != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null || !holdingAlt()) return
        val direction = when (event.key) {
            GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_W -> 0
            GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_KEY_D -> 1
            GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_S -> 2
            GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_A -> 3
            else -> return
        }
        input(direction)
    }
    var destroyerStage=0;private set
    fun destroyerMenu(stage:Int){destroyerStage=stage;prefix.clear();lastInput=Minecraft.getInstance().level?.gameTime?:0}
    fun input(direction: Int) {
        updateAlt()
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val hand = held() ?: return
        val stack = player.getItemInHand(hand)
        val token = BeaconData.identity(stack) ?: return
        if (BeaconData.selected(stack) != null) return
        if (identity != token || previousHand != hand) { prefix.clear(); identity = token; previousHand = hand }
        if(destroyerStage!=0){
            lastInput=mc.level?.gameTime?:0
            BeaconNetwork.CHANNEL.sendToServer(BeaconKeyPacket(hand,token,direction))
            return
        }
        prefix.add(direction)
        lastInput = mc.level?.gameTime ?: 0
        if (Stratagems.matching(StratagemCooldownClient.commands(BeaconData.loadout(stack)), prefix).filter { StratagemCooldownClient.available(it.id) }.isEmpty()) {
            prefix.clear(); errorUntil = lastInput + 12
        }
        BeaconNetwork.CHANNEL.sendToServer(BeaconKeyPacket(hand, token, direction))
    }
    @SubscribeEvent fun movement(event: MovementInputUpdateEvent) {
        if (holdingAlt() && held() != null && Minecraft.getInstance().screen == null) {
            event.input.forwardImpulse = 0f; event.input.leftImpulse = 0f
            event.input.up = false; event.input.down = false; event.input.left = false; event.input.right = false
        }
    }
    @SubscribeEvent fun tick(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.END) return
        val mc = Minecraft.getInstance()
        if (mc.level !== previousLevel) {
            failures.clear(); failureLoadout = List(4) { "" }; prefix.clear()
            previousSlot = -1; identity = null; previousHand = null; altWasDown = false; waitForAltRelease = false
            clearHud(); automaticRows.clear(); StratagemNotifications.clear(); automaticLoadout = emptyList(); automaticSelected = null; lastAutomaticRows = emptyMap(); previousLevel = mc.level;destroyerStage=0
        }
        observeAutomaticLoadout()
        StratagemNotifications.tick(automaticLoadout)
        failures.entries.removeIf { it.value <= (mc.level?.gameTime ?: Long.MAX_VALUE) }
        updateAlt()
        val hand = held()
        val slot = mc.player?.inventory?.selected ?: -1
        if (previousSlot >= 0 && slot != previousSlot) clearInventorySelections()
        previousSlot = slot
        val token = hand?.let { mc.player?.getItemInHand(it)?.let(BeaconData::identity) }
        if (!holdingAlt() || mc.screen != null || token != identity || hand != previousHand || (mc.level?.gameTime ?: 0) - lastInput > 100) {
            if (prefix.isNotEmpty() || destroyerStage!=0) {
                if (identity != null && previousHand != null && mc.connection != null) BeaconNetwork.CHANNEL.sendToServer(BeaconKeyPacket(previousHand!!, identity!!, -1))
                prefix.clear();destroyerStage=0
            }
            identity = token; previousHand = hand
        }
    }
    internal data class HudLayout(val x: Float, val y: Float, val width: Float, val height: Float)
    internal var lastHudLayout: HudLayout? = null
        private set
    fun hud(gui: GuiGraphics, partial: Float = 0f) {
        lastHudLayout = null
        lastFailureRows = emptySet()
        lastAutomaticRows = emptyMap()
        val mc = Minecraft.getInstance()
        val hand = held()
        val inputMode = holdingAlt() && hand != null && mc.player?.isSpectator == false
        val now = mc.level?.gameTime ?: Long.MAX_VALUE
        val activeFailures = failures.filterValues { it > now }.keys
        if (mc.screen == null && mc.player != null && !inputMode) drawAutomaticNotices(gui, partial)
        if (mc.screen != null || mc.player == null || beaconBlocked()) {
            clearHud()
            return
        }
        val wanted = inputMode || activeFailures.isNotEmpty()
        val nanos = System.nanoTime()
        if (wanted != hudTargetVisible) {
            // Reopening during an exit continues from the current position, without a jump.
            hudTransitionFrom = hudVisibility(nanos)
            hudTransitionAt = nanos
            hudTargetVisible = wanted
        }
        if (wanted) {
            val stack = if (inputMode) mc.player!!.getItemInHand(hand!!) else null
            val loadout = StratagemCooldownClient.commands(stack?.let(BeaconData::loadout) ?: failureLoadout.map(Stratagems::get))
            hudContents = HudContents(loadout, stack?.let(BeaconData::selected),
                if (inputMode) prefix.toList() else emptyList(),
                loadout.mapIndexedNotNull { index, def -> index.takeIf { def?.id in activeFailures } }.toSet(), now < errorUntil)
        }
        val visibility = hudVisibility(nanos)
        if (!wanted && visibility <= 0.0) { hudContents = null; return }
        // Keep the last displayed rows, arrows and panel height intact until the exit finishes.
        val contents = hudContents ?: return
        val loadout = contents.loadout
        val selected = contents.selected
        val visiblePrefix = contents.prefix
        val candidates = Stratagems.matching(loadout, visiblePrefix).filter { StratagemCooldownClient.available(it.id) }
        lastFailureRows = contents.failureRows
        // Match the reference's screen proportions independently of Minecraft's GUI scale.
        val scale = mc.window.guiScaledHeight / 480f
        val restingX = mc.window.guiScaledWidth * .023f
        val screenX = (restingX - (158 * scale + restingX) * (1.0 - visibility)).toFloat()
        val screenY = mc.window.guiScaledHeight * .028f
        val panelWidth = 158
        val panelX = 0
        val panelY = 0
        val rowHeight = 32
        val panelHeight = 25 + rowHeight * loadout.size + 6 + if (selected != null) 20 else 0
        val textX = 32
        lastHudLayout = HudLayout(screenX, screenY, panelWidth * scale, panelHeight * scale)
        gui.pose().pushPose()
        gui.pose().translate(screenX.toDouble(), screenY.toDouble(), 0.0)
        gui.pose().scale(scale, scale, 1f)
        gui.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0x70101C18)
        drawHudText(gui, Component.translatable("hud.sbw_helldiver.stratagems"), panelX + 8, panelY + 6, 0xEAF0EB)
        var equipmentSlot = 0
        for (i in loadout.indices) {
            val def = loadout[i]
            if (def == null || def in Stratagems.definitions) equipmentSlot++
            val y = panelY + 25 + rowHeight * i
            if (def == null) { drawHudText(gui, Component.translatable("hud.sbw_helldiver.empty_slot", equipmentSlot), textX, y + 6, 0x9DAFA3); continue }
            drawRow(gui, def, y, selected, visiblePrefix, candidates, contents.inputError, i in lastFailureRows, partial)
        }
        if (selected != null) {
            val ready = Component.translatable("hud.sbw_helldiver.ready").string
            drawHudText(gui, Component.literal(mc.font.plainSubstrByWidth(ready, ((panelWidth - 16) / 1.5f).toInt())), panelX + 8, panelY + 25 + rowHeight * loadout.size, 0x83E4FF)
        }
        gui.pose().popPose()
    }

    private fun drawAutomaticNotices(gui: GuiGraphics, partial: Float) {
        val mc = Minecraft.getInstance()
        val scale = mc.window.guiScaledHeight / 480f
        val x = mc.window.guiScaledWidth * .023f
        val y = mc.window.guiScaledHeight * .028f
        val rows = linkedMapOf<String, HudLayout>()
        val nanos=System.nanoTime()
        val wanted=mutableSetOf<String>()
        for((index,def) in automaticLoadout.withIndex()) {
            if(def==null || def!=automaticSelected && !StratagemNotifications.visible(def,partial))continue
            wanted.add(def.id)
            val row=automaticRows.getOrPut(def.id){AutomaticRow(def,index,def==automaticSelected)}
            row.definition=def;row.index=index;row.selected=def==automaticSelected
            row.slide.target(true,nanos)
        }
        val iterator=automaticRows.iterator()
        while(iterator.hasNext()) {
            val (id,row)=iterator.next()
            if(id !in wanted)row.slide.target(false,nanos)
            val visibility=row.slide.value(nanos)
            if(visibility<=0.0 && id !in wanted){iterator.remove();continue}
            val rowY=25+32*row.index
            val screenX=(x-(158*scale+x)*(1.0-visibility)).toFloat()
            gui.pose().pushPose()
            gui.pose().translate(screenX.toDouble(),y.toDouble(),0.0)
            gui.pose().scale(scale,scale,1f)
            gui.fill(0,rowY-2,158,rowY+30,0x70101C18)
            drawRow(gui,row.definition,rowY,if(row.selected)row.definition else null,emptyList(),emptyList(),false,false,partial,true)
            gui.pose().popPose()
            rows[id]=HudLayout(screenX,y+(rowY-2)*scale,158*scale,32*scale)
        }
        lastAutomaticRows = rows
    }

    private fun drawRow(gui: GuiGraphics, def: StratagemDefinition, y: Int, selected: StratagemDefinition?,
                        visiblePrefix: List<Int>, candidates: List<StratagemDefinition>, inputError: Boolean,
                        failure: Boolean, partial: Float, automatic: Boolean = false) {
        val mc = Minecraft.getInstance()
        val panelX = 0; val panelWidth = 158; val textX = 32
        StratagemCooldownClient.drawIcon(gui, def, panelX + 3, y + 2, partial)
        val status = StratagemCooldownClient.visual(def.id, partial)
        if (status.remaining >= 0 && def.id != "eagle_rearm") {
            val uses = status.remaining.toString()
            val textWidth = mc.font.width(uses) - 1
            val boxWidth = maxOf(9, textWidth + 3)
            val boxX = panelX + 31 - boxWidth
            gui.fill(boxX, y + 20, panelX + 31, y + 31, 0xFFFFFFFF.toInt())
            gui.drawString(mc.font, uses, boxX + (boxWidth - textWidth) / 2, y + 22, 0xFF202020.toInt(), false)
        }
        val enabled = StratagemCooldownClient.available(def.id) && (visiblePrefix.isEmpty() || def in candidates || selected == def)
        val label = Component.translatable(def.translation).string
        if(def==Stratagems.destroyerDirection||def==Stratagems.destroyerManage){
            gui.pose().pushPose();gui.pose().translate(textX.toDouble(),y.toDouble(),0.0)
            gui.drawString(mc.font,Component.translatable(def.translation),0,0,0xF4F4EF,true)
            val options=if(def==Stratagems.destroyerDirection)listOf(0 to "north",3 to "west",1 to "east",2 to "south") else listOf(3 to "depart",1 to "direction")
            options.forEachIndexed { i, (arrow, key) ->
                val width = if (options.size == 4) 30 else 58
                val x = i * width
                ArrowGlyph.draw(gui, x, 18, arrow, 0xFFE6ECE6.toInt(), 1)
                val text = Component.translatable("hud.sbw_helldiver.destroyer_$key")
                val scale = minOf(1f, (width - 11f) / mc.font.width(text).coerceAtLeast(1))
                gui.pose().pushPose()
                gui.pose().translate((x + 9).toDouble(), 17.0, 0.0)
                gui.pose().scale(scale, scale, 1f)
                gui.drawString(mc.font, text, 0, 0, 0xE6ECE6, true)
                gui.pose().popPose()
            }
            gui.pose().popPose();return
        }
        val maxLabelWidth = ((panelWidth - 40) / 1.5f).toInt()
        val fittedLabel = if (mc.font.width(label) <= maxLabelWidth) label
            else mc.font.plainSubstrByWidth(label, maxLabelWidth - mc.font.width("…")) + "…"
        if(def==Stratagems.destroyerCall){
            gui.pose().pushPose();gui.pose().translate(textX.toDouble(),y.toDouble(),0.0)
            val labelScale=minOf(1.5f,120f/mc.font.width(label).coerceAtLeast(1));gui.pose().scale(labelScale,labelScale,1f)
            gui.drawString(mc.font,label,0,0,if(enabled)0xF4F4EF else 0x95A79B,true);gui.pose().popPose()
        }else drawHudText(gui, Component.literal(fittedLabel), textX, y, if (enabled) 0xF4F4EF else 0x95A79B)
        if (failure) {
            drawHudText(gui, Component.translatable("message.sbw_helldiver.deployment_failed"), textX, y + 17, 0xF07668)
            return
        }
        val statusText = if (automatic && selected == def && !status.blocked && status.phase == com.example.sbwhelldiver.stratagem.CallPhase.READY) Component.translatable("hud.sbw_helldiver.ready") else StratagemCooldownClient.text(status, partial)
        if (statusText != null) {
            gui.pose().pushPose()
            gui.pose().translate(textX.toDouble(), (y + 17).toDouble(), 0.0)
            gui.drawString(mc.font, statusText, 0, 0, 0xE8E4CC, true)
            gui.pose().popPose()
            return
        }
        gui.pose().pushPose()
        gui.pose().translate(textX.toDouble(), (y + 17).toDouble(), 0.0)
        val arrowScale=minOf(1.5f,120f/(def.code.size.coerceAtLeast(1)*12));gui.pose().scale(arrowScale,arrowScale,1f)
        val revealed = if (automatic) StratagemNotifications.revealedArrows(def, partial) ?: def.code.size else def.code.size
        def.code.forEachIndexed { index, direction ->
            if (index >= revealed) return@forEachIndexed
            val color = if (inputError) 0xFFF07668.toInt()
                else if (selected == def || (def in candidates && index < visiblePrefix.size)) 0xFF82FFD0.toInt()
                else if (enabled) 0xFFE6ECE6.toInt() else 0xFF56675F.toInt()
            ArrowGlyph.draw(gui, index * 12, 0, direction, color, 1)
        }
        gui.pose().popPose()
    }

    private fun drawHudText(gui: GuiGraphics, text: Component, x: Int, y: Int, color: Int) {
        gui.pose().pushPose()
        gui.pose().translate(x.toDouble(), y.toDouble(), 0.0)
        gui.pose().scale(1.5f, 1.5f, 1f)
        gui.drawString(Minecraft.getInstance().font, text, 0, 0, color, true)
        gui.pose().popPose()
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = [Dist.CLIENT])
    object Registration {
        @SubscribeEvent fun keys(event: RegisterKeyMappingsEvent) { event.register(ALT) }
        @SubscribeEvent fun overlay(event: RegisterGuiOverlaysEvent) {
            event.registerAboveAll("stratagems") { _, gui, partial, _, _ -> hud(gui, partial) }
            event.registerAboveAll("beacon_call") { _, gui, partial, _, _ -> BeaconCallHud.draw(gui, partial) }
        }
    }
}
