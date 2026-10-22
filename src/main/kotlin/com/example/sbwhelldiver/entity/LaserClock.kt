package com.example.sbwhelldiver.entity

import net.minecraft.nbt.CompoundTag

/** One tick clock owns beam readiness, heat, alarm and the irreversible fuse. */
class LaserClock {
    enum class State { OFF, WARMING, FIRING, OVERLOADED }
    var state = State.OFF
        private set
    var heat = 0.0
        private set
    var warmupLeft = 0
        private set
    var fuseLeft = 0
        private set
    private var damageTick = 0.0
    var damagePulse = false
        private set
    val alarm get() = heat >= WARNING_HEAT - 1e-6
    val destroyNow get() = state == State.OVERLOADED && fuseLeft <= 0

    fun advance(hasTarget: Boolean, aligned: Boolean, coolingPerSecond: Double): Boolean {
        damagePulse = false
        if (state == State.OVERLOADED) { fuseLeft--; return false }
        if (!hasTarget) {
            state = State.OFF; warmupLeft = 0; damageTick = 0.0
            heat = (heat - coolingPerSecond / 20.0).coerceAtLeast(0.0)
            return false
        }
        if (state == State.OFF) { state = State.WARMING; warmupLeft = WARMUP_TICKS }
        if (warmupLeft > 0) {
            warmupLeft--
            heat = (heat - coolingPerSecond / 20.0).coerceAtLeast(0.0)
            return false
        }
        if (state != State.FIRING && !aligned) {
            heat = (heat - coolingPerSecond / 20.0).coerceAtLeast(0.0)
            return false
        }
        state = State.FIRING
        heat = (heat + HEAT_PER_SECOND / 20.0).coerceAtMost(MAX_HEAT)
        if (heat >= MAX_HEAT - 1e-6) {
            heat = MAX_HEAT; state = State.OVERLOADED; fuseLeft = FUSE_TICKS
            return false
        }
        if (damageTick <= 1e-9) {
            damagePulse = true
            damageTick += 1200.0 / settings.rpm.get()
        }
        damageTick -= 1.0
        return true
    }
    fun stop() { if (state != State.OVERLOADED) { state = State.OFF; warmupLeft = 0; damageTick = 0.0; damagePulse = false } }
    fun save(tag: CompoundTag) {
        tag.putInt("LaserState",state.ordinal);tag.putDouble("LaserHeat",heat)
        tag.putInt("LaserWarmup",warmupLeft);tag.putInt("LaserFuse",fuseLeft)
        tag.putDouble("LaserPulseCooldown",damageTick)
    }
    fun restore(tag: CompoundTag) {
        state = State.entries[tag.getInt("LaserState").coerceIn(0,State.entries.lastIndex)]
        heat = tag.getDouble("LaserHeat").takeIf { it.isFinite() }?.coerceIn(0.0,MAX_HEAT) ?: 0.0
        warmupLeft = tag.getInt("LaserWarmup").coerceIn(0,WARMUP_TICKS)
        fuseLeft = tag.getInt("LaserFuse").coerceIn(0,FUSE_TICKS)
        damageTick = if (tag.contains("LaserPulseCooldown")) (tag.getDouble("LaserPulseCooldown").takeIf {it.isFinite()} ?: 0.0).coerceIn(-1.0,1200.0)
            else tag.getInt("LaserDamageTick").let { if (it <= 0) 0.0 else (DAMAGE_INTERVAL_TICKS-it).toDouble().coerceIn(0.0,DAMAGE_INTERVAL_TICKS.toDouble()) }
        if (heat >= MAX_HEAT - 1e-6 && state != State.OVERLOADED) { state=State.OVERLOADED;fuseLeft=FUSE_TICKS }
    }
    companion object {
        const val DAMAGE_INTERVAL_TICKS = 5
        private val settings get() = com.example.sbwhelldiver.config.TurretConfigs.get("laser_sentry")
        val DIRECT_DAMAGE get() = settings.damage.get().toFloat()
        const val WARMUP_TICKS = 10
        const val FUSE_TICKS = 20
        val MAX_HEAT get() = settings.maxHeat!!.get()
        val WARNING_HEAT get() = MAX_HEAT * .8
        val HEAT_PER_SECOND get() = settings.heatPerSecond!!.get()
        val SEEK_RANGE get() = settings.range!!.get()
        val BEAM_RANGE get() = settings.effectiveBeamRange()
        fun coolingRate(nether: Boolean, temperature: Float) = when {
            nether || temperature >= 1.0f -> 3.5
            temperature <= .15f -> 7.5
            else -> 5.0
        }
    }
}
