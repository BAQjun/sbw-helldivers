package com.example.sbwhelldiver.survival

import com.google.gson.JsonParser
import net.minecraft.world.phys.Vec3

/** Exported from the accepted model, shared by screen hit detection and rendering. */
object EquipmentBeaconGeometry {
    private val data=javaClass.classLoader.getResourceAsStream("assets/sbw_helldiver/helldivers/equipment_beacon.json")!!.bufferedReader().use { JsonParser.parseReader(it).asJsonObject }
    private fun vector(key:String)=data.getAsJsonArray(key).let { Vec3(it[0].asDouble,it[1].asDouble,it[2].asDouble) }
    val center=vector("center");val normal=vector("normal");val up=vector("up");val right=vector("right")
    val width=data["width"].asDouble;val height=data["height"].asDouble
    val beam=vector("beam")
    val liftTravel=data["lift_travel"].asDouble
    val collision=data.getAsJsonArray("collision").map{raw->val a=raw.asJsonArray;val lo=a[0].asJsonArray;val hi=a[1].asJsonArray
        net.minecraft.world.phys.AABB(lo[0].asDouble,lo[1].asDouble,lo[2].asDouble,hi[0].asDouble,hi[1].asDouble,hi[2].asDouble)
    }
    val screenYaw=Math.toDegrees(kotlin.math.atan2(-normal.x,normal.z)).toFloat()
    val nozzles=data.getAsJsonArray("nozzles").map{raw->val n=raw.asJsonObject
        fun v(key:String)=n.getAsJsonArray(key).let{Vec3(it[0].asDouble,it[1].asDouble,it[2].asDouble)}
        v("position") to v("direction")
    }
    fun deploymentYaw(throwPoint:Vec3,landing:Vec3,fallbackYaw:Float):Float {
        val toPlayer=throwPoint.subtract(landing)
        val wanted=if(toPlayer.horizontalDistanceSqr()<1e-10)fallbackYaw+180f else Math.toDegrees(kotlin.math.atan2(-toPlayer.x,toPlayer.z)).toFloat()
        val cardinal=kotlin.math.floor((wanted+45f)/90f)*90f
        // The authored screen faces diagonally in model space; compensate before snapping.
        return net.minecraft.util.Mth.wrapDegrees(cardinal-screenYaw)
    }
}
