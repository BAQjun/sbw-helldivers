package com.example.sbwhelldiver.stratagem

import net.minecraft.world.phys.Vec3

/** Visual flight only: anchors, coverage and the three-second command delay stay authoritative. */
object DestroyerWarp {
    const val ARRIVAL_TICKS = 3L
    const val DEPARTURE_TICKS = 2L
    const val TRAVEL_DISTANCE = 8000.0
    data class Pose(val anchor: DestroyerAnchor, val position: Vec3, val trailLength: Double = 0.0, val motion: Vec3 = forward(anchor.direction), val yaw: Float = 180f-anchor.direction*90f)

    // The mesh faces local -Z and is rendered at 180 - direction*90 degrees.
    fun forward(direction: Int): Vec3 = when (direction) {
        Stratagems.UP -> Vec3(0.0, 0.0, 1.0)
        Stratagems.RIGHT -> Vec3(-1.0, 0.0, 0.0)
        Stratagems.DOWN -> Vec3(0.0, 0.0, -1.0)
        else -> Vec3(1.0, 0.0, 0.0)
    }
    fun arrivalStart(state: DestroyerState) = state.transitionEnd - ARRIVAL_TICKS
    fun departureEnd(state: DestroyerState) = state.transitionEnd - if (state.destination != null) ARRIVAL_TICKS else 0L
    fun directRelocation(state: DestroyerState) = state.anchor != null && state.destination != null && state.anchor!!.dimension == state.destination!!.dimension
    fun departureStart(state: DestroyerState) = if(directRelocation(state)) arrivalStart(state) else departureEnd(state) - DEPARTURE_TICKS

    private fun arrivalTail(age: Double) = if (age in 0.0..1.0) 1600.0 * (1.0 - age) else 0.0

    fun poses(state: DestroyerState, dimension: String, now: Double): List<Pose> {
        if (!Stratagems.dimensionAllowed(dimension)) return emptyList()
        val result = ArrayList<Pose>(1)
        val anchor = state.anchor
        if (state.transitionEnd == 0L) {
            if (anchor?.dimension == dimension) result.add(Pose(anchor, anchor.position, arrivalTail(now - state.appearedAt)))
            return result
        }
        if (directRelocation(state)) {
            val destination = state.destination!!
            if (anchor!!.dimension != dimension) return emptyList()
            val t = ((now-arrivalStart(state))/ARRIVAL_TICKS).coerceIn(0.0,1.0)
            val motion = destination.position.subtract(anchor.position)
            val yawDelta = net.minecraft.util.Mth.wrapDegrees((anchor.direction-destination.direction)*90f)
            return listOf(Pose(destination, anchor.position.lerp(destination.position,t),
                if(t>0 && t<1) minOf(1600.0,motion.length()*.8) else 0.0,
                if(motion.lengthSqr()>1e-8)motion.normalize() else forward(destination.direction),
                180f-anchor.direction*90f+yawDelta*t.toFloat()))
        }
        if (anchor?.dimension == dimension && now < departureEnd(state)) {
            val t = ((now - departureStart(state)) / DEPARTURE_TICKS).coerceIn(0.0, 1.0)
            result.add(Pose(anchor, anchor.position.add(forward(anchor.direction).scale(TRAVEL_DISTANCE * t * t * t)), 2200.0 * t))
        }
        val destination = state.destination
        if (destination?.dimension == dimension && now >= arrivalStart(state)) {
            val t = ((now - arrivalStart(state)) / ARRIVAL_TICKS).coerceIn(0.0, 1.0)
            // Constant high speed into the anchor, then a hard stop; no drifting or rebound.
            result.add(Pose(destination, destination.position.subtract(forward(destination.direction).scale(TRAVEL_DISTANCE * (1.0 - t))), if (now < state.transitionEnd) 1600.0 else arrivalTail(now - state.transitionEnd)))
        }
        return result
    }
}
