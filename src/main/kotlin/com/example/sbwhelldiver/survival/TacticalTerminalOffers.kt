package com.example.sbwhelldiver.survival

/** Sample costs are per purchase, including repeat purchases of a stratagem. */
data class SampleCost(val common:Int, val rare:Int=0, val superSample:Int=0)
enum class TerminalCategory { ORBITAL, EAGLE, SENTRY, DEFENSE }
data class TerminalOffer(val id:String, val category:TerminalCategory, val cost:SampleCost)

object TacticalTerminalOffers {
    val destroyerCall = SampleCost(50, 5)
    private fun offer(category:TerminalCategory, id:String, common:Int, rare:Int=0, superSample:Int=0) =
        TerminalOffer(id, category, SampleCost(common, rare, superSample))
    val all = listOf(
        offer(TerminalCategory.ORBITAL,"orbital_precision_strike",50,5),
        offer(TerminalCategory.ORBITAL,"orbital_smoke_strike",65,8),
        offer(TerminalCategory.ORBITAL,"orbital_ems_strike",65,8),
        offer(TerminalCategory.ORBITAL,"orbital_gas_strike",75,10),
        offer(TerminalCategory.ORBITAL,"orbital_gatling_barrage",75,10),
        offer(TerminalCategory.ORBITAL,"orbital_airburst_strike",90,12),
        offer(TerminalCategory.ORBITAL,"orbital_120mm_he_barrage",110,15,1),
        offer(TerminalCategory.ORBITAL,"orbital_railcannon_strike",125,18,2),
        offer(TerminalCategory.ORBITAL,"orbital_380mm_he_barrage",150,20,3),
        offer(TerminalCategory.ORBITAL,"orbital_walking_barrage",150,20,3),
        offer(TerminalCategory.ORBITAL,"orbital_napalm_barrage",160,22,3),
        offer(TerminalCategory.ORBITAL,"orbital_laser",180,25,5),
        offer(TerminalCategory.EAGLE,"eagle_strafing_run",50,5),
        offer(TerminalCategory.EAGLE,"eagle_smoke_strike",50,6),
        offer(TerminalCategory.EAGLE,"eagle_airstrike",80,10),
        offer(TerminalCategory.EAGLE,"eagle_cluster_bomb",80,10),
        offer(TerminalCategory.EAGLE,"eagle_napalm_airstrike",90,12),
        offer(TerminalCategory.EAGLE,"eagle_gas_strike",90,12),
        offer(TerminalCategory.EAGLE,"eagle_110mm_rocket_pods",110,15,1),
        offer(TerminalCategory.EAGLE,"eagle_500kg_bomb",150,20,3),
        offer(TerminalCategory.SENTRY,"machine_gun_sentry",50,5),
        offer(TerminalCategory.SENTRY,"gatling_sentry",75,8),
        offer(TerminalCategory.SENTRY,"mortar_sentry",75,8),
        offer(TerminalCategory.SENTRY,"flame_sentry",90,10),
        offer(TerminalCategory.SENTRY,"gas_mortar_sentry",90,10),
        offer(TerminalCategory.SENTRY,"ems_mortar_sentry",100,12),
        offer(TerminalCategory.SENTRY,"tesla_tower",100,12),
        offer(TerminalCategory.SENTRY,"rocket_sentry",125,15,1),
        offer(TerminalCategory.SENTRY,"autocannon_sentry",125,15,1),
        offer(TerminalCategory.SENTRY,"laser_sentry",150,20,3),
        offer(TerminalCategory.DEFENSE,"anti_personnel_minefield",50,5),
        offer(TerminalCategory.DEFENSE,"incendiary_minefield",70,8),
        offer(TerminalCategory.DEFENSE,"gas_minefield",70,8),
        offer(TerminalCategory.DEFENSE,"hmg_emplacement",75,8),
        offer(TerminalCategory.DEFENSE,"shield_generator",110,15,1),
        offer(TerminalCategory.DEFENSE,"anti_tank_minefield",125,15,1),
        offer(TerminalCategory.DEFENSE,"anti_tank_emplacement",125,15,1),
        offer(TerminalCategory.DEFENSE,"grenadier_battlement",125,15,1),
    )
    val byId = all.associateBy { it.id }
}

/** Visual easing only; purchase authorization must still use the full server-side duration. */
object TacticalTerminalHold {
    const val DURATION_TICKS = 60L
    fun maskProgress(elapsedTicks:Double):Double {
        val t = (elapsedTicks / DURATION_TICKS).coerceIn(0.0, 1.0)
        return 1.0 - (1.0 - t) * (1.0 - t) * (1.0 - t)
    }
}
