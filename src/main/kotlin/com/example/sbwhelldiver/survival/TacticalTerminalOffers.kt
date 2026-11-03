package com.example.sbwhelldiver.survival

/** Sample costs are per purchase, including repeat purchases of a stratagem. */
data class SampleCost(val common:Int, val rare:Int=0, val superSample:Int=0)
enum class TerminalCategory { ORBITAL, EAGLE, SENTRY, DEFENSE }
data class TerminalOffer(val id:String, val category:TerminalCategory, val cost:SampleCost)

object TacticalTerminalOffers {
    val destroyerCall = SampleCost(50, 15)
    private fun offer(category:TerminalCategory, id:String, common:Int, rare:Int=0, superSample:Int=0) =
        TerminalOffer(id, category, SampleCost(common, rare, superSample))
    val all = listOf(
        offer(TerminalCategory.ORBITAL,"orbital_precision_strike",50,15),
        offer(TerminalCategory.ORBITAL,"orbital_smoke_strike",65,15),
        offer(TerminalCategory.ORBITAL,"orbital_ems_strike",65,15),
        offer(TerminalCategory.ORBITAL,"orbital_gas_strike",75,15),
        offer(TerminalCategory.ORBITAL,"orbital_gatling_barrage",75,15),
        offer(TerminalCategory.ORBITAL,"orbital_airburst_strike",90,15),
        offer(TerminalCategory.ORBITAL,"orbital_120mm_he_barrage",125,18,5),
        offer(TerminalCategory.ORBITAL,"orbital_railcannon_strike",110,15,5),
        offer(TerminalCategory.ORBITAL,"orbital_380mm_he_barrage",150,20,5),
        offer(TerminalCategory.ORBITAL,"orbital_walking_barrage",150,20,5),
        offer(TerminalCategory.ORBITAL,"orbital_napalm_barrage",160,22,5),
        offer(TerminalCategory.ORBITAL,"orbital_laser",180,25,5),
        offer(TerminalCategory.EAGLE,"eagle_strafing_run",50,15),
        offer(TerminalCategory.EAGLE,"eagle_smoke_strike",50,15),
        offer(TerminalCategory.EAGLE,"eagle_airstrike",80,15),
        offer(TerminalCategory.EAGLE,"eagle_cluster_bomb",80,15),
        offer(TerminalCategory.EAGLE,"eagle_napalm_airstrike",110,15,5),
        offer(TerminalCategory.EAGLE,"eagle_gas_strike",110,15,5),
        offer(TerminalCategory.EAGLE,"eagle_110mm_rocket_pods",90,15),
        offer(TerminalCategory.EAGLE,"eagle_500kg_bomb",150,20,5),
        offer(TerminalCategory.SENTRY,"machine_gun_sentry",50,15),
        offer(TerminalCategory.SENTRY,"gatling_sentry",75,15),
        offer(TerminalCategory.SENTRY,"mortar_sentry",75,15),
        offer(TerminalCategory.SENTRY,"flame_sentry",150,20,5),
        offer(TerminalCategory.SENTRY,"gas_mortar_sentry",90,15),
        offer(TerminalCategory.SENTRY,"ems_mortar_sentry",100,15),
        offer(TerminalCategory.SENTRY,"tesla_tower",100,15),
        offer(TerminalCategory.SENTRY,"rocket_sentry",150,20,5),
        offer(TerminalCategory.SENTRY,"autocannon_sentry",150,20,5),
        offer(TerminalCategory.SENTRY,"laser_sentry",90,15),
        offer(TerminalCategory.DEFENSE,"anti_personnel_minefield",50,15),
        offer(TerminalCategory.DEFENSE,"incendiary_minefield",70,15),
        offer(TerminalCategory.DEFENSE,"gas_minefield",70,15),
        offer(TerminalCategory.DEFENSE,"hmg_emplacement",110,15,5),
        offer(TerminalCategory.DEFENSE,"shield_generator",75,15),
        offer(TerminalCategory.DEFENSE,"anti_tank_minefield",70,15),
        offer(TerminalCategory.DEFENSE,"anti_tank_emplacement",150,20,5),
        offer(TerminalCategory.DEFENSE,"grenadier_battlement",110,15,5),
    )
    val byId = all.associateBy { it.id }
}

/** Visual easing only; purchase authorization must still use the full server-side duration. */
object TacticalTerminalHold {
    // Original shop recording: hold starts at 7.550 s, completion at 8.873 s.
    const val DURATION_TICKS = 27L
    fun maskProgress(elapsedTicks:Double):Double {
        val t = (elapsedTicks / DURATION_TICKS).coerceIn(0.0, 1.0)
        return 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t)
    }
}
