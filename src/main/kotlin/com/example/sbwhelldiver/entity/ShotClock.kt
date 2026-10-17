package com.example.sbwhelldiver.entity

/** Losing aim cannot bank a burst; fractional RPM still permits multiple rounds per tick. */
class ShotClock {
    private var credit = 0.0
    fun reset() { credit = 0.0 }
    fun advance(rpm: Int): Int {
        credit = (credit + rpm.coerceIn(0, 4800) / 1200.0).coerceAtMost(4.0)
        val count = (credit + 1e-9).toInt()
        credit -= count
        return count
    }
}
