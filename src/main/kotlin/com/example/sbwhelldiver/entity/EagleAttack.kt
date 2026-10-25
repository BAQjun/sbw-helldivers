package com.example.sbwhelldiver.entity

/** Decoded EagleComponent attack timing/direction; Minecraft release heights are terrain fitted. */
enum class EagleAttack(
    val id: String, val perpendicular: Boolean, val levelBombing: Boolean,
    val callTicks: Int, val duration: Int, val count: Int, val interval: Double,
    val spread: Double, val defaultUses: Int
) {
    AIRSTRIKE("eagle_airstrike", true, true, 82, 30, 6, 4.0, 60.0, 3),
    STRAFE("eagle_strafing_run", false, false, 48, 30, 100, .3, 60.0, 5),
    CLUSTER("eagle_cluster_bomb", true, true, 82, 30, 8, 2.0, 50.0, 5),
    ROCKETS("eagle_110mm_rocket_pods", false, false, 82, 40, 6, 10.0, 10.0, 4),
    HEAVY_BOMB("eagle_500kg_bomb", false, true, 82, 30, 1, 1.0, 0.0, 2),
    NAPALM("eagle_napalm_airstrike", true, true, 82, 30, 5, 4.0, 42.0, 3),
    GAS("eagle_gas_strike", true, true, 82, 30, 4, 4.0, 36.0, 3),
    SMOKE("eagle_smoke_strike", true, true, 82, 40, 9, 4.0, 40.0, 3);

    val height get() = if (levelBombing) EagleFlight.HEIGHT else
        (if (this == ROCKETS) 350.0 else 600.0) * kotlin.math.sin(Math.toRadians(EagleFlight.ENTRY_PITCH))
    fun releaseAge(index: Int): Double = callTicks + if (this == ROCKETS) (index / 2) * interval else index * interval
    fun offset(index: Int): Double = if (count == 1) 0.0 else -spread / 2 + spread * index / (count - 1)
    /** Full upgraded patterns are centered on the beacon, including the extra canister.
     * Native spacing is retained; mirrored lateral offsets prevent duplicate impacts. */
    fun landingOffset(index: Int, forward: net.minecraft.world.phys.Vec3): net.minecraft.world.phys.Vec3 {
        val point = fieldPatterns[this]?.get(index) ?: (offset(index) to 0.0)
        return forward.scale(point.first).add(net.minecraft.world.phys.Vec3(-forward.z,0.0,forward.x).scale(point.second))
    }
    companion object {
        private val fieldPatterns = mapOf(
            NAPALM to listOf(-21.0 to 1.0,-7.0 to -1.0,7.0 to 1.0,21.0 to -1.0,0.0 to 0.0),
            GAS to listOf(-18.0 to -2.0,-6.0 to 2.0,6.0 to -2.0,18.0 to 2.0),
            SMOKE to listOf(-20.0 to 5.0,-15.0 to -5.0,-10.0 to 5.0,-5.0 to -5.0,0.0 to 0.0,5.0 to 5.0,10.0 to -5.0,15.0 to 5.0,20.0 to -5.0)
        )
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: AIRSTRIKE
        fun legacy(bombing: Boolean) = if (bombing) AIRSTRIKE else STRAFE
    }
}
