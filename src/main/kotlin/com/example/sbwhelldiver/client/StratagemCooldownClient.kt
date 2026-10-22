package com.example.sbwhelldiver.client

import com.example.sbwhelldiver.MOD_ID
import com.example.sbwhelldiver.network.CooldownSnapshotPacket
import com.example.sbwhelldiver.stratagem.CallPhase
import com.example.sbwhelldiver.stratagem.StratagemDefinition
import com.example.sbwhelldiver.stratagem.Stratagems
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.Tag
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener
import net.minecraftforge.api.distmarker.Dist
import net.minecraftforge.client.event.ClientPlayerNetworkEvent
import net.minecraftforge.eventbus.api.SubscribeEvent
import net.minecraftforge.fml.common.Mod
import kotlin.math.ceil

@Mod.EventBusSubscriber(modid = MOD_ID, value = [Dist.CLIENT])
object StratagemCooldownClient : ResourceManagerReloadListener {
    data class Visual(val phase: CallPhase = CallPhase.READY, val start: Long = 0, val end: Long = 0,
                      val remaining: Int = -1, val rearming: Boolean = false, val blocked: Boolean = false) {
        fun progress(now: Double) = if (end <= start) 1.0 else ((now - start) / (end - start)).coerceIn(0.0, 1.0)
    }
    private var data = CompoundTag()
    private var serverTime = 0L
    private var localTime = 0L
    private val grey = mutableMapOf<ResourceLocation, ResourceLocation>()
    private var entries = emptyMap<String, CompoundTag>()
    fun receive(packet: CooldownSnapshotPacket) {
        data = packet.state; serverTime = packet.serverTime
        entries = data.getList("Entries", Tag.TAG_COMPOUND.toInt()).associate { raw -> val e = raw as CompoundTag; e.getString("Id") to e }
        localTime = Minecraft.getInstance().level?.gameTime ?: 0
    }
    @SubscribeEvent fun logout(event: ClientPlayerNetworkEvent.LoggingOut) { data = CompoundTag(); entries = emptyMap(); serverTime = 0; localTime = 0 }
    fun commands(loadout: List<StratagemDefinition?>) = Stratagems.commands(loadout,
        !data.getList("EquippedEagles", Tag.TAG_STRING.toInt()).isEmpty() || loadout.any { it?.id?.startsWith("eagle_") == true })
    fun now(partial: Float = 0f) = serverTime + ((Minecraft.getInstance().level?.gameTime ?: localTime) - localTime).toDouble() + partial
    fun visual(id: String, partial: Float = 0f): Visual {
        if (!data.contains("Enabled") || !data.getBoolean("Enabled")) return Visual()
        val time = now(partial)
        val e = entries[id]
        val eagle = id.startsWith("eagle_")
        if (eagle && data.getLong("RearmEnd") > time) return Visual(CallPhase.COOLDOWN, data.getLong("RearmStart"), data.getLong("RearmEnd"), e?.getInt("Remaining") ?: -1, true, true)
        if (id == "eagle_rearm") return Visual()
        val remaining = e?.getInt("Remaining") ?: -1
        val phase = e?.let { CallPhase.entries.getOrElse(it.getInt("Phase")) { CallPhase.READY } } ?: CallPhase.READY
        val end = e?.getLong("End") ?: 0
        if (phase == CallPhase.THROWN) return Visual(phase, remaining = remaining, blocked = true)
        if (phase == CallPhase.ARRIVING && end > time) return Visual(phase, e!!.getLong("Start"), end, remaining, blocked = true)
        if (eagle && data.getLong("TurnaroundEnd") > time) return Visual(CallPhase.COOLDOWN, data.getLong("TurnaroundStart"), data.getLong("TurnaroundEnd"), remaining, blocked = true)
        if (phase == CallPhase.COOLDOWN && end > time) return Visual(phase, e!!.getLong("Start"), end, remaining, blocked = true)
        val otherEagleInFlight = eagle && entries.values.any { n ->
            n.getString("Id").startsWith("eagle_") && (n.getInt("Phase") == CallPhase.THROWN.ordinal ||
                n.getInt("Phase") == CallPhase.ARRIVING.ordinal && n.getLong("End") > time)
        }
        return Visual(remaining = remaining, blocked = eagle && (remaining == 0 || otherEagleInFlight))
    }
    fun available(id: String) = !visual(id).blocked && (id != "eagle_rearm" || !data.contains("Enabled") || data.getBoolean("Enabled"))
    fun text(v: Visual, partial: Float): Component? {
        val key = when {
            v.rearming -> "rearming"
            v.phase == CallPhase.ARRIVING -> "arrival"
            v.phase == CallPhase.COOLDOWN -> "cooldown"
            v.phase == CallPhase.THROWN -> "waiting_beacon"
            v.remaining == 0 -> "no_uses"
            v.blocked -> "eagle_busy"
            else -> return null
        }
        val seconds = ceil((v.end - now(partial)).coerceAtLeast(0.0) / 20.0).toInt()
        return Component.translatable("hud.sbw_helldiver.$key", "%02d:%02d".format(seconds / 60, seconds % 60))
    }
    fun drawIcon(gui: GuiGraphics, def: StratagemDefinition, x: Int, y: Int, partial: Float) {
        val v = visual(def.id, partial)
        val gray = grey[def.icon] ?: def.icon
        val arrival = v.phase == CallPhase.ARRIVING
        val cooling = v.phase == CallPhase.COOLDOWN
        val base = if (arrival || !v.blocked) def.icon else gray
        gui.blit(base, x, y, 26, 26, 0f, 0f, 64, 64, 64, 64)
        if (arrival || cooling && (v.remaining != 0 || v.rearming)) {
            val h = (v.progress(now(partial)) * 26).toInt().coerceIn(0,26)
            if (h > 0) gui.blit(if (arrival) gray else def.icon, x, y, 26, h, 0f, 0f, 64, (h * 64f / 26).toInt(), 64, 64)
        }
    }
    override fun onResourceManagerReload(manager: ResourceManager) {
        val textures = Minecraft.getInstance().textureManager
        grey.values.forEach(textures::release); grey.clear()
        for (def in Stratagems.definitions + Stratagems.eagleRearm) {
            val resource = manager.getResource(def.icon).orElse(null) ?: continue
            val pixels = resource.open().use(NativeImage::read)
            for (y in 0 until pixels.height) for (x in 0 until pixels.width) {
                val c = pixels.getPixelRGBA(x,y)
                val r = c and 255; val g = c ushr 8 and 255; val b = c ushr 16 and 255
                val l = (r * .2126 + g * .7152 + b * .0722).toInt()
                // Keep the native cream artwork; remove category colour from the background/border.
                val border = ((x in 1..2 || x in pixels.width-3..pixels.width-2) && y in 1 until pixels.height-1) ||
                    ((y in 1..2 || y in pixels.height-3..pixels.height-2) && x in 1 until pixels.width-1)
                val value = if (border || maxOf(r,g,b) - minOf(r,g,b) < 55 && l > 150) 233 else l
                pixels.setPixelRGBA(x,y,(c and -0x1000000) or value or (value shl 8) or (value shl 16))
            }
            val id = ResourceLocation(MOD_ID,"dynamic/cooldowns/${def.id}")
            textures.register(id, DynamicTexture(pixels)); grey[def.icon] = id
        }
    }
}
