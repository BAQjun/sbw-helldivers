package com.example.sbwhelldiver.stratagem

import com.example.sbwhelldiver.config.StratagemCooldownConfig
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import java.util.UUID

// Keep the original four ordinals stable; the appended values are presentation overlays.
enum class CallPhase { READY, THROWN, ARRIVING, COOLDOWN, ACTIVE, ARRIVED }
data class CallStatus(val id: String, var phase: CallPhase = CallPhase.READY,
    var start: Long = 0, var end: Long = 0, var remaining: Int = -1, var request: UUID? = null,
    var activityStart: Long = -1, var activityEnd: Long = -1, var activityFinished: Long = -1,
    var activityToken: UUID? = null) {
    /** Presentation overlays the existing cooldown; it never resets or delays that cooldown. */
    fun activity(now: Double): CallPhase? = activityPhase(activityStart, activityFinished, now)
    companion object {
        fun activityPhase(start: Long, finished: Long, now: Double): CallPhase? = when {
            start < 0 -> null
            finished < 0 -> CallPhase.ACTIVE
            now < finished + 60 -> CallPhase.ARRIVED
            else -> null
        }
    }
}

/** Absolute overworld game times survive logout, dimension changes and server restarts. */
class StratagemCooldownState {
    var enabled = true; private set
    val entries = linkedMapOf<String, CallStatus>()
    var rearmStart = 0L; private set
    var rearmEnd = 0L; private set
    var turnaroundStart = 0L; private set
    var turnaroundEnd = 0L; private set
    var equippedEagles: Set<String> = emptySet(); private set
    var revision = 0L; private set
    private fun changed() { revision++ }
    fun entry(id: String) = entries.getOrPut(id) { CallStatus(id, remaining = StratagemCooldownConfig.maxUses(id)).also { changed() } }
    fun equip(ids: Collection<String>) {
        val next = ids.filter { it.startsWith("eagle_") && it != "eagle_rearm" }.toSet()
        if (next != equippedEagles) { equippedEagles = next; next.forEach(::entry); changed() }
    }
    fun rearming(now: Long) = rearmEnd > now
    fun advance(now: Long) {
        if (rearmEnd != 0L && now >= rearmEnd) {
            entries.values.filter { it.id.startsWith("eagle_") }.forEach { it.remaining = StratagemCooldownConfig.maxUses(it.id) }
            rearmStart = 0; rearmEnd = 0; changed()
        }
        for (e in entries.values) {
            if (e.phase == CallPhase.THROWN && now >= e.end) { e.phase = CallPhase.READY; e.request = null; changed() }
            if (e.phase == CallPhase.ARRIVING && now >= e.end) {
                val arrivedAt = e.end
                e.phase = if (enabled) CallPhase.COOLDOWN else CallPhase.READY
                e.start = arrivedAt; e.end = arrivedAt + if (enabled) StratagemCooldownConfig.ticks(e.id) else 0
                if (e.id.startsWith("eagle_") && !rearming(now)) {
                    turnaroundStart = arrivedAt; turnaroundEnd = e.end
                }
                changed()
            }
            if (e.phase == CallPhase.COOLDOWN && now >= e.end) { e.phase = CallPhase.READY; e.request = null; changed() }
        }
        if (enabled && rearmEnd == 0L && equippedEagles.isNotEmpty() &&
            equippedEagles.all { entry(it).remaining <= 0 } &&
            entries.values.none { it.id.startsWith("eagle_") && it.phase in listOf(CallPhase.THROWN, CallPhase.ARRIVING) }) {
            val lastArrival = equippedEagles.maxOf { entry(it).start }
            startRearm(lastArrival.coerceAtMost(now), false)
            // An offline player can return after both the arrival and complete rearm.
            if (rearmEnd != 0L && now >= rearmEnd) advance(now)
        }
    }
    fun available(id: String, now: Long): Boolean {
        advance(now)
        if (!enabled) return true
        val e = entry(id)
        if (e.phase != CallPhase.READY) return false
        if (!id.startsWith("eagle_")) return e.remaining != 0
        return !rearming(now) && turnaroundEnd <= now && e.remaining > 0 &&
            entries.values.none { it.id.startsWith("eagle_") && it.phase in listOf(CallPhase.THROWN, CallPhase.ARRIVING) }
    }
    fun reserve(id: String, token: UUID, now: Long): Boolean {
        if (!available(id, now)) return false
        if (!enabled) return true
        entry(id).apply { activityStart = -1; activityEnd = -1; activityFinished = -1; activityToken = null; phase = CallPhase.THROWN; start = now; end = now + 1200; request = token }
        changed(); return true
    }
    fun beginArrival(id: String, token: UUID, now: Long, arrival: Long): Boolean {
        val e = entries[id] ?: return !enabled
        if (e.request != token || e.phase !in listOf(CallPhase.THROWN, CallPhase.ARRIVING)) return !enabled
        // Railcannon has no inbound/arrived stage: its beacon starts cooldown on landing.
        // Repeated marker timing updates must never restart an already-running cooldown.
        if (id == "orbital_railcannon_strike") {
            e.phase = if (enabled) CallPhase.COOLDOWN else CallPhase.READY
            e.start = now; e.end = now + if (enabled) StratagemCooldownConfig.ticks(id) else 0
            changed(); return true
        }
        if (e.phase == CallPhase.THROWN) {
            if ((id.startsWith("eagle_") || id=="orbital_laser") && enabled) e.remaining = (e.remaining - 1).coerceAtLeast(0)
            e.phase = CallPhase.ARRIVING; e.start = now; e.end = arrival; changed()
        } else if (e.end != arrival) { e.end = arrival; changed() }
        return true
    }
    fun beginActivity(id: String, token: UUID, start: Long, end: Long) {
        entry(id).apply {
            activityStart = start; activityEnd = end; activityFinished = -1; activityToken = token
        }
        changed()
    }
    fun finishActivity(id: String, token: UUID, now: Long) {
        val e = entries[id] ?: return
        if (e.activityToken != token || e.activityFinished >= 0) return
        e.activityEnd = now; e.activityFinished = now; changed()
    }
    fun cancel(id: String, token: UUID) {
        val e = entries[id] ?: return
        if(e.activityToken == token) {
            e.activityStart = -1; e.activityEnd = -1; e.activityFinished = -1; e.activityToken = null; changed()
        }
        if (e.request != token || e.phase !in listOf(CallPhase.THROWN, CallPhase.ARRIVING)) return
        if (e.phase == CallPhase.ARRIVING && (id.startsWith("eagle_") || id=="orbital_laser")) e.remaining = (e.remaining + 1).coerceAtMost(StratagemCooldownConfig.maxUses(id))
        e.phase = CallPhase.READY; e.request = null; changed()
    }
    private fun startRearm(now: Long, hasOrdnance: Boolean) {
        rearmStart = now; rearmEnd = now + StratagemCooldownConfig.rearmTicks(hasOrdnance)
        turnaroundStart = now; turnaroundEnd = now
        // Dispatched aircraft can finish, without spending freshly rearmed ordnance.
        entries.values.filter { it.id.startsWith("eagle_") }.forEach { it.phase = CallPhase.READY; it.request = null }
        if (rearmEnd == now) {
            entries.values.filter { it.id.startsWith("eagle_") }.forEach { it.remaining = StratagemCooldownConfig.maxUses(it.id) }
            rearmStart = 0; rearmEnd = 0
        }
        changed()
    }
    fun rearm(now: Long): Boolean {
        advance(now)
        if (!enabled || equippedEagles.isEmpty() || rearming(now)) return false
        startRearm(now, equippedEagles.any { entry(it).remaining > 0 }); advance(now); return true
    }
    /** A completed ship resupply restores charges only, without restarting calls or cooldowns. */
    fun restoreAllUses() {
        var restored = false
        for (e in entries.values) {
            val maximum = StratagemCooldownConfig.maxUses(e.id)
            if (maximum > 0 && e.remaining != maximum) { e.remaining = maximum; restored = true }
        }
        if (restored) changed()
    }
    fun setEnabled(value: Boolean, now: Long) {
        if (value == enabled) return
        enabled = value
        if (!value) {
            rearmStart = 0; rearmEnd = 0; turnaroundStart = 0; turnaroundEnd = 0
            for (e in entries.values) {
                e.remaining = StratagemCooldownConfig.maxUses(e.id)
                if (e.phase == CallPhase.COOLDOWN) { e.phase = CallPhase.READY; e.request = null }
            }
        }
        changed(); advance(now)
    }
    fun save(): CompoundTag = CompoundTag().also { n ->
        n.putBoolean("Enabled", enabled); n.putLong("RearmStart", rearmStart); n.putLong("RearmEnd", rearmEnd)
        n.putLong("TurnaroundStart", turnaroundStart); n.putLong("TurnaroundEnd", turnaroundEnd)
        n.put("Entries", ListTag().also { list -> entries.values.forEach { e -> list.add(CompoundTag().also {
            it.putString("Id", e.id); it.putInt("Phase", e.phase.ordinal); it.putLong("Start", e.start); it.putLong("End", e.end)
            it.putLong("ActivityStart", e.activityStart); it.putLong("ActivityEnd", e.activityEnd); it.putLong("ActivityFinished", e.activityFinished)
            e.activityToken?.let { token -> it.putUUID("ActivityToken", token) }
            it.putInt("Remaining", e.remaining); e.request?.let { token -> it.putUUID("Request", token) }
        }) } })
        n.put("EquippedEagles", ListTag().also { list -> equippedEagles.forEach { list.add(net.minecraft.nbt.StringTag.valueOf(it)) } })
    }
    companion object {
        fun load(n: CompoundTag) = StratagemCooldownState().also { s ->
            s.enabled = !n.contains("Enabled") || n.getBoolean("Enabled")
            s.rearmStart = n.getLong("RearmStart"); s.rearmEnd = n.getLong("RearmEnd")
            s.turnaroundStart = n.getLong("TurnaroundStart"); s.turnaroundEnd = n.getLong("TurnaroundEnd")
            for (raw in n.getList("Entries", Tag.TAG_COMPOUND.toInt())) {
                val e = raw as CompoundTag; val id = e.getString("Id")
                if (Stratagems.get(id) == null) continue
                s.entries[id] = CallStatus(id, CallPhase.entries.getOrElse(e.getInt("Phase")) { CallPhase.READY }, e.getLong("Start"), e.getLong("End"),
                    e.getInt("Remaining").coerceAtMost(StratagemCooldownConfig.maxUses(id)), if (e.hasUUID("Request")) e.getUUID("Request") else null,
                    if(e.contains("ActivityStart")) e.getLong("ActivityStart") else -1,
                    if(e.contains("ActivityEnd")) e.getLong("ActivityEnd") else -1,
                    if(e.contains("ActivityFinished")) e.getLong("ActivityFinished") else -1,
                    if(e.hasUUID("ActivityToken")) e.getUUID("ActivityToken") else null)
            }
            s.equippedEagles = n.getList("EquippedEagles", Tag.TAG_STRING.toInt()).map { it.asString }.filter { it in StratagemCooldownConfig.baseSeconds && it.startsWith("eagle_") }.toSet()
        }
    }
}
