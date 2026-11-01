package com.example.sbwhelldiver.survival

import com.google.gson.JsonParser
import net.minecraft.world.phys.Vec3

/** Exported from the accepted model, shared by screen hit detection and rendering. */
object EquipmentBeaconGeometry {
    private val data=javaClass.classLoader.getResourceAsStream("assets/sbw_helldiver/helldivers/equipment_beacon.json")!!.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    private fun vector(key:String)=data.getAsJsonArray(key).let { Vec3(it[0].asDouble,it[1].asDouble,it[2].asDouble) }
    val center=vector("center");val normal=vector("normal");val up=vector("up");val right=vector("right")
    val width=data["width"].asDouble;val height=data["height"].asDouble
    val screenYaw=Math.toDegrees(kotlin.math.atan2(-normal.x,normal.z)).toFloat()
    fun deploymentYaw(throwPoint:Vec3,landing:Vec3,fallbackYaw:Float):Float {
        val toPlayer=throwPoint.subtract(landing)
        val wanted=if(toPlayer.horizontalDistanceSqr()<1e-10)fallbackYaw+180f else Math.toDegrees(kotlin.math.atan2(-toPlayer.x,toPlayer.z)).toFloat()
        val cardinal=kotlin.math.floor((wanted+45f)/90f)*90f
        // The authored screen faces diagonally in model space; compensate before snapping.
        return net.minecraft.util.Mth.wrapDegrees(cardinal-screenYaw)
    }
}
